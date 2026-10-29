package br.com.ativeplay.modules.checklist.web;

import br.com.ativeplay.modules.checklist.service.ChecklistService;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.ChecklistItemRequest;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.ChecklistItemResponse;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.Situacao;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistDtos.SubitemStatusRequest;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

@Tag(name = "Checklist do cliente")
@RestController
@RequestMapping("/api/v1/empresas/{empresaId}/checklist")
@RequerModulo(Modulo.TAREFAS)
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService service;

    @Operation(summary = "Itens do checklist da empresa (pendentes primeiro, por prazo)")
    @GetMapping
    public List<ChecklistItemResponse> listar(UsuarioAutenticado u, @PathVariable UUID empresaId,
            @RequestParam(defaultValue = "TODOS") Situacao situacao) {
        return service.listar(u, empresaId, situacao);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChecklistItemResponse criar(UsuarioAutenticado u, @PathVariable UUID empresaId,
            @Valid @RequestBody ChecklistItemRequest req) {
        return service.criar(u, empresaId, req);
    }

    @Operation(summary = "Substitui o item (inclusive sub-itens)")
    @PutMapping("/{id}")
    public ChecklistItemResponse atualizar(UsuarioAutenticado u, @PathVariable UUID empresaId, @PathVariable UUID id,
            @Valid @RequestBody ChecklistItemRequest req) {
        return service.atualizar(u, empresaId, id, req);
    }

    @PostMapping("/{id}/concluir")
    public ChecklistItemResponse concluir(UsuarioAutenticado u, @PathVariable UUID empresaId, @PathVariable UUID id) {
        return service.concluir(u, empresaId, id);
    }

    @PostMapping("/{id}/reabrir")
    public ChecklistItemResponse reabrir(UsuarioAutenticado u, @PathVariable UUID empresaId, @PathVariable UUID id) {
        return service.reabrir(u, empresaId, id);
    }

    @Operation(summary = "Marca ou desmarca um sub-item")
    @PatchMapping("/{id}/subitens/{subitemId}")
    public ChecklistItemResponse subitem(UsuarioAutenticado u, @PathVariable UUID empresaId, @PathVariable UUID id,
            @PathVariable UUID subitemId, @Valid @RequestBody SubitemStatusRequest req) {
        return service.marcarSubitem(u, empresaId, id, subitemId, req.concluido());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(UsuarioAutenticado u, @PathVariable UUID empresaId, @PathVariable UUID id) {
        service.excluir(u, empresaId, id);
    }
}
