package br.com.ativeplay.shared.armazenamento;

import br.com.ativeplay.config.AtivePlayProperties;
import br.com.ativeplay.shared.erro.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Armazenamento de arquivos em disco local. A interface (salvar/abrir/remover por chave)
 * permite trocar por S3/GCS sem alterar os módulos que a usam.
 */
@Service
public class ArmazenamentoService {

    public record ArquivoSalvo(String storageKey, String sha256, long tamanho) {
    }

    private final Path raiz;

    public ArmazenamentoService(AtivePlayProperties props) {
        this.raiz = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    public ArquivoSalvo salvar(UUID escritorioId, String nomeOriginal, InputStream conteudo) {
        var hoje = LocalDate.now();
        var chave = "%s/%d/%02d/%s%s".formatted(escritorioId, hoje.getYear(), hoje.getMonthValue(), UUID.randomUUID(),
                extensao(nomeOriginal));
        var destino = caminhoSeguro(chave);
        try {
            Files.createDirectories(destino.getParent());
            var digest = MessageDigest.getInstance("SHA-256");
            long tamanho;
            try (var entrada = new java.security.DigestInputStream(conteudo, digest)) {
                tamanho = Files.copy(entrada, destino);
            }
            return new ArquivoSalvo(chave, HexFormat.of().formatHex(digest.digest()), tamanho);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Resource abrir(String storageKey) {
        var caminho = caminhoSeguro(storageKey);
        if (!Files.exists(caminho)) throw ApiException.naoEncontrado("Arquivo");
        return new FileSystemResource(caminho);
    }

    public void remover(String storageKey) {
        try {
            Files.deleteIfExists(caminhoSeguro(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Impede path traversal: a chave precisa resolver para dentro da raiz. */
    private Path caminhoSeguro(String chave) {
        var caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz)) throw ApiException.naoEncontrado("Arquivo");
        return caminho;
    }

    private static String extensao(String nome) {
        if (nome == null || !nome.contains(".")) return "";
        var ext = nome.substring(nome.lastIndexOf('.')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        return ext.length() > 10 ? ext.substring(0, 10) : ext;
    }
}
