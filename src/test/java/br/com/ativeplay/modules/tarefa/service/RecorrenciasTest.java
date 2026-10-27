package br.com.ativeplay.modules.tarefa.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.modules.tarefa.domain.Recorrencia;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecorrenciasTest {

    private static final LocalDate SEXTA = LocalDate.of(2026, 10, 16);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 19);

    private static boolean ocorre(Recorrencia r, List<Integer> dias, Integer diaMes, LocalDate unica, boolean uteis,
            LocalDate data) {
        return Recorrencias.deveOcorrer(r, dias, diaMes, unica, uteis, data);
    }

    @Test
    void diariaPulaFimDeSemanaQuandoApenasDiasUteis() {
        assertThat(ocorre(Recorrencia.DIARIA, List.of(), null, null, true, SEXTA)).isTrue();
        assertThat(ocorre(Recorrencia.DIARIA, List.of(), null, null, true, SABADO)).isFalse();
        assertThat(ocorre(Recorrencia.DIARIA, List.of(), null, null, true, DOMINGO)).isFalse();
        assertThat(ocorre(Recorrencia.DIARIA, List.of(), null, null, false, DOMINGO)).isTrue();
    }

    @Test
    void semanalUsaDiasDaSemanaComDomingoZero() {
        var segundaEQuinta = List.of(1, 4);
        assertThat(ocorre(Recorrencia.SEMANAL, segundaEQuinta, null, null, true, SEGUNDA)).isTrue();
        assertThat(ocorre(Recorrencia.SEMANAL, segundaEQuinta, null, null, true, SEXTA)).isFalse();
        assertThat(ocorre(Recorrencia.SEMANAL, List.of(0), null, null, true, DOMINGO)).isTrue();
        assertThat(ocorre(Recorrencia.SEMANAL, null, null, null, true, SEGUNDA)).isFalse();
    }

    @Test
    void mensalNoDiaConfiguradoEmDiaUtil() {
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), 16, null, true, SEXTA)).isTrue();
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), 16, null, true, SEGUNDA)).isFalse();
    }

    @Test
    void mensalDia31EmFevereiroCaiNoUltimoDiaDoMes() {
        // 28/02/2026 é sábado: sem a regra de dia útil, fica no dia 28.
        assertThat(Recorrencias.dataMensal(2026, 2, 31, false)).isEqualTo(LocalDate.of(2026, 2, 28));
        // Com a regra de dia útil, antecipa para sexta 27/02.
        assertThat(Recorrencias.dataMensal(2026, 2, 31, true)).isEqualTo(LocalDate.of(2026, 2, 27));
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), 31, null, true, LocalDate.of(2026, 2, 27))).isTrue();
    }

    @Test
    void mensalEmFimDeSemanaAntecipaParaDiaUtilAnterior() {
        // Dia 18/10/2026 é domingo → sexta 16/10.
        assertThat(Recorrencias.dataMensal(2026, 10, 18, true)).isEqualTo(SEXTA);
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), 18, null, true, SEXTA)).isTrue();
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), 18, null, true, DOMINGO)).isFalse();
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), 18, null, false, DOMINGO)).isTrue();
    }

    @Test
    void mensalQueAnteciparSairiaDoMesAdiaParaProximoDiaUtil() {
        // 01/02/2026 é domingo: antecipar cairia em janeiro, então adia para segunda 02/02.
        assertThat(Recorrencias.dataMensal(2026, 2, 1, true)).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), 1, null, true, LocalDate.of(2026, 1, 30))).isFalse();
    }

    @Test
    void mensalSemDiaConfiguradoNuncaOcorre() {
        assertThat(ocorre(Recorrencia.MENSAL, List.of(), null, null, true, SEXTA)).isFalse();
    }

    @Test
    void unicaApenasNaData() {
        assertThat(ocorre(Recorrencia.UNICA, List.of(), null, SABADO, true, SABADO)).isTrue();
        assertThat(ocorre(Recorrencia.UNICA, List.of(), null, SABADO, true, DOMINGO)).isFalse();
        assertThat(ocorre(Recorrencia.UNICA, List.of(), null, null, true, DOMINGO)).isFalse();
    }

    @Test
    void diariaApenasDiasUteisPulaFeriadoNacional() {
        var finados = LocalDate.of(2026, 11, 2); // segunda-feira
        assertThat(ocorre(Recorrencia.DIARIA, List.of(), null, null, true, finados)).isFalse();
        assertThat(ocorre(Recorrencia.DIARIA, List.of(), null, null, false, finados)).isTrue();
        var sextaSanta = LocalDate.of(2026, 4, 3);
        assertThat(ocorre(Recorrencia.DIARIA, List.of(), null, null, true, sextaSanta)).isFalse();
    }

    @Test
    void mensalEmFeriadoAntecipaParaDiaUtilAnterior() {
        // 02/11/2027 (Finados) é terça: antecipa para segunda 01/11.
        assertThat(Recorrencias.dataMensal(2027, 11, 2, true)).isEqualTo(LocalDate.of(2027, 11, 1));
        // 01/01/2026 (quinta, feriado): antecipar sairia do mês, então adia para sexta 02/01.
        assertThat(Recorrencias.dataMensal(2026, 1, 1, true)).isEqualTo(LocalDate.of(2026, 1, 2));
    }
}
