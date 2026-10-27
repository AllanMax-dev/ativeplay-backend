package br.com.ativeplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** Limite baixo só nesta classe (contexto próprio) para exercitar o 429 no login. */
@TestPropertySource(properties = {"ativeplay.limite.login-falhas=3"})
class LimiteTentativasApiTest extends IntegracaoTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    @Test
    void loginBloqueiaAposFalhasRepetidasComProblemDetail() {
        var email = "teste-" + UUID.randomUUID() + "@ativeplay.dev";
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/v1/auth/login", Map.of("email", email, "senha", "errada123")).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        var bloqueado = post("/api/v1/auth/login", Map.of("email", email, "senha", "errada123"));
        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(bloqueado.getBody()).containsEntry("status", 429).containsKey("detail");

        // outro e-mail não é afetado
        assertThat(post("/api/v1/auth/login", Map.of("email", "outro-" + email, "senha", "x12345678")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginCorretoZeraOContadorDeFalhas() {
        var email = "teste-" + UUID.randomUUID() + "@ativeplay.dev";
        assertThat(registrar(email).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        for (int rodada = 0; rodada < 3; rodada++) {
            post("/api/v1/auth/login", Map.of("email", email, "senha", "errada123"));
            post("/api/v1/auth/login", Map.of("email", email, "senha", "errada123"));
            assertThat(post("/api/v1/auth/login", Map.of("email", email, "senha", "senha1234")).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    private ResponseEntity<Map<String, Object>> registrar(String email) {
        return post("/api/v1/auth/registrar", Map.of("nomeEscritorio", "Esc " + email, "nome", "Admin",
                "email", email, "senha", "senha1234"));
    }

    private ResponseEntity<Map<String, Object>> post(String url, Object corpo) {
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(corpo), MAPA);
    }
}
