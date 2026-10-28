package br.com.ativeplay.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.shared.armazenamento.ArmazenamentoS3;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import software.amazon.awssdk.services.s3.S3Client;

class S3ConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(S3Config.class, ArmazenamentoS3.class)
            .withInitializer(ctx -> {
                try {
                    var fontes = new YamlPropertySourceLoader().load("config", new ClassPathResource("application.yml"));
                    ctx.getEnvironment().getPropertySources().addLast(fontes.getFirst());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

    private ApplicationContextRunner s3Configurado() {
        return contexto.withPropertyValues("STORAGE_TIPO=s3", "S3_ENDPOINT=https://storage.example.test",
                "S3_BUCKET=documentos", "S3_ACCESS_KEY=teste", "S3_SECRET_KEY=segredo-de-teste");
    }

    @Test
    void modoLocalNaoExigeConfiguracaoS3() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(S3Client.class).doesNotHaveBean(S3Properties.class);
        });
        contexto.withPropertyValues("STORAGE_TIPO=local").run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(S3Client.class).doesNotHaveBean(ArmazenamentoS3.class);
        });
    }

    @Test
    void variaveisExistentesConfiguramOClienteEOArmazenamento() {
        s3Configurado().run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(S3Client.class).hasSingleBean(ArmazenamentoS3.class);
            var props = ctx.getBean(S3Properties.class);
            assertThat(props.getEndpoint().toString()).isEqualTo("https://storage.example.test");
            assertThat(props.getBucket()).isEqualTo("documentos");
            assertThat(props.getRegion()).isEqualTo("us-east-1");
            assertThat(props.getAccessKey()).isEqualTo("teste");
            assertThat(props.getSecretKey()).isEqualTo("segredo-de-teste");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"S3_ENDPOINT", "S3_REGION", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY"})
    void rejeitaConfiguracaoObrigatoriaVazia(String variavel) {
        s3Configurado().withPropertyValues(variavel + "=").run(ctx -> assertThat(ctx).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://storage.example.test", "storage.example.test", "https:///sem-host"})
    void rejeitaEndpointSemHostOuProtocoloHttp(String endpoint) {
        s3Configurado().withPropertyValues("S3_ENDPOINT=" + endpoint).run(ctx -> assertThat(ctx).hasFailed());
    }
}
