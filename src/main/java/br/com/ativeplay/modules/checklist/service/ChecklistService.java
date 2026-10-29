package br.com.ativeplay.modules.checklist.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.checklist.domain.ChecklistItem;
import br.com.ativeplay.modules.checklist.domain.ChecklistSubitem;
import br.com.ativeplay.modules.checklist.repository.ChecklistItemRepository;
import br.com.ativeplay.modules.checklist.repository.ChecklistModeloItemRepository;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.ChecklistItemRequest;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.ChecklistItemResponse;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.Situacao;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.SubitemRequest;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.SubitemResponse;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Datas;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checklist dentro de cada cliente: observações pontuais (ausência de alguma informação,
 * pendência do cliente) com prazo, responsável e sub-itens.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChecklistService {

    /** Pendentes primeiro; prazo mais próximo primeiro (sem prazo por último); depois ordem de criação. */
    private static final Comparator<ChecklistItem> ORDEM = Comparator
            .comparing(ChecklistItem::isConcluido)
            .thenComparing(ChecklistItem::getPrazo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(ChecklistItem::getOrdem);

    private final ChecklistItemRepository itens;
    private final ChecklistModeloItemRepository modelo;
    private final UsuarioRepository usuarios;
    private final EscritorioRepository escritorios;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;

    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> listar(UsuarioAutenticado u, UUID empresaId, Situacao situacao) {
        escopo.garantirEmpresa(u, empresaId);
        var lista = switch (situacao == null ? Situacao.TODOS : situacao) {
            case PENDENTES -> itens.findByEscritorioIdAndEmpresaIdAndConcluido(u.escritorioId(), empresaId, false);
            case CONCLUIDOS -> itens.findByEscritorioIdAndEmpresaIdAndConcluido(u.escritorioId(), empresaId, true);
            case TODOS -> itens.findByEscritorioIdAndEmpresaId(u.escritorioId(), empresaId);
        };
        // o EntityGraph com coleção pode repetir a raiz; distinct por identidade
        var unicos = lista.stream().distinct().sorted(ORDEM).toList();
        return montar(u, unicos);
    }

    public ChecklistItemResponse criar(UsuarioAutenticado u, UUID empresaId, ChecklistItemRequest req) {
        escopo.garantirEmpresa(u, empresaId);
        escopo.garantirUsuarioDaEquipe(u, req.responsavelId());
        var item = new ChecklistItem();
        item.setEscritorioId(u.escritorioId());
        item.setEmpresaId(empresaId);
        item.setCriadoPorId(u.id());
        item.setOrdem(itens.ultimaOrdem(empresaId) + 1);
        aplicar(item, req);
        itens.save(item);
        auditoria.registrar(u, "checklist.criar", "checklist", item.getId(), empresaId,
                "Item de checklist " + item.getTitulo() + " criado");
        avisarResponsavel(u, item, null);
        return montar(u, List.of(item)).getFirst();
    }

    /**
     * Copia o checklist modelo do escritório para uma empresa recém-criada, na mesma transação:
     * sem responsável e sem aviso; prazo = hoje (fuso do escritório) + dias do modelo.
     */
    public void copiarModelo(UsuarioAutenticado u, UUID empresaId) {
        var base = modelo.findByEscritorioIdOrderByOrdem(u.escritorioId()).stream().distinct().toList();
        if (base.isEmpty()) return;
        LocalDate hoje = Datas.hoje(escritorios.findById(u.escritorioId()).map(e -> e.getFusoHorario()).orElse(null));
        var ordem = 0;
        for (var m : base) {
            var item = new ChecklistItem();
            item.setEscritorioId(u.escritorioId());
            item.setEmpresaId(empresaId);
            item.setCriadoPorId(u.id());
            item.setOrdem(ordem++);
            item.setTitulo(m.getTitulo());
            item.setObservacoes(m.getObservacoes());
            item.setPrioridade(m.getPrioridade());
            item.setPrazo(m.getPrazoDias() == null ? null : hoje.plusDays(m.getPrazoDias()));
            for (var ms : m.getSubitens()) {
                var s = new ChecklistSubitem();
                s.setItem(item);
                s.setTexto(ms.getTexto());
                s.setOrdem(ms.getOrdem());
                item.getSubitens().add(s);
            }
            itens.save(item);
        }
    }

    public ChecklistItemResponse atualizar(UsuarioAutenticado u, UUID empresaId, UUID id, ChecklistItemRequest req) {
        var item = buscar(u, empresaId, id);
        if (item.isConcluido() && req.subitens() != null && req.subitens().stream().anyMatch(s -> !s.concluido())) {
            throw ApiException.conflito("Reabra o item antes de alterar os sub-itens");
        }
        escopo.garantirUsuarioDaEquipe(u, req.responsavelId());
        var responsavelAnterior = item.getResponsavelId();
        aplicar(item, req);
        auditoria.registrar(u, "checklist.editar", "checklist", item.getId(), empresaId,
                "Item de checklist " + item.getTitulo() + " editado");
        avisarResponsavel(u, item, responsavelAnterior);
        itens.saveAndFlush(item);
        return montar(u, List.of(item)).getFirst();
    }

    public ChecklistItemResponse concluir(UsuarioAutenticado u, UUID empresaId, UUID id) {
        var item = buscar(u, empresaId, id);
        if (item.isConcluido()) throw ApiException.conflito("O item já está concluído");
        if (item.getSubitens().stream().anyMatch(s -> !s.isConcluido())) {
            throw ApiException.conflito("Conclua todos os sub-itens antes de concluir o item");
        }
        item.concluir(u.id(), Instant.now());
        auditoria.registrar(u, "checklist.concluir", "checklist", item.getId(), empresaId,
                "Item de checklist " + item.getTitulo() + " concluído");
        itens.saveAndFlush(item);
        return montar(u, List.of(item)).getFirst();
    }

    public ChecklistItemResponse reabrir(UsuarioAutenticado u, UUID empresaId, UUID id) {
        var item = buscar(u, empresaId, id);
        if (!item.isConcluido()) throw ApiException.conflito("O item já está pendente");
        item.reabrir();
        auditoria.registrar(u, "checklist.reabrir", "checklist", item.getId(), empresaId,
                "Item de checklist " + item.getTitulo() + " reaberto");
        itens.saveAndFlush(item);
        return montar(u, List.of(item)).getFirst();
    }

    public ChecklistItemResponse marcarSubitem(UsuarioAutenticado u, UUID empresaId, UUID id, UUID subitemId,
            boolean concluido) {
        var item = buscar(u, empresaId, id);
        if (item.isConcluido()) throw ApiException.conflito("Reabra o item antes de alterar os sub-itens");
        var sub = item.getSubitens().stream().filter(s -> s.getId().equals(subitemId)).findFirst()
                .orElseThrow(() -> ApiException.naoEncontrado("Sub-item"));
        sub.setConcluido(concluido);
        itens.saveAndFlush(item);
        return montar(u, List.of(item)).getFirst();
    }

    public void excluir(UsuarioAutenticado u, UUID empresaId, UUID id) {
        var item = buscar(u, empresaId, id);
        auditoria.registrar(u, "checklist.excluir", "checklist", item.getId(), empresaId,
                "Item de checklist " + item.getTitulo() + " excluído");
        itens.delete(item);
    }

    // ------------------------------------------------------------------ apoio

    private ChecklistItem buscar(UsuarioAutenticado u, UUID empresaId, UUID id) {
        escopo.garantirEmpresa(u, empresaId);
        return itens.findByIdAndEscritorioIdAndEmpresaId(id, u.escritorioId(), empresaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Item do checklist"));
    }

    private void aplicar(ChecklistItem item, ChecklistItemRequest req) {
        item.setTitulo(req.titulo().trim());
        item.setObservacoes(req.observacoes() == null || req.observacoes().isBlank() ? null : req.observacoes().trim());
        item.setPrazo(req.prazo());
        item.setPrioridade(req.prioridade() != null ? req.prioridade() : Prioridade.MEDIA);
        item.setResponsavelId(req.responsavelId());
        sincronizarSubitens(item, req.subitens() == null ? List.of() : req.subitens());
    }

    /** Mantém os sub-itens com id, cria os novos e remove (orphanRemoval) os que não vieram. */
    private static void sincronizarSubitens(ChecklistItem item, List<SubitemRequest> pedidos) {
        var existentes = item.getSubitens().stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(ChecklistSubitem::getId, Function.identity()));
        var resultado = new ArrayList<ChecklistSubitem>();
        var ordem = 0;
        for (var p : pedidos) {
            var s = p.id() == null ? null : existentes.get(p.id());
            if (s == null) {
                s = new ChecklistSubitem();
                s.setItem(item);
            }
            s.setTexto(p.texto().trim());
            s.setConcluido(p.concluido());
            s.setOrdem(ordem++);
            resultado.add(s);
        }
        item.getSubitens().retainAll(resultado);
        resultado.stream().filter(s -> !item.getSubitens().contains(s)).forEach(item.getSubitens()::add);
        item.getSubitens().sort(Comparator.comparingInt(ChecklistSubitem::getOrdem));
    }

    private void avisarResponsavel(UsuarioAutenticado u, ChecklistItem item, UUID responsavelAnterior) {
        var resp = item.getResponsavelId();
        if (resp == null || resp.equals(u.id()) || resp.equals(responsavelAnterior)) return;
        notificacoes.notificar(u.escritorioId(), resp, TipoNotificacao.TAREFA_ATRIBUIDA,
                "Checklist: " + item.getTitulo(), item.getPrazo() == null ? null : "Prazo: " + item.getPrazo(),
                "tarefas?empresa=" + item.getEmpresaId() + "&aba=checklist");
    }

    private List<ChecklistItemResponse> montar(UsuarioAutenticado u, List<ChecklistItem> lista) {
        if (lista.isEmpty()) return List.of();
        var ids = lista.stream()
                .flatMap(i -> Stream.of(i.getResponsavelId(), i.getCriadoPorId(), i.getConcluidoPorId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> nomes = new HashMap<>();
        if (!ids.isEmpty()) {
            usuarios.findByIdInAndEscritorioId(ids, u.escritorioId()).forEach(x -> nomes.put(x.getId(), x.getNome()));
        }
        LocalDate hoje = Datas.hoje(escritorios.findById(u.escritorioId()).map(e -> e.getFusoHorario()).orElse(null));
        return lista.stream().map(i -> {
            var subitens = i.getSubitens().stream()
                    .map(s -> new SubitemResponse(s.getId(), s.getTexto(), s.isConcluido()))
                    .toList();
            var feitos = (int) i.getSubitens().stream().filter(ChecklistSubitem::isConcluido).count();
            return new ChecklistItemResponse(i.getId(), i.getEmpresaId(), i.getTitulo(), i.getObservacoes(),
                    i.getPrazo(), i.getPrioridade(), !i.isConcluido() && i.getPrazo() != null && i.getPrazo().isBefore(hoje),
                    ref(i.getResponsavelId(), nomes), ref(i.getCriadoPorId(), nomes), i.isConcluido(),
                    i.getConcluidoEm(), ref(i.getConcluidoPorId(), nomes), subitens, feitos, i.getCriadoEm(),
                    i.getAtualizadoEm());
        }).toList();
    }

    private static UsuarioRef ref(UUID id, Map<UUID, String> nomes) {
        return id == null ? null : new UsuarioRef(id, nomes.get(id));
    }
}
