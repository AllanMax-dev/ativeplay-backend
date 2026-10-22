package br.com.ativeplay.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configurações da aplicação (prefixo "ativeplay" no application.yml), validadas na inicialização. */
@Validated
@ConfigurationProperties(prefix = "ativeplay")
public record AtivePlayProperties(
        List<String> corsOrigins,
        @NotNull Jwt jwt,
        @NotBlank String encryptionKey,
        @NotBlank String uploadDir,
        boolean jobsEnabled) {

    public record Jwt(
            @NotBlank @Size(min = 32, message = "deve ter pelo menos 32 caracteres") String secret,
            @NotNull Duration accessTtl,
            int refreshTtlDias) {
    }
}
