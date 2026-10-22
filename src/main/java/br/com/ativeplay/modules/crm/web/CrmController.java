package br.com.ativeplay.modules.crm.web;

import br.com.ativeplay.modules.crm.service.EtapaCrmService;
import br.com.ativeplay.modules.crm.service.OportunidadeCrmService;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.AtividadeRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.AtividadeResponse;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.ConversaoResponse;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.EtapaFunil;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.EtapaRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.EtapaResponse;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.MoverRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.OportunidadeRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.OportunidadeResponse;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.OrdemRequest;
import br.com.ativeplay.shared.seguranca.Modulo;
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

@Tag(name = "CRM")
@RestController
@RequestMapping("/api/v1/crm")
@RequerModulo(Modulo.CRM)
@RequiredArgsConstructor
public class CrmController {

    private final EtapaCrmService etapas;
    private final OportunidadeCrmService oportunidades;

    // ---------- etapas ----------

    @GetMapping("/etapas")
    public List<EtapaResponse> listarEtapas(UsuarioAutenticado u) {
        return etapas.listar(u);
    }

    @PostMapping("/etapas")
    @ResponseStatus(HttpStatus.CREATED)
    public EtapaResponse criarEtapa(UsuarioAutenticado u, @Valid @RequestBody EtapaRequest req) {
        return etapas.criar(u, req);
    }

    @PutMapping("/etapas/{id}")
    public EtapaResponse atualizarEtapa(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody EtapaRequest req) {
        return etapas.atualizar(u, id, req);
    }

    @DeleteMapping("/etapas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirEtapa(UsuarioAutenticado u, @PathVariable UUID id) {
        etapas.excluir(u, id);
    }

    @PatchMapping("/etapas/ordem")
    public List<EtapaResponse> reordenarEtapas(UsuarioAutenticado u, @Valid @RequestBody OrdemRequest req) {
        return etapas.reordenar(u, req.ids());
    }

    // ---------- oportunidades ----------

    @Operation(summary = "Quadro kanban: etapas com suas oportunidades")
    @GetMapping("/funil")
    public List<EtapaFunil> funil(UsuarioAutenticado u) {
        return oportunidades.funil(u);
    }

    @GetMapping("/oportunidades")
    public Pagina<OportunidadeResponse> listar(UsuarioAutenticado u,
            @RequestParam(required = false) UUID etapaId,
            @RequestParam(required = false) UUID responsavelId,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean abertas,
            Pageable pageable) {
        return oportunidades.listar(u, etapaId, responsavelId, busca, abertas, pageable);
    }

    @GetMapping("/oportunidades/{id}")
    public OportunidadeResponse obter(UsuarioAutenticado u, @PathVariable UUID id) {
        return oportunidades.obter(u, id);
    }

    @PostMapping("/oportunidades")
    @ResponseStatus(HttpStatus.CREATED)
    public OportunidadeResponse criar(UsuarioAutenticado u, @Valid @RequestBody OportunidadeRequest req) {
        return oportunidades.criar(u, req);
    }

    @PutMapping("/oportunidades/{id}")
    public OportunidadeResponse atualizar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody OportunidadeRequest req) {
        return oportunidades.atualizar(u, id, req);
    }

    @DeleteMapping("/oportunidades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(UsuarioAutenticado u, @PathVariable UUID id) {
        oportunidades.excluir(u, id);
    }

    @Operation(summary = "Move a oportunidade de etapa/posição (arrastar e soltar no kanban)")
    @PatchMapping("/oportunidades/{id}/mover")
    public OportunidadeResponse mover(UsuarioAutenticado u, @PathVariable UUID id, @Valid @RequestBody MoverRequest req) {
        return oportunidades.mover(u, id, req);
    }

    @Operation(summary = "Converte a oportunidade ganha em empresa (cliente) em onboarding")
    @PostMapping("/oportunidades/{id}/converter")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversaoResponse converter(UsuarioAutenticado u, @PathVariable UUID id) {
        return oportunidades.converter(u, id);
    }

    // ---------- atividades ----------

    @GetMapping("/oportunidades/{id}/atividades")
    public List<AtividadeResponse> atividades(UsuarioAutenticado u, @PathVariable UUID id) {
        return oportunidades.atividades(u, id);
    }

    @PostMapping("/oportunidades/{id}/atividades")
    @ResponseStatus(HttpStatus.CREATED)
    public AtividadeResponse criarAtividade(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody AtividadeRequest req) {
        return oportunidades.criarAtividade(u, id, req);
    }

    @PatchMapping("/atividades/{id}/concluir")
    public AtividadeResponse concluirAtividade(UsuarioAutenticado u, @PathVariable UUID id) {
        return oportunidades.concluirAtividade(u, id);
    }

    @DeleteMapping("/atividades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirAtividade(UsuarioAutenticado u, @PathVariable UUID id) {
        oportunidades.excluirAtividade(u, id);
    }
}
