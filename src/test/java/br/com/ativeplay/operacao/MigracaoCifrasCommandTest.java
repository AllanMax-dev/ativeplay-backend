package br.com.ativeplay.operacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigracaoCifrasCommandTest {
    @Test
    void ajudaNaoExigeSegredosNemAbreBanco() {
        var saida = new ByteArrayOutputStream();
        assertThat(MigracaoCifrasCommand.executar(new String[] {"--help"}, Map.of(), new PrintStream(saida))).isZero();
        assertThat(saida.toString()).contains("--confirmar-backup", "--confirmar-manutencao");
    }

    @Test
    void recusaExecucaoSemConfirmacoesEArgumentosInvalidosAntesDeAbrirBanco() {
        for (var args : new String[][] {{}, {"--confirmar-backup"}, {"--confirmar-manutencao"},
                {"--confirmar-backup", "--confirmar-manutencao", "--lote=0"},
                {"--confirmar-backup", "--confirmar-manutencao", "--lote=1001"},
                {"--confirmar-backup", "--confirmar-manutencao", "--lote=invalido"},
                {"--confirmar-backup", "--confirmar-manutencao", "--lote"},
                {"--confirmar-backup", "--confirmar-manutencao", "--lote=1", "--lote=2"},
                {"--confirmar-backup", "--confirmar-manutencao", "--nao-existe"}}) {
            assertThat(MigracaoCifrasCommand.executar(args, Map.of(), new PrintStream(new ByteArrayOutputStream())))
                    .isEqualTo(2);
        }
    }

    @Test
    void configuracaoInvalidaNaoExpoeSegredosOuCausasNaSaida() {
        var saida = new ByteArrayOutputStream();
        var status = MigracaoCifrasCommand.executar(new String[] {"--confirmar-backup", "--confirmar-manutencao"},
                Map.of("ENCRYPTION_ACTIVE_KEY", "marcador-confidencial", "POSTGRES_PASSWORD", "senha-teste-privada"),
                new PrintStream(saida));
        assertThat(status).isEqualTo(1);
        assertThat(saida.toString()).doesNotContain("marcador-confidencial", "senha-teste-privada", "Exception", "at br.com");
    }
}
