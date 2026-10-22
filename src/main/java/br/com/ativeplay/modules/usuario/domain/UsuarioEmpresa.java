package br.com.ativeplay.modules.usuario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Carteira: empresas atendidas por um usuário da equipe. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuario_empresas")
@IdClass(UsuarioEmpresa.Chave.class)
public class UsuarioEmpresa {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Id
    @Column(name = "empresa_id")
    private UUID empresaId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Chave implements Serializable {
        private UUID usuarioId;
        private UUID empresaId;
    }
}
