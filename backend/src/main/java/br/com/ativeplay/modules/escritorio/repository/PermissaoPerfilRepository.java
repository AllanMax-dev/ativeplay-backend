package br.com.ativeplay.modules.escritorio.repository;

import br.com.ativeplay.modules.escritorio.domain.PermissaoPerfil;
import br.com.ativeplay.shared.seguranca.Perfil;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PermissaoPerfilRepository extends JpaRepository<PermissaoPerfil, PermissaoPerfil.Chave> {

    List<PermissaoPerfil> findByEscritorioIdAndPerfil(UUID escritorioId, Perfil perfil);

    List<PermissaoPerfil> findByEscritorioId(UUID escritorioId);

    @Modifying
    @Query("delete from PermissaoPerfil p where p.escritorioId = :escritorioId")
    void deleteByEscritorioId(UUID escritorioId);
}
