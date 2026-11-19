
package br.com.ativeplay.modules.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sessoes_acesso")
public class SessaoAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private Instant inicio;

    private Instant fim;

    @Column(name = "ultimo_sinal")
    private Instant ultimoSinal;

    /** Sem sinal do navegador pelo tempo limite: fim continua nulo até o gestor informar a saída. */
    @Column(nullable = false)
    private boolean pendente;
}
