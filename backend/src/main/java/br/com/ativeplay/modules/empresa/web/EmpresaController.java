package br.com.ativeplay.modules.empresa.web;

import br.com.ativeplay.modules.empresa.domain.RegimeTributario;
import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.empresa.service.EmpresaService;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.EmpresaDetalhe;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.EmpresaRequest;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.EmpresaResumoResposta;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.EquipeRequest;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.StatusRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@Tag(name = "Empresas")
@RestController
@RequestMapping("/api/v1/empresas")
@RequerModulo(Modulo.EMPRESAS)
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService service;

    @Operation(summary = "Empresas visíveis ao usuário (carteira), com tarefas abertas hoje e atrasadas")
    @GetMapping
    public Pagina<EmpresaResumoResposta> listar(UsuarioAutenticado u,
            @RequestParam(required = false) StatusEmpresa status,
            @RequestParam(required = false) RegimeTributario regimeTributario,
            @RequestParam(required = false) UUID responsavelId,
            @RequestParam(required = false) String busca,
            Pageable pageable) {
        return service.listar(u, status, regimeTributario, responsavelId, busca, pageable);
    }

    @GetMapping("/{id}")
    public EmpresaDetalhe detalhe(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.detalhe(u, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpresaDetalhe criar(UsuarioAutenticado u, @Valid @RequestBody EmpresaRequest req) {
        return service.criar(u, req);
    }

    @PutMapping("/{id}")
    public EmpresaDetalhe atualizar(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody EmpresaRequest req) {
        return service.atualizar(u, id, req);
    }

    @PatchMapping("/{id}/status")
    public EmpresaDetalhe status(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        return service.alterarStatus(u, id, req.status());
    }

    @Operation(summary = "Exclui definitivamente uma empresa ENCERRADA e todos os seus dados (somente ADMIN)")
    @Perfis(Perfil.ADMIN)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(UsuarioAutenticado u, @PathVariable UUID id) {
        service.excluir(u, id);
    }

    @GetMapping("/{id}/equipe")
    public List<UsuarioRef> equipe(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.equipe(u, id);
    }

    @Operation(summary = "Define quem da equipe atende a empresa (carteira, visto pelo lado da empresa)")
    @PutMapping("/{id}/equipe")
    public List<UsuarioRef> definirEquipe(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody EquipeRequest req) {
        return service.definirEquipe(u, id, req.usuarioIds());
    }
}
