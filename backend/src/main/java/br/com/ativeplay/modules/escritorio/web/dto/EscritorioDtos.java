package br.com.ativeplay.modules.escritorio.web.dto;

import br.com.ativeplay.modules.escritorio.domain.Plano;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.validacao.CnpjValido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** DTOs do escritório (tenant) e da matriz de permissões. */
public final class EscritorioDtos {

    private EscritorioDtos() {
    }

    public enum NivelPermissao { NENHUM, LEITURA, ESCRITA }

    public record EscritorioResposta(UUID id, String nome, String slug, String cnpj, String cnpjFormatado,
            Plano plano, String fusoHorario, boolean ativo, Instant criadoEm, long totalUsuarios, long totalEmpresas) {
    }

    public record AtualizarEscritorioRequest(
            @NotBlank @Size(min = 2, max = 160) String nome,
            @CnpjValido String cnpj,
            @NotBlank @Size(max = 64) String fusoHorario) {
    }

    public record PermissaoItem(@NotNull Perfil perfil, @NotNull Modulo modulo, @NotNull NivelPermissao nivel) {
    }
}
