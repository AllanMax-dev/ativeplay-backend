package br.com.ativeplay;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.security.SecureRandom;
import java.util.Base64;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: sobe a API inteira contra um PostgreSQL 16 real em
 * container (Testcontainers), com as migrations Flyway aplicadas. Requer Docker.
 * O container é único para todas as classes de teste (padrão singleton).
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.config.import=")
public abstract class IntegracaoTest {

    private static final String CHAVE_ATIVA = novaChave();
    protected static final String CHAVE_LEGADA = novaChave();

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void criptografiaDeTeste(DynamicPropertyRegistry registry) {
        registry.add("ativeplay.criptografia.chave-ativa-id", () -> "teste");
        registry.add("ativeplay.criptografia.chave-ativa", () -> CHAVE_ATIVA);
        registry.add("ativeplay.criptografia.chave-legada", () -> CHAVE_LEGADA);
        registry.add("ativeplay.criptografia.chaves-anteriores", () -> "");
    }

    private static String novaChave() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Autowired
    protected TestRestTemplate http;
}
