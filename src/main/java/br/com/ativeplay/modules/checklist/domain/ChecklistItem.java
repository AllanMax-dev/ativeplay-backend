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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Observação pontual dentro de um cliente, com prazo e responsável opcionais
 * (ex.: "Cliente pendente de envio do acesso bancário"). Pode ter sub-itens.
 */
@Getter
@Setter
@Entity
@Table(name = "checklist_itens")
public class ChecklistItem extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "text")
    private String observacoes;

    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridade prioridade = Prioridade.MEDIA;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(name = "criado_por_id", updatable = false)
    private UUID criadoPorId;

    @Column(nullable = false)
    private boolean concluido;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Column(name = "concluido_por_id")
    private UUID concluidoPorId;

    @Column(nullable = false)
    private int ordem;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem asc")
    private List<ChecklistSubitem> subitens = new ArrayList<>();

    public void concluir(UUID usuarioId, Instant quando) {
        concluido = true;
        concluidoEm = quando;
        concluidoPorId = usuarioId;
    }

    public void reabrir() {
        concluido = false;
        concluidoEm = null;
        concluidoPorId = null;
    }
}
