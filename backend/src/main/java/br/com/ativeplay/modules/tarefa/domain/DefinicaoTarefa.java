package br.com.ativeplay.modules.tarefa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Tarefa recorrente configurada para uma empresa; gera uma ocorrência por data aplicável. */
@Getter
@Setter
@Entity
@Table(name = "definicoes_tarefa")
public class DefinicaoTarefa extends RegraTarefa {

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "padrao_id")
    private UUID padraoId;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    /** Data da tarefa quando a recorrência é UNICA. */
    @Column(name = "data_unica")
    private LocalDate dataUnica;

    @Column(name = "visivel_cliente", nullable = false)
    private boolean visivelCliente;

    @Column(nullable = false)
    private boolean ativa = true;
}
