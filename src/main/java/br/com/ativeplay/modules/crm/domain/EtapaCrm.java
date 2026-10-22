package br.com.ativeplay.modules.crm.domain;

import br.com.ativeplay.shared.dominio.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Coluna do funil de vendas. */
@Getter
@Setter
@Entity
@Table(name = "etapas_crm")
public class EtapaCrm extends EntidadeBase {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(length = 7)
    private String cor;

    @Column(nullable = false)
    private int ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoEtapaCrm tipo = TipoEtapaCrm.ABERTA;
}
