package br.com.ativeplay.shared.armazenamento;

import br.com.ativeplay.shared.erro.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Armazenamento em um bucket S3 compatível (pensado para o Supabase Storage). Ativado com STORAGE_TIPO=s3;
 * configurado por S3_ENDPOINT, S3_REGION, S3_BUCKET, S3_ACCESS_KEY e S3_SECRET_KEY.
 */
@Service
@ConditionalOnProperty(name = "STORAGE_TIPO", havingValue = "s3")
public class ArmazenamentoS3 implements ArmazenamentoService, DisposableBean {

    private final S3Client s3;
    private final String bucket;

    public ArmazenamentoS3(@Value("${S3_ENDPOINT}") String endpoint, @Value("${S3_REGION:us-east-1}") String regiao,
            @Value("${S3_BUCKET}") String bucket, @Value("${S3_ACCESS_KEY}") String accessKey,
            @Value("${S3_SECRET_KEY}") String secretKey) {
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(regiao))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // Supabase e outros S3 compatíveis usam o bucket no caminho, não no host
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public ArquivoSalvo salvar(UUID escritorioId, String nomeOriginal, InputStream conteudo) {
        var chave = ArmazenamentoService.novaChave(escritorioId, nomeOriginal);
        try {
            // o S3 precisa do tamanho antes do envio: grava num temporário calculando o SHA-256
            var temporario = Files.createTempFile("ativeplay-upload-", ".tmp");
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                long tamanho;
                try (var entrada = new DigestInputStream(conteudo, digest)) {
                    tamanho = Files.copy(entrada, temporario, StandardCopyOption.REPLACE_EXISTING);
                }
                s3.putObject(r -> r.bucket(bucket).key(chave), RequestBody.fromFile(temporario));
                return new ArquivoSalvo(chave, HexFormat.of().formatHex(digest.digest()), tamanho);
            } finally {
                Files.deleteIfExists(temporario);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Resource abrir(String storageKey) {
        try {
            var tamanho = s3.headObject(r -> r.bucket(bucket).key(storageKey)).contentLength();
            return new ObjetoS3(storageKey, tamanho);
        } catch (NoSuchKeyException e) {
            throw ApiException.naoEncontrado("Arquivo");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) throw ApiException.naoEncontrado("Arquivo");
            throw e;
        }
    }

    @Override
    public void remover(String storageKey) {
        s3.deleteObject(r -> r.bucket(bucket).key(storageKey));
    }

    @Override
    public void destroy() {
        s3.close();
    }

    /** Resource que busca o objeto no bucket somente quando o conteúdo é lido. */
    private class ObjetoS3 extends AbstractResource {

        private final String chave;
        private final long tamanho;

        ObjetoS3(String chave, long tamanho) {
            this.chave = chave;
            this.tamanho = tamanho;
        }

        @Override
        public String getDescription() {
            return "s3://" + bucket + "/" + chave;
        }

        @Override
        public long contentLength() {
            return tamanho;
        }

        @Override
        public InputStream getInputStream() {
            return s3.getObject(r -> r.bucket(bucket).key(chave));
        }
    }
}
