package br.com.ativeplay.modules.escritorio.domain;

import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Módulo liberado para um perfil dentro de um escritório (escrita=false: somente leitura). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "permissoes_perfil")
@IdClass(PermissaoPerfil.Chave.class)
public class PermissaoPerfil {

    @Id
    @Column(name = "escritorio_id")
    private UUID escritorioId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Perfil perfil;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Modulo modulo;

    @Column(nullable = false)
    private boolean escrita;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Chave implements Serializable {
        private UUID escritorioId;
        private Perfil perfil;
        private Modulo modulo;
    }
}
