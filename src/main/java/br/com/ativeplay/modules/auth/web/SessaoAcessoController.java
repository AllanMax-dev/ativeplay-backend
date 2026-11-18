package br.com.ativeplay.modules.auth.web;

import br.com.ativeplay.modules.auth.service.SessaoAcessoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import br.com.ativeplay.shared.seguranca.Perfil;

@RestController
@RequestMapping("/api/v1/sessoes-acesso")
@RequiredArgsConstructor
public class SessaoAcessoController {

    private final SessaoAcessoService sessoesAcesso;

    @GetMapping("/atual")
    public ResponseEntity<SessaoAcessoService.SessaoAcessoResponse> atual(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var usuario = usuarioAutenticado(jwt);

        var sid = jwt.getClaimAsString("sid");
        if (sid == null || sid.isBlank()) {
            return ResponseEntity.noContent().build();
        }

        UUID sessaoId;
        try {
            sessaoId = UUID.fromString(sid);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.noContent().build();
        }

        return sessoesAcesso.atual(usuario, sessaoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public List<SessaoAcessoService.SessaoAcessoResponse> historico(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return sessoesAcesso.historico(usuarioAutenticado(jwt));
    }

private UsuarioAutenticado usuarioAutenticado(Jwt jwt) {
    String empresa = jwt.getClaimAsString("emp");

    return new UsuarioAutenticado(
            UUID.fromString(jwt.getSubject()),
            UUID.fromString(jwt.getClaimAsString("esc")),
            Perfil.valueOf(jwt.getClaimAsString("perfil")),
            empresa == null ? null : UUID.fromString(empresa)
    );
}
}