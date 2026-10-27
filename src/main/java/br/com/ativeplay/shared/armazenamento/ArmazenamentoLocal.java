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
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Armazenamento de arquivos em disco local (padrão em dev/test; em produção o disco do Render é efêmero). */
@Service
@ConditionalOnProperty(name = "STORAGE_TIPO", havingValue = "local", matchIfMissing = true)
public class ArmazenamentoLocal implements ArmazenamentoService {

    private final Path raiz;

    public ArmazenamentoLocal(AtivePlayProperties props) {
        this.raiz = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    @Override
    public ArquivoSalvo salvar(UUID escritorioId, String nomeOriginal, InputStream conteudo) {
        var chave = ArmazenamentoService.novaChave(escritorioId, nomeOriginal);
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

    @Override
    public Resource abrir(String storageKey) {
        var caminho = caminhoSeguro(storageKey);
        if (!Files.exists(caminho)) throw ApiException.naoEncontrado("Arquivo");
        return new FileSystemResource(caminho);
    }

    @Override
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
}
