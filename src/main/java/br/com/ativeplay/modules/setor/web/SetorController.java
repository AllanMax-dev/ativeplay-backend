package br.com.ativeplay.modules.setor.web;

import br.com.ativeplay.modules.setor.service.SetorService;
import br.com.ativeplay.modules.setor.web.dto.SetorDtos.OrdemRequest;
import br.com.ativeplay.modules.setor.web.dto.SetorDtos.SetorRequest;
import br.com.ativeplay.modules.setor.web.dto.SetorDtos.SetorResposta;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Leitura liberada para toda a equipe; alterações exigem o módulo CONFIGURACOES. */
@Tag(name = "Setores")
@RestController
@RequestMapping("/api/v1/setores")
@RequiredArgsConstructor
public class SetorController {

    private final SetorService service;

    @GetMapping
    public List<SetorResposta> listar(UsuarioAutenticado u) {
        return service.listar(u);
    }

    @RequerModulo(Modulo.CONFIGURACOES)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SetorResposta criar(UsuarioAutenticado u, @Valid @RequestBody SetorRequest req) {
        return service.criar(u, req);
    }

    @RequerModulo(Modulo.CONFIGURACOES)
    @PutMapping("/{id}")
    public SetorResposta atualizar(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody SetorRequest req) {
        return service.atualizar(u, id, req);
    }

    @RequerModulo(Modulo.CONFIGURACOES)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(UsuarioAutenticado u, @PathVariable UUID id) {
        service.desativar(u, id);
    }

    @RequerModulo(Modulo.CONFIGURACOES)
    @PatchMapping("/ordem")
    public List<SetorResposta> reordenar(UsuarioAutenticado u, @Valid @RequestBody OrdemRequest req) {
        return service.reordenar(u, req.ids());
    }
}
