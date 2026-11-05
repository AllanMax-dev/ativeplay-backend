package br.com.ativeplay.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.shared.util.CofreCifras;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class CriptografiaConfigTest {
    @Test
    void configuraChaveAtivaSemDependerDeChaveLegada() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        new ApplicationContextRunner().withUserConfiguration(Config.class).withBean(CofreCifras.class)
                .withInitializer(ctx -> {
                    var fontes = ctx.getEnvironment().getPropertySources();
                    fontes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    fontes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    fontes.addFirst(new MapPropertySource("teste", Map.of("ENCRYPTION_ACTIVE_KEY_ID", "teste",
                            "ENCRYPTION_ACTIVE_KEY", Base64.getEncoder().encodeToString(bytes))));
                    try {
                        fontes.addLast(new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yml")).getFirst());
                    } catch (Exception e) { throw new IllegalStateException(e); }
                }).run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var cofre = ctx.getBean(CofreCifras.class);
                    assertThat(cofre.decifrar(cofre.cifrar("teste"))).isEqualTo("teste");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CriptografiaProperties.class)
    static class Config {}
}
