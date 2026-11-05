package br.com.ativeplay.operacao;

import br.com.ativeplay.config.CriptografiaProperties;
import br.com.ativeplay.shared.util.CofreCifras;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Entry point separado: não inicia Spring Boot, HTTP, Flyway, seed ou jobs. */
public final class MigracaoCifrasCommand {
    private MigracaoCifrasCommand() {}

    public static void main(String[] args) {
        // Bibliotecas não devem registrar SQL/parâmetros/causas durante a operação.
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
            ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
        }
        System.exit(executar(args, System.getenv(), System.out));
    }

    static int executar(String[] args, Map<String, String> env, PrintStream saida) {
        if (args.length == 1 && args[0].equals("--help")) {
            saida.println("Recifragem: --confirmar-backup --confirmar-manutencao [--lote=200]. "
                    + "Leia docs/rotacao-cifras.md; exige backup restaurado, chaves protegidas e ausência de escritores.");
            return 0;
        }
        int lote = 200;
        var flags = new HashSet<String>();
        try {
            for (var arg : args) {
                var nome = arg.startsWith("--lote=") ? "--lote" : arg;
                if (!flags.add(nome)) throw new IllegalArgumentException();
                if (nome.equals("--lote")) {
                    if (!arg.startsWith("--lote=")) throw new IllegalArgumentException();
                    lote = Integer.parseInt(arg.substring("--lote=".length()));
                }
                else if (!nome.equals("--confirmar-backup") && !nome.equals("--confirmar-manutencao")) throw new IllegalArgumentException();
            }
            if (!flags.contains("--confirmar-backup") || !flags.contains("--confirmar-manutencao") || lote < 1 || lote > 1000) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            saida.println("Argumentos inválidos ou confirmações ausentes. Use --help.");
            return 2;
        }
        try {
            var cofre = new CofreCifras(new CriptografiaProperties(env.get("ENCRYPTION_ACTIVE_KEY_ID"),
                    env.get("ENCRYPTION_ACTIVE_KEY"), env.get("ENCRYPTION_KEY"), env.get("ENCRYPTION_PREVIOUS_KEYS")));
            var url = obrigatoria(env, "MIGRATION_JDBC_URL");
            if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException();
            var ds = new DriverManagerDataSource(url, obrigatoria(env, "POSTGRES_USER"), obrigatoria(env, "POSTGRES_PASSWORD"));
            var conexao = new Properties();
            conexao.setProperty("connectTimeout", "10");
            conexao.setProperty("socketTimeout", "70");
            ds.setConnectionProperties(conexao);
            new MigracaoCifras(ds, cofre).executar(lote, progresso -> saida.printf(
                    "tabela=%s checkpoint=%s total_inicial=%d cifradas_inicial=%d processadas=%d alteradas=%d concluida=%s%n",
                    progresso.tabela(), progresso.ultimoId(), progresso.totalInicial(), progresso.cifradasInicial(),
                    progresso.processadas(), progresso.alteradas(), progresso.concluida()));
            saida.println("Recifragem e verificação concluídas nas duas tabelas. Preserve as chaves antigas até o aceite operacional.");
            return 0;
        } catch (RuntimeException e) {
            saida.println("Recifragem interrompida. Verifique configuração, integridade, manutenção e checkpoint antes de retomar.");
            return 1;
        }
    }

    private static String obrigatoria(Map<String, String> env, String nome) {
        var valor = env.get(nome);
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException();
        return valor;
    }
}
