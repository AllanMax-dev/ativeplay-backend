package br.com.ativeplay.shared.seguranca;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restringe a rota aos perfis informados. Sem esta anotação, a rota é da equipe do
 * escritório: usuários CLIENTE só acessam rotas que declaram Perfil.CLIENTE aqui.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Perfis {
    Perfil[] value();
}
