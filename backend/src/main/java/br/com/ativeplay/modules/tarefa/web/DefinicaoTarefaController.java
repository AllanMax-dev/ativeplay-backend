package br.com.ativeplay.modules.tarefa.web;

import br.com.ativeplay.modules.tarefa.service.DefinicaoTarefaService;
import br.com.ativeplay.modules.tarefa.web.dto.DefinicaoDtos.AplicarPadroesRequest;
import br.com.ativeplay.modules.tarefa.web.dto.DefinicaoDtos.DefinicaoRequest;
import br.com.ativeplay.modules.tarefa.web.dto.DefinicaoDtos.DefinicaoResponse;
import br.com.ativeplay.modules.tarefa.web.dto.DefinicaoDtos.OrdemDefinicoesRequest;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "Tarefas — recorrências por empresa")
@RestController
@RequestMapping("/api/v1/tarefas/definicoes")
@RequerModulo(Modulo.TAREFAS)
@RequiredArgsConstructor
public class DefinicaoTarefaController {

    private final DefinicaoTarefaService service;

    @GetMapping
    public List<DefinicaoResponse> listar(UsuarioAutenticado u, @RequestParam UUID empresaId,
            @RequestParam(required = false) Boolean ativa) {
        return service.listar(u, empresaId, ativa);
    }

    @Operation(summary = "Cria uma recorrência (opcionalmente a partir de um padrão do catálogo)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DefinicaoResponse criar(UsuarioAutenticado u, @Valid @RequestBody DefinicaoRequest req) {
        return service.criar(u, req);
    }

    @PutMapping("/{id}")
    public DefinicaoResponse atualizar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody DefinicaoRequest req) {
        return service.atualizar(u, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(UsuarioAutenticado u, @PathVariable UUID id) {
        service.desativar(u, id);
    }

    @Operation(summary = "Reordena as tarefas da empresa (arrastar e soltar)")
    @PatchMapping("/ordem")
    public List<DefinicaoResponse> reordenar(UsuarioAutenticado u, @Valid @RequestBody OrdemDefinicoesRequest req) {
        return service.reordenar(u, req.empresaId(), req.ids());
    }

    @Operation(summary = "Aplica à empresa os padrões do catálogo que ela ainda não tem")
    @PostMapping("/aplicar-padroes")
    public Map<String, Integer> aplicarPadroes(UsuarioAutenticado u, @Valid @RequestBody AplicarPadroesRequest req) {
        return Map.of("criadas", service.aplicarPadroes(u, req.empresaId(), req.padraoIds()));
    }
}
