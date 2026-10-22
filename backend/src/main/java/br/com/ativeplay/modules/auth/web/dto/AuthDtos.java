package br.com.ativeplay.modules.auth.web.dto;

import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.validacao.CnpjValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTOs de autenticação. */
public final class AuthDtos {

    /** Mínimo 8 caracteres, com letras e números. */
    public static final String SENHA_FORTE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String SENHA_FORTE_MSG = "A senha deve ter 8+ caracteres, com letras e números";

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {
    }

    public record RefreshRequest(@NotBlank @Size(min = 20) String refreshToken) {
    }

    public record RegistrarEscritorioRequest(
            @NotBlank @Size(min = 2, max = 160) String nomeEscritorio,
            @CnpjValido String cnpj,
            @NotBlank @Size(min = 2, max = 160) String nome,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = SENHA_FORTE, message = SENHA_FORTE_MSG) String senha) {
    }

    public record AlterarSenhaRequest(
            @NotBlank String senhaAtual,
            @NotBlank @Pattern(regexp = SENHA_FORTE, message = SENHA_FORTE_MSG) String novaSenha) {
    }

    public record UsuarioResumo(UUID id, String nome, String email, Perfil perfil, UUID escritorioId, UUID empresaId) {
    }

    public record SessaoResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
            UsuarioResumo usuario) {
    }
}
