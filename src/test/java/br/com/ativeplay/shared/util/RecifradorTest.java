package br.com.ativeplay.shared.util;

import static br.com.ativeplay.shared.util.CriptoVersionadaTest.legado;
import static br.com.ativeplay.shared.util.CriptoVersionadaTest.novaChave;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.config.CriptografiaProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecifradorTest {
    private final String antiga = novaChave();
    private final CofreCifras cofre = new CofreCifras(new CriptografiaProperties("nova", novaChave(), antiga, ""));
    private final Recifrador recifrador = new Recifrador(cofre);

    @Test
    void mantemTextoIdsNulosEContagemESegundaExecucaoNaoReescreveCifras() throws Exception {
        var id = UUID.randomUUID();
        var atual = cofre.cifrar("atual");
        var linhas = List.of(new Recifrador.Linha(id, legado("anterior", antiga)),
                new Recifrador.Linha(UUID.randomUUID(), atual), new Recifrador.Linha(UUID.randomUUID(), null));
        var lote = recifrador.preparar(linhas);
        assertThat(lote).hasSameSizeAs(linhas);
        assertThat(lote.getFirst().id()).isEqualTo(id);
        assertThat(cofre.usaChaveAtiva(lote.getFirst().cifra())).isTrue();
        assertThat(cofre.decifrar(lote.getFirst().cifra())).isEqualTo("anterior");
        assertThat(lote.get(1).cifra()).isEqualTo(atual);
        assertThat(lote.get(2).cifra()).isNull();
        assertThat(recifrador.preparar(lote)).isEqualTo(lote);
        assertThat(cofre.decifrar(linhas.getFirst().cifra())).isEqualTo("anterior");
    }

    @Test
    void loteComFalhaNaoEntregaResultadoParcialNemAlteraEntrada() throws Exception {
        var primeira = new Recifrador.Linha(UUID.randomUUID(), legado("anterior", antiga));
        var invalida = new Recifrador.Linha(UUID.randomUUID(), "corrompida");
        var linhas = List.of(primeira, invalida);
        assertThatThrownBy(() -> recifrador.preparar(linhas)).isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThat(linhas.getFirst()).isEqualTo(primeira);
        assertThat(cofre.decifrar(primeira.cifra())).isEqualTo("anterior");
    }

    @Test
    void validaIntegridadeMesmoQuandoCabecalhoJaIndicaChaveAtiva() {
        var linha = new Recifrador.Linha(UUID.randomUUID(), "apc:1:nova:AAAA");
        assertThatThrownBy(() -> recifrador.preparar(List.of(linha))).isInstanceOf(IllegalStateException.class);
        assertThat(linha.toString()).doesNotContain(linha.cifra());
    }
}
