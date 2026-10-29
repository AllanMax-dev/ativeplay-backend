package br.com.ativeplay.modules.senha.web;

import br.com.ativeplay.modules.senha.domain.CategoriaSenha;
import br.com.ativeplay.modules.senha.service.SenhaEmpresaService;
import br.com.ativeplay.modules.senha.web.dto.SenhaDtos.SenhaReveladaResponse;
import br.com.ativeplay.modules.senha.web.dto.SenhaDtos.SenhaRequest;
import br.com.ativeplay.modules.senha.web.dto.SenhaDtos.SenhaResponse;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Senhas salvas do cliente")
@RestController
@RequestMapping("/api/v1/empresas/{empresaId}/senhas")
@RequerModulo(Modulo.SENHAS)
@RequiredArgsConstructor
public class SenhaEmpresaController {

    private final SenhaEmpresaService service;

    @Operation(summary = "Acessos salvos da empresa (sem as senhas)")
    @GetMapping
    public List<SenhaResponse> listar(UsuarioAutenticado u, @PathVariable UUID empresaId,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) CategoriaSenha categoria) {
        return service.listar(u, empresaId, busca, categoria);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SenhaResponse criar(UsuarioAutenticado u, @PathVariable UUID empresaId,
            @Valid @RequestBody SenhaRequest req) {
        return service.criar(u, empresaId, req);
    }

    @Operation(summary = "Edita o acesso (senha nula mantém a atual; removerSenha=true apaga)")
    @PutMapping("/{id}")
    public SenhaResponse atualizar(UsuarioAutenticado u, @PathVariable UUID empresaId, @PathVariable UUID id,
            @Valid @RequestBody SenhaRequest req) {
        return service.atualizar(u, empresaId, id, req);
    }

    @Operation(summary = "Revela a senha em texto (registrado na auditoria)")
    @GetMapping("/{id}/senha")
    public ResponseEntity<SenhaReveladaResponse> revelar(UsuarioAutenticado u, @PathVariable UUID empresaId,
            @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.revelar(u, empresaId, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(UsuarioAutenticado u, @PathVariable UUID empresaId, @PathVariable UUID id) {
        service.excluir(u, empresaId, id);
    }
}
