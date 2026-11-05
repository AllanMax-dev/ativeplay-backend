package br.com.ativeplay.shared.util;

import static br.com.ativeplay.shared.util.CriptoVersionadaTest.legado;
import static br.com.ativeplay.shared.util.CriptoVersionadaTest.novaChave;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.config.CriptografiaProperties;
import org.junit.jupiter.api.Test;

class CofreCifrasTest {
    private final String ativa = novaChave();
    private final String antiga = novaChave();

    @Test
    void leLegadoEChavesAnterioresMasEscreveSomenteComAtiva() throws Exception {
        var cofre = new CofreCifras(new CriptografiaProperties("atual", ativa, antiga,
                "{\"anterior\":\"" + antiga + "\"}"));
        assertThat(cofre.decifrar(legado("legado", antiga))).isEqualTo("legado");
        assertThat(cofre.decifrar(Cripto.cifrar("versionado", antiga, "anterior"))).isEqualTo("versionado");
        var cifra = cofre.cifrar("novo");
        assertThat(cifra).startsWith("apc:1:atual:");
        assertThat(cofre.decifrar(cifra)).isEqualTo("novo");
        assertThat(cofre.usaChaveAtiva(cifra)).isTrue();
        assertThat(cofre.usaChaveAtiva(legado("legado", antiga))).isFalse();
    }

    @Test
    void permiteRetirarChavesAntigasAposMigracao() throws Exception {
        var cofre = new CofreCifras(new CriptografiaProperties("atual", ativa, "", ""));
        assertThat(cofre.decifrar(cofre.cifrar("novo"))).isEqualTo("novo");
        var legado = legado("antigo", antiga);
        assertThatThrownBy(() -> cofre.decifrar(legado)).isInstanceOf(IllegalStateException.class);
        var anterior = Cripto.cifrar("antigo", antiga, "anterior");
        assertThatThrownBy(() -> cofre.decifrar(anterior)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void falhaNaInicializacaoSemAtivaOuAoReutilizarChaveAntiga() {
        for (var props : new CriptografiaProperties[] {
                new CriptografiaProperties("atual", "", antiga, ""),
                new CriptografiaProperties("", ativa, antiga, ""),
                new CriptografiaProperties("atual", ativa, ativa, ""),
                new CriptografiaProperties("atual", ativa, "", "{\"antiga\":\"" + ativa + "\"}"),
                new CriptografiaProperties("atual", ativa, "", "{\"atual\":\"" + antiga + "\"}"),
                new CriptografiaProperties("atual", ativa, "invalida", "")}) {
            assertThatThrownBy(() -> new CofreCifras(props)).isInstanceOf(IllegalStateException.class)
                    .hasNoCause().hasMessageNotContaining(ativa).hasMessageNotContaining(antiga);
        }
    }

    @Test
    void rejeitaJsonMalformadoIdsDuplicadosEValoresNaoTextuaisSemExporEntrada() {
        for (var anteriores : new String[] {"{entrada-privada", "[]", "{\"antiga\":123}",
                "{\"antiga\":null}", "{\"antiga\":\"invalida\"}", "{} {}",
                "{\"antiga\":\"" + antiga + "\",\"antiga\":\"" + antiga + "\"}"}) {
            assertThatThrownBy(() -> new CofreCifras(new CriptografiaProperties("atual", ativa, "", anteriores)))
                    .isInstanceOf(IllegalStateException.class).hasNoCause()
                    .hasMessageNotContaining(anteriores).hasMessageNotContaining(antiga);
        }
        assertThat(new CriptografiaProperties("atual", ativa, antiga, antiga).toString())
                .doesNotContain(ativa, antiga);
    }
}
