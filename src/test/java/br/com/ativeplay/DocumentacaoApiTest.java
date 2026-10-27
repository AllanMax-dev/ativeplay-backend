package br.com.ativeplay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** A documentação é pública e não expõe o usuário injetado pelo token como parâmetro. */
class DocumentacaoApiTest extends IntegracaoTest {

    @Test
    void swaggerUiNaoExigeAutenticacao() {
        assertThat(http.getForEntity("/swagger-ui.html", String.class).getStatusCode())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void openApiNaoExpoeOParametroDoUsuarioAutenticado() {
        var resposta = http.getForEntity("/v3/api-docs", String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).doesNotContain("\"name\":\"u\"").doesNotContain("UsuarioAutenticado");
    }
}
