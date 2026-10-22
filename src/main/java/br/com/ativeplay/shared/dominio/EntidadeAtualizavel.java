package br.com.ativeplay.shared.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.UpdateTimestamp;

/** Entidade com data da última alteração. */
@Getter
@MappedSuperclass
public abstract class EntidadeAtualizavel extends EntidadeBase {

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
