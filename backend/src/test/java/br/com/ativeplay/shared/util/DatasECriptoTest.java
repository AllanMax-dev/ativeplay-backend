package br.com.ativeplay.shared.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DatasECriptoTest {

    private static final String CHAVE = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void diaSemanaSegueConvencaoDoFrontend() {
        assertThat(Datas.diaSemana(LocalDate.of(2026, 10, 18))).isZero(); // domingo
        assertThat(Datas.diaSemana(LocalDate.of(2026, 10, 24))).isEqualTo(6); // sábado
        assertThat(Datas.diaUtil(LocalDate.of(2026, 10, 19))).isTrue();
        assertThat(Datas.diaUtil(LocalDate.of(2026, 10, 17))).isFalse();
    }

    @Test
    void converteHorarioLocalDoEscritorioEmInstante() {
        assertThat(Datas.instanteLocal(LocalDate.of(2026, 10, 21), "18:00", "America/Sao_Paulo"))
                .isEqualTo(Instant.parse("2026-10-21T21:00:00Z"));
        assertThat(Datas.instanteLocal(LocalDate.of(2026, 7, 1), "09:30", "Europe/Lisbon"))
                .isEqualTo(Instant.parse("2026-07-01T08:30:00Z"));
    }

    @Test
    void cifraEDecifraComAesGcm() {
        var cifrado = Cripto.cifrar("{\"appKey\":\"123\"}", CHAVE);
        assertThat(cifrado).doesNotContain("appKey");
        assertThat(Cripto.decifrar(cifrado, CHAVE)).isEqualTo("{\"appKey\":\"123\"}");
        assertThat(Cripto.cifrar("x", CHAVE)).isNotEqualTo(Cripto.cifrar("x", CHAVE)); // IV aleatório
    }

    @Test
    void detectaAdulteracaoDoTextoCifrado() {
        var bytes = Base64.getDecoder().decode(Cripto.cifrar("segredo", CHAVE));
        bytes[bytes.length - 1] ^= 1;
        var adulterado = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> Cripto.decifrar(adulterado, CHAVE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sha256EmHexadecimal() {
        assertThat(Cripto.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
