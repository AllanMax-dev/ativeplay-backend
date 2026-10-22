package br.com.ativeplay;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Fluxos críticos de segurança: autenticação, rotação de sessão, multi-tenancy e permissões. */
class AutenticacaoEIsolamentoTest extends IntegracaoTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    record Sessao(String accessToken, String refreshToken) {
    }

    @Test
    void cadastraEscritorioELogaComOAdministrador() {
        var email = emailUnico();
        var cadastro = registrar(email);
        assertThat(cadastro.accessToken()).isNotBlank();

        var me = get("/api/v1/auth/me", cadastro.accessToken());
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("perfil", "ADMIN").containsEntry("permissoes", "TODAS");

        var login = post("/api/v1/auth/login", null, Map.of("email", email.toUpperCase(), "senha", "senha1234"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejeitaCredenciaisInvalidasETokenAusente() {
        assertThat(post("/api/v1/auth/login", null, Map.of("email", emailUnico(), "senha", "senha1234"))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/empresas", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/empresas", "token.invalido.x").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validaDadosDoCadastroComMensagensPorCampo() {
        var resposta = post("/api/v1/auth/registrar", null, Map.of("nomeEscritorio", "X", "cnpj", "11.111.111/1111-11",
                "nome", "Ana", "email", "invalido", "senha", "123"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        var campos = (Map<String, String>) resposta.getBody().get("campos");
        assertThat(campos).containsKeys("cnpj", "email", "senha", "nomeEscritorio");
    }

    @Test
    void rotacionaRefreshTokenERevogaTudoQuandoUmTokenAntigoEReutilizado() {
        var sessao = registrar(emailUnico());

        var renovada = post("/api/v1/auth/refresh", null, Map.of("refreshToken", sessao.refreshToken()));
        assertThat(renovada.getStatusCode()).isEqualTo(HttpStatus.OK);
        var novoRefresh = (String) renovada.getBody().get("refreshToken");
        assertThat(novoRefresh).isNotEqualTo(sessao.refreshToken());

        // reuso do token antigo: suspeita de vazamento → todas as sessões são revogadas
        assertThat(post("/api/v1/auth/refresh", null, Map.of("refreshToken", sessao.refreshToken())).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/v1/auth/refresh", null, Map.of("refreshToken", novoRefresh)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void escritorioNaoEnxergaDadosDeOutroEscritorio() {
        var a = registrar(emailUnico());
        var b = registrar(emailUnico());

        var criada = post("/api/v1/empresas", a.accessToken(), Map.of(
                "razaoSocial", "Empresa do Escritorio A Ltda", "cnpj", "11.222.333/0001-81"));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var empresaId = (String) criada.getBody().get("id");

        assertThat(get("/api/v1/empresas/" + empresaId, a.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/empresas/" + empresaId, b.accessToken()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((List<?>) get("/api/v1/empresas", b.accessToken()).getBody().get("dados")).isEmpty();

        // o mesmo CNPJ pode existir em escritórios diferentes, mas não duas vezes no mesmo
        assertThat(post("/api/v1/empresas", b.accessToken(), Map.of("razaoSocial", "Outra", "cnpj", "11222333000181"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("/api/v1/empresas", a.accessToken(), Map.of("razaoSocial", "Dup", "cnpj", "11222333000181"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void operadorSoveEmpresasDaCarteiraEClienteSoAcessaOPortal() {
        var admin = registrar(emailUnico());
        var e1 = (String) post("/api/v1/empresas", admin.accessToken(),
                Map.of("razaoSocial", "Carteira Ltda", "cnpj", "11444777000161")).getBody().get("id");
        post("/api/v1/empresas", admin.accessToken(), Map.of("razaoSocial", "Fora Ltda", "cnpj", "45723174000110"));

        var emailOperador = emailUnico();
        var operador = post("/api/v1/usuarios", admin.accessToken(), Map.of("nome", "Op", "email", emailOperador,
                "perfil", "OPERADOR", "senha", "senha1234", "empresaIds", List.of(e1)));
        assertThat(operador.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var tokenOperador = login(emailOperador);
        var lista = (List<?>) get("/api/v1/empresas", tokenOperador).getBody().get("dados");
        assertThat(lista).hasSize(1);
        // OPERADOR tem EMPRESAS somente leitura na matriz padrão
        assertThat(post("/api/v1/empresas", tokenOperador, Map.of("razaoSocial", "X", "cnpj", "11222333000181"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var emailCliente = emailUnico();
        assertThat(post("/api/v1/usuarios", admin.accessToken(), Map.of("nome", "Cliente", "email", emailCliente,
                "perfil", "CLIENTE", "senha", "senha1234", "empresaId", e1)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var tokenCliente = login(emailCliente);
        assertThat(get("/api/v1/empresas", tokenCliente).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/portal/empresa", tokenCliente).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- apoio ----------

    private Sessao registrar(String email) {
        var resposta = post("/api/v1/auth/registrar", null, Map.of("nomeEscritorio", "Escritório " + email,
                "nome", "Admin", "email", email, "senha", "senha1234"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Sessao((String) resposta.getBody().get("accessToken"), (String) resposta.getBody().get("refreshToken"));
    }

    private String login(String email) {
        var resposta = post("/api/v1/auth/login", null, Map.of("email", email, "senha", "senha1234"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resposta.getBody().get("accessToken");
    }

    private ResponseEntity<Map<String, Object>> get(String url, String token) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), MAPA);
    }

    private ResponseEntity<Map<String, Object>> post(String url, String token, Object corpo) {
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(corpo, headers(token)), MAPA);
    }

    private static HttpHeaders headers(String token) {
        var h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    private static String emailUnico() {
        return "teste-" + UUID.randomUUID() + "@ativeplay.dev";
    }
}
