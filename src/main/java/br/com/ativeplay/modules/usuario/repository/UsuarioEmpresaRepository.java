package br.com.ativeplay.modules.usuario.repository;

import br.com.ativeplay.modules.usuario.domain.UsuarioEmpresa;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioEmpresaRepository extends JpaRepository<UsuarioEmpresa, UsuarioEmpresa.Chave> {

    @Query("select c.empresaId from UsuarioEmpresa c where c.usuarioId = :usuarioId")
    List<UUID> findEmpresaIdsByUsuarioId(UUID usuarioId);

    @Query("select c.usuarioId from UsuarioEmpresa c where c.empresaId = :empresaId")
    List<UUID> findUsuarioIdsByEmpresaId(UUID empresaId);

    List<UsuarioEmpresa> findByUsuarioIdIn(Collection<UUID> usuarioIds);

    @Modifying
    @Query("delete from UsuarioEmpresa c where c.usuarioId = :usuarioId")
    void deleteByUsuarioId(UUID usuarioId);

    @Modifying
    @Query("delete from UsuarioEmpresa c where c.empresaId = :empresaId")
    void deleteByEmpresaId(UUID empresaId);
}
