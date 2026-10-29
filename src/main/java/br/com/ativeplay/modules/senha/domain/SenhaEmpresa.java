package br.com.ativeplay.modules.senha.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Acesso de um cliente guardado pelo escritório (banco, certificado, portal de energia,
 * prefeitura, ERP...). A senha fica cifrada (AES-256-GCM) e nunca sai nas listagens.
 */
@Getter
@Setter
@Entity
@Table(name = "senhas_empresa")
public class SenhaEmpresa extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    /** Nome do acesso, ex.: "Banco Itaú", "Portal Equatorial". */
    @Column(nullable = false, length = 150)
    private String acesso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaSenha categoria = CategoriaSenha.OUTRO;

    /** Login, CPF/CNPJ, agência/conta ou e-mail usado no acesso. */
    @Column(length = 200)
    private String usuario;

    @Column(name = "senha_cifrada", columnDefinition = "text")
    private String senhaCifrada;

    @Column(length = 500)
    private String url;

    @Column(columnDefinition = "text")
    private String observacoes;

    @Column(name = "criado_por_id", updatable = false)
    private UUID criadoPorId;

    @Column(name = "atualizado_por_id")
    private UUID atualizadoPorId;
}
