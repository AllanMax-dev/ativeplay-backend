package br.com.ativeplay.shared.seguranca;

import br.com.ativeplay.modules.escritorio.domain.PermissaoPerfil;
import br.com.ativeplay.modules.escritorio.repository.PermissaoPerfilRepository;
import br.com.ativeplay.shared.erro.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Autorização por perfil e por módulo, aplicada a todas as rotas /api:
 * - usuários CLIENTE só acessam rotas anotadas com @Perfis(CLIENTE) (portal);
 * - @RequerModulo consulta a matriz permissoes_perfil do escritório do usuário.
 */
@Component
@RequiredArgsConstructor
public class AcessoInterceptor implements HandlerInterceptor {

    private final PermissaoPerfilRepository permissoes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        var usuario = UsuarioAutenticadoResolver.atual();
        if (usuario == null) return true; // rota pública: o Spring Security já barrou as demais

        var perfis = anotacao(metodo, Perfis.class);
        if (perfis != null) {
            if (Arrays.stream(perfis.value()).noneMatch(p -> p == usuario.perfil())) {
                throw ApiException.proibido("Perfil sem acesso a este recurso");
            }
        } else if (usuario.isCliente()) {
            throw ApiException.proibido("Usuários do portal acessam apenas as rotas /api/v1/portal");
        }

        var requer = anotacao(metodo, RequerModulo.class);
        if (requer == null || usuario.isAdmin()) return true;

        var modulo = requer.value();
        var permissao = permissoes
                .findById(new PermissaoPerfil.Chave(usuario.escritorioId(), usuario.perfil(), modulo))
                .orElseThrow(() -> ApiException.proibido("Sem acesso ao módulo " + modulo));
        if (!"GET".equals(request.getMethod()) && !permissao.isEscrita()) {
            throw ApiException.proibido("Acesso somente leitura ao módulo " + modulo);
        }
        return true;
    }

    private static <A extends java.lang.annotation.Annotation> A anotacao(HandlerMethod metodo, Class<A> tipo) {
        var noMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), tipo);
        return noMetodo != null ? noMetodo : AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), tipo);
    }
}
