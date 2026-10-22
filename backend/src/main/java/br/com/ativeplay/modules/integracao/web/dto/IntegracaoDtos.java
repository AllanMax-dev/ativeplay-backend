package br.com.ativeplay.modules.integracao.web.dto;

import br.com.ativeplay.modules.integracao.domain.Integracao;
import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import br.com.ativeplay.modules.integracao.domain.SincronizacaoIntegracao;
import br.com.ativeplay.modules.integracao.domain.StatusIntegracao;
import br.com.ativeplay.modules.integracao.domain.StatusSincronizacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** DTOs de integrações. Credenciais só entram (nunca saem) pela API. */
public final class IntegracaoDtos {

    private IntegracaoDtos() {
    }

    public record CriarIntegracaoRequest(
            @NotNull UUID empresaId,
            @NotNull ProvedorIntegracao provedor,
            @NotEmpty Map<String, String> credenciais,
            Map<String, Object> configuracoes) {
    }

    public record AtualizarIntegracaoRequest(Map<String, String> credenciais, Map<String, Object> configuracoes) {
    }

    public record StatusRequest(@NotNull StatusIntegracao status) {
    }

    public record SincronizarRequest(@NotBlank @Size(max = 60) String recurso) {
    }

    public record TesteResponse(boolean ok, String mensagem, StatusIntegracao status) {
    }

    public record IntegracaoResponse(UUID id, UUID empresaId, String empresaNome, ProvedorIntegracao provedor,
            StatusIntegracao status, boolean possuiCredenciais, Map<String, Object> configuracoes,
            Instant ultimaSincronizacao, String ultimoErro, Instant criadoEm, Instant atualizadoEm) {
        public static IntegracaoResponse de(Integracao i, String empresaNome) {
            return new IntegracaoResponse(i.getId(), i.getEmpresaId(), empresaNome, i.getProvedor(), i.getStatus(),
                    i.getCredenciaisCifradas() != null, i.getConfiguracoes(), i.getUltimaSincronizacao(),
                    i.getUltimoErro(), i.getCriadoEm(), i.getAtualizadoEm());
        }
    }

    public record SincronizacaoResponse(UUID id, UUID integracaoId, String recurso, StatusSincronizacao status,
            int registros, String erro, Instant iniciadaEm, Instant finalizadaEm) {
        public static SincronizacaoResponse de(SincronizacaoIntegracao s) {
            return new SincronizacaoResponse(s.getId(), s.getIntegracaoId(), s.getRecurso(), s.getStatus(),
                    s.getRegistros(), s.getErro(), s.getIniciadaEm(), s.getFinalizadaEm());
        }
    }
}
