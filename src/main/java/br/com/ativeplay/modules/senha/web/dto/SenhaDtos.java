package br.com.ativeplay.modules.senha.web.dto;

import br.com.ativeplay.modules.senha.domain.CategoriaSenha;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** DTOs das senhas salvas dos clientes. A senha em texto só aparece em {@link SenhaReveladaResponse}. */
public final class SenhaDtos {

    private SenhaDtos() {
    }

    /**
     * Criação e edição. Na edição, {@code senha} nula mantém a senha atual e
     * {@code removerSenha=true} apaga a senha guardada.
     */
    public record SenhaRequest(
            @NotBlank @Size(max = 150) String acesso,
            CategoriaSenha categoria,
            @Size(max = 200) String usuario,
            @Size(max = 500) String senha,
            @Size(max = 500) @Pattern(regexp = "^$|^https?://\\S+$", message = "deve começar com http:// ou https://")
            String url,
            @Size(max = 5000) String observacoes,
            Boolean removerSenha) {
    }

    /** @param possuiSenha existe senha guardada (o valor só sai pela rota de revelar) */
    public record SenhaResponse(UUID id, UUID empresaId, String acesso, CategoriaSenha categoria, String usuario,
            boolean possuiSenha, String url, String observacoes, UsuarioRef criadoPor, UsuarioRef atualizadoPor,
            Instant criadoEm, Instant atualizadoEm) {
    }

    public record SenhaReveladaResponse(UUID id, String senha) {
    }
}
