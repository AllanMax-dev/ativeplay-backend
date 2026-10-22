package br.com.ativeplay.modules.usuario.web;

import br.com.ativeplay.modules.usuario.service.UsuarioService;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.AtivoRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.AtualizarUsuarioRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.CarteiraRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.CriarUsuarioRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.SenhaTemporariaResposta;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.SetoresRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.UsuarioCriadoResposta;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.UsuarioDetalhe;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.UsuarioResposta;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuários")
@RestController
@RequestMapping("/api/v1/usuarios")
@RequerModulo(Modulo.USUARIOS)
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService service;

    @GetMapping
    public Pagina<UsuarioResposta> listar(UsuarioAutenticado u,
            @RequestParam(required = false) Perfil perfil,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) UUID setorId,
            @RequestParam(required = false) String busca,
            Pageable pageable) {
        return service.listar(u, perfil, ativo, setorId, busca, pageable);
    }

    @GetMapping("/{id}")
    public UsuarioDetalhe detalhe(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.detalhe(u, id);
    }

    @Operation(summary = "Cria um usuário. Sem senha informada, gera uma senha temporária retornada uma única vez.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioCriadoResposta criar(UsuarioAutenticado u, @Valid @RequestBody CriarUsuarioRequest req) {
        return service.criar(u, req);
    }

    @PutMapping("/{id}")
    public UsuarioDetalhe atualizar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody AtualizarUsuarioRequest req) {
        return service.atualizar(u, id, req);
    }

    @PatchMapping("/{id}/ativo")
    public UsuarioDetalhe alterarAtivo(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody AtivoRequest req) {
        return service.alterarAtivo(u, id, req.ativo());
    }

    @Operation(summary = "Gera uma nova senha temporária (retornada uma única vez) e encerra as sessões do usuário")
    @PostMapping("/{id}/redefinir-senha")
    public SenhaTemporariaResposta redefinirSenha(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.redefinirSenha(u, id);
    }

    @PutMapping("/{id}/setores")
    public UsuarioDetalhe setores(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody SetoresRequest req) {
        return service.atualizarSetores(u, id, req.setorIds());
    }

    @Operation(summary = "Define a carteira de empresas atendidas pelo usuário")
    @PutMapping("/{id}/carteira")
    public UsuarioDetalhe carteira(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody CarteiraRequest req) {
        return service.atualizarCarteira(u, id, req.empresaIds());
    }
}
