package br.com.ativeplay.modules.auth.web;

import br.com.ativeplay.modules.auth.service.AuthService;
import br.com.ativeplay.modules.auth.service.AuthService.Contexto;
import br.com.ativeplay.modules.auth.service.LimiteTentativas;
import br.com.ativeplay.modules.auth.service.PerfilService;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.AlterarSenhaRequest;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.LoginRequest;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.RefreshRequest;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.RegistrarEscritorioRequest;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.SessaoResponse;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;
    private final PerfilService perfil;
    private final LimiteTentativas limite;

    // limites por padrão no código; configuráveis por ativeplay.limite.* (sem exigir mudança de config)
    @Value("${ativeplay.limite.login-falhas:5}")
    private int loginMaxFalhas;
    @Value("${ativeplay.limite.login-janela-minutos:15}")
    private long loginJanelaMinutos;
    @Value("${ativeplay.limite.cadastro-max:5}")
    private int cadastroMax;
    @Value("${ativeplay.limite.cadastro-janela-minutos:60}")
    private long cadastroJanelaMinutos;

    @Operation(summary = "Cadastra um novo escritório com seu usuário administrador")
    @PostMapping("/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    public SessaoResponse registrar(@Valid @RequestBody RegistrarEscritorioRequest req, HttpServletRequest http) {
        var chave = "cadastro:" + http.getRemoteAddr();
        var janela = Duration.ofMinutes(cadastroJanelaMinutos);
        limite.verificar(chave, cadastroMax, janela);
        limite.registrar(chave, janela);
        return auth.registrar(req, contexto(http));
    }

    @PostMapping("/login")
    public SessaoResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        var chave = "login:" + http.getRemoteAddr() + ":" + req.email().trim().toLowerCase();
        var janela = Duration.ofMinutes(loginJanelaMinutos);
        limite.verificar(chave, loginMaxFalhas, janela);
        try {
            var sessao = auth.login(req, contexto(http));
            limite.limpar(chave);
            return sessao;
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) limite.registrar(chave, janela);
            throw e;
        }
    }

    @Operation(summary = "Troca o refresh token por um novo par de tokens (rotação)")
    @PostMapping("/refresh")
    public SessaoResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return auth.renovar(req.refreshToken(), contexto(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
    }

    @Operation(summary = "Dados do usuário logado, escritório e permissões")
    @Perfis({Perfil.ADMIN, Perfil.GESTOR, Perfil.OPERADOR, Perfil.CLIENTE})
    @GetMapping("/me")
    public Map<String, Object> me(UsuarioAutenticado u) {
        return perfil.perfil(u);
    }

    @Perfis({Perfil.ADMIN, Perfil.GESTOR, Perfil.OPERADOR, Perfil.CLIENTE})
    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(UsuarioAutenticado u, @Valid @RequestBody AlterarSenhaRequest req) {
        auth.alterarSenha(u, req);
    }

    private static Contexto contexto(HttpServletRequest http) {
        return new Contexto(http.getRemoteAddr(), http.getHeader("User-Agent"));
    }
}
