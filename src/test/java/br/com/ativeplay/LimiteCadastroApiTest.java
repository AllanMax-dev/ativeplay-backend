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

/** Limite baixo só nesta classe (contexto próprio) para exercitar o 429 no cadastro. */
@TestPropertySource(properties = "ativeplay.limite.cadastro-max=2")
class LimiteCadastroApiTest extends IntegracaoTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    @Test
    void cadastroBloqueiaAposOLimiteDoIp() {
        for (int i = 0; i < 2; i++) {
            assertThat(registrar("teste-" + UUID.randomUUID() + "@ativeplay.dev").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
        var bloqueado = registrar("teste-" + UUID.randomUUID() + "@ativeplay.dev");
        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(bloqueado.getBody()).containsEntry("status", 429).containsKey("detail");
    }

    private ResponseEntity<Map<String, Object>> registrar(String email) {
        return http.exchange("/api/v1/auth/registrar", HttpMethod.POST, new HttpEntity<>(Map.of(
                "nomeEscritorio", "Esc " + email, "nome", "Admin", "email", email, "senha", "senha1234")), MAPA);
    }
}
