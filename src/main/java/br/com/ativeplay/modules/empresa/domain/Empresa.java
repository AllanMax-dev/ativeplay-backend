package br.com.ativeplay.modules.empresa.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Cliente atendido pelo escritório. */
@Getter
@Setter
@Entity
@Table(name = "empresas")
public class Empresa extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 200)
    private String nomeFantasia;

    /** Normalizado (14 caracteres, aceita o formato alfanumérico). */
    @Column(nullable = false, length = 14)
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_tributario", nullable = false, length = 20)
    private RegimeTributario regimeTributario = RegimeTributario.SIMPLES_NACIONAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEmpresa status = StatusEmpresa.ONBOARDING;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(name = "honorario_mensal", precision = 12, scale = 2)
    private BigDecimal honorarioMensal;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(columnDefinition = "text")
    private String observacoes;
}
