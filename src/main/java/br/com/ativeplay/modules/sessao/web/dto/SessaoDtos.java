package br.com.ativeplay.modules.sessao.web.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs das sessões de trabalho. */
public final class SessaoDtos {

    private SessaoDtos() {
    }

    public record IniciarSessaoRequest(@NotNull UUID empresaId) {
    }

    public record SessaoResponse(UUID id, UUID usuarioId, String usuarioNome, UUID empresaId, String empresaNome,
            Instant inicio, Instant fim, long duracaoMs) {
    }

    /** Tempo de um usuário em uma empresa dentro do período consultado. */
    public record TempoEmpresa(UUID empresaId, String empresaNome, long totalMs, int sessoes) {
    }

    /**
     * Contador geral do usuário no dia: total trabalhado "dentro" das empresas, a sessão
     * aberta (se houver) e a divisão por empresa. Os tempos já contam até {@code geradoEm}.
     */
    public record ResumoDiaResponse(LocalDate data, UUID usuarioId, long totalMs, SessaoResponse atual,
            List<TempoEmpresa> empresas, Instant geradoEm) {
    }

    /**
     * Tempo da equipe por empresa no período, comparado ao honorário mensal, para avaliar
     * a margem de cada cliente.
     *
     * @param valorHora honorário mensal proporcional ao período dividido pelas horas trabalhadas
     */
    public record RentabilidadeEmpresa(UUID empresaId, String empresaNome, long totalMs, int pessoas,
            BigDecimal honorarioMensal, BigDecimal valorHora) {
    }

    public record RentabilidadeResponse(LocalDate de, LocalDate ate, long totalMs, List<RentabilidadeEmpresa> empresas) {
    }
}
