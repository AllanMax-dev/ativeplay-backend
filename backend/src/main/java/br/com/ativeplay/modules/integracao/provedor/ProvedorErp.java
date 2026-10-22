package br.com.ativeplay.modules.integracao.provedor;

import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import java.time.Instant;
import java.util.Map;

/** Adaptador de um ERP externo. Cada provedor conhece o formato das suas credenciais e da sua API. */
public interface ProvedorErp {

    ProvedorIntegracao tipo();

    /** Valida o formato das credenciais antes de gravá-las (lança ApiException 400). */
    void validarCredenciais(Map<String, String> credenciais);

    /** Verifica se as credenciais dão acesso à API do provedor (lança exceção em caso de falha). */
    void testarConexao(Map<String, String> credenciais);

    /** Sincroniza o recurso desde o instante informado (nulo = carga completa); retorna registros processados. */
    int sincronizar(Map<String, String> credenciais, String recurso, Instant desde);
}
