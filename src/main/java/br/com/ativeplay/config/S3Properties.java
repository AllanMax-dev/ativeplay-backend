package br.com.ativeplay.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

/** Vinculadas e validadas somente quando STORAGE_TIPO=s3. */
@Getter
@Setter
@Validated
public class S3Properties {

    @NotNull
    private URI endpoint;
    @NotBlank
    private String region = "us-east-1";
    @NotBlank
    private String bucket;
    @NotBlank
    private String accessKey;
    @NotBlank
    private String secretKey;

    @AssertTrue(message = "endpoint deve ser uma URL HTTP ou HTTPS com host")
    public boolean isEndpointValido() {
        return endpoint == null || (endpoint.getHost() != null
                && ("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme())));
    }
}
