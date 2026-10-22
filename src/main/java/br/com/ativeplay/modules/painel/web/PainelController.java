package br.com.ativeplay.modules.painel.web;

import br.com.ativeplay.modules.painel.service.PainelService;
import br.com.ativeplay.modules.painel.web.dto.PainelDtos.ProdutividadeResponse;
import br.com.ativeplay.modules.painel.web.dto.PainelDtos.ResumoResponse;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.RequerModulo;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Painel")
@RestController
@RequestMapping("/api/v1/painel")
@RequerModulo(Modulo.PAINEL)
@RequiredArgsConstructor
public class PainelController {

    private final PainelService service;

    @Operation(summary = "Indicadores do dia e dos últimos 30 dias (respeita a carteira do usuário)")
    @GetMapping("/resumo")
    public ResumoResponse resumo(UsuarioAutenticado u,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.resumo(u, data);
    }

    @Operation(summary = "Produtividade por responsável no período (padrão: últimos 30 dias)")
    @GetMapping("/produtividade")
    public ProdutividadeResponse produtividade(UsuarioAutenticado u,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.produtividade(u, de, ate);
    }
}
