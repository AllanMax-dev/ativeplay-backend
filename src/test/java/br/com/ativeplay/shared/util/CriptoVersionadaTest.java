package br.com.ativeplay.shared.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

public class CriptoVersionadaTest {
    private final String chave = novaChave();

    @Test
    void escreveEnvelopeELeTextoUnicodeInclusiveVazioComIvAleatorio() {
        for (var texto : new String[] {"", "conteúdo de teste 🔐"}) {
            var payload = Cripto.cifrar(texto, chave, "nova");
            assertThat(payload).startsWith("apc:1:nova:");
            if (!texto.isEmpty()) assertThat(payload).doesNotContain(texto);
            assertThat(Cripto.decifrar(payload, Map.of("nova", chave)::get, null)).isEqualTo(texto);
            assertThat(Cripto.cifrar(texto, chave, "nova")).isNotEqualTo(payload);
        }
    }

    @Test
    void leLegadoSomenteComChaveLegadaExplicita() throws Exception {
        var payload = legado("dado antigo", chave);
        assertThat(Cripto.decifrar(payload, Map.<String, String>of()::get, chave)).isEqualTo("dado antigo");
        assertThatThrownBy(() -> Cripto.decifrar(payload, Map.of("nova", chave)::get, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void autenticaIdEVersaoMesmoQuandoDuasEntradasResolvemAMesmaChave() {
        var payload = Cripto.cifrar("teste", chave, "nova");
        assertThatThrownBy(() -> Cripto.decifrar(payload.replace(":nova:", ":outra:"), id -> chave, chave))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Cripto.decifrar(payload.replace(":1:", ":2:"), id -> chave, chave))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejeitaChaveAusenteDesconhecidaIncorretaETagAdulteradaSemVazarDados() {
        var payload = Cripto.cifrar("texto privado de teste", chave, "nova");
        assertThatThrownBy(() -> Cripto.decifrar(payload, id -> null, chave)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Cripto.decifrar(payload, id -> novaChave(), chave)).isInstanceOf(IllegalStateException.class);
        var bytes = Base64.getDecoder().decode(payload.substring(payload.lastIndexOf(':') + 1));
        bytes[bytes.length - 1] ^= 1;
        var adulterado = "apc:1:nova:" + Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> Cripto.decifrar(adulterado, id -> chave, null))
                .isInstanceOf(IllegalStateException.class).hasNoCause()
                .hasMessageNotContaining(chave).hasMessageNotContaining(adulterado)
                .hasMessageNotContaining("texto privado de teste");
    }

    @Test
    void rejeitaPayloadsTruncadosMalformadosEIdsInvalidos() {
        for (var payload : new String[] {"", "%%%", "apc:1:nova:", "apc:1:nova:AA==", "apc:1:nova:AA==:extra",
                Base64.getEncoder().encodeToString(new byte[27]), "apc:1:invalido!:AAAA"}) {
            assertThatThrownBy(() -> Cripto.decifrar(payload, id -> chave, chave))
                    .isInstanceOf(IllegalStateException.class).hasNoCause();
        }
        assertThatThrownBy(() -> Cripto.decifrar(null, id -> chave, chave)).isInstanceOf(IllegalStateException.class);
        for (var id : new String[] {"", "a:b", "a b", "a".repeat(65)}) {
            assertThatThrownBy(() -> Cripto.cifrar("teste", chave, id)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> Cripto.cifrar("teste", null, "nova")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Cripto.cifrar("teste", "invalida", "nova")).isInstanceOf(IllegalStateException.class);
    }

    public static String novaChave() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Fixture independente: reproduz exatamente o formato anterior, sem chamar a implementação nova.
    public static String legado(String texto, String chave) throws Exception {
        var iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(chave), "AES"),
                new GCMParameterSpec(128, iv));
        var cifra = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        var bytes = new byte[iv.length + cifra.length];
        System.arraycopy(iv, 0, bytes, 0, iv.length);
        System.arraycopy(cifra, 0, bytes, iv.length, cifra.length);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
