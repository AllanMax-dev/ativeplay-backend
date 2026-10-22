package br.com.ativeplay.modules.tarefa.web.dto;

import br.com.ativeplay.modules.tarefa.domain.DefinicaoTarefa;
import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.modules.tarefa.domain.Recorrencia;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs das tarefas recorrentes de uma empresa (definições). */
public final class DefinicaoDtos {

    private DefinicaoDtos() {
    }

    /**
     * Criação/edição. Na criação com padraoId, os campos do padrão são copiados e os
     * informados aqui prevalecem; sem padraoId, nome e recorrencia são obrigatórios.
     */
    public record DefinicaoRequest(
            UUID empresaId,
            UUID padraoId,
            UUID setorId,
            UUID responsavelId,
            @Size(max = 160) String nome,
            String descricao,
            Recorrencia recorrencia,
            List<@NotNull @Min(0) @Max(6) Integer> diasSemana,
            @Min(1) @Max(31) Integer diaMes,
            LocalDate dataUnica,
            Boolean apenasDiasUteis,
            @Pattern(regexp = PadraoDtos.HORARIO, message = PadraoDtos.HORARIO_MSG) String horarioLimite,
            Prioridade prioridade,
            @Positive Integer tempoEstimadoMin,
            Boolean visivelCliente) {
    }

    public record OrdemDefinicoesRequest(@NotNull UUID empresaId, @NotEmpty List<@NotNull UUID> ids) {
    }

    public record AplicarPadroesRequest(@NotNull UUID empresaId, List<UUID> padraoIds) {
    }

    public record DefinicaoResponse(UUID id, UUID empresaId, UUID padraoId, UUID setorId, UUID responsavelId,
            String nome, String descricao, Recorrencia recorrencia, List<Integer> diasSemana, Integer diaMes,
            LocalDate dataUnica, boolean apenasDiasUteis, String horarioLimite, Prioridade prioridade,
            Integer tempoEstimadoMin, boolean visivelCliente, int ordem, boolean ativa, Instant criadoEm,
            Instant atualizadoEm) {

        public static DefinicaoResponse de(DefinicaoTarefa d) {
            return new DefinicaoResponse(d.getId(), d.getEmpresaId(), d.getPadraoId(), d.getSetorId(),
                    d.getResponsavelId(), d.getNome(), d.getDescricao(), d.getRecorrencia(), d.getDiasSemana(),
                    d.getDiaMes(), d.getDataUnica(), d.isApenasDiasUteis(), d.getHorarioLimite(), d.getPrioridade(),
                    d.getTempoEstimadoMin(), d.isVisivelCliente(), d.getOrdem(), d.isAtiva(), d.getCriadoEm(),
                    d.getAtualizadoEm());
        }
    }
}
