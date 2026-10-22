package br.com.ativeplay.modules.painel.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Indicadores do painel. */
public final class PainelDtos {

    private PainelDtos() {
    }

    /**
     * @param percentualNoPrazo30d concluídas sem violar o SLA / concluídas nos últimos 30 dias (0–100; nulo sem dados)
     * @param mrr soma dos honorários das empresas ativas (só ADMIN/GESTOR)
     * @param pipelineCrm funil por etapa (só ADMIN/GESTOR)
     */
    public record ResumoResponse(
            LocalDate data,
            long totalDia,
            Map<String, Long> ocorrenciasDiaPorStatus,
            long atrasadas,
            Double percentualNoPrazo30d,
            long tempoTrabalhado30dMs,
            List<TempoResponsavel> tempoPorResponsavel30d,
            Map<String, Long> empresasPorStatus,
            BigDecimal mrr,
            List<EtapaPipeline> pipelineCrm) {
    }

    public record TempoResponsavel(UUID usuarioId, String nome, long tempoMs) {
    }

    public record EtapaPipeline(UUID etapaId, String nome, String tipo, long quantidade, BigDecimal valorMensal) {
    }

    public record ProdutividadeUsuario(UUID usuarioId, String nome, long concluidas, long noPrazo, long tempoTotalMs,
            long tempoMedioMs) {
    }

    public record ProdutividadeResponse(LocalDate de, LocalDate ate, List<ProdutividadeUsuario> usuarios) {
    }
}
