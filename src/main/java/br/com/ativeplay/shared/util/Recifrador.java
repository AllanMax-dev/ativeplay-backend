package br.com.ativeplay.shared.util;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public final class Recifrador {
    private final CofreCifras cofre;

    public Recifrador(CofreCifras cofre) { this.cofre = cofre; }

    /** Prepara todo o lote em memória; nenhuma alteração é entregue se uma cifra falhar. */
    public List<Linha> preparar(List<Linha> linhas) {
        var resultado = new ArrayList<Linha>(linhas.size());
        for (var linha : linhas) {
            if (linha.cifra() == null) {
                resultado.add(linha);
                continue;
            }
            var texto = cofre.decifrar(linha.cifra());
            if (cofre.usaChaveAtiva(linha.cifra())) {
                resultado.add(linha);
                continue;
            }
            var nova = cofre.cifrar(texto);
            if (!texto.equals(cofre.decifrar(nova))) throw new IllegalStateException("Falha de integridade na recifragem");
            resultado.add(new Linha(linha.id(), nova));
        }
        return List.copyOf(resultado);
    }
    public record Linha(UUID id, String cifra) {
        @Override public String toString() { return "Linha[id=" + id + ", cifra=protegida]"; }
    }
}
