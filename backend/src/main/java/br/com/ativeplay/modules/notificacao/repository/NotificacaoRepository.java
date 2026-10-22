package br.com.ativeplay.modules.notificacao.repository;

import br.com.ativeplay.modules.notificacao.domain.Notificacao;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    Page<Notificacao> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId, Pageable pageable);

    Page<Notificacao> findByUsuarioIdAndLidaEmIsNullOrderByCriadoEmDesc(UUID usuarioId, Pageable pageable);

    long countByUsuarioIdAndLidaEmIsNull(UUID usuarioId);

    Optional<Notificacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora where n.usuarioId = :usuarioId and n.lidaEm is null")
    int marcarTodasLidas(UUID usuarioId, Instant agora);
}
