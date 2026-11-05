package br.com.ativeplay.operacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.config.CriptografiaProperties;
import br.com.ativeplay.shared.util.CofreCifras;
import br.com.ativeplay.shared.util.CriptoVersionadaTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL isolado, sem contexto da aplicação e sem leitura de .env. */
@Testcontainers
class MigracaoCifrasTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private final String antiga = CriptoVersionadaTest.novaChave();
    private final String atual = CriptoVersionadaTest.novaChave();
    private final CofreCifras cofre = new CofreCifras(new CriptografiaProperties("nova", atual, antiga, ""));
    private DriverManagerDataSource ds;
    private JdbcTemplate jdbc;
    private MigracaoCifras migracao;

    @BeforeEach
    void preparar() {
        ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP TABLE IF EXISTS senhas_empresa, integracoes, rotacoes_cifras CASCADE");
        jdbc.execute("CREATE TABLE senhas_empresa (id UUID PRIMARY KEY, senha_cifrada TEXT)");
        jdbc.execute("CREATE TABLE integracoes (id UUID PRIMARY KEY, credenciais_cifradas TEXT)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5__rotacao_cifras.sql")).execute(ds);
        migracao = new MigracaoCifras(ds, cofre);
    }

    @Test
    void migraAmbasTabelasPreservaNulosContagensERepeticao() throws Exception {
        for (var tabela : MigracaoCifras.Tabela.values()) {
            inserir(tabela, 1, legado("antiga"));
            inserir(tabela, 2, cofre.cifrar("atual"));
            inserir(tabela, 3, null);
        }
        migracao.executar(1, progresso -> {});
        var primeira = cifra(MigracaoCifras.Tabela.SENHAS, 1);
        migracao.executar(2, progresso -> {});
        assertThat(cifra(MigracaoCifras.Tabela.SENHAS, 1)).isEqualTo(primeira);
        for (var tabela : MigracaoCifras.Tabela.values()) {
            assertThat(cofre.decifrar(cifra(tabela, 1))).isEqualTo("antiga");
            assertThat(cofre.usaChaveAtiva(cifra(tabela, 1))).isTrue();
            assertThat(cofre.decifrar(cifra(tabela, 2))).isEqualTo("atual");
            assertThat(cifra(tabela, 3)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + tabela.nome, Long.class)).isEqualTo(3);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rotacoes_cifras WHERE concluida", Long.class)).isEqualTo(2);
    }

    @Test
    void retomaAposFalhaSemAvancarCheckpointOuReescreverLoteConfirmado() throws Exception {
        inserir(MigracaoCifras.Tabela.SENHAS, 1, legado("primeira"));
        inserir(MigracaoCifras.Tabela.SENHAS, 2, "corrompida");
        assertThatThrownBy(() -> migracao.executar(1, progresso -> {})).isInstanceOf(IllegalStateException.class).hasNoCause();
        var confirmada = cifra(MigracaoCifras.Tabela.SENHAS, 1);
        assertThat(jdbc.queryForObject("SELECT ultimo_id FROM rotacoes_cifras WHERE tabela='senhas_empresa'", UUID.class)).isEqualTo(id(1));
        jdbc.update("UPDATE senhas_empresa SET senha_cifrada=? WHERE id=?", legado("segunda"), id(2));
        migracao.executar(1, progresso -> {});
        assertThat(cifra(MigracaoCifras.Tabela.SENHAS, 1)).isEqualTo(confirmada);
        assertThat(cofre.decifrar(cifra(MigracaoCifras.Tabela.SENHAS, 2))).isEqualTo("segunda");
    }

    @Test
    void erroSqlNoSegundoUpdateRevertePrimeiroUpdateECheckpointDoLote() throws Exception {
        var primeira = legado("primeira");
        var segunda = legado("segunda");
        inserir(MigracaoCifras.Tabela.SENHAS, 1, primeira);
        inserir(MigracaoCifras.Tabela.SENHAS, 2, segunda);
        jdbc.execute("ALTER TABLE senhas_empresa ADD CONSTRAINT falha_teste CHECK (id <> '" + id(2)
                + "'::uuid OR senha_cifrada NOT LIKE 'apc:%')");
        assertThatThrownBy(() -> migracao.executar(2, progresso -> {})).isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThat(cifra(MigracaoCifras.Tabela.SENHAS, 1)).isEqualTo(primeira);
        assertThat(cifra(MigracaoCifras.Tabela.SENHAS, 2)).isEqualTo(segunda);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rotacoes_cifras", Long.class)).isZero();
        jdbc.execute("ALTER TABLE senhas_empresa DROP CONSTRAINT falha_teste");
        migracao.executar(2, progresso -> {});
        assertThat(cofre.decifrar(cifra(MigracaoCifras.Tabela.SENHAS, 1))).isEqualTo("primeira");
    }

    @Test
    void verificacaoFinalDetectaCifraAntigaInseridaAntesDoCheckpointMesmoSemMudarContagem() throws Exception {
        inserir(MigracaoCifras.Tabela.SENHAS, 1, legado("primeira"));
        inserir(MigracaoCifras.Tabela.SENHAS, 2, legado("segunda"));
        var antigaNovamente = legado("primeira");
        assertThatThrownBy(() -> migracao.executar(1, progresso -> {
            if (progresso.tabela().equals("senhas_empresa") && progresso.processadas() == 2) {
                jdbc.update("UPDATE senhas_empresa SET senha_cifrada=? WHERE id=?", antigaNovamente, id(1));
            }
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT concluida FROM rotacoes_cifras WHERE tabela='senhas_empresa'", Boolean.class)).isFalse();
    }

    @Test
    void contagemDivergenteImpedeConclusao() throws Exception {
        inserir(MigracaoCifras.Tabela.SENHAS, 1, legado("primeira"));
        assertThatThrownBy(() -> migracao.executar(1, progresso -> {
            if (progresso.tabela().equals("senhas_empresa") && progresso.processadas() == 1) {
                jdbc.update("DELETE FROM senhas_empresa WHERE id=?", id(1));
            }
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outraExecucaoNaoObtemTravaESegundaExecucaoPodeRetomarAposFalha() throws Exception {
        inserir(MigracaoCifras.Tabela.SENHAS, 1, legado("primeira"));
        assertThatThrownBy(() -> migracao.executar(1, progresso -> {
            assertThatThrownBy(() -> new MigracaoCifras(ds, cofre).executar(1, outro -> {}))
                    .isInstanceOf(IllegalStateException.class);
            throw new IllegalStateException("interrupção simulada após commit");
        })).isInstanceOf(IllegalStateException.class);
        new MigracaoCifras(ds, cofre).executar(1, progresso -> {});
        assertThat(cofre.decifrar(cifra(MigracaoCifras.Tabela.SENHAS, 1))).isEqualTo("primeira");
    }

    @Test
    void segundaRotacaoRetomaERetiraChaveVersionadaAnterior() throws Exception {
        inserir(MigracaoCifras.Tabela.SENHAS, 1, legado("primeira"));
        inserir(MigracaoCifras.Tabela.SENHAS, 2, cofre.cifrar("segunda"));
        migracao.executar(1, progresso -> {});
        var proxima = CriptoVersionadaTest.novaChave();
        var cofre2 = new CofreCifras(new CriptografiaProperties("proxima", proxima, "", "{\"nova\":\"" + atual + "\"}"));
        var segundaRotacao = new MigracaoCifras(ds, cofre2);
        assertThatThrownBy(() -> segundaRotacao.executar(1, progresso -> {
            throw new IllegalStateException("interrupção simulada");
        })).isInstanceOf(IllegalStateException.class);
        var confirmada = cifra(MigracaoCifras.Tabela.SENHAS, 1);
        segundaRotacao.executar(1, progresso -> {});
        assertThat(cifra(MigracaoCifras.Tabela.SENHAS, 1)).isEqualTo(confirmada);
        var semAntigas = new CofreCifras(new CriptografiaProperties("proxima", proxima, "", ""));
        assertThat(semAntigas.decifrar(cifra(MigracaoCifras.Tabela.SENHAS, 1))).isEqualTo("primeira");
        assertThat(semAntigas.decifrar(cifra(MigracaoCifras.Tabela.SENHAS, 2))).isEqualTo("segunda");
    }

    @Test
    void novaValidacaoNaoMantemMarcadorConcluidoSeCifraFoiAdulterada() throws Exception {
        inserir(MigracaoCifras.Tabela.SENHAS, 1, legado("primeira"));
        migracao.executar(1, progresso -> {});
        jdbc.update("UPDATE senhas_empresa SET senha_cifrada='apc:1:nova:AAAA' WHERE id=?", id(1));
        assertThatThrownBy(() -> migracao.executar(1, progresso -> {})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT concluida FROM rotacoes_cifras WHERE tabela='senhas_empresa'", Boolean.class)).isFalse();
    }

    private void inserir(MigracaoCifras.Tabela tabela, int id, String cifra) {
        jdbc.update("INSERT INTO " + tabela.nome + " (id, " + tabela.coluna + ") VALUES (?, ?)", id(id), cifra);
    }
    private String cifra(MigracaoCifras.Tabela tabela, int id) {
        return jdbc.queryForObject("SELECT " + tabela.coluna + " FROM " + tabela.nome + " WHERE id=?", String.class, id(id));
    }
    private String legado(String texto) throws Exception { return CriptoVersionadaTest.legado(texto, antiga); }
    private UUID id(int valor) { return new UUID(0, valor); }
}
