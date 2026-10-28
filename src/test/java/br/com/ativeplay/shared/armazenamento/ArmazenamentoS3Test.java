package br.com.ativeplay.shared.armazenamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.config.S3Config;
import br.com.ativeplay.config.S3Properties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

/** Exercita o cliente S3 contra um servidor S3 mínimo em memória (PUT/GET/HEAD/DELETE em modo path-style). */
class ArmazenamentoS3Test {

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
    private HttpServer servidor;
    private ArmazenamentoS3 armazenamento;
    private S3Client cliente;

    @BeforeEach
    void subirS3Falso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
        var props = new S3Properties();
        props.setEndpoint(URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()));
        props.setRegion("sa-east-1");
        props.setBucket("documentos");
        props.setAccessKey("chave-de-teste");
        props.setSecretKey("segredo-de-teste");
        cliente = new S3Config().s3Client(props);
        armazenamento = new ArmazenamentoS3(cliente, props);
    }

    @AfterEach
    void desligar() {
        if (cliente != null) cliente.close();
        servidor.stop(0);
    }

    @Test
    void salvaAbreERemoveUmArquivo() throws Exception {
        var conteudo = "conteúdo do documento".getBytes(StandardCharsets.UTF_8);
        var escritorio = UUID.randomUUID();

        var salvo = armazenamento.salvar(escritorio, "Balancete Março.PDF", new ByteArrayInputStream(conteudo));

        assertThat(salvo.storageKey()).startsWith(escritorio + "/").endsWith(".pdf");
        assertThat(salvo.tamanho()).isEqualTo(conteudo.length);
        assertThat(salvo.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo)));
        assertThat(objetos).containsOnlyKeys("documentos/" + salvo.storageKey());

        var recurso = armazenamento.abrir(salvo.storageKey());
        assertThat(recurso.contentLength()).isEqualTo(conteudo.length);
        try (var entrada = recurso.getInputStream()) {
            assertThat(entrada.readAllBytes()).isEqualTo(conteudo);
        }

        armazenamento.remover(salvo.storageKey());
        assertThat(objetos).isEmpty();
    }

    @Test
    void abrirChaveInexistenteDevolve404() {
        assertThatThrownBy(() -> armazenamento.abrir(UUID.randomUUID() + "/2026/10/nao-existe.pdf"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(404));
    }

    // ---------- S3 falso ----------

    private void atender(HttpExchange troca) throws IOException {
        // path-style: /{bucket}/{chave}
        var caminho = troca.getRequestURI().getPath().substring(1);
        switch (troca.getRequestMethod()) {
            case "PUT" -> {
                var dados = corpo(troca);
                objetos.put(caminho, dados);
                // o SDK confere o ETag (MD5 em hexadecimal) com o que enviou, como faz o S3 real
                troca.getResponseHeaders().add("ETag", "\"" + HexFormat.of().formatHex(md5(dados)) + "\"");
                troca.sendResponseHeaders(200, -1);
            }
            case "GET", "HEAD" -> {
                var dados = objetos.get(caminho);
                if (dados == null) {
                    var corpo = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>NoSuchKey</Code>"
                            + "<Message>nao existe</Message></Error>").getBytes(StandardCharsets.UTF_8);
                    troca.getResponseHeaders().add("Content-Type", "application/xml");
                    troca.sendResponseHeaders(404, "GET".equals(troca.getRequestMethod()) ? corpo.length : -1);
                    if ("GET".equals(troca.getRequestMethod())) troca.getResponseBody().write(corpo);
                } else if ("HEAD".equals(troca.getRequestMethod())) {
                    troca.getResponseHeaders().add("Content-Length", String.valueOf(dados.length));
                    troca.sendResponseHeaders(200, -1);
                } else {
                    troca.sendResponseHeaders(200, dados.length);
                    troca.getResponseBody().write(dados);
                }
            }
            case "DELETE" -> {
                objetos.remove(caminho);
                troca.sendResponseHeaders(204, -1);
            }
            default -> troca.sendResponseHeaders(405, -1);
        }
        troca.close();
    }

    private static byte[] md5(byte[] dados) {
        try {
            return MessageDigest.getInstance("MD5").digest(dados);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Corpo do PUT; o SDK pode enviá-lo em aws-chunked (tamanho em hexa, assinatura, dados). */
    private static byte[] corpo(HttpExchange troca) throws IOException {
        var bruto = troca.getRequestBody().readAllBytes();
        if (troca.getRequestHeaders().getFirst("x-amz-decoded-content-length") == null) return bruto;
        var saida = new java.io.ByteArrayOutputStream();
        int pos = 0;
        while (pos < bruto.length) {
            int fim = pos;
            while (bruto[fim] != '\r') fim++;
            var cabecalho = new String(bruto, pos, fim - pos, StandardCharsets.US_ASCII);
            int tamanho = Integer.parseInt(cabecalho.split(";")[0], 16);
            if (tamanho == 0) break;
            saida.write(bruto, fim + 2, tamanho);
            pos = fim + 2 + tamanho + 2;
        }
        return saida.toByteArray();
    }
}
