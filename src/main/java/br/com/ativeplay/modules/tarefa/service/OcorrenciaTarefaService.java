package br.com.ativeplay.modules.tarefa.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.documento.domain.Documento;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.setor.repository.SetorRepository;
import br.com.ativeplay.modules.tarefa.domain.ComentarioOcorrencia;
import br.com.ativeplay.modules.tarefa.domain.OcorrenciaTarefa;
import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.modules.tarefa.domain.StatusOcorrencia;
import br.com.ativeplay.modules.tarefa.repository.ComentarioOcorrenciaRepository;
import br.com.ativeplay.modules.tarefa.repository.OcorrenciaTarefaRepository;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.AnexoResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.ComentarioRequest;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.ComentarioResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.EmpresaRef;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaAvulsaRequest;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaDetalheResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaUpdateRequest;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.Ref;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.SetorRef;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Datas;
import br.com.ativeplay.shared.web.Pagina;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Execução das tarefas: listagem, tarefas avulsas, cronômetro, conclusão e comentários. */
@Service
@RequiredArgsConstructor
@Transactional
public class OcorrenciaTarefaService {

    /** Filtros da listagem; todos opcionais. */
    public record Filtro(UUID empresaId, LocalDate data, LocalDate de, LocalDate ate, List<StatusOcorrencia> status,
            UUID responsavelId, UUID setorId, boolean atrasadas, boolean minhas) {
    }

    private final OcorrenciaTarefaRepository ocorrencias;
    private final ComentarioOcorrenciaRepository comentarios;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final SetorRepository setores;
    private final EscritorioRepository escritorios;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;
    private final EntityManager em;

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Pagina<OcorrenciaResponse> listar(UsuarioAutenticado u, Filtro f, Pageable pageable) {
        var permitidas = escopo.empresasPermitidas(u);
        if (f.empresaId() != null) escopo.garantirAcesso(u, f.empresaId());
        if (f.empresaId() == null && permitidas.isPresent() && permitidas.get().isEmpty()) {
            return new Pagina<>(List.of(), 0, pageable.getPageNumber() + 1, pageable.getPageSize());
        }
        var agora = Instant.now();
        Specification<OcorrenciaTarefa> spec = (r, q, cb) -> {
            var p = new ArrayList<Predicate>();
            p.add(cb.equal(r.get("escritorioId"), u.escritorioId()));
            if (f.empresaId() != null) p.add(cb.equal(r.get("empresaId"), f.empresaId()));
            else permitidas.ifPresent(ids -> p.add(r.get("empresaId").in(ids)));
            if (f.data() != null) p.add(cb.equal(r.get("data"), f.data()));
            if (f.de() != null) p.add(cb.greaterThanOrEqualTo(r.get("data"), f.de()));
            if (f.ate() != null) p.add(cb.lessThanOrEqualTo(r.get("data"), f.ate()));
            if (f.status() != null && !f.status().isEmpty()) p.add(r.get("status").in(f.status()));
            if (f.responsavelId() != null) p.add(cb.equal(r.get("responsavelId"), f.responsavelId()));
            if (f.minhas()) p.add(cb.equal(r.get("responsavelId"), u.id()));
            if (f.setorId() != null) p.add(cb.equal(r.get("setorId"), f.setorId()));
            if (f.atrasadas()) {
                p.add(r.get("status").in(OcorrenciaTarefa.ABERTAS));
                p.add(cb.lessThan(r.get("prazo"), agora));
            }
            // Ordenação: prazo mais próximo primeiro, depois prioridade (URGENTE > ALTA > MEDIA > BAIXA).
            if (q.getResultType() != Long.class && q.getResultType() != long.class) {
                var peso = cb.selectCase(r.get("prioridade"))
                        .when(Prioridade.URGENTE, 0).when(Prioridade.ALTA, 1).when(Prioridade.MEDIA, 2)
                        .otherwise(3);
                q.orderBy(cb.asc(r.get("prazo")), cb.asc(peso), cb.asc(r.get("id")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = ocorrencias.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        var respostas = montar(u, page.getContent(), agora);
        return Pagina.de(new PageImpl<>(respostas, page.getPageable(), page.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public OcorrenciaDetalheResponse detalhar(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        var lista = comentarios.findByOcorrenciaIdOrderByCriadoEmAsc(id);
        var autores = nomesUsuarios(u, lista.stream().map(ComentarioOcorrencia::getAutorId).toList());
        var respostaComentarios = lista.stream()
                .map(c -> new ComentarioResponse(c.getId(), c.getTexto(), ref(c.getAutorId(), autores), c.getCriadoEm()))
                .toList();
        var anexos = em.createQuery("""
                        select d from Documento d where d.ocorrenciaId = :id and d.escritorioId = :esc
                        order by d.criadoEm
                        """, Documento.class)
                .setParameter("id", id)
                .setParameter("esc", u.escritorioId())
                .getResultList().stream()
                .map(d -> new AnexoResponse(d.getId(), d.getNome(), d.getMimeType(), d.getTamanho(), d.getCriadoEm()))
                .toList();
        return new OcorrenciaDetalheResponse(montar(u, List.of(o), Instant.now()).getFirst(), respostaComentarios,
                anexos);
    }

    // ------------------------------------------------------------------ criação e edição

    public OcorrenciaResponse criarAvulsa(UsuarioAutenticado u, OcorrenciaAvulsaRequest req) {
        escopo.garantirEmpresa(u, req.empresaId());
        escopo.garantirUsuarioDaEquipe(u, req.responsavelId());
        escopo.garantirSetor(u, req.setorId());
        var o = new OcorrenciaTarefa();
        o.setEscritorioId(u.escritorioId());
        o.setEmpresaId(req.empresaId());
        o.setTitulo(req.titulo().trim());
        o.setDescricao(req.descricao());
        o.setData(req.data());
        o.setPrazo(req.prazo() != null ? req.prazo()
                : Datas.instanteLocal(req.data(), req.horarioLimite() != null ? req.horarioLimite() : "18:00", fuso(u)));
        o.setResponsavelId(req.responsavelId());
        o.setSetorId(req.setorId());
        if (req.prioridade() != null) o.setPrioridade(req.prioridade());
        if (req.visivelCliente() != null) o.setVisivelCliente(req.visivelCliente());
        ocorrencias.save(o);
        auditoria.registrar(u, "tarefa.criar", "ocorrencia", o.getId(), o.getEmpresaId(),
                "Tarefa avulsa " + o.getTitulo() + " criada");
        avisarAtribuicao(u, o);
        return montar(u, List.of(o), Instant.now()).getFirst();
    }

    public OcorrenciaResponse atualizar(UsuarioAutenticado u, UUID id, OcorrenciaUpdateRequest req) {
        var o = buscar(u, id);
        var responsavelAnterior = o.getResponsavelId();
        if (req.titulo() != null) o.setTitulo(req.titulo().trim());
        if (req.descricao() != null) o.setDescricao(req.descricao());
        if (req.prioridade() != null) o.setPrioridade(req.prioridade());
        if (req.visivelCliente() != null) o.setVisivelCliente(req.visivelCliente());
        if (req.prazo() != null) {
            o.setPrazo(req.prazo());
            // Novo prazo no futuro de uma tarefa aberta volta a cumprir o SLA.
            if (o.isAberta() && req.prazo().isAfter(Instant.now())) o.setSlaViolado(false);
        }
        if (req.responsavelId() != null && !req.responsavelId().equals(responsavelAnterior)) {
            escopo.garantirUsuarioDaEquipe(u, req.responsavelId());
            o.setResponsavelId(req.responsavelId());
            avisarAtribuicao(u, o);
        }
        auditoria.registrar(u, "tarefa.editar", "ocorrencia", o.getId(), o.getEmpresaId(), o.getTitulo() + " editada");
        return montar(u, List.of(o), Instant.now()).getFirst();
    }

    // ------------------------------------------------------------------ cronômetro e status

    public OcorrenciaResponse iniciar(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        if (!o.isAberta()) throw ApiException.conflito("Tarefa " + rotulo(o.getStatus()) + " não pode ser iniciada");
        if (o.getCronometroInicio() != null) throw ApiException.conflito("O cronômetro desta tarefa já está rodando");
        var agora = Instant.now();
        // Um cronômetro por pessoa: pausa o que estiver rodando em outra tarefa.
        for (var outra : ocorrencias.findByEscritorioIdAndResponsavelIdAndCronometroInicioIsNotNull(u.escritorioId(),
                u.id())) {
            if (outra.getId().equals(o.getId())) continue;
            outra.pararCronometro(agora);
            outra.setStatus(StatusOcorrencia.PAUSADA);
            auditoria.registrar(u, "tarefa.pausar", "ocorrencia", outra.getId(), outra.getEmpresaId(),
                    outra.getTitulo() + " pausada automaticamente");
        }
        o.setStatus(StatusOcorrencia.EM_ANDAMENTO);
        o.setCronometroInicio(agora);
        if (o.getIniciadaEm() == null) o.setIniciadaEm(agora);
        if (o.getResponsavelId() == null) o.setResponsavelId(u.id());
        auditoria.registrar(u, "tarefa.iniciar", "ocorrencia", o.getId(), o.getEmpresaId(), o.getTitulo() + " iniciada");
        return montar(u, List.of(o), agora).getFirst();
    }

    public OcorrenciaResponse pausar(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        if (o.getStatus() != StatusOcorrencia.EM_ANDAMENTO) {
            throw ApiException.conflito("Só tarefas em andamento podem ser pausadas");
        }
        var agora = Instant.now();
        o.pararCronometro(agora);
        o.setStatus(StatusOcorrencia.PAUSADA);
        auditoria.registrar(u, "tarefa.pausar", "ocorrencia", o.getId(), o.getEmpresaId(), o.getTitulo() + " pausada");
        return montar(u, List.of(o), agora).getFirst();
    }

    public OcorrenciaResponse concluir(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        if (!o.isAberta()) throw ApiException.conflito("Tarefa " + rotulo(o.getStatus()) + " não pode ser concluída");
        var agora = Instant.now();
        o.pararCronometro(agora);
        o.setStatus(StatusOcorrencia.CONCLUIDA);
        o.setConcluidaEm(agora);
        o.setConcluidaPorId(u.id());
        if (agora.isAfter(o.getPrazo())) o.setSlaViolado(true);
        auditoria.registrar(u, "tarefa.concluir", "ocorrencia", o.getId(), o.getEmpresaId(),
                o.getTitulo() + " concluída" + (o.isSlaViolado() ? " fora do prazo" : ""));
        return montar(u, List.of(o), agora).getFirst();
    }

    public OcorrenciaResponse reabrir(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        if (o.isAberta()) throw ApiException.conflito("A tarefa já está aberta");
        o.setStatus(o.getTempoGastoMs() > 0 ? StatusOcorrencia.PAUSADA : StatusOcorrencia.PENDENTE);
        o.setConcluidaEm(null);
        o.setConcluidaPorId(null);
        auditoria.registrar(u, "tarefa.reabrir", "ocorrencia", o.getId(), o.getEmpresaId(), o.getTitulo() + " reaberta");
        return montar(u, List.of(o), Instant.now()).getFirst();
    }

    public OcorrenciaResponse cancelar(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        if (!o.isAberta()) throw ApiException.conflito("Tarefa " + rotulo(o.getStatus()) + " não pode ser cancelada");
        var agora = Instant.now();
        o.pararCronometro(agora);
        o.setStatus(StatusOcorrencia.CANCELADA);
        auditoria.registrar(u, "tarefa.cancelar", "ocorrencia", o.getId(), o.getEmpresaId(),
                o.getTitulo() + " cancelada");
        return montar(u, List.of(o), agora).getFirst();
    }

    // ------------------------------------------------------------------ comentários

    @Transactional(readOnly = true)
    public List<ComentarioResponse> listarComentarios(UsuarioAutenticado u, UUID id) {
        buscar(u, id);
        var lista = comentarios.findByOcorrenciaIdOrderByCriadoEmAsc(id);
        var autores = nomesUsuarios(u, lista.stream().map(ComentarioOcorrencia::getAutorId).toList());
        return lista.stream()
                .map(c -> new ComentarioResponse(c.getId(), c.getTexto(), ref(c.getAutorId(), autores), c.getCriadoEm()))
                .toList();
    }

    public ComentarioResponse comentar(UsuarioAutenticado u, UUID id, ComentarioRequest req) {
        var o = buscar(u, id);
        var c = new ComentarioOcorrencia();
        c.setEscritorioId(u.escritorioId());
        c.setOcorrenciaId(o.getId());
        c.setAutorId(u.id());
        c.setTexto(req.texto().trim());
        comentarios.save(c);
        auditoria.registrar(u, "tarefa.comentar", "ocorrencia", o.getId(), o.getEmpresaId(),
                "Comentário em " + o.getTitulo());
        var autores = nomesUsuarios(u, List.of(u.id()));
        if (o.getResponsavelId() != null && !o.getResponsavelId().equals(u.id())) {
            var autor = autores.getOrDefault(u.id(), "Alguém");
            notificacoes.notificar(u.escritorioId(), o.getResponsavelId(), TipoNotificacao.COMENTARIO,
                    autor + " comentou em " + o.getTitulo(), resumo(c.getTexto()), link(o));
        }
        return new ComentarioResponse(c.getId(), c.getTexto(), ref(u.id(), autores), c.getCriadoEm());
    }

    // ------------------------------------------------------------------ apoio

    private OcorrenciaTarefa buscar(UsuarioAutenticado u, UUID id) {
        var o = ocorrencias.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Tarefa"));
        escopo.garantirAcesso(u, o.getEmpresaId());
        return o;
    }

    private void avisarAtribuicao(UsuarioAutenticado u, OcorrenciaTarefa o) {
        if (o.getResponsavelId() == null || o.getResponsavelId().equals(u.id())) return;
        notificacoes.notificar(u.escritorioId(), o.getResponsavelId(), TipoNotificacao.TAREFA_ATRIBUIDA,
                "Nova tarefa: " + o.getTitulo(), null, link(o));
    }

    private String fuso(UsuarioAutenticado u) {
        return escritorios.findById(u.escritorioId()).map(e -> e.getFusoHorario()).orElse(null);
    }

    /** Monta as respostas carregando nomes e contagens em lote (sem N+1). */
    private List<OcorrenciaResponse> montar(UsuarioAutenticado u, List<OcorrenciaTarefa> lista, Instant agora) {
        if (lista.isEmpty()) return List.of();
        var ids = lista.stream().map(OcorrenciaTarefa::getId).toList();
        var empresasPorId = empresas.findByIdInAndEscritorioId(distintos(lista, OcorrenciaTarefa::getEmpresaId),
                u.escritorioId()).stream().collect(Collectors.toMap(e -> e.getId(), Function.identity()));
        var nomes = nomesUsuarios(u, lista.stream().map(OcorrenciaTarefa::getResponsavelId).toList());
        var setoresPorId = setores.findByIdInAndEscritorioId(distintos(lista, OcorrenciaTarefa::getSetorId),
                u.escritorioId()).stream().collect(Collectors.toMap(s -> s.getId(), Function.identity()));
        var qtdComentarios = contagem(comentarios.contarPorOcorrencia(ids));
        var qtdAnexos = contagem(em.createQuery("""
                        select d.ocorrenciaId, count(d) from Documento d
                        where d.ocorrenciaId in :ids group by d.ocorrenciaId
                        """, Object[].class)
                .setParameter("ids", ids).getResultList());

        return lista.stream().map(o -> {
            var e = empresasPorId.get(o.getEmpresaId());
            var s = o.getSetorId() == null ? null : setoresPorId.get(o.getSetorId());
            return new OcorrenciaResponse(o.getId(), o.getDefinicaoId(), o.getTitulo(), o.getDescricao(), o.getData(),
                    o.getPrazo(), o.getPrioridade(), o.getStatus(), o.isSlaViolado(),
                    o.isAberta() && o.getPrazo().isBefore(agora), o.isVisivelCliente(), o.getTempoEstimadoMin(),
                    o.tempoGastoAtualMs(agora), o.getCronometroInicio(), o.getIniciadaEm(), o.getConcluidaEm(),
                    o.getConcluidaPorId(),
                    e == null ? null : new EmpresaRef(e.getId(), e.getRazaoSocial(), e.getNomeFantasia()),
                    ref(o.getResponsavelId(), nomes),
                    s == null ? null : new SetorRef(s.getId(), s.getNome(), s.getCor()),
                    qtdComentarios.getOrDefault(o.getId(), 0L), qtdAnexos.getOrDefault(o.getId(), 0L),
                    o.getVersao(), o.getCriadoEm(), o.getAtualizadoEm());
        }).toList();
    }

    private Map<UUID, String> nomesUsuarios(UsuarioAutenticado u, Collection<UUID> ids) {
        Set<UUID> distintos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distintos.isEmpty()) return Map.of();
        var mapa = new HashMap<UUID, String>();
        usuarios.findByIdInAndEscritorioId(distintos, u.escritorioId()).forEach(x -> mapa.put(x.getId(), x.getNome()));
        return mapa;
    }

    private static Set<UUID> distintos(List<OcorrenciaTarefa> lista, Function<OcorrenciaTarefa, UUID> campo) {
        var set = new HashSet<UUID>();
        lista.forEach(o -> {
            var v = campo.apply(o);
            if (v != null) set.add(v);
        });
        return set;
    }

    private static Map<UUID, Long> contagem(List<Object[]> linhas) {
        var mapa = new HashMap<UUID, Long>();
        linhas.forEach(l -> mapa.put((UUID) l[0], ((Number) l[1]).longValue()));
        return mapa;
    }

    private static Ref ref(UUID id, Map<UUID, String> nomes) {
        return id == null ? null : new Ref(id, nomes.get(id));
    }

    private static String link(OcorrenciaTarefa o) {
        return "tarefas?ocorrencia=" + o.getId();
    }

    private static String resumo(String texto) {
        return texto.length() > 200 ? texto.substring(0, 197) + "..." : texto;
    }

    private static String rotulo(StatusOcorrencia status) {
        return switch (status) {
            case PENDENTE -> "pendente";
            case EM_ANDAMENTO -> "em andamento";
            case PAUSADA -> "pausada";
            case CONCLUIDA -> "concluída";
            case CANCELADA -> "cancelada";
        };
    }
}
