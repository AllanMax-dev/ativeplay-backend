package br.com.ativeplay.modules.tarefa.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Campos comuns entre o catálogo (PadraoTarefa) e a recorrência da empresa (DefinicaoTarefa). */
@Getter
@Setter
@MappedSuperclass
public abstract class RegraTarefa extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "setor_id")
    private UUID setorId;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Recorrencia recorrencia = Recorrencia.MENSAL;

    /** 0 = domingo ... 6 = sábado (recorrência SEMANAL). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dias_semana", nullable = false, columnDefinition = "integer[]")
    private List<Integer> diasSemana = new ArrayList<>();

    /** Recorrência MENSAL; dias maiores que o mês caem no último dia. */
    @Column(name = "dia_mes")
    private Integer diaMes;

    @Column(name = "apenas_dias_uteis", nullable = false)
    private boolean apenasDiasUteis = true;

    /** HH:mm no fuso do escritório. */
    @Column(name = "horario_limite", nullable = false, length = 5)
    private String horarioLimite = "18:00";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridade prioridade = Prioridade.MEDIA;

    /** Meta de execução em minutos (produtividade). */
    @Column(name = "tempo_estimado_min")
    private Integer tempoEstimadoMin;

    @Column(nullable = false)
    private int ordem;
}
