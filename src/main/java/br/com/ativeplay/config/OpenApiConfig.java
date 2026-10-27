package br.com.ativeplay.config;

import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        // parâmetro injetado pelo resolver a partir do token; não faz parte do contrato da API
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UsuarioAutenticado.class);
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ative Play API")
                        .version("0.1.0")
                        .description("API da plataforma de gestão operacional para BPO Financeiro e Contabilidade"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
