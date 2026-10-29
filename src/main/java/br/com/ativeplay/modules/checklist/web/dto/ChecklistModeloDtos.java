package br.com.ativeplay.modules.checklist.web.dto;

import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** DTOs do checklist modelo do escritório. */
public final class ChecklistModeloDtos {

    private ChecklistModeloDtos() {
    }

    /**
     * Criação e edição (PUT substitui o item inteiro, inclusive os sub-itens: os que vierem
     * com id são mantidos/atualizados, os sem id são criados e os ausentes são removidos).
     *
     * @param prazoDias dias corridos após a criação da empresa; nulo = cópia sem prazo
     */
    public record ModeloItemRequest(
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 10_000) String observacoes,
            Prioridade prioridade,
            @Min(0) @Max(365) Integer prazoDias,
            @Valid @Size(max = 50, message = "no máximo 50 sub-itens") List<ModeloSubitemRequest> subitens) {
    }

    public record ModeloSubitemRequest(UUID id, @NotBlank @Size(max = 200) String texto) {
    }

    public record ModeloOrdemRequest(@NotEmpty List<@NotNull UUID> ids) {
    }

    public record ModeloSubitemResponse(UUID id, String texto) {
    }

    public record ModeloItemResponse(UUID id, String titulo, String observacoes, Prioridade prioridade,
            Integer prazoDias, int ordem, List<ModeloSubitemResponse> subitens) {
    }
}
