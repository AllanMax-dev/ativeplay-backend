package br.com.ativeplay.modules.sessao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Período em que um usuário trabalhou "dentro" de uma empresa. */
@Getter
@Setter
@Entity
@Table(name = "sessoes_trabalho")
public class SessaoTrabalho {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(nullable = false)
    private Instant inicio;

    private Instant fim;
}
