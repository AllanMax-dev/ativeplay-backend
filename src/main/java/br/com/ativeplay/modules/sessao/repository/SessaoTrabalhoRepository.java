package br.com.ativeplay.modules.sessao.repository;

import br.com.ativeplay.modules.sessao.domain.SessaoTrabalho;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface SessaoTrabalhoRepository
        extends JpaRepository<SessaoTrabalho, UUID>, JpaSpecificationExecutor<SessaoTrabalho> {

    Optional<SessaoTrabalho> findByUsuarioIdAndFimIsNull(UUID usuarioId);

    /** Sessões do usuário que tocam o intervalo [de, ate) (inclusive a aberta). */
    @Query("""
            select s from SessaoTrabalho s
            where s.escritorioId = :escritorioId and s.usuarioId = :usuarioId
              and s.inicio < :ate and (s.fim is null or s.fim > :de)
            order by s.inicio
            """)
    List<SessaoTrabalho> doUsuarioNoPeriodo(UUID escritorioId, UUID usuarioId, Instant de, Instant ate);

    /** Sessões de toda a equipe que tocam o intervalo [de, ate). */
    @Query("""
            select s from SessaoTrabalho s
            where s.escritorioId = :escritorioId and s.inicio < :ate and (s.fim is null or s.fim > :de)
            """)
    List<SessaoTrabalho> doEscritorioNoPeriodo(UUID escritorioId, Instant de, Instant ate);
}
