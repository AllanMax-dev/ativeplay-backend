package br.com.ativeplay.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Function;
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

    public static String cifrar(String texto, String chaveBase64, String keyId) {
        validarKeyId(keyId);
        if (texto == null) throw new IllegalStateException("Texto ausente para cifragem");
        try {
            var iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            var cabecalho = "apc:1:" + keyId + ":";
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave(chaveBase64), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(cabecalho.getBytes(StandardCharsets.UTF_8));
            var cifra = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            var saida = new byte[iv.length + cifra.length];
            System.arraycopy(iv, 0, saida, 0, iv.length);
            System.arraycopy(cifra, 0, saida, iv.length, cifra.length);
            return cabecalho + Base64.getEncoder().encodeToString(saida);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao cifrar");
        }
    }

    public static String decifrar(String payload, Function<String, String> resolvedor, String chaveLegada) {
        if (payload == null || payload.isEmpty()) throw new IllegalStateException("Cifra inválida");
        try {
            String cabecalho = null;
            String chaveBase64;
            String corpo;
            if (payload.contains(":")) {
                var partes = payload.split(":", -1);
                if (partes.length != 4 || !partes[0].equals("apc") || !partes[1].equals("1")) {
                    throw new IllegalStateException("Formato de cifra não suportado");
                }
                validarKeyId(partes[2]);
                chaveBase64 = resolvedor.apply(partes[2]);
                cabecalho = "apc:1:" + partes[2] + ":";
                corpo = partes[3];
            } else {
                chaveBase64 = chaveLegada;
                corpo = payload;
            }
            var bytes = Base64.getDecoder().decode(corpo);
            if (bytes.length < IV_BYTES + TAG_BITS / 8) throw new IllegalStateException("Cifra truncada");
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave(chaveBase64), new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            if (cabecalho != null) cipher.updateAAD(cabecalho.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Sem causas: erros de parser/provedor podem propagar dados para logs ou respostas.
            throw new IllegalStateException("Falha ao decifrar");
        }
    }

    public static void validarKeyId(String keyId) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalStateException("Identificador de chave inválido");
        }
    }

    public static void validarChave(String chaveBase64) {
        chave(chaveBase64);
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
        try {
            if (chaveBase64 == null || chaveBase64.isBlank()) throw new IllegalArgumentException();
            var bytes = Base64.getDecoder().decode(chaveBase64);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Chave ausente ou inválida: esperado AES-256 em Base64");
        }
    }
}
