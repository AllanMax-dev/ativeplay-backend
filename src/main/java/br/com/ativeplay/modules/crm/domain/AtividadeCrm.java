package br.com.ativeplay.modules.crm.domain;

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
@Table(name = "atividades_crm")
public class AtividadeCrm extends EntidadeBase {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "oportunidade_id", nullable = false)
    private UUID oportunidadeId;

    @Column(name = "autor_id")
    private UUID autorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoAtividadeCrm tipo = TipoAtividadeCrm.NOTA;

    @Column(nullable = false, columnDefinition = "text")
    private String descricao;

    @Column(name = "agendada_para")
    private Instant agendadaPara;

    @Column(name = "concluida_em")
    private Instant concluidaEm;
}
