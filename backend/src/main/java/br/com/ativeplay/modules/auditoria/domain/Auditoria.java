package br.com.ativeplay.modules.auditoria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Trilha de auditoria imutável. */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "escritorio_id", nullable = false)
    private UUID escritorioId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "empresa_id")
    private UUID empresaId;

    /** Padrão entidade.verbo, ex.: tarefa.concluir */
    @Column(nullable = false, length = 60)
    private String acao;

    @Column(nullable = false, length = 60)
    private String entidade;

    @Column(name = "entidade_id", length = 64)
    private String entidadeId;

    @Column(columnDefinition = "text")
    private String descricao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> dados;

    @Column(length = 45)
    private String ip;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
