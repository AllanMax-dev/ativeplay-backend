package br.com.ativeplay.modules.integracao.web;

import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import br.com.ativeplay.modules.integracao.domain.StatusIntegracao;
import br.com.ativeplay.modules.integracao.service.IntegracaoService;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.AtualizarIntegracaoRequest;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.CriarIntegracaoRequest;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.IntegracaoResponse;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.SincronizacaoResponse;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.SincronizarRequest;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.StatusRequest;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.TesteResponse;
import br.com.ativeplay.shared.seguranca.Modulo;
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

@Tag(name = "Integrações")
@RestController
@RequestMapping("/api/v1/integracoes")
@RequerModulo(Modulo.INTEGRACOES)
@RequiredArgsConstructor
public class IntegracaoController {

    private final IntegracaoService service;

    @GetMapping
    public Pagina<IntegracaoResponse> listar(UsuarioAutenticado u,
            @RequestParam(required = false) UUID empresaId,
            @RequestParam(required = false) ProvedorIntegracao provedor,
            @RequestParam(required = false) StatusIntegracao status,
            Pageable pageable) {
        return service.listar(u, empresaId, provedor, status, pageable);
    }

    @GetMapping("/{id}")
    public IntegracaoResponse obter(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.obter(u, id);
    }

    @Operation(summary = "Cadastra a integração; as credenciais são cifradas e nunca retornadas")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntegracaoResponse criar(UsuarioAutenticado u, @Valid @RequestBody CriarIntegracaoRequest req) {
        return service.criar(u, req);
    }

    @PutMapping("/{id}")
    public IntegracaoResponse atualizar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody AtualizarIntegracaoRequest req) {
        return service.atualizar(u, id, req);
    }

    @PatchMapping("/{id}/status")
    public IntegracaoResponse status(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        return service.alterarStatus(u, id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(UsuarioAutenticado u, @PathVariable UUID id) {
        service.excluir(u, id);
    }

    @PostMapping("/{id}/testar")
    public TesteResponse testar(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.testar(u, id);
    }

    @Operation(summary = "Executa uma sincronização; o resultado (SUCESSO/ERRO) fica no histórico")
    @PostMapping("/{id}/sincronizar")
    public SincronizacaoResponse sincronizar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody SincronizarRequest req) {
        return service.sincronizar(u, id, req.recurso());
    }

    @GetMapping("/{id}/sincronizacoes")
    public Pagina<SincronizacaoResponse> historico(UsuarioAutenticado u, @PathVariable UUID id, Pageable pageable) {
        return service.historico(u, id, pageable);
    }
}
