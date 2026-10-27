package br.com.ativeplay;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.modules.tarefa.service.AgendadorTarefas;
import java.time.Duration;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Os jobs agendados usam trava no banco (tabela criada pela migration V2). */
class TravaDosJobsTest extends IntegracaoTest {

    @Autowired
    LockProvider lockProvider;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void segundaInstanciaNaoObtemATravaEnquantoAPrimeiraAMantem() {
        var config = new LockConfiguration(java.time.Instant.now(), "teste-trava-" + System.nanoTime(),
                Duration.ofMinutes(1), Duration.ZERO);

        var primeira = lockProvider.lock(config);
        assertThat(primeira).isPresent();
        assertThat(lockProvider.lock(config)).as("outra instância").isEmpty();

        primeira.get().unlock();
        var depois = lockProvider.lock(config);
        assertThat(depois).as("após liberar").isPresent();
        depois.get().unlock();
    }

    @Test
    void tabelaShedlockExisteNoBanco() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name = 'shedlock'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void osDoisJobsAgendadosTemTrava() throws NoSuchMethodException {
        for (var metodo : new String[] {"gerarDoDia", "varrerSla"}) {
            assertThat(AgendadorTarefas.class.getMethod(metodo).isAnnotationPresent(SchedulerLock.class))
                    .as(metodo).isTrue();
        }
    }
}
