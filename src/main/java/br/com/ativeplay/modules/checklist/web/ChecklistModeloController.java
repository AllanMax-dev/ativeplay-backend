package br.com.ativeplay.modules.checklist.web;

import br.com.ativeplay.modules.checklist.service.ChecklistModeloService;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistModeloDtos.ModeloItemRequest;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistModeloDtos.ModeloItemResponse;
import br.com.ativeplay.modules.checklist.web.dto.ChecklistModeloDtos.ModeloOrdemRequest;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Checklist modelo do escritório")
@RestController
@RequestMapping("/api/v1/checklist-modelo")
@RequerModulo(Modulo.TAREFAS)
@Perfis({Perfil.ADMIN, Perfil.GESTOR})
@RequiredArgsConstructor
public class ChecklistModeloController {

    private final ChecklistModeloService service;

    @Operation(summary = "Itens do checklist modelo (copiados para toda empresa nova)")
    @GetMapping
    public List<ModeloItemResponse> listar(UsuarioAutenticado u) {
        return service.listar(u);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloItemResponse criar(UsuarioAutenticado u, @Valid @RequestBody ModeloItemRequest req) {
        return service.criar(u, req);
    }

    @Operation(summary = "Substitui o item (inclusive sub-itens); não altera as cópias já feitas")
    @PutMapping("/{id}")
    public ModeloItemResponse atualizar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody ModeloItemRequest req) {
        return service.atualizar(u, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(UsuarioAutenticado u, @PathVariable UUID id) {
        service.excluir(u, id);
    }

    @Operation(summary = "Reordena o modelo na ordem dos ids informados")
    @PatchMapping("/ordem")
    public List<ModeloItemResponse> reordenar(UsuarioAutenticado u, @Valid @RequestBody ModeloOrdemRequest req) {
        return service.reordenar(u, req.ids());
    }
}
