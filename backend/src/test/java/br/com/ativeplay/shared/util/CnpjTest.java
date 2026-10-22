package br.com.ativeplay.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CnpjTest {

    @Test
    void aceitaCnpjNumericoComESemMascara() {
        assertThat(Cnpj.valido("11.222.333/0001-81")).isTrue();
        assertThat(Cnpj.valido("11222333000181")).isTrue();
    }

    @Test
    void aceitaCnpjAlfanumerico() {
        var base = "12ABC34501DE";
        assertThat(Cnpj.valido(base + Cnpj.digitos(base))).isTrue();
        assertThat(Cnpj.valido("12.abc.345/01de-35")).isTrue();
    }

    @Test
    void rejeitaDigitoErradoSequenciaRepetidaECaracteresInvalidos() {
        assertThat(Cnpj.valido("11222333000182")).isFalse();
        assertThat(Cnpj.valido("00000000000000")).isFalse();
        assertThat(Cnpj.valido("11 222 333 0001 81")).isFalse();
        assertThat(Cnpj.valido("")).isFalse();
        assertThat(Cnpj.valido(null)).isFalse();
    }

    @Test
    void normalizaEFormata() {
        assertThat(Cnpj.normalizar("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
        assertThat(Cnpj.formatar("11222333000181")).isEqualTo("11.222.333/0001-81");
    }
}
