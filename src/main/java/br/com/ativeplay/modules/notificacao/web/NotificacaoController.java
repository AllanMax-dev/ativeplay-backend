package br.com.ativeplay.modules.notificacao.web;

import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService.PaginaNotificacoes;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notificações")
@RestController
@RequestMapping("/api/v1/notificacoes")
@Perfis({Perfil.ADMIN, Perfil.GESTOR, Perfil.OPERADOR, Perfil.CLIENTE})
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService service;

    @GetMapping
    public PaginaNotificacoes listar(UsuarioAutenticado u, @RequestParam(defaultValue = "false") boolean naoLidas,
            Pageable pageable) {
        return service.listar(u, naoLidas, pageable);
    }

    @PatchMapping("/lidas")
    public Map<String, Integer> marcarTodas(UsuarioAutenticado u) {
        return Map.of("atualizadas", service.marcarTodasLidas(u));
    }

    @PatchMapping("/{id}/lida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcar(UsuarioAutenticado u, @PathVariable UUID id) {
        service.marcarLida(u, id);
    }
}
