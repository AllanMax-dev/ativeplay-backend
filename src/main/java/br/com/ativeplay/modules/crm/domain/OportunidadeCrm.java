package br.com.ativeplay.modules.crm.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "oportunidades_crm")
public class OportunidadeCrm extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "etapa_id", nullable = false)
    private UUID etapaId;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    /** Empresa criada na conversão (oportunidade ganha). */
    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(name = "razao_social", length = 200)
    private String razaoSocial;

    @Column(length = 14)
    private String cnpj;

    @Column(name = "contato_nome", length = 160)
    private String contatoNome;

    @Column(name = "contato_email", length = 254)
    private String contatoEmail;

    @Column(name = "contato_telefone", length = 20)
    private String contatoTelefone;

    @Column(length = 60)
    private String origem;

    @Column(name = "valor_mensal", precision = 12, scale = 2)
    private BigDecimal valorMensal;

    @Column(name = "previsao_fechamento")
    private LocalDate previsaoFechamento;

    @Column(name = "motivo_perda", columnDefinition = "text")
    private String motivoPerda;

    @Column(nullable = false)
    private int ordem;

    @Column(name = "fechada_em")
    private Instant fechadaEm;
}
