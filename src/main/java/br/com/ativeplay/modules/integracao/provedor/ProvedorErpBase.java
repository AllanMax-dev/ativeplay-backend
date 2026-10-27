package br.com.ativeplay.modules.integracao.provedor;

import br.com.ativeplay.shared.erro.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Comportamento comum: validação das chaves obrigatórias e provedor SIMULADO. Não há chamada à
 * API real do ERP (sem sandbox): a conexão é aceita se as credenciais estão completas e a
 * sincronização apenas finge buscar dados, devolvendo uma contagem plausível de registros.
 */
abstract class ProvedorErpBase implements ProvedorErp {

    protected abstract List<String> chavesObrigatorias();

    protected abstract String nome();

    @Override
    public void validarCredenciais(Map<String, String> credenciais) {
        var faltando = chavesObrigatorias().stream()
                .filter(c -> credenciais == null || credenciais.get(c) == null || credenciais.get(c).isBlank())
                .toList();
        if (!faltando.isEmpty()) {
            throw ApiException.invalido("Credenciais do " + nome() + " incompletas: informe " + String.join(", ", faltando));
        }
    }

    @Override
    public boolean simulado() {
        return true;
    }

    @Override
    public void testarConexao(Map<String, String> credenciais) {
        // SIMULADO: só o formato das credenciais é verificado.
        validarCredenciais(credenciais);
    }

    @Override
    public int sincronizar(Map<String, String> credenciais, String recurso, Instant desde) {
        // SIMULADO: carga completa traz dezenas/centenas de registros; sincronização incremental, poucos.
        return desde == null ? sortear(40, 400) : sortear(1, 30);
    }

    /** Ponto de sorteio isolado para os testes. */
    protected int sortear(int minimo, int maximo) {
        return ThreadLocalRandom.current().nextInt(minimo, maximo + 1);
    }
}
