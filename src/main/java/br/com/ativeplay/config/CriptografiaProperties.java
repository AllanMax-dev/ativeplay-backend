package br.com.ativeplay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ativeplay.criptografia")
public record CriptografiaProperties(String chaveAtivaId, String chaveAtiva, String chaveLegada, String chavesAnteriores) {
    @Override
    public String toString() {
        return "CriptografiaProperties[protegidas]";
    }
}
