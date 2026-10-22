package br.com.ativeplay.modules.integracao.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Conexão de uma empresa com um ERP (Conta Azul, Omie). */
@Getter
@Setter
@Entity
@Table(name = "integracoes")
public class Integracao extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvedorIntegracao provedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusIntegracao status = StatusIntegracao.PENDENTE;

    /** AES-256-GCM; nunca retornadas pela API. */
    @Column(name = "credenciais_cifradas", columnDefinition = "text")
    private String credenciaisCifradas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuracoes = new HashMap<>();

    @Column(name = "ultima_sincronizacao")
    private Instant ultimaSincronizacao;

    @Column(name = "ultimo_erro", columnDefinition = "text")
    private String ultimoErro;
}
