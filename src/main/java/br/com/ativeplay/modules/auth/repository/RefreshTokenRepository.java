package br.com.ativeplay.modules.auth.repository;

import br.com.ativeplay.modules.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revogadoEm = :agora where t.usuarioId = :usuarioId and t.revogadoEm is null")
    int revogarTodos(UUID usuarioId, Instant agora);
}
