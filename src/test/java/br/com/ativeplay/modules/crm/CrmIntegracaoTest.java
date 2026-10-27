package br.com.ativeplay.modules.crm;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.ApiDeTeste;
import br.com.ativeplay.IntegracaoTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Funil de vendas: etapas, oportunidades, movimentação e conversão em empresa. */
class CrmIntegracaoTest extends IntegracaoTest {

    private ApiDeTeste api;
    private String token;
    private String lead;
    private String negociacao;
    private String ganho;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(http);
        token = api.registrar(ApiDeTeste.novoEmail()).accessToken();
        // o cadastro do escritório já semeia o funil padrão
        lead = etapaId("Lead");
        negociacao = etapaId("Negociação");
        ganho = etapaId("Ganho");
    }

    @Test
    @SuppressWarnings("unchecked")
    void oportunidadeAparecenaEtapaDoFunil() {
        var criada = criarOportunidade("Pão Dourado - implantação", "Pão Dourado Ltda", "11444777000161", lead);
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criada.getBody()).containsEntry("etapaId", lead);
        assertThat(criada.getBody().get("empresaId")).isNull();

        var etapaLead = etapaDoFunil("Lead");
        assertThat(etapaLead).containsEntry("quantidade", 1);
        assertThat((List<Map<String, Object>>) etapaLead.get("oportunidades"))
                .extracting(o -> o.get("titulo"))
                .containsExactly("Pão Dourado - implantação");
    }

    @Test
    void moverOportunidadeAtualizaEtapasEFechaAoIrParaGanho() {
        var id = (String) criarOportunidade("Mercado Azul", "Mercado Azul Ltda", "45723174000110", lead).getBody().get("id");

        var emNegociacao = api.patch("/api/v1/crm/oportunidades/" + id + "/mover", token,
                Map.of("etapaId", negociacao, "ordem", 0));
        assertThat(emNegociacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emNegociacao.getBody()).containsEntry("etapaId", negociacao);
        assertThat(emNegociacao.getBody().get("fechadaEm")).isNull();
        assertThat(etapaDoFunil("Lead")).containsEntry("quantidade", 0);
        assertThat(etapaDoFunil("Negociação")).containsEntry("quantidade", 1);

        var fechada = api.patch("/api/v1/crm/oportunidades/" + id + "/mover", token,
                Map.of("etapaId", ganho, "ordem", 0));
        assertThat(fechada.getBody()).containsEntry("etapaId", ganho);
        assertThat(fechada.getBody().get("fechadaEm")).isNotNull();
    }

    @Test
    void converterOportunidadeGanhaCriaEmpresaEmOnboarding() {
        var id = (String) criarOportunidade("Lucro Azul", "Lucro Azul Consultoria Ltda", "11222333000181", lead)
                .getBody().get("id");

        var conversao = api.post("/api/v1/crm/oportunidades/" + id + "/converter", token, null);
        assertThat(conversao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var empresaId = (String) conversao.getBody().get("empresaId");
        assertThat(empresaId).isNotNull();
        assertThat(conversao.getBody()).containsEntry("oportunidadeId", id).containsEntry("etapaId", ganho);

        var empresa = api.get("/api/v1/empresas/" + empresaId, token);
        assertThat(empresa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(empresa.getBody())
                .containsEntry("razaoSocial", "Lucro Azul Consultoria Ltda")
                .containsEntry("status", "ONBOARDING");

        assertThat(api.get("/api/v1/crm/oportunidades/" + id, token).getBody()).containsEntry("empresaId", empresaId);
        // converter de novo é conflito: a oportunidade já virou empresa
        assertThat(api.post("/api/v1/crm/oportunidades/" + id + "/converter", token, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void conversaoExigeRazaoSocialECnpjValido() {
        var semRazao = api.post("/api/v1/crm/oportunidades", token, Map.of("etapaId", ganho, "titulo", "Sem razão",
                "cnpj", "11444777000161"));
        assertThat(semRazao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(api.post("/api/v1/crm/oportunidades/" + semRazao.getBody().get("id") + "/converter", token, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- apoio ----------

    private ResponseEntity<Map<String, Object>> criarOportunidade(String titulo, String razaoSocial, String cnpj,
            String etapaId) {
        return api.post("/api/v1/crm/oportunidades", token, Map.of("etapaId", etapaId, "titulo", titulo,
                "razaoSocial", razaoSocial, "cnpj", cnpj, "contatoEmail", "contato@exemplo.com.br",
                "valorMensal", 1500.00));
    }

    private String etapaId(String nome) {
        return (String) etapaDoFunil(nome, api.getLista("/api/v1/crm/etapas", token).getBody()).get("id");
    }

    private Map<String, Object> etapaDoFunil(String nome) {
        return etapaDoFunil(nome, api.getLista("/api/v1/crm/funil", token).getBody());
    }

    private static Map<String, Object> etapaDoFunil(String nome, List<Map<String, Object>> etapas) {
        return etapas.stream().filter(e -> nome.equals(e.get("nome"))).findFirst()
                .orElseThrow(() -> new AssertionError("Etapa não encontrada: " + nome));
    }
}
