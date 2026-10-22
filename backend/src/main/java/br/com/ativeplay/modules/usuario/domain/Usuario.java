package br.com.ativeplay.modules.usuario.domain;

import br.com.ativeplay.shared.dominio.EntidadeAtualizavel;
import br.com.ativeplay.shared.seguranca.Perfil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class Usuario extends EntidadeAtualizavel {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    /** Preenchido apenas para o perfil CLIENTE: a empresa que o usuário representa. */
    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil = Perfil.OPERADOR;

    @Column(length = 80)
    private String cargo;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_acesso")
    private Instant ultimoAcesso;
}
