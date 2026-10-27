package br.com.ativeplay.modules.painel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.ApiDeTeste;
import br.com.ativeplay.IntegracaoTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Painel: indicadores de faturamento e CRM só para quem vê todas as empresas; demais, só a carteira. */
class PainelIntegracaoTest extends IntegracaoTest {

    private ApiDeTeste api;
    private String tokenAdmin;
    private UUID empresaCarteira;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(http);
        tokenAdmin = api.registrar(ApiDeTeste.novoEmail()).accessToken();
        empresaCarteira = UUID.fromString(api.criarEmpresa(tokenAdmin, "Carteira Ltda", "11444777000161"));
        api.criarEmpresa(tokenAdmin, "Fora da carteira Ltda", "45723174000110");
    }

    @Test
    void adminVeMrrEPipelineDoCrm() {
        var resumo = api.get("/api/v1/painel/resumo", tokenAdmin);
        assertThat(resumo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resumo.getBody().get("mrr")).isNotNull();
        // o funil padrão do escritório tem 6 etapas
        assertThat((List<?>) resumo.getBody().get("pipelineCrm")).hasSize(6);
    }

    @Test
    void operadorNaoRecebeMrrNemPipelineEVeSoASuaCarteira() {
        var tokenOperador = api.criarOperador(tokenAdmin, List.of(empresaCarteira));

        var resumo = api.get("/api/v1/painel/resumo", tokenOperador);
        assertThat(resumo.getStatusCode()).isEqualTo(HttpStatus.OK);
        // intencional: indicadores de faturamento e CRM não são expostos ao OPERADOR
        assertThat(resumo.getBody().get("mrr")).isNull();
        assertThat(resumo.getBody().get("pipelineCrm")).isNull();

        // o agrupamento por status conta só a empresa da carteira
        var porStatus = (Map<?, ?>) resumo.getBody().get("empresasPorStatus");
        long total = porStatus.values().stream().mapToLong(v -> ((Number) v).longValue()).sum();
        assertThat(total).isEqualTo(1);
    }
}
