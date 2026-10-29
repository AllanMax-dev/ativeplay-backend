package br.com.ativeplay.modules.checklist.repository;

import br.com.ativeplay.modules.checklist.domain.ChecklistModeloItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChecklistModeloItemRepository extends JpaRepository<ChecklistModeloItem, UUID> {

    Optional<ChecklistModeloItem> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    @EntityGraph(attributePaths = "subitens")
    List<ChecklistModeloItem> findByEscritorioIdOrderByOrdem(UUID escritorioId);

    List<ChecklistModeloItem> findByEscritorioIdAndIdIn(UUID escritorioId, Collection<UUID> ids);

    @Query("select coalesce(max(i.ordem), -1) from ChecklistModeloItem i where i.escritorioId = :escritorioId")
    int ultimaOrdem(UUID escritorioId);
}
