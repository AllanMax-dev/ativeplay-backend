package br.com.ativeplay.modules.tarefa.service;

import br.com.ativeplay.config.AtivePlayProperties;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.shared.util.Datas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotinas agendadas da operação (desligáveis com JOBS_ENABLED=false; com várias instâncias
 * da API, o ShedLock garante que só uma execute cada rotina por vez):
 * - 00:05 (Brasília) e na inicialização: gera as ocorrências do dia;
 * - a cada minuto: marca prazos vencidos (SLA).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendadorTarefas {

    private final AtivePlayProperties props;
    private final EscritorioRepository escritorios;
    private final GeracaoOcorrenciasService geracao;

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "gerarOcorrenciasDoDia", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void gerarDoDia() {
        if (!props.jobsEnabled()) return;
        for (var escritorio : escritorios.findByAtivoTrue()) {
            try {
                geracao.gerar(escritorio.getId(), Datas.hoje(escritorio.getFusoHorario()), null);
            } catch (RuntimeException e) {
                log.error("Falha ao gerar ocorrências do escritório {}", escritorio.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @SchedulerLock(name = "varrerSla", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void varrerSla() {
        if (!props.jobsEnabled()) return;
        try {
            int marcadas = geracao.varrerSla();
            if (marcadas > 0) log.info("{} ocorrência(s) com prazo vencido", marcadas);
        } catch (RuntimeException e) {
            log.error("Falha na varredura de SLA", e);
        }
    }
}
