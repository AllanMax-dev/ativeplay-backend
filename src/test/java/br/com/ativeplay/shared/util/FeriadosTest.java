package br.com.ativeplay.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeriadosTest {

    @Test
    void calculaAPascoa() {
        assertThat(Feriados.pascoa(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(Feriados.pascoa(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(Feriados.pascoa(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(Feriados.pascoa(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void reconheceFeriadosFixosEMovel() {
        List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 21), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 4, 3), LocalDate.of(2025, 4, 18), LocalDate.of(2027, 3, 26))
                .forEach(d -> assertThat(Feriados.isFeriadoNacional(d)).as(d.toString()).isTrue());
    }

    @Test
    void diasComunsECarnavalNaoSaoFeriadoNacional() {
        assertThat(Feriados.isFeriadoNacional(LocalDate.of(2026, 10, 19))).isFalse();
        assertThat(Feriados.isFeriadoNacional(LocalDate.of(2026, 2, 17))).isFalse(); // terça de Carnaval (facultativo)
        assertThat(Feriados.isFeriadoNacional(LocalDate.of(2026, 6, 4))).isFalse(); // Corpus Christi (facultativo)
    }

    @Test
    void consciencianegraSoEFeriadoNacionalDesde2024() {
        assertThat(Feriados.isFeriadoNacional(LocalDate.of(2023, 11, 20))).isFalse();
        assertThat(Feriados.isFeriadoNacional(LocalDate.of(2024, 11, 20))).isTrue();
    }

    @Test
    void feriadoNaoEDiaUtil() {
        assertThat(Datas.diaUtil(LocalDate.of(2026, 11, 2))).isFalse(); // segunda, Finados
        assertThat(Datas.diaUtil(LocalDate.of(2026, 11, 3))).isTrue();
    }
}
