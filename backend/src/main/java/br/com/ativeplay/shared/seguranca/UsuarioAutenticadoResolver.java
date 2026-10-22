package br.com.ativeplay.shared.seguranca;

import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Injeta o UsuarioAutenticado (a partir das claims do JWT) em parâmetros de controllers. */
public class UsuarioAutenticadoResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == UsuarioAutenticado.class;
    }

    @Override
    public UsuarioAutenticado resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return atual();
    }

    public static UsuarioAutenticado atual() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            return null;
        }
        var jwt = token.getToken();
        var emp = jwt.getClaimAsString("emp");
        return new UsuarioAutenticado(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString("esc")),
                Perfil.valueOf(jwt.getClaimAsString("perfil")),
                emp == null ? null : UUID.fromString(emp));
    }
}
