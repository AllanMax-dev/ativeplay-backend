package br.com.ativeplay.modules.usuario.repository;

import br.com.ativeplay.modules.usuario.domain.UsuarioSetor;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioSetorRepository extends JpaRepository<UsuarioSetor, UsuarioSetor.Chave> {

    @Query("select us.setorId from UsuarioSetor us where us.usuarioId = :usuarioId")
    List<UUID> findSetorIdsByUsuarioId(UUID usuarioId);

    long countBySetorId(UUID setorId);

    List<UsuarioSetor> findByUsuarioIdIn(Collection<UUID> usuarioIds);

    @Modifying
    @Query("delete from UsuarioSetor us where us.usuarioId = :usuarioId")
    void deleteByUsuarioId(UUID usuarioId);
}
