package br.com.ativeplay.modules.tarefa.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.tarefa.domain.DefinicaoTarefa;
import br.com.ativeplay.modules.tarefa.domain.PadraoTarefa;
import br.com.ativeplay.modules.tarefa.repository.DefinicaoTarefaRepository;
import br.com.ativeplay.modules.tarefa.repository.PadraoTarefaRepository;
import br.com.ativeplay.modules.tarefa.web.dto.DefinicaoDtos.DefinicaoRequest;
import br.com.ativeplay.modules.tarefa.web.dto.DefinicaoDtos.DefinicaoResponse;
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

/** Tarefas recorrentes configuradas para cada empresa. */
@Service
@RequiredArgsConstructor
@Transactional
public class DefinicaoTarefaService {

    private final DefinicaoTarefaRepository definicoes;
    private final PadraoTarefaRepository padroes;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<DefinicaoResponse> listar(UsuarioAutenticado u, UUID empresaId, Boolean ativa) {
        escopo.garantirEmpresa(u, empresaId);
        var lista = ativa == null
                ? definicoes.findByEscritorioIdAndEmpresaIdOrderByOrdemAsc(u.escritorioId(), empresaId)
                : definicoes.findByEscritorioIdAndEmpresaIdAndAtivaOrderByOrdemAsc(u.escritorioId(), empresaId, ativa);
        return lista.stream().map(DefinicaoResponse::de).toList();
    }

    public DefinicaoResponse criar(UsuarioAutenticado u, DefinicaoRequest req) {
        if (req.empresaId() == null) throw ApiException.invalido("empresaId é obrigatório");
        escopo.garantirEmpresa(u, req.empresaId());
        var d = new DefinicaoTarefa();
        d.setEscritorioId(u.escritorioId());
        d.setEmpresaId(req.empresaId());
        if (req.padraoId() != null) {
            var padrao = padroes.findByIdAndEscritorioId(req.padraoId(), u.escritorioId())
                    .orElseThrow(() -> ApiException.naoEncontrado("Padrão de tarefa"));
            copiarPadrao(d, padrao);
        } else if (req.nome() == null || req.nome().isBlank() || req.recorrencia() == null) {
            throw ApiException.invalido("nome e recorrencia são obrigatórios sem padraoId");
        }
        aplicar(u, d, req);
        d.setOrdem(definicoes.maiorOrdem(req.empresaId()) + 1);
        definicoes.save(d);
        auditoria.registrar(u, "tarefa.criar", "definicao", d.getId(), d.getEmpresaId(),
                "Tarefa recorrente " + d.getNome() + " criada");
        return DefinicaoResponse.de(d);
    }

    public DefinicaoResponse atualizar(UsuarioAutenticado u, UUID id, DefinicaoRequest req) {
        var d = buscar(u, id);
        if (req.empresaId() != null && !req.empresaId().equals(d.getEmpresaId())) {
            throw ApiException.invalido("Não é possível mover a definição para outra empresa");
        }
        aplicar(u, d, req);
        auditoria.registrar(u, "tarefa.editar", "definicao", d.getId(), d.getEmpresaId(), d.getNome() + " editada");
        return DefinicaoResponse.de(d);
    }

    public void desativar(UsuarioAutenticado u, UUID id) {
        var d = buscar(u, id);
        d.setAtiva(false);
        auditoria.registrar(u, "tarefa.desativar", "definicao", d.getId(), d.getEmpresaId(),
                d.getNome() + " desativada");
    }

    /** Reordena as definições da empresa (arrastar e soltar no frontend). */
    public List<DefinicaoResponse> reordenar(UsuarioAutenticado u, UUID empresaId, List<UUID> ids) {
        escopo.garantirEmpresa(u, empresaId);
        if (new HashSet<>(ids).size() != ids.size()) throw ApiException.invalido("Lista de ids com repetições");
        var daEmpresa = definicoes.findByEscritorioIdAndEmpresaIdOrderByOrdemAsc(u.escritorioId(), empresaId);
        var resultado = new ArrayList<DefinicaoTarefa>();
        for (int i = 0; i < ids.size(); i++) {
            var id = ids.get(i);
            var d = daEmpresa.stream().filter(x -> x.getId().equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.naoEncontrado("Definição de tarefa"));
            if (d.getOrdem() != i) {
                d.setOrdem(i);
                auditoria.registrar(u, "tarefa.editar", "definicao", d.getId(), empresaId,
                        d.getNome() + ": nova posição " + (i + 1));
            }
            resultado.add(d);
        }
        return resultado.stream().map(DefinicaoResponse::de).toList();
    }

    /** Cria definições a partir dos padrões ativos ainda não aplicados à empresa. */
    public int aplicarPadroes(UsuarioAutenticado u, UUID empresaId, List<UUID> padraoIds) {
        escopo.garantirEmpresa(u, empresaId);
        var jaAplicados = new HashSet<>(definicoes.findPadraoIdsByEmpresaId(empresaId));
        var candidatos = padraoIds == null || padraoIds.isEmpty()
                ? padroes.findByEscritorioIdAndAtivoOrderByOrdemAsc(u.escritorioId(), true)
                : padroes.findByEscritorioIdAndIdIn(u.escritorioId(), padraoIds).stream()
                        .filter(PadraoTarefa::isAtivo).toList();
        int ordem = definicoes.maiorOrdem(empresaId) + 1;
        int criadas = 0;
        for (var p : candidatos) {
            if (jaAplicados.contains(p.getId())) continue;
            var d = new DefinicaoTarefa();
            d.setEscritorioId(u.escritorioId());
            d.setEmpresaId(empresaId);
            copiarPadrao(d, p);
            d.setOrdem(ordem++);
            definicoes.save(d);
            criadas++;
        }
        if (criadas > 0) {
            auditoria.registrar(u, "tarefa.aplicar_padroes", "empresa", empresaId, empresaId,
                    criadas + " tarefa(s) aplicada(s) a partir do catálogo");
        }
        return criadas;
    }

    private DefinicaoTarefa buscar(UsuarioAutenticado u, UUID id) {
        var d = definicoes.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Definição de tarefa"));
        escopo.garantirAcesso(u, d.getEmpresaId());
        return d;
    }

    private static void copiarPadrao(DefinicaoTarefa d, PadraoTarefa p) {
        d.setPadraoId(p.getId());
        d.setSetorId(p.getSetorId());
        d.setNome(p.getNome());
        d.setDescricao(p.getDescricao());
        d.setRecorrencia(p.getRecorrencia());
        d.setDiasSemana(new ArrayList<>(p.getDiasSemana()));
        d.setDiaMes(p.getDiaMes());
        d.setApenasDiasUteis(p.isApenasDiasUteis());
        d.setHorarioLimite(p.getHorarioLimite());
        d.setPrioridade(p.getPrioridade());
        d.setTempoEstimadoMin(p.getTempoEstimadoMin());
    }

    /** Aplica os campos informados (não nulos) e valida a coerência da regra. */
    private void aplicar(UsuarioAutenticado u, DefinicaoTarefa d, DefinicaoRequest req) {
        if (req.setorId() != null) {
            escopo.garantirSetor(u, req.setorId());
            d.setSetorId(req.setorId());
        }
        if (req.responsavelId() != null) {
            escopo.garantirUsuarioDaEquipe(u, req.responsavelId());
            d.setResponsavelId(req.responsavelId());
        }
        if (req.nome() != null && !req.nome().isBlank()) d.setNome(req.nome().trim());
        if (req.descricao() != null) d.setDescricao(req.descricao());
        if (req.recorrencia() != null) d.setRecorrencia(req.recorrencia());
        if (req.diasSemana() != null) d.setDiasSemana(new ArrayList<>(req.diasSemana()));
        if (req.diaMes() != null) d.setDiaMes(req.diaMes());
        if (req.dataUnica() != null) d.setDataUnica(req.dataUnica());
        if (req.apenasDiasUteis() != null) d.setApenasDiasUteis(req.apenasDiasUteis());
        if (req.horarioLimite() != null) d.setHorarioLimite(req.horarioLimite());
        if (req.prioridade() != null) d.setPrioridade(req.prioridade());
        if (req.tempoEstimadoMin() != null) d.setTempoEstimadoMin(req.tempoEstimadoMin());
        if (req.visivelCliente() != null) d.setVisivelCliente(req.visivelCliente());
        RegrasValidacao.validar(d.getRecorrencia(), d.getDiasSemana(), d.getDiaMes(), d.getDataUnica(), true);
    }
}
