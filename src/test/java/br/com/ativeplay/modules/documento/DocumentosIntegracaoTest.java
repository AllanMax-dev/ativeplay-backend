package br.com.ativeplay.modules.documento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.ApiDeTeste;
import br.com.ativeplay.IntegracaoTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Documentos: envio, download íntegro, bloqueio de tipos perigosos e isolamento entre escritórios. */
class DocumentosIntegracaoTest extends IntegracaoTest {

    private static final byte[] CONTEUDO = "Contrato social — versão 3".getBytes(StandardCharsets.UTF_8);

    private ApiDeTeste api;
    private String token;
    private String empresaId;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(http);
        token = api.registrar(ApiDeTeste.novoEmail()).accessToken();
        empresaId = api.criarEmpresa(token, "Documentos Ltda", "11444777000161");
    }

    @Test
    void enviaEBaixaDocumentoComConteudoIntegro() {
        var envio = api.enviarArquivos(token, partes(empresaId, "contrato.txt", CONTEUDO));
        assertThat(envio.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var documento = primeiro(envio);
        assertThat(documento)
                .containsEntry("nome", "contrato.txt")
                .containsEntry("categoria", "CONTRATO")
                .containsEntry("empresaId", empresaId)
                .containsEntry("tamanho", CONTEUDO.length);
        assertThat(documento.get("sha256")).isNotNull();
        var id = (String) documento.get("id");

        var download = api.baixar("/api/v1/documentos/" + id + "/download", token);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isEqualTo(CONTEUDO);
        assertThat(download.getHeaders().getFirst("Content-Disposition")).contains("contrato.txt");

        var listagem = api.get("/api/v1/documentos?empresaId=" + empresaId, token);
        assertThat(listagem.getBody().get("total")).isEqualTo(1);
    }

    @Test
    void recusaExtensaoPerigosa() {
        var envio = api.enviarArquivos(token, partes(empresaId, "instalador.exe", CONTEUDO));
        assertThat(envio.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void outroEscritorioNaoEnviaNemBaixaDocumentos() {
        var enviado = primeiro(api.enviarArquivos(token, partes(empresaId, "contrato.txt", CONTEUDO)));
        var id = (String) enviado.get("id");

        var outroToken = api.registrar(ApiDeTeste.novoEmail()).accessToken();

        assertThat(api.baixar("/api/v1/documentos/" + id + "/download", outroToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.get("/api/v1/documentos", outroToken).getBody().get("total")).isEqualTo(0);
        assertThat(api.enviarArquivos(outroToken, partes(empresaId, "intruso.txt", CONTEUDO))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- apoio ----------

    /** Confere o envio bem-sucedido e devolve o primeiro documento criado. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> primeiro(ResponseEntity<Object> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((List<Map<String, Object>>) resposta.getBody()).getFirst();
    }

    /** Monta o multipart com a parte "arquivos" e os campos de empresa do envio. */
    private MultiValueMap<String, Object> partes(String empresa, String nome, byte[] bytes) {
        var arquivo = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return nome;
            }
        };
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("arquivos", arquivo);
        partes.add("empresaId", empresa);
        partes.add("categoria", "CONTRATO");
        return partes;
    }
}
