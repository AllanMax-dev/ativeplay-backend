package br.com.ativeplay.shared.armazenamento;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;

/**
 * Armazenamento de arquivos enviados (salvar/abrir/remover por chave). Implementações:
 * {@link ArmazenamentoLocal} (disco, padrão) e {@link ArmazenamentoS3} (S3 compatível, ex.: Supabase Storage),
 * escolhidas pela variável STORAGE_TIPO (local | s3).
 */
public interface ArmazenamentoService {

    record ArquivoSalvo(String storageKey, String sha256, long tamanho) {
    }

    ArquivoSalvo salvar(UUID escritorioId, String nomeOriginal, InputStream conteudo);

    /** Lança 404 se a chave não existir. */
    Resource abrir(String storageKey);

    void remover(String storageKey);

    /** Chave no formato {escritorio}/{ano}/{mes}/{uuid}{extensão}. */
    static String novaChave(UUID escritorioId, String nomeOriginal) {
        var hoje = LocalDate.now();
        return "%s/%d/%02d/%s%s".formatted(escritorioId, hoje.getYear(), hoje.getMonthValue(), UUID.randomUUID(),
                extensao(nomeOriginal));
    }

    private static String extensao(String nome) {
        if (nome == null || !nome.contains(".")) return "";
        var ext = nome.substring(nome.lastIndexOf('.')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        return ext.length() > 10 ? ext.substring(0, 10) : ext;
    }
}
