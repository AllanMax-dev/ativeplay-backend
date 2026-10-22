package br.com.ativeplay.modules.documento.domain;

import br.com.ativeplay.shared.dominio.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Documento de uma empresa; com ocorrenciaId preenchido é anexo de uma tarefa. */
@Getter
@Setter
@Entity
@Table(name = "documentos")
public class Documento extends EntidadeBase {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "ocorrencia_id")
    private UUID ocorrenciaId;

    @Column(name = "enviado_por_id")
    private UUID enviadoPorId;

    @Column(nullable = false)
    private String nome;

    @Column(length = 60)
    private String categoria;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(nullable = false)
    private long tamanho;

    /** Caminho relativo no armazenamento (disco local ou bucket). */
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(length = 64)
    private String sha256;

    @Column(name = "visivel_cliente", nullable = false)
    private boolean visivelCliente = true;
}
