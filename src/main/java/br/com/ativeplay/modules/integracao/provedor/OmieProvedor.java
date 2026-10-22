package br.com.ativeplay.modules.integracao.provedor;

import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import java.util.List;
import org.springframework.stereotype.Component;

/** Omie: appKey e appSecret gerados no portal do desenvolvedor. */
@Component
public class OmieProvedor extends ProvedorErpBase {

    @Override
    public ProvedorIntegracao tipo() {
        return ProvedorIntegracao.OMIE;
    }

    @Override
    protected List<String> chavesObrigatorias() {
        return List.of("appKey", "appSecret");
    }

    @Override
    protected String nome() {
        return "Omie";
    }
}
