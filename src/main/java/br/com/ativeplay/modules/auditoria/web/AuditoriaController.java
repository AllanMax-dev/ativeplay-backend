package br.com.ativeplay.modules.auditoria.web;

import br.com.ativeplay.modules.auditoria.domain.Auditoria;
import br.com.ativeplay.modules.auditoria.repository.AuditoriaRepository;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auditoria")
@RestController
@RequestMapping("/api/v1/auditoria")
@RequerModulo(Modulo.AUDITORIA)
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaRepository repositorio;

    /** Linha do tempo do escritório, com filtros opcionais. acao filtra por prefixo (ex.: "tarefa."). */
    @GetMapping
    public Pagina<Auditoria> listar(UsuarioAutenticado u,
            @RequestParam(required = false) UUID empresaId,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            Pageable pageable) {
        Specification<Auditoria> spec = (r, q, cb) -> cb.equal(r.get("escritorioId"), u.escritorioId());
        if (empresaId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("empresaId"), empresaId));
        if (usuarioId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("usuarioId"), usuarioId));
        if (entidade != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("entidade"), entidade));
        if (entidadeId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("entidadeId"), entidadeId));
        if (acao != null) spec = spec.and((r, q, cb) -> cb.like(r.get("acao"), acao + "%"));
        if (de != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("criadoEm"), de));
        if (ate != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("criadoEm"), ate));
        var ordenado = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "criadoEm"));
        return Pagina.de(repositorio.findAll(spec, ordenado));
    }
}
