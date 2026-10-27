package br.com.ativeplay.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Confere as configurações de application.yml / application-prod.yml sem subir o contexto. */
class PerfilProducaoTest {

    private static StandardEnvironment ambiente(Map<String, Object> variaveis, String... arquivos) throws IOException {
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("variaveis", variaveis));
        for (var arquivo : arquivos) {
            for (PropertySource<?> fonte : new YamlPropertySourceLoader().load(arquivo, new ClassPathResource(arquivo))) {
                env.getPropertySources().addLast(fonte);
            }
        }
        return env;
    }

    @Test
    void portaVemDePortAntesDeServerPort() throws IOException {
        assertThat(ambiente(Map.of("PORT", "10000", "SERVER_PORT", "9000"), "application.yml")
                .getProperty("server.port")).isEqualTo("10000");
        assertThat(ambiente(Map.of("SERVER_PORT", "9000"), "application.yml").getProperty("server.port"))
                .isEqualTo("9000");
        assertThat(ambiente(Map.of(), "application.yml").getProperty("server.port")).isEqualTo("8080");
    }

    @Test
    void bancoExigeSslPorPadraoNoPerfilProd() throws IOException {
        var env = ambiente(Map.of("DB_HOST", "aws-0-sa-east-1.pooler.supabase.com"), "application-prod.yml");
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(
                "jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require");

        var customizado = ambiente(Map.of("DB_HOST", "h", "POSTGRES_PORT", "6543", "POSTGRES_DB", "d",
                "DB_SSLMODE", "verify-full"), "application-prod.yml");
        assertThat(customizado.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://h:6543/d?sslmode=verify-full");
    }

    @Test
    void swaggerFicaDesligadoEOSeedNaoEhAtivadoPeloPerfil() throws IOException {
        var env = ambiente(Map.of(), "application-prod.yml");
        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(env.containsProperty("spring.profiles.include")).isFalse();
        assertThat(env.containsProperty("spring.profiles.active")).isFalse();
    }
}
