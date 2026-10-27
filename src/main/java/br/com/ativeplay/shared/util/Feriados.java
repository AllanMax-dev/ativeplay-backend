package br.com.ativeplay.shared.util;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;

/**
 * Feriados nacionais do Brasil calculados no código (sem API externa): os fixos e a Sexta-feira
 * Santa, que depende da Páscoa. Carnaval e Corpus Christi são pontos facultativos e não entram;
 * feriados estaduais e municipais também não.
 */
public final class Feriados {

    private static final Set<MonthDay> FIXOS = Set.of(
            MonthDay.of(1, 1),    // Confraternização Universal
            MonthDay.of(4, 21),   // Tiradentes
            MonthDay.of(5, 1),    // Dia do Trabalho
            MonthDay.of(9, 7),    // Independência
            MonthDay.of(10, 12),  // Nossa Senhora Aparecida
            MonthDay.of(11, 2),   // Finados
            MonthDay.of(11, 15),  // Proclamação da República
            MonthDay.of(11, 20),  // Consciência Negra (nacional desde 2024)
            MonthDay.of(12, 25)); // Natal

    private Feriados() {
    }

    public static boolean isFeriadoNacional(LocalDate data) {
        if (FIXOS.contains(MonthDay.from(data))) {
            return data.getYear() >= 2024 || !(data.getMonthValue() == 11 && data.getDayOfMonth() == 20);
        }
        return data.equals(pascoa(data.getYear()).minusDays(2));
    }

    /** Domingo de Páscoa (algoritmo gregoriano anônimo, de Meeus/Jones/Butcher). */
    public static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
