package br.com.ativeplay.modules.integracao.provedor;

import br.com.ativeplay.shared.erro.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Comportamento comum: validação das chaves obrigatórias e chamadas ainda não implementadas. */
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
    public void testarConexao(Map<String, String> credenciais) {
        // TODO: chamar a API real do provedor. Por enquanto só o formato das credenciais é verificado.
        validarCredenciais(credenciais);
    }

    @Override
    public int sincronizar(Map<String, String> credenciais, String recurso, Instant desde) {
        // TODO: implementar o cliente HTTP do provedor. Não simulamos sucesso para não mascarar a pendência.
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "Sincronização com " + nome() + " ainda não implementada");
    }
}
