package br.com.ativeplay.modules.auth.domain;

import br.com.ativeplay.shared.dominio.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Sessão de refresh token; só o hash SHA-256 do token é persistido. */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends EntidadeBase {

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(length = 45)
    private String ip;
}
