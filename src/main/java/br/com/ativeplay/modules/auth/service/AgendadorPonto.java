package br.com.ativeplay.modules.auth.service;

import br.com.ativeplay.config.AtivePlayProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada minuto marca como pendentes os registros de ponto abertos sem sinal do navegador há mais
 * de ativeplay.ponto.pendente-minutos (padrão 15). Desligável com JOBS_ENABLED=false; o ShedLock
 * garante uma única execução entre as instâncias da API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendadorPonto {

    private final AtivePlayProperties props;
    private final SessaoAcessoService sessoesAcesso;

    // padrão no código; configurável por ativeplay.ponto.pendente-minutos (sem exigir mudança de config)
    @Value("${ativeplay.ponto.pendente-minutos:15}")
    private long pendenteMinutos;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @SchedulerLock(name = "marcarPontoPendente", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void marcarPendentes() {
        if (!props.jobsEnabled()) return;
        try {
            int marcados = sessoesAcesso.marcarPendentes(Instant.now().minus(Duration.ofMinutes(pendenteMinutos)));
            if (marcados > 0) log.info("{} registro(s) de ponto pendente(s)", marcados);
        } catch (RuntimeException e) {
            log.error("Falha ao marcar registros de ponto pendentes", e);
        }
    }
}
