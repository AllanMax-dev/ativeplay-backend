package br.com.ativeplay.modules.sessao.web;

import br.com.ativeplay.modules.sessao.service.SessaoTrabalhoService;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.IniciarSessaoRequest;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.RentabilidadeResponse;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.ResumoDiaResponse;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.SessaoResponse;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.Perfis;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sessões de trabalho")
@RestController
@RequestMapping("/api/v1/sessoes")
@RequerModulo(Modulo.TAREFAS)
@RequiredArgsConstructor
public class SessaoTrabalhoController {

    private final SessaoTrabalhoService service;

    @Operation(summary = "Entra em uma empresa (encerra a sessão aberta, se houver)")
    @PostMapping("/iniciar")
    public SessaoResponse iniciar(UsuarioAutenticado u, @Valid @RequestBody IniciarSessaoRequest req) {
        return service.iniciar(u, req.empresaId());
    }

    @PostMapping("/encerrar")
    public SessaoResponse encerrar(UsuarioAutenticado u) {
        return service.encerrar(u);
    }

    @Operation(summary = "Sessão aberta do usuário (204 se não houver)")
    @GetMapping("/atual")
    public ResponseEntity<SessaoResponse> atual(UsuarioAutenticado u) {
        return service.atual(u).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Contador geral do dia: tempo total dentro das empresas, sessão aberta e divisão por empresa")
    @GetMapping("/resumo")
    public ResumoDiaResponse resumo(UsuarioAutenticado u,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) UUID usuarioId) {
        return service.resumoDia(u, data, usuarioId);
    }

    @Operation(summary = "Tempo da equipe por empresa x honorário no período (padrão: últimos 30 dias)")
    @Perfis({Perfil.ADMIN, Perfil.GESTOR})
    @GetMapping("/rentabilidade")
    public RentabilidadeResponse rentabilidade(UsuarioAutenticado u,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.rentabilidade(u, de, ate);
    }

    @GetMapping
    public Pagina<SessaoResponse> listar(UsuarioAutenticado u,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) UUID empresaId,
            @RequestParam(required = false) Instant de,
            @RequestParam(required = false) Instant ate,
            Pageable pageable) {
        return service.listar(u, usuarioId, empresaId, de, ate, pageable);
    }
}
