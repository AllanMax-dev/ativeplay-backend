package br.com.ativeplay.modules.documento.web.dto;

import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import java.time.Instant;
import java.util.UUID;

/** Metadados de um documento; o conteúdo é baixado em /documentos/{id}/download. */
public record DocumentoResposta(UUID id, UUID empresaId, UUID ocorrenciaId, String nome, String categoria,
        String mimeType, long tamanho, String sha256, boolean visivelCliente, UsuarioRef enviadoPor,
        Instant criadoEm) {
}
