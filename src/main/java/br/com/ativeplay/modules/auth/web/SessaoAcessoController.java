package br.com.ativeplay.modules.auth.web;

import br.com.ativeplay.modules.auth.service.SessaoAcessoService;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import br.com.ativeplay.shared.seguranca.Perfil;

@Tag(name = "Ponto eletrônico")
@RestController
@RequestMapping("/api/v1/sessoes-acesso")
@Perfis({Perfil.ADMIN, Perfil.GESTOR, Perfil.OPERADOR})
@RequiredArgsConstructor
public class SessaoAcessoController {

    private final SessaoAcessoService sessoesAcesso;

    public record SaidaRequest(@NotNull Instant fim) {}

    @Operation(summary = "Registro de ponto aberto do token (204 se não houver)")
    @GetMapping("/atual")
    public ResponseEntity<SessaoAcessoService.SessaoAcessoResponse> atual(
            UsuarioAutenticado usuario,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var sessaoId = sessaoId(jwt);
        if (sessaoId == null) {
            return ResponseEntity.noContent().build();
        }

        return sessoesAcesso.atual(usuario, sessaoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Sinal de vida do navegador (409 se o ponto já foi encerrado)")
    @PostMapping("/sinal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sinal(
            UsuarioAutenticado usuario,
            @AuthenticationPrincipal Jwt jwt
    ) {
        sessoesAcesso.sinal(usuario, sessaoId(jwt));
    }

    @Operation(summary = "Últimos 100 registros de ponto do próprio usuário")
    @GetMapping
    public List<SessaoAcessoService.SessaoAcessoResponse> historico(
            UsuarioAutenticado usuario
    ) {
        return sessoesAcesso.historico(usuario);
    }

    @Operation(summary = "Carga horária da equipe no mês, com total por dia")
    @Perfis({Perfil.ADMIN, Perfil.GESTOR})
    @GetMapping("/equipe")
    public List<SessaoAcessoService.EquipeResponse> equipe(
            UsuarioAutenticado usuario,
            @RequestParam(required = false) String mes
    ) {
        return sessoesAcesso.equipe(usuario, mes);
    }

    @Operation(summary = "Registros de ponto de um usuário da equipe no mês")
    @Perfis({Perfil.ADMIN, Perfil.GESTOR})
    @GetMapping("/usuarios/{usuarioId}")
    public List<SessaoAcessoService.SessaoAcessoResponse> doUsuario(
            UsuarioAutenticado usuario,
            @PathVariable UUID usuarioId,
            @RequestParam(required = false) String mes
    ) {
        return sessoesAcesso.doUsuario(usuario, usuarioId, mes);
    }

    @Operation(summary = "Informa a saída de um registro pendente")
    @Perfis({Perfil.ADMIN, Perfil.GESTOR})
    @PatchMapping("/{id}/saida")
    public SessaoAcessoService.SessaoAcessoResponse informarSaida(
            UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @Valid @RequestBody SaidaRequest req
    ) {
        return sessoesAcesso.informarSaida(usuario, id, req.fim());
    }

    private static UUID sessaoId(Jwt jwt) {
        var sid = jwt.getClaimAsString("sid");
        if (sid == null || sid.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(sid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
