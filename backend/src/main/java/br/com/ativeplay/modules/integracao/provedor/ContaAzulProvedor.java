package br.com.ativeplay.modules.integracao.provedor;

import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import java.util.List;
import org.springframework.stereotype.Component;

/** Conta Azul (OAuth 2.0): clientId, clientSecret e refreshToken do aplicativo autorizado. */
@Component
public class ContaAzulProvedor extends ProvedorErpBase {

    @Override
    public ProvedorIntegracao tipo() {
        return ProvedorIntegracao.CONTA_AZUL;
    }

    @Override
    protected List<String> chavesObrigatorias() {
        return List.of("clientId", "clientSecret", "refreshToken");
    }

    @Override
    protected String nome() {
        return "Conta Azul";
    }
}
