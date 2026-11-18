package br.com.ativeplay.modules.auth.repository;

import br.com.ativeplay.modules.auth.domain.SessaoAcesso;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoAcessoRepository
        extends JpaRepository<SessaoAcesso, UUID> {

    Optional<SessaoAcesso> findByIdAndUsuarioIdAndEscritorioId(
            UUID id,
            UUID usuarioId,
            UUID escritorioId
    );

    List<SessaoAcesso> findByUsuarioIdAndEscritorioIdOrderByInicioDesc(
            UUID usuarioId,
            UUID escritorioId,
            Pageable pageable
    );

    List<SessaoAcesso> findByUsuarioIdAndFimIsNull(
            UUID usuarioId
    );
}
