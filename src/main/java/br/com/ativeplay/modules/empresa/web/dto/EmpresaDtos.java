package br.com.ativeplay.modules.empresa.web.dto;

import br.com.ativeplay.modules.empresa.domain.RegimeTributario;
import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import br.com.ativeplay.modules.integracao.domain.StatusIntegracao;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import br.com.ativeplay.shared.validacao.CnpjValido;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EmpresaDtos {

    private EmpresaDtos() {
    }

    public record EmpresaRequest(
            @NotBlank @Size(max = 200) String razaoSocial,
            @Size(max = 200) String nomeFantasia,
            @NotBlank @CnpjValido String cnpj,
            RegimeTributario regimeTributario,
            @Email @Size(max = 254) String email,
            @Size(max = 20) String telefone,
            UUID responsavelId,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal honorarioMensal,
            LocalDate dataInicio,
            @Size(max = 5000) String observacoes) {
    }

    public record StatusRequest(@NotNull StatusEmpresa status) {
    }

    public record EquipeRequest(@NotNull List<UUID> usuarioIds) {
    }

    /** Item da listagem, com indicadores de tarefas abertas. */
    public record EmpresaResumoResposta(UUID id, String razaoSocial, String nomeFantasia, String cnpj,
            String cnpjFormatado, RegimeTributario regimeTributario, StatusEmpresa status, String email,
            String telefone, UsuarioRef responsavel, BigDecimal honorarioMensal, LocalDate dataInicio,
            long tarefasAbertasHoje, long tarefasAtrasadas) {
    }

    public record UsuarioPortal(UUID id, String nome, String email, boolean ativo, Instant ultimoAcesso) {
    }

    public record IntegracaoResumo(UUID id, ProvedorIntegracao provedor, StatusIntegracao status,
            Instant ultimaSincronizacao) {
    }

    public record EmpresaDetalhe(UUID id, String razaoSocial, String nomeFantasia, String cnpj, String cnpjFormatado,
            RegimeTributario regimeTributario, StatusEmpresa status, String email, String telefone,
            UsuarioRef responsavel, BigDecimal honorarioMensal, LocalDate dataInicio, String observacoes,
            Instant criadoEm, Instant atualizadoEm, List<UsuarioRef> equipe, List<UsuarioPortal> usuariosPortal,
            List<IntegracaoResumo> integracoes) {
    }
}
