package br.com.ativeplay.modules.usuario.web.dto;

import br.com.ativeplay.modules.auth.web.dto.AuthDtos;
import br.com.ativeplay.shared.seguranca.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs de usuários. Nenhuma resposta expõe o hash da senha. */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record SetorResumo(UUID id, String nome) {
    }

    public record EmpresaResumo(UUID id, String razaoSocial) {
    }

    /** Item da listagem. */
    public record UsuarioResposta(UUID id, String nome, String email, Perfil perfil, String cargo, String telefone,
            boolean ativo, UUID empresaId, Instant ultimoAcesso, Instant criadoEm, List<String> setores,
            int empresasNaCarteira) {
    }

    public record UsuarioDetalhe(UUID id, String nome, String email, Perfil perfil, String cargo, String telefone,
            boolean ativo, UUID empresaId, Instant ultimoAcesso, Instant criadoEm, Instant atualizadoEm,
            List<SetorResumo> setores, List<EmpresaResumo> carteira) {
    }

    /** senhaTemporaria só é preenchida quando gerada pelo sistema, e só nesta resposta. */
    public record UsuarioCriadoResposta(UsuarioDetalhe usuario, String senhaTemporaria) {
    }

    public record SenhaTemporariaResposta(String senhaTemporaria) {
    }

    public record CriarUsuarioRequest(
            @NotBlank @Size(max = 160) String nome,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull Perfil perfil,
            @Size(max = 80) String cargo,
            @Size(max = 20) String telefone,
            @Pattern(regexp = AuthDtos.SENHA_FORTE, message = AuthDtos.SENHA_FORTE_MSG) String senha,
            UUID empresaId,
            List<UUID> setorIds,
            List<UUID> empresaIds) {
    }

    public record AtualizarUsuarioRequest(
            @NotBlank @Size(max = 160) String nome,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull Perfil perfil,
            @Size(max = 80) String cargo,
            @Size(max = 20) String telefone,
            UUID empresaId) {
    }

    public record AtivoRequest(@NotNull Boolean ativo) {
    }

    public record SetoresRequest(@NotNull List<UUID> setorIds) {
    }

    public record CarteiraRequest(@NotNull List<UUID> empresaIds) {
    }
}
