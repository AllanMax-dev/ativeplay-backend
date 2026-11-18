package br.com.ativeplay.modules.auth.repository;

import br.com.ativeplay.modules.auth.domain.SessaoAcesso;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessaoAcessoRepository
        extends JpaRepository<SessaoAcesso, UUID> {

    Optional<SessaoAcesso> findByIdAndUsuarioIdAndEscritorioId(
            UUID id,
            UUID usuarioId,
            UUID escritorioId
    );

    Optional<SessaoAcesso> findByIdAndEscritorioId(
            UUID id,
            UUID escritorioId
    );

    List<SessaoAcesso> findByUsuarioIdAndEscritorioIdOrderByInicioDesc(
            UUID usuarioId,
            UUID escritorioId,
            Pageable pageable
    );

    /** Registros abertos (sem fim e não pendentes) do usuário. */
    List<SessaoAcesso> findByUsuarioIdAndFimIsNullAndPendenteFalse(
            UUID usuarioId
    );

    List<SessaoAcesso> findByEscritorioIdAndFimIsNullAndPendenteTrue(
            UUID escritorioId
    );

    /**
     * Registros do escritório que tocam a janela [de, ate): o fim efetivo de um registro aberto é
     * agora e o de um pendente é o último sinal (ou o início).
     */
    @Query("""
            select s from SessaoAcesso s
            where s.escritorioId = :escritorioId and s.inicio < :ate
              and coalesce(s.fim, case when s.pendente = true then coalesce(s.ultimoSinal, s.inicio) else :agora end) > :de
            """)
    List<SessaoAcesso> findDoEscritorioNaJanela(UUID escritorioId, Instant de, Instant ate, Instant agora);

    @Query("""
            select s from SessaoAcesso s
            where s.escritorioId = :escritorioId and s.usuarioId = :usuarioId and s.inicio < :ate
              and coalesce(s.fim, case when s.pendente = true then coalesce(s.ultimoSinal, s.inicio) else :agora end) > :de
            order by s.inicio desc
            """)
    List<SessaoAcesso> findDoUsuarioNaJanela(UUID escritorioId, UUID usuarioId, Instant de, Instant ate,
            Instant agora);

    /** Atualiza o sinal só se o registro ainda estiver aberto (atômico com o job de pendentes). */
    @Modifying
    @Query("""
            update SessaoAcesso s set s.ultimoSinal = :agora
            where s.id = :id and s.usuarioId = :usuarioId and s.escritorioId = :escritorioId
              and s.fim is null and s.pendente = false
            """)
    int registrarSinal(UUID id, UUID usuarioId, UUID escritorioId, Instant agora);

    @Modifying
    @Query("""
            update SessaoAcesso s set s.pendente = true
            where s.fim is null and s.pendente = false and coalesce(s.ultimoSinal, s.inicio) < :limite
            """)
    int marcarPendentes(Instant limite);
}
