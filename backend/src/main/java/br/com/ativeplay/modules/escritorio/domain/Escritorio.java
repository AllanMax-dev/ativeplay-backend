package br.com.ativeplay.modules.escritorio.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Escritório de BPO/contabilidade: o tenant da plataforma. */
@Getter
@Setter
@Entity
@Table(name = "escritorios")
public class Escritorio extends EntidadeAtualizavel {

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(length = 14)
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plano plano = Plano.TRIAL;

    @Column(name = "fuso_horario", nullable = false, length = 64)
    private String fusoHorario = "America/Sao_Paulo";

    @Column(nullable = false)
    private boolean ativo = true;
}
