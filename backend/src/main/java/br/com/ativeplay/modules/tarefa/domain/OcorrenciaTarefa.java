package br.com.ativeplay.modules.tarefa.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Instância de uma tarefa em uma data: é o que a equipe executa. */
@Getter
@Setter
@Entity
@Table(name = "ocorrencias_tarefa")
public class OcorrenciaTarefa extends EntidadeAtualizavel {

    public static final Set<StatusOcorrencia> ABERTAS =
            EnumSet.of(StatusOcorrencia.PENDENTE, StatusOcorrencia.EM_ANDAMENTO, StatusOcorrencia.PAUSADA);

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    /** Nulo para tarefas avulsas. */
    @Column(name = "definicao_id")
    private UUID definicaoId;

    @Column(name = "setor_id")
    private UUID setorId;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(columnDefinition = "text")
    private String descricao;

    /** Data de competência. */
    @Column(nullable = false)
    private LocalDate data;

    /** SLA de entrega: data + horário limite, no fuso do escritório. */
    @Column(nullable = false)
    private Instant prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridade prioridade = Prioridade.MEDIA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusOcorrencia status = StatusOcorrencia.PENDENTE;

    @Column(name = "tempo_estimado_min")
    private Integer tempoEstimadoMin;

    @Column(name = "sla_violado", nullable = false)
    private boolean slaViolado;

    @Column(name = "visivel_cliente", nullable = false)
    private boolean visivelCliente;

    /** Tempo acumulado dos intervalos já encerrados do cronômetro. */
    @Column(name = "tempo_gasto_ms", nullable = false)
    private long tempoGastoMs;

    /** Início do intervalo em andamento (nulo = cronômetro parado). */
    @Column(name = "cronometro_inicio")
    private Instant cronometroInicio;

    @Column(name = "iniciada_em")
    private Instant iniciadaEm;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    @Column(name = "concluida_por_id")
    private UUID concluidaPorId;

    @Version
    @Column(nullable = false)
    private int versao;

    public boolean isAberta() {
        return ABERTAS.contains(status);
    }

    /** Tempo total trabalhado, incluindo o intervalo em andamento. */
    public long tempoGastoAtualMs(Instant agora) {
        return cronometroInicio == null
                ? tempoGastoMs
                : tempoGastoMs + Math.max(0, Duration.between(cronometroInicio, agora).toMillis());
    }

    /** Encerra o intervalo do cronômetro, se houver, acumulando o tempo. */
    public void pararCronometro(Instant agora) {
        if (cronometroInicio != null) {
            tempoGastoMs = tempoGastoAtualMs(agora);
            cronometroInicio = null;
        }
    }
}
