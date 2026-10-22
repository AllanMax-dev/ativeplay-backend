package br.com.ativeplay.modules.usuario.repository;

import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.shared.seguranca.Perfil;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    boolean existsByIdAndEscritorioIdAndPerfilNot(UUID id, UUID escritorioId, Perfil perfil);

    List<Usuario> findByIdInAndEscritorioId(Collection<UUID> ids, UUID escritorioId);

    List<Usuario> findByEmpresaId(UUID empresaId);

    long countByEscritorioId(UUID escritorioId);
}
