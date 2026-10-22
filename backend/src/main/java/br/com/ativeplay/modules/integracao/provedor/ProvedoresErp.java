package br.com.ativeplay.modules.integracao.provedor;

import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import br.com.ativeplay.shared.erro.ApiException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registro dos adaptadores disponíveis, indexados pelo tipo de provedor. */
@Component
public class ProvedoresErp {

    private final Map<ProvedorIntegracao, ProvedorErp> porTipo = new EnumMap<>(ProvedorIntegracao.class);

    public ProvedoresErp(List<ProvedorErp> provedores) {
        provedores.forEach(p -> porTipo.put(p.tipo(), p));
    }

    public ProvedorErp de(ProvedorIntegracao tipo) {
        var provedor = porTipo.get(tipo);
        if (provedor == null) throw ApiException.invalido("Provedor não suportado: " + tipo);
        return provedor;
    }
}
