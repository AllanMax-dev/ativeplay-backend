package br.com.ativeplay.shared.erro;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Erro de negócio com status HTTP; vira um ProblemDetail (RFC 9457) no GlobalExceptionHandler. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public static ApiException naoEncontrado(String recurso) {
        return new ApiException(HttpStatus.NOT_FOUND, recurso + " não encontrado(a)");
    }

    public static ApiException conflito(String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, mensagem);
    }

    public static ApiException invalido(String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static ApiException proibido(String mensagem) {
        return new ApiException(HttpStatus.FORBIDDEN, mensagem);
    }

    public static ApiException naoAutorizado(String mensagem) {
        return new ApiException(HttpStatus.UNAUTHORIZED, mensagem);
    }
}
