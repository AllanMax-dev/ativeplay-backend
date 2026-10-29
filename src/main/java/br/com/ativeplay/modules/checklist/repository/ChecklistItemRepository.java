package br.com.ativeplay.modules.checklist.repository;

import br.com.ativeplay.modules.checklist.domain.ChecklistItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    Optional<ChecklistItem> findByIdAndEscritorioIdAndEmpresaId(UUID id, UUID escritorioId, UUID empresaId);

    @EntityGraph(attributePaths = "subitens")
    List<ChecklistItem> findByEscritorioIdAndEmpresaId(UUID escritorioId, UUID empresaId);

    @EntityGraph(attributePaths = "subitens")
    List<ChecklistItem> findByEscritorioIdAndEmpresaIdAndConcluido(UUID escritorioId, UUID empresaId,
            boolean concluido);

    @Query("select coalesce(max(i.ordem), -1) from ChecklistItem i where i.empresaId = :empresaId")
    int ultimaOrdem(UUID empresaId);
}
