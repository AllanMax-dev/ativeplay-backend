package br.com.ativeplay.modules.integracao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Histórico de cada execução de sincronização. */
@Getter
@Setter
@Entity
@Table(name = "sincronizacoes_integracao")
public class SincronizacaoIntegracao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "integracao_id", nullable = false)
    private UUID integracaoId;

    @Column(nullable = false, length = 60)
    private String recurso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusSincronizacao status = StatusSincronizacao.EM_EXECUCAO;

    @Column(nullable = false)
    private int registros;

    @Column(columnDefinition = "text")
    private String erro;

    @Column(name = "iniciada_em", nullable = false)
    private Instant iniciadaEm = Instant.now();

    @Column(name = "finalizada_em")
    private Instant finalizadaEm;
}
