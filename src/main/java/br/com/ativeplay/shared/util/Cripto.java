package br.com.ativeplay.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM para credenciais de integrações, SHA-256 e geração de tokens aleatórios. */
public final class Cripto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private Cripto() {
    }

    /** Saída: base64(iv || cifra+tag). */
    public static String cifrar(String texto, String chaveBase64) {
        try {
            var iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave(chaveBase64), new GCMParameterSpec(TAG_BITS, iv));
            var cifra = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            var saida = new byte[iv.length + cifra.length];
            System.arraycopy(iv, 0, saida, 0, iv.length);
            System.arraycopy(cifra, 0, saida, iv.length, cifra.length);
            return Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    public static String decifrar(String payload, String chaveBase64) {
        try {
            var bytes = Base64.getDecoder().decode(payload);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave(chaveBase64), new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao decifrar", e);
        }
    }

    public static String sha256(String valor) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String tokenAleatorio(int bytes) {
        var buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static SecretKeySpec chave(String chaveBase64) {
        var bytes = Base64.getDecoder().decode(chaveBase64);
        if (bytes.length != 32) throw new IllegalStateException("ENCRYPTION_KEY deve ter 32 bytes em base64");
        return new SecretKeySpec(bytes, "AES");
    }
}
