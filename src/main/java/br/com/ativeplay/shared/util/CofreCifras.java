package br.com.ativeplay.shared.util;

import br.com.ativeplay.config.CriptografiaProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Único ponto de cifragem da aplicação: escrita ativa, leitura explícita das chaves antigas. */
@Component
public class CofreCifras {
    private final String ativaId;
    private final String ativa;
    private final String legada;
    private final Map<String, String> chaves;

    public CofreCifras(CriptografiaProperties props) {
        ativaId = props.chaveAtivaId();
        ativa = props.chaveAtiva();
        legada = props.chaveLegada() == null || props.chaveLegada().isBlank() ? null : props.chaveLegada();
        Cripto.validarKeyId(ativaId);
        Cripto.validarChave(ativa);
        if (legada != null) validarAntiga(legada);
        var mapa = new HashMap<String, String>();
        if (props.chavesAnteriores() != null && !props.chavesAnteriores().isBlank()) {
            try {
                var json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(props.chavesAnteriores());
                if (!json.isObject()) throw new IllegalArgumentException();
                var campos = json.fields();
                while (campos.hasNext()) {
                    var campo = campos.next();
                    Cripto.validarKeyId(campo.getKey());
                    if (campo.getKey().equals(ativaId) || !campo.getValue().isTextual()) throw new IllegalArgumentException();
                    var chave = campo.getValue().textValue();
                    validarAntiga(chave);
                    mapa.put(campo.getKey(), chave);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Configuração de chaves anteriores inválida");
            }
        }
        mapa.put(ativaId, ativa);
        chaves = Map.copyOf(mapa);
    }

    private void validarAntiga(String chave) {
        Cripto.validarChave(chave);
        if (MessageDigest.isEqual(Base64.getDecoder().decode(ativa), Base64.getDecoder().decode(chave))) {
            throw new IllegalStateException("A chave ativa deve ser diferente das chaves antigas");
        }
    }

    public String cifrar(String texto) {
        return Cripto.cifrar(texto, ativa, ativaId);
    }

    public String decifrar(String payload) {
        return Cripto.decifrar(payload, chaves::get, legada);
    }

    /** Somente classificação; a autenticidade deve ser verificada com decifrar antes de confiar no resultado. */
    public boolean usaChaveAtiva(String payload) {
        return payload != null && payload.startsWith("apc:1:" + ativaId + ":");
    }

    public String chaveAtivaId() {
        return ativaId;
    }
}
