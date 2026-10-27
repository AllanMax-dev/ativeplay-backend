package br.com.ativeplay.modules.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import br.com.ativeplay.shared.erro.ApiException;

/**
 * Limite de tentativas em memória (janela deslizante por chave), sem dependências externas.
 * O estado é por instância da API e some ao reiniciar: protege contra força bruta simples,
 * não substitui um limitador distribuído.
 */
@Component
public class LimiteTentativas {

    private static final int LIMPEZA_A_CADA = 1000;

    private final Clock relogio;
    private final ConcurrentHashMap<String, Deque<Instant>> tentativas = new ConcurrentHashMap<>();
    private int operacoes;

    public LimiteTentativas() {
        this(Clock.systemUTC());
    }

    LimiteTentativas(Clock relogio) {
        this.relogio = relogio;
    }

    /** Lança 429 se a chave já atingiu {@code maximo} tentativas dentro da janela. */
    public void verificar(String chave, int maximo, Duration janela) {
        var agora = relogio.instant();
        var fila = tentativas.get(chave);
        if (fila == null) return;
        synchronized (fila) {
            descartarExpiradas(fila, agora, janela);
            if (fila.size() >= maximo) {
                long minutos = Math.max(1, Duration.between(agora, fila.peekFirst().plus(janela)).toMinutes() + 1);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas tentativas. Tente novamente em cerca de " + minutos + " minuto(s)");
            }
        }
    }

    /** Registra uma tentativa para a chave. */
    public void registrar(String chave, Duration janela) {
        var agora = relogio.instant();
        var fila = tentativas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            descartarExpiradas(fila, agora, janela);
            fila.addLast(agora);
        }
        limparSeNecessario(agora, janela);
    }

    /** Zera a chave (ex.: login bem-sucedido). */
    public void limpar(String chave) {
        tentativas.remove(chave);
    }

    private static void descartarExpiradas(Deque<Instant> fila, Instant agora, Duration janela) {
        while (!fila.isEmpty() && !fila.peekFirst().plus(janela).isAfter(agora)) fila.pollFirst();
    }

    private synchronized void limparSeNecessario(Instant agora, Duration janela) {
        if (++operacoes % LIMPEZA_A_CADA != 0) return;
        tentativas.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                descartarExpiradas(e.getValue(), agora, janela);
                return e.getValue().isEmpty();
            }
        });
    }
}
