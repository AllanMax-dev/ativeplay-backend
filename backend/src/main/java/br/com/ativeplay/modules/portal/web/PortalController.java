package br.com.ativeplay.modules.portal.web;

import br.com.ativeplay.modules.portal.service.PortalService;
import br.com.ativeplay.modules.portal.web.dto.PortalDtos.DocumentoPortal;
import br.com.ativeplay.modules.portal.web.dto.PortalDtos.EmpresaPortal;
import br.com.ativeplay.modules.portal.web.dto.PortalDtos.TarefaPortal;
import br.com.ativeplay.modules.tarefa.domain.StatusOcorrencia;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Rotas do cliente final (perfil CLIENTE), sempre limitadas à própria empresa. */
@Tag(name = "Portal do cliente")
@RestController
@RequestMapping("/api/v1/portal")
@Perfis(Perfil.CLIENTE)
@RequiredArgsConstructor
public class PortalController {

    private final PortalService service;

    @GetMapping("/empresa")
    public EmpresaPortal empresa(UsuarioAutenticado u) {
        return service.empresa(u);
    }

    @Operation(summary = "Tarefas visíveis ao cliente")
    @GetMapping("/tarefas")
    public Pagina<TarefaPortal> tarefas(UsuarioAutenticado u,
            @RequestParam(required = false) StatusOcorrencia status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            Pageable pageable) {
        return service.tarefas(u, status, de, ate, pageable);
    }

    @GetMapping("/documentos")
    public Pagina<DocumentoPortal> documentos(UsuarioAutenticado u, Pageable pageable) {
        return service.documentos(u, pageable);
    }

    @GetMapping("/documentos/{id}/download")
    public ResponseEntity<Resource> download(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.download(u, id);
    }

    @Operation(summary = "Envia documentos para o escritório (até 10 arquivos)")
    @PostMapping(value = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<DocumentoPortal> enviar(UsuarioAutenticado u, @RequestPart("arquivos") List<MultipartFile> arquivos,
            @RequestParam(required = false) String categoria) {
        return service.enviar(u, arquivos, categoria);
    }
}
