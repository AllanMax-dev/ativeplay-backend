package br.com.ativeplay.shared.seguranca;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige permissão no módulo (tabela permissoes_perfil). GET exige leitura; os demais
 * métodos exigem escrita. ADMIN tem acesso total. Pode ser usada na classe ou no método
 * (a do método prevalece).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequerModulo {
    Modulo value();
}
