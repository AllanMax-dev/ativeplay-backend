package br.com.ativeplay.modules.setor.repository;

import br.com.ativeplay.modules.setor.domain.Setor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetorRepository extends JpaRepository<Setor, UUID> {

    boolean existsByIdAndEscritorioId(UUID id, UUID escritorioId);

    Optional<Setor> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    List<Setor> findByEscritorioIdOrderByOrdemAsc(UUID escritorioId);

    List<Setor> findByIdInAndEscritorioId(Collection<UUID> ids, UUID escritorioId);

    boolean existsByEscritorioIdAndNomeIgnoreCase(UUID escritorioId, String nome);
}
