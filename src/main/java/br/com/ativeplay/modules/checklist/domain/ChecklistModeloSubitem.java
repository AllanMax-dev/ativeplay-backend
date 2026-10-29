package br.com.ativeplay.modules.checklist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Passo de um item do checklist modelo. */
@Getter
@Setter
@Entity
@Table(name = "checklist_modelo_subitens")
public class ChecklistModeloSubitem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private ChecklistModeloItem item;

    @Column(nullable = false, length = 200)
    private String texto;

    @Column(nullable = false)
    private int ordem;
}
