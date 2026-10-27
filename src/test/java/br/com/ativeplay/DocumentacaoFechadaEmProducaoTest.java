package br.com.ativeplay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Com o perfil prod a documentação não é servida (a conexão real vem do Testcontainers). */
@ActiveProfiles("prod")
@TestPropertySource(properties = "DB_HOST=ignorado-nos-testes")
class DocumentacaoFechadaEmProducaoTest extends IntegracaoTest {

    @Test
    void swaggerEOpenApiNaoSaoServidos() {
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/docs", String.class).getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
                .isNotEqualTo(HttpStatus.OK);
    }
}
