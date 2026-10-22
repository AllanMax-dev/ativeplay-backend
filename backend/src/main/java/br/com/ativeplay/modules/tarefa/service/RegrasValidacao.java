package br.com.ativeplay.modules.tarefa.service;

import br.com.ativeplay.modules.tarefa.domain.Recorrencia;
import br.com.ativeplay.shared.erro.ApiException;
import java.time.LocalDate;
import java.util.List;

/** Coerência entre o tipo de recorrência e seus campos. */
final class RegrasValidacao {

    private RegrasValidacao() {
    }

    static void validar(Recorrencia recorrencia, List<Integer> diasSemana, Integer diaMes, LocalDate dataUnica,
            boolean exigeDataUnica) {
        switch (recorrencia) {
            case SEMANAL -> {
                if (diasSemana == null || diasSemana.isEmpty()) {
                    throw ApiException.invalido("Recorrência semanal exige ao menos um dia da semana (0 a 6)");
                }
            }
            case MENSAL -> {
                if (diaMes == null) throw ApiException.invalido("Recorrência mensal exige o dia do mês (1 a 31)");
            }
            case UNICA -> {
                if (exigeDataUnica && dataUnica == null) {
                    throw ApiException.invalido("Recorrência única exige a data (dataUnica)");
                }
            }
            case DIARIA -> {
            }
        }
    }
}
