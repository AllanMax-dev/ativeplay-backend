package br.com.ativeplay.modules.tarefa.web;

import br.com.ativeplay.modules.tarefa.domain.StatusOcorrencia;
import br.com.ativeplay.modules.tarefa.service.GeracaoOcorrenciasService;
import br.com.ativeplay.modules.tarefa.service.OcorrenciaTarefaService;
import br.com.ativeplay.modules.tarefa.service.OcorrenciaTarefaService.Filtro;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.ComentarioRequest;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.ComentarioResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.EmpresaDoDiaResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.GerarRequest;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaAvulsaRequest;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaDetalheResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaResponse;
import br.com.ativeplay.modules.tarefa.web.dto.OcorrenciaDtos.OcorrenciaUpdateRequest;
import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Datas;
import br.com.ativeplay.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tarefas — ocorrências")
@RestController
@RequestMapping("/api/v1/tarefas/ocorrencias")
@RequerModulo(Modulo.TAREFAS)
@RequiredArgsConstructor
public class OcorrenciaTarefaController {

    private final OcorrenciaTarefaService service;
    private final GeracaoOcorrenciasService geracao;
    private final EscritorioRepository escritorios;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;

    @Operation(summary = "Lista ocorrências (ordenadas por prazo e prioridade), no escopo da carteira do usuário")
    @GetMapping
    public Pagina<OcorrenciaResponse> listar(UsuarioAutenticado u,
            @RequestParam(required = false) UUID empresaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) List<StatusOcorrencia> status,
            @RequestParam(required = false) UUID responsavelId,
            @RequestParam(required = false) UUID setorId,
            @RequestParam(defaultValue = "false") boolean atrasadas,
            @RequestParam(defaultValue = "false") boolean minhas,
            @RequestParam(defaultValue = "false") boolean carteira,
            Pageable pageable) {
        var filtro = new Filtro(empresaId, data, de, ate, status, responsavelId, setorId, atrasadas, minhas, carteira);
        return service.listar(u, filtro, pageable);
    }

    @Operation(summary = "Empresas para o filtro de Tarefas do dia, com contagens da data (padrão: hoje). "
            + "carteira=true mostra só as empresas da carteira do usuário logado")
    @GetMapping("/empresas")
    public List<EmpresaDoDiaResponse> empresasDoDia(UsuarioAutenticado u,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(defaultValue = "false") boolean carteira) {
        return service.empresasDoDia(u, data, carteira);
    }

    @GetMapping("/{id}")
    public OcorrenciaDetalheResponse detalhar(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.detalhar(u, id);
    }

    @Operation(summary = "Cria uma tarefa avulsa (sem recorrência)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OcorrenciaResponse criar(UsuarioAutenticado u, @Valid @RequestBody OcorrenciaAvulsaRequest req) {
        return service.criarAvulsa(u, req);
    }

    @PatchMapping("/{id}")
    public OcorrenciaResponse atualizar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody OcorrenciaUpdateRequest req) {
        return service.atualizar(u, id, req);
    }

    @Operation(summary = "Inicia o cronômetro (pausa outra tarefa do usuário que estiver rodando)")
    @PostMapping("/{id}/iniciar")
    public OcorrenciaResponse iniciar(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.iniciar(u, id);
    }

    @PostMapping("/{id}/pausar")
    public OcorrenciaResponse pausar(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.pausar(u, id);
    }

    @PostMapping("/{id}/concluir")
    public OcorrenciaResponse concluir(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.concluir(u, id);
    }

    @PostMapping("/{id}/reabrir")
    public OcorrenciaResponse reabrir(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.reabrir(u, id);
    }

    @PostMapping("/{id}/cancelar")
    public OcorrenciaResponse cancelar(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.cancelar(u, id);
    }

    @GetMapping("/{id}/comentarios")
    public List<ComentarioResponse> comentarios(UsuarioAutenticado u, @PathVariable UUID id) {
        return service.listarComentarios(u, id);
    }

    @PostMapping("/{id}/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ComentarioResponse comentar(UsuarioAutenticado u, @PathVariable UUID id,
            @Valid @RequestBody ComentarioRequest req) {
        return service.comentar(u, id, req);
    }

    @Operation(summary = "Gera sob demanda as ocorrências de uma data (padrão: hoje no fuso do escritório)")
    @Perfis({Perfil.ADMIN, Perfil.GESTOR})
    @PostMapping("/gerar")
    public Map<String, Integer> gerar(UsuarioAutenticado u, @RequestBody(required = false) GerarRequest req) {
        var empresaId = req == null ? null : req.empresaId();
        if (empresaId != null) escopo.garantirEmpresa(u, empresaId);
        var data = req != null && req.data() != null ? req.data()
                : Datas.hoje(escritorios.findById(u.escritorioId()).map(e -> e.getFusoHorario()).orElse(null));
        int criadas = geracao.gerar(u.escritorioId(), data, empresaId);
        auditoria.registrar(u, "tarefa.gerar", "ocorrencia", null, empresaId,
                criadas + " ocorrência(s) gerada(s) para " + data);
        return Map.of("criadas", criadas);
    }
}
