package br.com.ativeplay.modules.setor.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class SetorDtos {

    private SetorDtos() {
    }

    public record SetorResposta(UUID id, String nome, String cor, int ordem, boolean ativo, long membros) {
    }

    public record SetorRequest(
            @NotBlank @Size(max = 80) String nome,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB") String cor) {
    }

    public record OrdemRequest(@NotEmpty List<UUID> ids) {
    }
}
