package br.com.ativeplay.modules.tarefa.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.tarefa.domain.PadraoTarefa;
import br.com.ativeplay.modules.tarefa.repository.PadraoTarefaRepository;
import br.com.ativeplay.modules.tarefa.web.dto.PadraoDtos.PadraoRequest;
import br.com.ativeplay.modules.tarefa.web.dto.PadraoDtos.PadraoResponse;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de tarefas do escritório. */
@Service
@RequiredArgsConstructor
@Transactional
public class PadraoTarefaService {

    private final PadraoTarefaRepository padroes;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<PadraoResponse> listar(UsuarioAutenticado u, Boolean ativo) {
        var lista = ativo == null
                ? padroes.findByEscritorioIdOrderByOrdemAsc(u.escritorioId())
                : padroes.findByEscritorioIdAndAtivoOrderByOrdemAsc(u.escritorioId(), ativo);
        return lista.stream().map(PadraoResponse::de).toList();
    }

    public PadraoResponse criar(UsuarioAutenticado u, PadraoRequest req) {
        escopo.garantirSetor(u, req.setorId());
        var p = new PadraoTarefa();
        p.setEscritorioId(u.escritorioId());
        p.setOrdem(padroes.findByEscritorioIdOrderByOrdemAsc(u.escritorioId()).size());
        aplicar(p, req);
        padroes.save(p);
        auditoria.registrar(u, "padrao.criar", "padrao_tarefa", p.getId(), null, "Padrão " + p.getNome() + " criado");
        return PadraoResponse.de(p);
    }

    public PadraoResponse atualizar(UsuarioAutenticado u, UUID id, PadraoRequest req) {
        var p = buscar(u, id);
        escopo.garantirSetor(u, req.setorId());
        aplicar(p, req);
        auditoria.registrar(u, "padrao.editar", "padrao_tarefa", p.getId(), null, "Padrão " + p.getNome() + " editado");
        return PadraoResponse.de(p);
    }

    public void desativar(UsuarioAutenticado u, UUID id) {
        var p = buscar(u, id);
        p.setAtivo(false);
        auditoria.registrar(u, "padrao.desativar", "padrao_tarefa", p.getId(), null,
                "Padrão " + p.getNome() + " desativado");
    }

    /** Reordena o catálogo na ordem dos ids informados (arrastar e soltar). */
    public List<PadraoResponse> reordenar(UsuarioAutenticado u, List<UUID> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw ApiException.invalido("Lista de ids com repetições");
        var encontrados = padroes.findByEscritorioIdAndIdIn(u.escritorioId(), ids);
        if (encontrados.size() != ids.size()) throw ApiException.naoEncontrado("Padrão de tarefa");
        var resultado = new ArrayList<PadraoTarefa>();
        for (int i = 0; i < ids.size(); i++) {
            var id = ids.get(i);
            var p = encontrados.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            p.setOrdem(i);
            resultado.add(p);
        }
        auditoria.registrar(u, "padrao.reordenar", "padrao_tarefa", null, null, "Catálogo reordenado");
        return resultado.stream().map(PadraoResponse::de).toList();
    }

    private PadraoTarefa buscar(UsuarioAutenticado u, UUID id) {
        return padroes.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Padrão de tarefa"));
    }

    private static void aplicar(PadraoTarefa p, PadraoRequest req) {
        p.setSetorId(req.setorId());
        p.setNome(req.nome().trim());
        p.setDescricao(req.descricao());
        p.setRecorrencia(req.recorrencia());
        p.setDiasSemana(req.diasSemana() == null ? new ArrayList<>() : new ArrayList<>(req.diasSemana()));
        p.setDiaMes(req.diaMes());
        if (req.apenasDiasUteis() != null) p.setApenasDiasUteis(req.apenasDiasUteis());
        if (req.horarioLimite() != null) p.setHorarioLimite(req.horarioLimite());
        if (req.prioridade() != null) p.setPrioridade(req.prioridade());
        p.setTempoEstimadoMin(req.tempoEstimadoMin());
        RegrasValidacao.validar(p.getRecorrencia(), p.getDiasSemana(), p.getDiaMes(), null, false);
    }
}
