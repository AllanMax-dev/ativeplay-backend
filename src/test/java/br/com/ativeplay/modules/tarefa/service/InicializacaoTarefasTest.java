package br.com.ativeplay.modules.tarefa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.ativeplay.config.AtivePlayProperties;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class InicializacaoTarefasTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void inicializacaoRespeitaTravaOcupadaEExecutaQuandoLiberada() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var escritorios = ctx.getBean(EscritorioRepository.class);
            var trava = ctx.getBean(LockProvider.class).lock(new LockConfiguration(Instant.now(),
                    "gerarOcorrenciasDoDia", Duration.ofMinutes(10), Duration.ZERO)).orElseThrow();

            ctx.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                    ctx.getSourceApplicationContext(), Duration.ZERO));
            verifyNoInteractions(escritorios);

            trava.unlock();
            ctx.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                    ctx.getSourceApplicationContext(), Duration.ZERO));
            verify(escritorios).findByAtivoTrue();
        });
    }

    @Test
    void chamadaDoAgendamentoTambemRespeitaATrava() {
        contexto.run(ctx -> {
            var escritorios = ctx.getBean(EscritorioRepository.class);
            var trava = ctx.getBean(LockProvider.class).lock(new LockConfiguration(Instant.now(),
                    "gerarOcorrenciasDoDia", Duration.ofMinutes(10), Duration.ZERO)).orElseThrow();

            ctx.getBean(AgendadorTarefas.class).gerarDoDia();
            verifyNoInteractions(escritorios);
            trava.unlock();
            ctx.getBean(AgendadorTarefas.class).gerarDoDia();
            verify(escritorios).findByAtivoTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
    @Import({AgendadorTarefas.class, InicializacaoTarefas.class})
    static class Config {
        @Bean
        LockProvider lockProvider() {
            var ocupada = new AtomicBoolean();
            return config -> ocupada.compareAndSet(false, true)
                    ? Optional.of(() -> ocupada.set(false)) : Optional.empty();
        }

        @Bean
        AtivePlayProperties properties() {
            var props = mock(AtivePlayProperties.class);
            when(props.jobsEnabled()).thenReturn(true);
            return props;
        }

        @Bean
        EscritorioRepository escritorios() {
            return mock(EscritorioRepository.class);
        }

        @Bean
        GeracaoOcorrenciasService geracao() {
            return mock(GeracaoOcorrenciasService.class);
        }
    }
}
