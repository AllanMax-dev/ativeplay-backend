package br.com.ativeplay.operacao;

import br.com.ativeplay.shared.util.CofreCifras;
import br.com.ativeplay.shared.util.Recifrador;
import br.com.ativeplay.shared.util.Recifrador.Linha;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Ferramenta operacional explícita, fora do contexto HTTP e dos jobs da API. */
public final class MigracaoCifras {
    private static final long LOCK_ID = 1635019110L;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CofreCifras cofre;
    private final Recifrador recifrador;

    enum Tabela {
        SENHAS("senhas_empresa", "senha_cifrada"), INTEGRACOES("integracoes", "credenciais_cifradas");
        final String nome;
        final String coluna;
        Tabela(String nome, String coluna) { this.nome = nome; this.coluna = coluna; }
    }

    public record Progresso(String tabela, UUID ultimoId, long totalInicial, long cifradasInicial,
                            long processadas, long alteradas, boolean concluida) {}

    public MigracaoCifras(DataSource dataSource, CofreCifras cofre) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(60);
        this.cofre = cofre;
        this.recifrador = new Recifrador(cofre);
    }

    /** Exige manutenção sem escritores: a aplicação antiga não entende o envelope novo. */
    public void executar(int tamanhoLote, Consumer<Progresso> auditoria) {
        if (tamanhoLote < 1 || tamanhoLote > 1000) throw new IllegalArgumentException("Lote deve estar entre 1 e 1000");
        // Conexão dedicada: trava de sessão permanece entre commits e é liberada ao encerrar o processo.
        try (var trava = dataSource.getConnection()) {
            if (!obterTrava(trava)) throw new IllegalStateException("Outra recifragem está em execução");
            try {
                for (var tabela : Tabela.values()) {
                    while (true) {
                        var anterior = progressoOuNulo(tabela);
                        var progresso = processarLote(tabela, tamanhoLote);
                        if ((anterior != null && anterior.processadas() == progresso.processadas())
                                || progresso.cifradasInicial() == 0) break;
                        auditoria.accept(progresso);
                    }
                    validarTudo(tabela, tamanhoLote);
                    var finalizado = tx.execute(status -> {
                        bloquearTabela(tabela);
                        conferirContagens(tabela, progressoOuNulo(tabela));
                        jdbc.update("UPDATE rotacoes_cifras SET concluida=true, atualizado_em=now() WHERE chave_destino=? AND tabela=?",
                                cofre.chaveAtivaId(), tabela.nome);
                        return progressoOuNulo(tabela);
                    });
                    auditoria.accept(finalizado);
                }
            } finally {
                try (var comando = trava.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    comando.setLong(1, LOCK_ID);
                    comando.execute();
                }
            }
        } catch (SQLException | RuntimeException e) {
            // Não encadear exceções JDBC/JSON: podem conter dados de linhas ou conexão.
            throw new IllegalStateException("Recifragem interrompida; confira configuração, dados e último checkpoint confirmado");
        }
    }

    private boolean obterTrava(Connection conexao) throws SQLException {
        try (var comando = conexao.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            comando.setLong(1, LOCK_ID);
            try (var resultado = comando.executeQuery()) { resultado.next(); return resultado.getBoolean(1); }
        }
    }

    private Progresso processarLote(Tabela tabela, int tamanho) {
        return tx.execute(status -> {
            bloquearTabela(tabela);
            var contagem = contar(tabela);
            jdbc.update("INSERT INTO rotacoes_cifras (chave_destino, tabela, total_inicial, cifradas_inicial) "
                            + "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    cofre.chaveAtivaId(), tabela.nome, contagem[0], contagem[1]);
            var progresso = progressoOuNulo(tabela);
            conferirContagens(tabela, progresso);
            // Uma nova execução só pode declarar conclusão depois de validar novamente toda a tabela.
            jdbc.update("UPDATE rotacoes_cifras SET concluida=false WHERE chave_destino=? AND tabela=?",
                    cofre.chaveAtivaId(), tabela.nome);
            var linhas = ler(tabela, progresso.ultimoId(), tamanho);
            var novas = recifrador.preparar(linhas);
            long alteradas = 0;
            for (int i = 0; i < linhas.size(); i++) {
                var nova = novas.get(i);
                if (!Objects.equals(nova.cifra(), linhas.get(i).cifra())) {
                    // Nomes SQL vêm exclusivamente do enum; valores sempre são parâmetros.
                    if (jdbc.update("UPDATE " + tabela.nome + " SET " + tabela.coluna + "=? WHERE id=?",
                            nova.cifra(), nova.id()) != 1) throw new IllegalStateException("Linha ausente na recifragem");
                    alteradas++;
                }
            }
            if (!linhas.isEmpty()) {
                jdbc.update("UPDATE rotacoes_cifras SET ultimo_id=?, processadas=processadas+?, "
                                + "alteradas=alteradas+?, concluida=false, atualizado_em=now() WHERE chave_destino=? AND tabela=?",
                        linhas.getLast().id(), linhas.size(), alteradas, cofre.chaveAtivaId(), tabela.nome);
            }
            return progressoOuNulo(tabela);
        });
    }

    private void validarTudo(Tabela tabela, int tamanho) {
        UUID ultimo = null;
        long verificadas = 0;
        // Recomeça do início: UUIDs não são sequenciais. Nunca confiar só no checkpoint.
        while (true) {
            var cursor = ultimo;
            var linhas = tx.execute(status -> {
                bloquearTabela(tabela);
                conferirContagens(tabela, progressoOuNulo(tabela));
                var lote = ler(tabela, cursor, tamanho);
                for (var linha : lote) {
                    cofre.decifrar(linha.cifra());
                    if (!cofre.usaChaveAtiva(linha.cifra())) throw new IllegalStateException("Ainda existem cifras antigas");
                }
                return lote;
            });
            if (linhas.isEmpty()) break;
            verificadas += linhas.size();
            ultimo = linhas.getLast().id();
        }
        if (verificadas != progressoOuNulo(tabela).cifradasInicial()) throw new IllegalStateException("Contagem de validação divergente");
    }

    private void bloquearTabela(Tabela tabela) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '60s'");
        // Evita alterações durante cada lote; a manutenção cobre os intervalos entre transações.
        jdbc.execute("LOCK TABLE " + tabela.nome + " IN SHARE ROW EXCLUSIVE MODE");
    }

    private List<Linha> ler(Tabela tabela, UUID ultimo, int tamanho) {
        var sql = "SELECT id, " + tabela.coluna + " FROM " + tabela.nome + " WHERE " + tabela.coluna + " IS NOT NULL"
                + (ultimo == null ? "" : " AND id > ?") + " ORDER BY id LIMIT ?";
        Object[] args = ultimo == null ? new Object[] {tamanho} : new Object[] {ultimo, tamanho};
        return jdbc.query(sql, (rs, n) -> new Linha(rs.getObject(1, UUID.class), rs.getString(2)), args);
    }

    private long[] contar(Tabela tabela) {
        return jdbc.queryForObject("SELECT count(*), count(" + tabela.coluna + ") FROM " + tabela.nome,
                (rs, n) -> new long[] {rs.getLong(1), rs.getLong(2)});
    }

    private void conferirContagens(Tabela tabela, Progresso progresso) {
        var atual = contar(tabela);
        if (atual[0] != progresso.totalInicial() || atual[1] != progresso.cifradasInicial()) {
            throw new IllegalStateException("Contagens divergentes; mantenha a janela sem escritas");
        }
    }

    private Progresso progressoOuNulo(Tabela tabela) {
        return jdbc.query("SELECT ultimo_id, total_inicial, cifradas_inicial, processadas, alteradas, concluida "
                        + "FROM rotacoes_cifras WHERE chave_destino=? AND tabela=?",
                (rs, n) -> new Progresso(tabela.nome, rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rs.getBoolean(6)), cofre.chaveAtivaId(), tabela.nome)
                .stream().findFirst().orElse(null);
    }
}
