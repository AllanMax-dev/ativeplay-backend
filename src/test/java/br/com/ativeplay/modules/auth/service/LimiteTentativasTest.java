package br.com.ativeplay.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.shared.erro.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LimiteTentativasTest {

    private static final Duration JANELA = Duration.ofMinutes(15);

    /** Relógio controlável para avançar o tempo sem esperar. */
    static class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-01-01T10:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioMutavel relogio = new RelogioMutavel();
    private final LimiteTentativas limite = new LimiteTentativas(relogio);

    @Test
    void bloqueiaComStatus429AoAtingirOMaximo() {
        for (int i = 0; i < 3; i++) {
            limite.consumir("a", 3, JANELA);
        }
        assertThatThrownBy(() -> limite.consumir("a", 3, JANELA))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void chavesSaoIndependentes() {
        for (int i = 0; i < 3; i++) limite.consumir("a", 3, JANELA);
        assertThatCode(() -> limite.consumir("b", 3, JANELA)).doesNotThrowAnyException();
    }

    @Test
    void liberaDepoisDaJanela() {
        for (int i = 0; i < 3; i++) limite.consumir("a", 3, JANELA);
        relogio.agora = relogio.agora.plus(JANELA).plusSeconds(1);
        assertThatCode(() -> limite.consumir("a", 3, JANELA)).doesNotThrowAnyException();
    }

    @Test
    void limpezaDoLoginPreservaAJanelaMaiorDoCadastro() {
        var janelaCadastro = Duration.ofHours(1);
        for (int i = 0; i < 5; i++) limite.consumir("cadastro:ip", 5, janelaCadastro);
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(20));

        // A milésima operação dispara a limpeza global usando tráfego de login.
        for (int i = 0; i < 995; i++) falharLogin("login:" + i, 5);

        assertThatThrownBy(() -> limite.consumir("cadastro:ip", 5, janelaCadastro))
                .isInstanceOf(ApiException.class);
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(40));
        assertThatCode(() -> limite.consumir("cadastro:ip", 5, janelaCadastro)).doesNotThrowAnyException();
    }

    @Test
    void loginCorretoZeraFalhasAnteriores() {
        for (int rodada = 0; rodada < 3; rodada++) {
            falharLogin("a", 3);
            falharLogin("a", 3);
            assertThat(limite.executarLogin("a", 3, JANELA, () -> "ok")).isEqualTo("ok");
        }
    }

    @Test
    void erroInternoLiberaAVagaSemContarComoFalhaDeLogin() {
        assertThatThrownBy(() -> limite.executarLogin("a", 1, JANELA, () -> {
            throw new IllegalStateException("indisponível");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(limite.executarLogin("a", 1, JANELA, () -> "ok")).isEqualTo("ok");
    }

    @Test
    void sucessoNaoApagaOutraTentativaEmAndamento() {
        limite.executarLogin("a", 2, JANELA, () -> {
            assertThat(limite.executarLogin("a", 2, JANELA, () -> "ok")).isEqualTo("ok");
            falharLogin("a", 2);
            assertThatThrownBy(() -> limite.executarLogin("a", 2, JANELA, () -> "indevido"))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
            return "ok";
        });
    }

    @Test
    void cadastrosConcorrentesRespeitamOMaximo() throws Exception {
        var inicio = new CountDownLatch(1);
        var aceitos = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tarefas = new ArrayList<Future<?>>();
            for (int i = 0; i < 30; i++) tarefas.add(executor.submit(() -> {
                aguardar(inicio);
                try {
                    limite.consumir("cadastro:ip", 5, JANELA);
                    aceitos.incrementAndGet();
                } catch (ApiException e) {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                }
            }));
            inicio.countDown();
            for (var tarefa : tarefas) tarefa.get(10, TimeUnit.SECONDS);
        }
        assertThat(aceitos.get()).isEqualTo(5);
    }

    @Test
    void loginsEmAndamentoOcupamVagasMesmoDepoisDaJanela() throws Exception {
        var admitidos = new CountDownLatch(3);
        var liberar = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tarefas = new ArrayList<Future<?>>();
            try {
                for (int i = 0; i < 3; i++) tarefas.add(executor.submit(() -> {
                    assertThatThrownBy(() -> limite.executarLogin("a", 3, JANELA, () -> {
                        admitidos.countDown();
                        aguardar(liberar);
                        throw ApiException.naoAutorizado("senha incorreta");
                    })).isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
                }));
                aguardar(admitidos);
                relogio.agora = relogio.agora.plus(JANELA);
                assertThatThrownBy(() -> limite.executarLogin("a", 3, JANELA, () -> "indevido"))
                        .isInstanceOfSatisfying(ApiException.class,
                                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
            } finally {
                liberar.countDown();
            }
            for (var tarefa : tarefas) tarefa.get(10, TimeUnit.SECONDS);
        }
        assertThatThrownBy(() -> limite.executarLogin("a", 3, JANELA, () -> "indevido"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        relogio.agora = relogio.agora.plus(JANELA);
        assertThat(limite.executarLogin("a", 3, JANELA, () -> "ok")).isEqualTo("ok");
    }

    private void falharLogin(String chave, int maximo) {
        assertThatThrownBy(() -> limite.executarLogin(chave, maximo, JANELA, () -> {
            throw ApiException.naoAutorizado("senha incorreta");
        })).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
