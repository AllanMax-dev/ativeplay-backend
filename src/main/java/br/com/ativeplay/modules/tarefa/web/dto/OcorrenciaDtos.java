package br.com.ativeplay.modules.tarefa.web.dto;

import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.modules.tarefa.domain.StatusOcorrencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs das ocorrências (instâncias executáveis das tarefas). */
public final class OcorrenciaDtos {

    private OcorrenciaDtos() {
    }

    /** Tarefa avulsa. Sem prazo explícito, o prazo é data + horarioLimite (padrão 18:00) no fuso do escritório. */
    public record OcorrenciaAvulsaRequest(
            @NotNull UUID empresaId,
            @NotBlank @Size(max = 160) String titulo,
            String descricao,
            @NotNull LocalDate data,
            @Pattern(regexp = PadraoDtos.HORARIO, message = PadraoDtos.HORARIO_MSG) String horarioLimite,
            Instant prazo,
            UUID responsavelId,
            UUID setorId,
            Prioridade prioridade,
            Boolean visivelCliente) {
    }

    /** Edição parcial: apenas campos não nulos são aplicados. */
    public record OcorrenciaUpdateRequest(
            @Size(min = 1, max = 160) String titulo,
            String descricao,
            UUID responsavelId,
            Prioridade prioridade,
            Instant prazo,
            Boolean visivelCliente) {
    }

    public record ComentarioRequest(@NotBlank @Size(max = 5000) String texto) {
    }

    public record GerarRequest(LocalDate data, UUID empresaId) {
    }

    public record Ref(UUID id, String nome) {
    }

    public record EmpresaRef(UUID id, String razaoSocial, String nomeFantasia) {
    }

    /**
     * Empresa no filtro de "Tarefas do dia", com as contagens da data.
     *
     * @param naCarteira a empresa está na carteira do usuário logado
     * @param checklistPendentes itens do checklist do cliente ainda não concluídos
     */
    public record EmpresaDoDiaResponse(UUID id, String razaoSocial, String nomeFantasia, String status,
            boolean naCarteira, long total, long abertas, long emAndamento, long concluidas, long atrasadas,
            long checklistPendentes) {
    }

    public record SetorRef(UUID id, String nome, String cor) {
    }

    public record OcorrenciaResponse(UUID id, UUID definicaoId, String titulo, String descricao, LocalDate data,
            Instant prazo, Prioridade prioridade, StatusOcorrencia status, boolean slaViolado, boolean atrasada,
            boolean visivelCliente, Integer tempoEstimadoMin, long tempoGastoAtualMs, Instant cronometroInicio,
            Instant iniciadaEm, Instant concluidaEm, UUID concluidaPorId, EmpresaRef empresa, Ref responsavel,
            SetorRef setor, long comentarios, long anexos, int versao, Instant criadoEm, Instant atualizadoEm) {
    }

    public record ComentarioResponse(UUID id, String texto, Ref autor, Instant criadoEm) {
    }

    public record AnexoResponse(UUID id, String nome, String mimeType, long tamanho, Instant criadoEm) {
    }

    public record OcorrenciaDetalheResponse(OcorrenciaResponse ocorrencia, List<ComentarioResponse> comentarios,
            List<AnexoResponse> anexos) {
    }
}
