package br.com.ativeplay.modules.setor.domain;

import br.com.ativeplay.shared.dominio.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Setor operacional (Financeiro, Contábil, Fiscal, Departamento Pessoal...). */
@Getter
@Setter
@Entity
@Table(name = "setores")
public class Setor extends EntidadeBase {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(length = 7)
    private String cor;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false)
    private boolean ativo = true;
}
