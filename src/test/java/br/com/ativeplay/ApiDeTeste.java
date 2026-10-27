package br.com.ativeplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

/** Chamadas HTTP e cadastros de apoio dos testes de integração dos módulos. */
public final class ApiDeTeste {

    public static final String SENHA = "senha1234";

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA = new ParameterizedTypeReference<>() {
    };

    public record Sessao(String accessToken, String refreshToken) {
    }

    private final TestRestTemplate http;

    public ApiDeTeste(TestRestTemplate http) {
        this.http = http;
    }

    public static String novoEmail() {
        return "teste-" + UUID.randomUUID() + "@ativeplay.dev";
    }

    /** Cria um escritório com um administrador novo e devolve as sessões dele. */
    public Sessao registrar(String email) {
        var resposta = post("/api/v1/auth/registrar", null, Map.of("nomeEscritorio", "Escritório " + email,
                "nome", "Admin", "email", email, "senha", SENHA));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Sessao((String) resposta.getBody().get("accessToken"), (String) resposta.getBody().get("refreshToken"));
    }

    public String login(String email) {
        var resposta = post("/api/v1/auth/login", null, Map.of("email", email, "senha", SENHA));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resposta.getBody().get("accessToken");
    }

    /** Cria um OPERADOR com a carteira informada e devolve o token dele. */
    public String criarOperador(String token, List<UUID> empresaIds) {
        var email = novoEmail();
        var resposta = post("/api/v1/usuarios", token, Map.of("nome", "Operador", "email", email,
                "perfil", "OPERADOR", "senha", SENHA, "empresaIds", empresaIds));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return login(email);
    }

    public String criarEmpresa(String token, String razaoSocial, String cnpj) {
        var resposta = post("/api/v1/empresas", token, Map.of("razaoSocial", razaoSocial, "cnpj", cnpj));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resposta.getBody().get("id");
    }

    public ResponseEntity<Map<String, Object>> get(String url, String token) {
        return chamar(HttpMethod.GET, url, token, null, MAPA);
    }

    public ResponseEntity<List<Map<String, Object>>> getLista(String url, String token) {
        return chamar(HttpMethod.GET, url, token, null, LISTA);
    }

    public ResponseEntity<byte[]> baixar(String url, String token) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), byte[].class);
    }

    public ResponseEntity<Map<String, Object>> post(String url, String token, Object corpo) {
        return chamar(HttpMethod.POST, url, token, corpo, MAPA);
    }

    public ResponseEntity<Map<String, Object>> patch(String url, String token, Object corpo) {
        return chamar(HttpMethod.PATCH, url, token, corpo, MAPA);
    }

    /** Sucesso devolve a lista de documentos; erro devolve o ProblemDetail, por isso o corpo é Object. */
    public ResponseEntity<Object> enviarArquivos(String token, MultiValueMap<String, Object> partes) {
        var h = headers(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/documentos", HttpMethod.POST, new HttpEntity<>(partes, h), Object.class);
    }

    private <T> ResponseEntity<T> chamar(HttpMethod metodo, String url, String token, Object corpo,
            ParameterizedTypeReference<T> tipo) {
        return http.exchange(url, metodo, new HttpEntity<>(corpo, headers(token)), tipo);
    }

    private static HttpHeaders headers(String token) {
        var h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return h;
    }
}
