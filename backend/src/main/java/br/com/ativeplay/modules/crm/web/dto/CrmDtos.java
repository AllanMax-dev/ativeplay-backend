package br.com.ativeplay.modules.crm.web.dto;

import br.com.ativeplay.modules.crm.domain.AtividadeCrm;
import br.com.ativeplay.modules.crm.domain.EtapaCrm;
import br.com.ativeplay.modules.crm.domain.OportunidadeCrm;
import br.com.ativeplay.modules.crm.domain.TipoAtividadeCrm;
import br.com.ativeplay.modules.crm.domain.TipoEtapaCrm;
import br.com.ativeplay.shared.util.Cnpj;
import br.com.ativeplay.shared.validacao.CnpjValido;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs do CRM (funil, oportunidades e atividades). */
public final class CrmDtos {

    private static final String COR = "^#[0-9A-Fa-f]{6}$";

    private CrmDtos() {
    }

    // ---------- etapas ----------

    public record EtapaRequest(
            @NotBlank @Size(max = 80) String nome,
            @Pattern(regexp = COR, message = "cor deve estar no formato #RRGGBB") String cor,
            TipoEtapaCrm tipo) {
    }

    public record OrdemRequest(@NotEmpty List<@NotNull UUID> ids) {
    }

    public record EtapaResponse(UUID id, String nome, String cor, int ordem, TipoEtapaCrm tipo, long quantidade,
            BigDecimal valorTotal) {
        public static EtapaResponse de(EtapaCrm e, long quantidade, BigDecimal valorTotal) {
            return new EtapaResponse(e.getId(), e.getNome(), e.getCor(), e.getOrdem(), e.getTipo(), quantidade,
                    valorTotal);
        }
    }

    public record EtapaFunil(UUID id, String nome, String cor, int ordem, TipoEtapaCrm tipo, long quantidade,
            BigDecimal valorTotal, List<OportunidadeResponse> oportunidades) {
    }

    // ---------- oportunidades ----------

    public record OportunidadeRequest(
            UUID etapaId,
            UUID responsavelId,
            @NotBlank @Size(max = 160) String titulo,
            @Size(max = 200) String razaoSocial,
            @CnpjValido String cnpj,
            @Size(max = 160) String contatoNome,
            @Email @Size(max = 254) String contatoEmail,
            @Size(max = 20) String contatoTelefone,
            @Size(max = 60) String origem,
            @DecimalMin("0.00") BigDecimal valorMensal,
            LocalDate previsaoFechamento) {

        public String cnpjNormalizado() {
            return cnpj == null || cnpj.isBlank() ? null : Cnpj.normalizar(cnpj);
        }
    }

    public record MoverRequest(@NotNull UUID etapaId, @Min(0) Integer ordem, String motivoPerda) {
    }

    public record OportunidadeResponse(UUID id, UUID etapaId, UUID responsavelId, String responsavelNome,
            UUID empresaId, String titulo, String razaoSocial, String cnpj, String cnpjFormatado, String contatoNome,
            String contatoEmail, String contatoTelefone, String origem, BigDecimal valorMensal,
            LocalDate previsaoFechamento, String motivoPerda, int ordem, Instant fechadaEm, Instant criadoEm,
            Instant atualizadoEm) {
        public static OportunidadeResponse de(OportunidadeCrm o, String responsavelNome) {
            return new OportunidadeResponse(o.getId(), o.getEtapaId(), o.getResponsavelId(), responsavelNome,
                    o.getEmpresaId(), o.getTitulo(), o.getRazaoSocial(), o.getCnpj(),
                    o.getCnpj() == null ? null : Cnpj.formatar(o.getCnpj()), o.getContatoNome(), o.getContatoEmail(),
                    o.getContatoTelefone(), o.getOrigem(), o.getValorMensal(), o.getPrevisaoFechamento(),
                    o.getMotivoPerda(), o.getOrdem(), o.getFechadaEm(), o.getCriadoEm(), o.getAtualizadoEm());
        }
    }

    public record ConversaoResponse(UUID oportunidadeId, UUID empresaId, UUID etapaId) {
    }

    // ---------- atividades ----------

    public record AtividadeRequest(TipoAtividadeCrm tipo, @NotBlank String descricao, Instant agendadaPara) {
    }

    public record AtividadeResponse(UUID id, UUID oportunidadeId, UUID autorId, String autorNome,
            TipoAtividadeCrm tipo, String descricao, Instant agendadaPara, Instant concluidaEm, Instant criadoEm) {
        public static AtividadeResponse de(AtividadeCrm a, String autorNome) {
            return new AtividadeResponse(a.getId(), a.getOportunidadeId(), a.getAutorId(), autorNome, a.getTipo(),
                    a.getDescricao(), a.getAgendadaPara(), a.getConcluidaEm(), a.getCriadoEm());
        }
    }
}
