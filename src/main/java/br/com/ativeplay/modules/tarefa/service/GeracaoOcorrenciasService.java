package br.com.ativeplay.modules.tarefa.service;

import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.tarefa.domain.OcorrenciaTarefa;
import br.com.ativeplay.modules.tarefa.repository.DefinicaoTarefaRepository;
import br.com.ativeplay.modules.tarefa.repository.OcorrenciaTarefaRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.util.Datas;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transforma definições recorrentes em ocorrências do dia e marca prazos vencidos.
 * A geração é idempotente: a restrição única (definicao_id, data) com ON CONFLICT DO NOTHING
 * permite rodar várias vezes (ou em paralelo) sem duplicar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeracaoOcorrenciasService {

    private static final EnumSet<StatusEmpresa> EMPRESAS_EM_OPERACAO =
            EnumSet.of(StatusEmpresa.ATIVA, StatusEmpresa.ONBOARDING);

    private static final String INSERIR = """
            INSERT INTO ocorrencias_tarefa (escritorio_id, empresa_id, definicao_id, setor_id, responsavel_id, titulo,
                descricao, data, prazo, prioridade, tempo_estimado_min, visivel_cliente)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (definicao_id, data) DO NOTHING
            """;

    private final EscritorioRepository escritorios;
    private final DefinicaoTarefaRepository definicoes;
    private final OcorrenciaTarefaRepository ocorrencias;
    private final NotificacaoService notificacoes;
    private final JdbcTemplate jdbc;

    /** Gera as ocorrências da data para o escritório (ou só para uma empresa). Retorna quantas foram criadas. */
    @Transactional
    public int gerar(UUID escritorioId, LocalDate data, UUID empresaIdOuNull) {
        var escritorio = escritorios.findById(escritorioId).orElseThrow(() -> ApiException.naoEncontrado("Escritório"));
        var candidatas = empresaIdOuNull == null
                ? definicoes.ativasParaGeracao(escritorioId, EMPRESAS_EM_OPERACAO)
                : definicoes.ativasParaGeracaoDaEmpresa(escritorioId, empresaIdOuNull, EMPRESAS_EM_OPERACAO);
        var aplicaveis = candidatas.stream().filter(d -> Recorrencias.deveOcorrer(d, data)).toList();
        if (aplicaveis.isEmpty()) return 0;

        var linhas = aplicaveis.stream().map(d -> new Object[] {
            escritorioId, d.getEmpresaId(), d.getId(), d.getSetorId(), d.getResponsavelId(), d.getNome(),
            d.getDescricao(), data,
            Timestamp.from(Datas.instanteLocal(data, d.getHorarioLimite(), escritorio.getFusoHorario())),
            d.getPrioridade().name(), d.getTempoEstimadoMin(), d.isVisivelCliente()
        }).toList();
        int criadas = 0;
        for (int afetadas : jdbc.batchUpdate(INSERIR, linhas)) {
            if (afetadas > 0) criadas += afetadas;
        }
        if (criadas > 0) log.info("Escritório {}: {} ocorrência(s) gerada(s) para {}", escritorioId, criadas, data);
        return criadas;
    }

    /** Marca como sla_violado as ocorrências abertas com prazo vencido e avisa os responsáveis. */
    @Transactional
    public int varrerSla() {
        List<OcorrenciaTarefa> vencidas = ocorrencias.vencidasSemSla(OcorrenciaTarefa.ABERTAS, Instant.now());
        if (vencidas.isEmpty()) return 0;
        int marcadas = ocorrencias.marcarSlaViolado(vencidas.stream().map(OcorrenciaTarefa::getId).toList());
        for (var o : vencidas) {
            notificacoes.notificar(o.getEscritorioId(), o.getResponsavelId(), TipoNotificacao.SLA_VIOLADO,
                    "Prazo vencido: " + o.getTitulo(), null, "tarefas?ocorrencia=" + o.getId());
        }
        return marcadas;
    }
}
