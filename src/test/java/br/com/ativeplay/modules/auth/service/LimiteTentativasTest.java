package br.com.ativeplay.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.shared.erro.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
            limite.verificar("a", 3, JANELA);
            limite.registrar("a", JANELA);
        }
        assertThatThrownBy(() -> limite.verificar("a", 3, JANELA))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void chavesSaoIndependentes() {
        for (int i = 0; i < 3; i++) limite.registrar("a", JANELA);
        assertThatCode(() -> limite.verificar("b", 3, JANELA)).doesNotThrowAnyException();
    }

    @Test
    void liberaDepoisDaJanela() {
        for (int i = 0; i < 3; i++) limite.registrar("a", JANELA);
        relogio.agora = relogio.agora.plus(JANELA).plusSeconds(1);
        assertThatCode(() -> limite.verificar("a", 3, JANELA)).doesNotThrowAnyException();
    }

    @Test
    void limpezaDoLoginPreservaAJanelaMaiorDoCadastro() {
        var janelaCadastro = Duration.ofHours(1);
        for (int i = 0; i < 5; i++) limite.registrar("cadastro:ip", janelaCadastro);
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(20));

        // A milésima operação dispara a limpeza global usando tráfego de login.
        for (int i = 0; i < 995; i++) limite.registrar("login:" + i, JANELA);

        assertThatThrownBy(() -> limite.verificar("cadastro:ip", 5, janelaCadastro))
                .isInstanceOf(ApiException.class);
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(40));
        assertThatCode(() -> limite.verificar("cadastro:ip", 5, janelaCadastro)).doesNotThrowAnyException();
    }

    @Test
    void limparZeraAChave() {
        for (int i = 0; i < 3; i++) limite.registrar("a", JANELA);
        limite.limpar("a");
        assertThatCode(() -> limite.verificar("a", 3, JANELA)).doesNotThrowAnyException();
    }
}
