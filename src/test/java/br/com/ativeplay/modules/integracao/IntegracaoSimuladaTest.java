package br.com.ativeplay.modules.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.IntegracaoTest;
import br.com.ativeplay.modules.integracao.provedor.ContaAzulProvedor;
import br.com.ativeplay.modules.integracao.provedor.OmieProvedor;
import br.com.ativeplay.shared.erro.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Provedores Conta Azul e Omie são simulados: validam credenciais e fingem sincronizar. */
class IntegracaoSimuladaTest extends IntegracaoTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    @Test
    void provedoresEstaoMarcadosComoSimulados() {
        assertThat(new ContaAzulProvedor().simulado()).isTrue();
        assertThat(new OmieProvedor().simulado()).isTrue();
    }

    @Test
    void sincronizacaoGeraQuantidadePlausivelEIncrementalMenor() {
        var omie = new OmieProvedor();
        var credenciais = Map.of("appKey", "k", "appSecret", "s");
        int completa = omie.sincronizar(credenciais, "clientes", null);
        int incremental = omie.sincronizar(credenciais, "clientes", Instant.now());
        assertThat(completa).isBetween(40, 400);
        assertThat(incremental).isBetween(1, 30);
    }

    @Test
    void testarConexaoContinuaExigindoCredenciaisCompletas() {
        assertThatThrownBy(() -> new ContaAzulProvedor().testarConexao(Map.of("clientId", "x")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fluxoCompletoTestaESincronizaSemErro501() {
        var email = "teste-" + UUID.randomUUID() + "@ativeplay.dev";
        var token = (String) post("/api/v1/auth/registrar", null, Map.of("nomeEscritorio", "Esc " + email,
                "nome", "Admin", "email", email, "senha", "senha1234")).getBody().get("accessToken");
        var empresaId = (String) post("/api/v1/empresas", token,
                Map.of("razaoSocial", "Empresa ERP Ltda", "cnpj", "11444777000161")).getBody().get("id");

        var criada = post("/api/v1/integracoes", token, Map.of("empresaId", empresaId, "provedor", "OMIE",
                "credenciais", Map.of("appKey", "chave", "appSecret", "segredo")));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var id = (String) criada.getBody().get("id");

        var teste = post("/api/v1/integracoes/" + id + "/testar", token, null);
        assertThat(teste.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(teste.getBody()).containsEntry("ok", true).containsEntry("status", "ATIVA");
        assertThat((String) teste.getBody().get("mensagem")).contains("SIMULADA");

        var sync = post("/api/v1/integracoes/" + id + "/sincronizar", token, Map.of("recurso", "contas_a_receber"));
        assertThat(sync.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sync.getBody()).containsEntry("status", "SUCESSO");
        assertThat((Integer) sync.getBody().get("registros")).isPositive();

        var historico = http.exchange("/api/v1/integracoes/" + id + "/sincronizacoes", HttpMethod.GET,
                new HttpEntity<>(headers(token)), MAPA);
        assertThat((List<?>) historico.getBody().get("dados")).hasSize(1);
    }

    private ResponseEntity<Map<String, Object>> post(String url, String token, Object corpo) {
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(corpo, headers(token)), MAPA);
    }

    private static HttpHeaders headers(String token) {
        var h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return h;
    }
}
