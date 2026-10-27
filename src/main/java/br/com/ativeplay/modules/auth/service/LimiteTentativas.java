package br.com.ativeplay.modules.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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
    private final ConcurrentHashMap<String, List<Tentativa>> tentativas = new ConcurrentHashMap<>();
    private final AtomicInteger operacoes = new AtomicInteger();

    public LimiteTentativas() {
        this(Clock.systemUTC());
    }

    LimiteTentativas(Clock relogio) {
        this.relogio = relogio;
    }

    /** Cadastro: verifica e contabiliza a tentativa em uma única operação. */
    public void consumir(String chave, int maximo, Duration janela) {
        adicionar(chave, maximo, janela, false);
    }

    /**
     * Reserva uma vaga sem manter locks durante a autenticação. Só falhas 401 ficam na janela;
     * sucesso limpa falhas anteriores, preservando outros logins em andamento.
     */
    public <T> T executarLogin(String chave, int maximo, Duration janela, Supplier<T> autenticar) {
        var tentativa = adicionar(chave, maximo, janela, true);
        boolean sucesso = false;
        boolean falha = false;
        try {
            T resultado = autenticar.get();
            sucesso = true;
            return resultado;
        } catch (ApiException e) {
            falha = e.getStatus() == HttpStatus.UNAUTHORIZED;
            throw e;
        } finally {
            concluir(chave, tentativa, janela, sucesso, falha);
        }
    }

    private Tentativa adicionar(String chave, int maximo, Duration janela, boolean emAndamento) {
        if (maximo < 1 || janela.isZero() || janela.isNegative()) {
            throw new IllegalArgumentException("Limite e janela devem ser positivos");
        }
        var tentativa = new Tentativa();
        tentativas.compute(chave, (k, fila) -> {
            var agora = relogio.instant();
            if (fila == null) fila = new ArrayList<>();
            descartarExpiradas(fila, agora);
            if (fila.size() >= maximo) {
                var proxima = fila.stream().filter(t -> t.expiracao != null).map(t -> t.expiracao)
                        .min(Instant::compareTo).orElse(agora.plus(janela));
                long minutos = Math.max(1, Duration.between(agora, proxima).toMinutes() + 1);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas tentativas. Tente novamente em cerca de " + minutos + " minuto(s)");
            }
            tentativa.expiracao = emAndamento ? null : agora.plus(janela);
            fila.add(tentativa);
            return fila;
        });
        limparSeNecessario();
        return tentativa;
    }

    private void concluir(String chave, Tentativa tentativa, Duration janela, boolean sucesso, boolean falha) {
        tentativas.computeIfPresent(chave, (k, fila) -> {
            if (sucesso) fila.removeIf(t -> t.expiracao != null);
            if (falha) tentativa.expiracao = relogio.instant().plus(janela);
            else fila.remove(tentativa);
            return fila.isEmpty() ? null : fila;
        });
    }

    private static void descartarExpiradas(List<Tentativa> fila, Instant agora) {
        fila.removeIf(t -> t.expiracao != null && !t.expiracao.isAfter(agora));
    }

    private void limparSeNecessario() {
        if (operacoes.incrementAndGet() % LIMPEZA_A_CADA != 0) return;
        // A limpeza usa a mesma operação atômica das admissões, sem filas removidas em uso.
        tentativas.keySet().forEach(chave -> tentativas.computeIfPresent(chave, (k, fila) -> {
            descartarExpiradas(fila, relogio.instant());
            return fila.isEmpty() ? null : fila;
        }));
    }

    private static final class Tentativa {
        // null: autenticação em andamento, que não expira antes de terminar.
        private Instant expiracao;
    }
}
