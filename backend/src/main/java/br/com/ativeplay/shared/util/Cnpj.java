package br.com.ativeplay.shared.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CNPJ numérico e alfanumérico (IN RFB 2.229/2024): os 12 primeiros caracteres podem ser
 * letras ou dígitos e os 2 verificadores são numéricos. Mesmo algoritmo do frontend
 * (src/js/01_utilitarios.js).
 */
public final class Cnpj {

    private static final Pattern CARACTERES = Pattern.compile("^[A-Z0-9./-]+$");
    private static final Pattern FORMATO = Pattern.compile("^[A-Z0-9]{12}\\d{2}$");
    private static final Pattern REPETIDO = Pattern.compile("^(\\d)\\1{13}$");
    private static final int[] PESOS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {
    }

    public static String normalizar(String valor) {
        return valor == null ? null : valor.replaceAll("[./-]", "").trim().toUpperCase(Locale.ROOT);
    }

    public static boolean valido(String valor) {
        if (valor == null) return false;
        var bruto = valor.trim().toUpperCase(Locale.ROOT);
        if (!CARACTERES.matcher(bruto).matches()) return false;
        var n = normalizar(bruto);
        if (!FORMATO.matcher(n).matches() || REPETIDO.matcher(n).matches()) return false;
        return digitos(n.substring(0, 12)).equals(n.substring(12));
    }

    /** Calcula os dois dígitos verificadores para uma base de 12 caracteres. */
    public static String digitos(String base) {
        int primeiro = calcular(base, PESOS_1);
        int segundo = calcular(base + primeiro, PESOS_2);
        return "" + primeiro + segundo;
    }

    public static String formatar(String valor) {
        var n = normalizar(valor);
        if (n == null || n.length() != 14) return valor;
        return n.substring(0, 2) + "." + n.substring(2, 5) + "." + n.substring(5, 8) + "/" + n.substring(8, 12) + "-"
                + n.substring(12);
    }

    private static int calcular(String valor, int[] pesos) {
        int total = 0;
        for (int i = 0; i < pesos.length; i++) {
            total += (valor.charAt(i) - '0') * pesos[i];
        }
        int resto = total % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
