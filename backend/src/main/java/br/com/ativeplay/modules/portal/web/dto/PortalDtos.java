package br.com.ativeplay.modules.portal.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Visões do portal do cliente: apenas campos seguros para o cliente final. */
public final class PortalDtos {

    private PortalDtos() {
    }

    public record EmpresaPortal(UUID id, String razaoSocial, String nomeFantasia, String cnpj, String cnpjFormatado,
            String regimeTributario, String status, String email, String telefone, String responsavelNome,
            LocalDate dataInicio) {
    }

    public record TarefaPortal(UUID id, String titulo, LocalDate data, Instant prazo, String status,
            Instant concluidaEm, String setor) {
    }

    public record DocumentoPortal(UUID id, String nome, String categoria, String mimeType, long tamanho,
            boolean enviadoPeloCliente, Instant criadoEm) {
    }
}
