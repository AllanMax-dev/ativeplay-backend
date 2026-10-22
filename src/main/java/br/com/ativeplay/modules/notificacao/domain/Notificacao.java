package br.com.ativeplay.modules.notificacao.domain;

import br.com.ativeplay.shared.dominio.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notificacoes")
public class Notificacao extends EntidadeBase {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(columnDefinition = "text")
    private String mensagem;

    /** Rota do frontend para abrir o item. */
    private String link;

    @Column(name = "lida_em")
    private Instant lidaEm;
}
