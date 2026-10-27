package br.com.ativeplay.shared.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Datas de competência (LocalDate) e conversão de horários locais do escritório em instantes. */
public final class Datas {

    public static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    private Datas() {
    }

    public static LocalDate hoje(String fuso) {
        return LocalDate.now(zona(fuso));
    }

    public static ZoneId zona(String fuso) {
        return fuso == null || fuso.isBlank() ? FUSO_PADRAO : ZoneId.of(fuso);
    }

    /** Dia útil: não é fim de semana nem feriado nacional. */
    public static boolean diaUtil(LocalDate data) {
        var dia = data.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !Feriados.isFeriadoNacional(data);
    }

    /** 0 = domingo ... 6 = sábado (mesma convenção do frontend e da coluna dias_semana). */
    public static int diaSemana(LocalDate data) {
        return data.getDayOfWeek().getValue() % 7;
    }

    /** Instante correspondente a "data HH:mm" no fuso informado. */
    public static Instant instanteLocal(LocalDate data, String horario, String fuso) {
        return data.atTime(LocalTime.parse(horario)).atZone(zona(fuso)).toInstant();
    }
}
