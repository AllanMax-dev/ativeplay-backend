package br.com.ativeplay.modules.checklist.domain;

import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Item do checklist modelo do escritório. Toda empresa nova recebe uma cópia dele
 * (o prazo vira data: criação da empresa + prazoDias).
 */
@Getter
@Setter
@Entity
@Table(name = "checklist_modelo_itens")
public class ChecklistModeloItem extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "text")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridade prioridade = Prioridade.MEDIA;

    @Column(name = "prazo_dias")
    private Integer prazoDias;

    @Column(nullable = false)
    private int ordem;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem asc")
    private List<ChecklistModeloSubitem> subitens = new ArrayList<>();
}
