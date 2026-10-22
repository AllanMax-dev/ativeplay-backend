package br.com.ativeplay.modules.documento.web;

import br.com.ativeplay.modules.documento.service.DocumentoService;
import br.com.ativeplay.modules.documento.web.dto.DocumentoResposta;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Documentos")
@RestController
@RequestMapping("/api/v1/documentos")
@RequerModulo(Modulo.DOCUMENTOS)
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService service;

    @Operation(summary = "Envia até 10 arquivos para uma empresa (opcionalmente como anexo de uma tarefa)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<DocumentoResposta> enviar(UsuarioAutenticado u,
            @RequestPart("arquivos") List<MultipartFile> arquivos,
            @RequestParam UUID empresaId,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) UUID ocorrenciaId,
            @RequestParam(defaultValue = "true") boolean visivelCliente) {
        return service.salvarArquivos(u, empresaId, arquivos, categoria, ocorrenciaId, visivelCliente);
    }

    @GetMapping
    public Pagina<DocumentoResposta> listar(UsuarioAutenticado u,
            @RequestParam(required = false) UUID empresaId,
            @RequestParam(required = false) UUID ocorrenciaId,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String busca,
            Pageable pageable) {
        return service.listar(u, empresaId, ocorrenciaId, categoria, busca, pageable);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.download(service.buscar(u, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(UsuarioAutenticado u, @PathVariable UUID id) {
        service.excluir(u, id);
    }
}
