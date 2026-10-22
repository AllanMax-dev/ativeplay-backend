package br.com.ativeplay.modules.tarefa.web.dto;

import br.com.ativeplay.modules.tarefa.domain.PadraoTarefa;
import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.modules.tarefa.domain.Recorrencia;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs do catálogo de tarefas (padrões). */
public final class PadraoDtos {

    public static final String HORARIO = "^([01][0-9]|2[0-3]):[0-5][0-9]$";
    public static final String HORARIO_MSG = "deve estar no formato HH:mm";

    private PadraoDtos() {
    }

    public record PadraoRequest(
            UUID setorId,
            @NotBlank @Size(max = 160) String nome,
            String descricao,
            @NotNull Recorrencia recorrencia,
            List<@NotNull @Min(0) @Max(6) Integer> diasSemana,
            @Min(1) @Max(31) Integer diaMes,
            Boolean apenasDiasUteis,
            @Pattern(regexp = HORARIO, message = HORARIO_MSG) String horarioLimite,
            Prioridade prioridade,
            @Positive Integer tempoEstimadoMin) {
    }

    public record OrdemRequest(@NotEmpty List<@NotNull UUID> ids) {
    }

    public record PadraoResponse(UUID id, UUID setorId, String nome, String descricao, Recorrencia recorrencia,
            List<Integer> diasSemana, Integer diaMes, boolean apenasDiasUteis, String horarioLimite,
            Prioridade prioridade, Integer tempoEstimadoMin, int ordem, boolean ativo, Instant criadoEm,
            Instant atualizadoEm) {

        public static PadraoResponse de(PadraoTarefa p) {
            return new PadraoResponse(p.getId(), p.getSetorId(), p.getNome(), p.getDescricao(), p.getRecorrencia(),
                    p.getDiasSemana(), p.getDiaMes(), p.isApenasDiasUteis(), p.getHorarioLimite(), p.getPrioridade(),
                    p.getTempoEstimadoMin(), p.getOrdem(), p.isAtivo(), p.getCriadoEm(), p.getAtualizadoEm());
        }
    }
}
