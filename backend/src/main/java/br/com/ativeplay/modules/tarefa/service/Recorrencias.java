package br.com.ativeplay.modules.tarefa.service;

import br.com.ativeplay.modules.tarefa.domain.DefinicaoTarefa;
import br.com.ativeplay.modules.tarefa.domain.Recorrencia;
import br.com.ativeplay.shared.util.Datas;
import java.time.LocalDate;
import java.util.List;

/**
 * Decide se uma regra de recorrência gera ocorrência em uma data. Considera fins de
 * semana como dias não úteis; feriados ainda não são tratados.
 */
public final class Recorrencias {

    private Recorrencias() {
    }

    public static boolean deveOcorrer(DefinicaoTarefa d, LocalDate data) {
        return deveOcorrer(d.getRecorrencia(), d.getDiasSemana(), d.getDiaMes(), d.getDataUnica(),
                d.isApenasDiasUteis(), data);
    }

    public static boolean deveOcorrer(Recorrencia recorrencia, List<Integer> diasSemana, Integer diaMes,
            LocalDate dataUnica, boolean apenasDiasUteis, LocalDate data) {
        return switch (recorrencia) {
            case DIARIA -> !apenasDiasUteis || Datas.diaUtil(data);
            case SEMANAL -> diasSemana != null && diasSemana.contains(Datas.diaSemana(data));
            case MENSAL -> diaMes != null && data.equals(dataMensal(data.getYear(), data.getMonthValue(), diaMes,
                    apenasDiasUteis));
            case UNICA -> data.equals(dataUnica);
        };
    }

    /**
     * Data efetiva da recorrência mensal: o dia configurado (limitado ao último dia do mês).
     * Se cair em fim de semana e apenasDiasUteis, antecipa para o dia útil anterior; se isso
     * sair do mês, adia para o próximo dia útil.
     */
    static LocalDate dataMensal(int ano, int mes, int diaMes, boolean apenasDiasUteis) {
        var inicio = LocalDate.of(ano, mes, 1);
        var alvo = inicio.withDayOfMonth(Math.min(diaMes, inicio.lengthOfMonth()));
        if (!apenasDiasUteis || Datas.diaUtil(alvo)) return alvo;

        var anterior = alvo;
        while (!Datas.diaUtil(anterior)) anterior = anterior.minusDays(1);
        if (anterior.getMonthValue() == mes) return anterior;

        var seguinte = alvo;
        while (!Datas.diaUtil(seguinte)) seguinte = seguinte.plusDays(1);
        return seguinte;
    }
}
