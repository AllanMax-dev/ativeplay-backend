package br.com.ativeplay.shared.validacao;

import br.com.ativeplay.shared.util.Cnpj;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** CNPJ válido, com ou sem máscara, numérico ou alfanumérico. Nulo é aceito (use @NotBlank junto se obrigatório). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CnpjValido.Validador.class)
public @interface CnpjValido {

    String message() default "CNPJ inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<CnpjValido, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return valor == null || valor.isBlank() || Cnpj.valido(valor);
        }
    }
}
