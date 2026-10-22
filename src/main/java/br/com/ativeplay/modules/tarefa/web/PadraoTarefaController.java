package br.com.ativeplay.modules.tarefa.web;

import br.com.ativeplay.modules.tarefa.service.PadraoTarefaService;
import br.com.ativeplay.modules.tarefa.web.dto.PadraoDtos.OrdemRequest;
import br.com.ativeplay.modules.tarefa.web.dto.PadraoDtos.PadraoRequest;
import br.com.ativeplay.modules.tarefa.web.dto.PadraoDtos.PadraoResponse;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
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

@Tag(name = "Tarefas — catálogo de padrões")
@RestController
@RequestMapping("/api/v1/tarefas/padroes")
@RequerModulo(Modulo.TAREFAS)
@RequiredArgsConstructor
public class PadraoTarefaController {

    private final PadraoTarefaService service;

    @GetMapping
    public List<PadraoResponse> listar(UsuarioAutenticado u, @RequestParam(required = false) Boolean ativo) {
        return service.listar(u, ativo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PadraoResponse criar(UsuarioAutenticado u, @Valid @RequestBody PadraoRequest req) {
        return service.criar(u, req);
    }

    @PutMapping("/{id}")
    public PadraoResponse atualizar(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody PadraoRequest req) {
        return service.atualizar(u, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(UsuarioAutenticado u, @PathVariable UUID id) {
        service.desativar(u, id);
    }

    @PatchMapping("/ordem")
    public List<PadraoResponse> reordenar(UsuarioAutenticado u, @Valid @RequestBody OrdemRequest req) {
        return service.reordenar(u, req.ids());
    }
}
