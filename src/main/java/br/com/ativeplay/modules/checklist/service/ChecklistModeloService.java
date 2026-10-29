package br.com.ativeplay.modules.checklist.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.checklist.domain.ChecklistModeloItem;
import br.com.ativeplay.modules.checklist.domain.ChecklistModeloSubitem;
import br.com.ativeplay.modules.checklist.repository.ChecklistModeloItemRepository;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistModeloDtos.ModeloItemRequest;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistModeloDtos.ModeloItemResponse;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistModeloDtos.ModeloSubitemRequest;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistModeloDtos.ModeloSubitemResponse;
import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checklist modelo do escritório: ADMIN/GESTOR mantêm os itens que toda empresa nova recebe. */
@Service
@RequiredArgsConstructor
@Transactional
public class ChecklistModeloService {

    private final ChecklistModeloItemRepository modelo;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<ModeloItemResponse> listar(UsuarioAutenticado u) {
        return modelo.findByEscritorioIdOrderByOrdem(u.escritorioId()).stream().distinct()
                .map(ChecklistModeloService::resposta).toList();
    }

    public ModeloItemResponse criar(UsuarioAutenticado u, ModeloItemRequest req) {
        var item = new ChecklistModeloItem();
        item.setEscritorioId(u.escritorioId());
        item.setOrdem(modelo.ultimaOrdem(u.escritorioId()) + 1);
        aplicar(item, req);
        modelo.saveAndFlush(item);
        auditoria.registrar(u, "checklist_modelo.criar", "checklist_modelo", item.getId(), null,
                "Item do checklist modelo " + item.getTitulo() + " criado");
        return resposta(item);
    }

    public ModeloItemResponse atualizar(UsuarioAutenticado u, UUID id, ModeloItemRequest req) {
        var item = buscar(u, id);
        aplicar(item, req);
        modelo.saveAndFlush(item);
        auditoria.registrar(u, "checklist_modelo.editar", "checklist_modelo", item.getId(), null,
                "Item do checklist modelo " + item.getTitulo() + " editado");
        return resposta(item);
    }

    public void excluir(UsuarioAutenticado u, UUID id) {
        var item = buscar(u, id);
        auditoria.registrar(u, "checklist_modelo.excluir", "checklist_modelo", item.getId(), null,
                "Item do checklist modelo " + item.getTitulo() + " excluído");
        modelo.delete(item);
    }

    /** Reordena o modelo na ordem dos ids informados (arrastar e soltar). */
    public List<ModeloItemResponse> reordenar(UsuarioAutenticado u, List<UUID> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw ApiException.invalido("Lista de ids com repetições");
        var encontrados = modelo.findByEscritorioIdAndIdIn(u.escritorioId(), ids);
        if (encontrados.size() != ids.size()) throw ApiException.naoEncontrado("Item do checklist modelo");
        var porId = encontrados.stream().collect(Collectors.toMap(ChecklistModeloItem::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) porId.get(ids.get(i)).setOrdem(i);
        modelo.flush();
        auditoria.registrar(u, "checklist_modelo.reordenar", "checklist_modelo", null, null,
                "Checklist modelo reordenado");
        return ids.stream().map(id -> resposta(porId.get(id))).toList();
    }

    // ------------------------------------------------------------------ apoio

    private ChecklistModeloItem buscar(UsuarioAutenticado u, UUID id) {
        return modelo.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Item do checklist modelo"));
    }

    private static void aplicar(ChecklistModeloItem item, ModeloItemRequest req) {
        item.setTitulo(req.titulo().trim());
        item.setObservacoes(req.observacoes() == null || req.observacoes().isBlank() ? null : req.observacoes().trim());
        item.setPrioridade(req.prioridade() != null ? req.prioridade() : Prioridade.MEDIA);
        item.setPrazoDias(req.prazoDias());
        sincronizarSubitens(item, req.subitens() == null ? List.of() : req.subitens());
    }

    /** Mantém os sub-itens com id, cria os novos e remove (orphanRemoval) os que não vieram. */
    private static void sincronizarSubitens(ChecklistModeloItem item, List<ModeloSubitemRequest> pedidos) {
        var existentes = item.getSubitens().stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(ChecklistModeloSubitem::getId, Function.identity()));
        var resultado = new ArrayList<ChecklistModeloSubitem>();
        var ordem = 0;
        for (var p : pedidos) {
            var s = p.id() == null ? null : existentes.get(p.id());
            if (s == null) {
                s = new ChecklistModeloSubitem();
                s.setItem(item);
            }
            s.setTexto(p.texto().trim());
            s.setOrdem(ordem++);
            resultado.add(s);
        }
        item.getSubitens().retainAll(resultado);
        resultado.stream().filter(s -> !item.getSubitens().contains(s)).forEach(item.getSubitens()::add);
        item.getSubitens().sort(Comparator.comparingInt(ChecklistModeloSubitem::getOrdem));
    }

    private static ModeloItemResponse resposta(ChecklistModeloItem i) {
        return new ModeloItemResponse(i.getId(), i.getTitulo(), i.getObservacoes(), i.getPrioridade(), i.getPrazoDias(),
                i.getOrdem(), i.getSubitens().stream().map(s -> new ModeloSubitemResponse(s.getId(), s.getTexto())).toList());
    }
}
