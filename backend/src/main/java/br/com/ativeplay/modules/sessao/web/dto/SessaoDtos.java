package br.com.ativeplay.modules.sessao.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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
}
