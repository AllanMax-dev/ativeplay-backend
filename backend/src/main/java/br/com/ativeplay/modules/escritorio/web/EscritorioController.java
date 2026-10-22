package br.com.ativeplay.modules.escritorio.web;

import br.com.ativeplay.modules.escritorio.service.EscritorioService;
import br.com.ativeplay.modules.escritorio.web.dto.EscritorioDtos.AtualizarEscritorioRequest;
import br.com.ativeplay.modules.escritorio.web.dto.EscritorioDtos.EscritorioResposta;
import br.com.ativeplay.modules.escritorio.web.dto.EscritorioDtos.PermissaoItem;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Escritório")
@RestController
@RequestMapping("/api/v1/escritorio")
@RequerModulo(Modulo.CONFIGURACOES)
@Validated
@RequiredArgsConstructor
public class EscritorioController {

    private final EscritorioService service;

    @GetMapping
    public EscritorioResposta atual(UsuarioAutenticado u) {
        return service.atual(u);
    }

    @PutMapping
    public EscritorioResposta atualizar(UsuarioAutenticado u, @Valid @RequestBody AtualizarEscritorioRequest req) {
        return service.atualizar(u, req);
    }

    @Operation(summary = "Matriz de permissões perfil × módulo (NENHUM, LEITURA, ESCRITA)")
    @GetMapping("/permissoes")
    public List<PermissaoItem> permissoes(UsuarioAutenticado u) {
        return service.permissoes(u);
    }

    @Operation(summary = "Substitui a matriz de permissões (somente ADMIN)")
    @Perfis(Perfil.ADMIN)
    @PutMapping("/permissoes")
    public List<PermissaoItem> substituirPermissoes(UsuarioAutenticado u,
            @RequestBody @NotNull List<@Valid PermissaoItem> itens) {
        return service.substituirPermissoes(u, itens);
    }
}
