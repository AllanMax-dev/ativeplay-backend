package br.com.ativeplay.modules.tarefa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Catálogo reutilizável de tarefas do escritório. */
@Getter
@Setter
@Entity
@Table(name = "padroes_tarefa")
public class PadraoTarefa extends RegraTarefa {

    @Column(nullable = false)
    private boolean ativo = true;
}
