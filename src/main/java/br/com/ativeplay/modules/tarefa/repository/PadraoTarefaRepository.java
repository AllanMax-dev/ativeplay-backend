package br.com.ativeplay.modules.tarefa.repository;

import br.com.ativeplay.modules.tarefa.domain.PadraoTarefa;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PadraoTarefaRepository extends JpaRepository<PadraoTarefa, UUID> {

    List<PadraoTarefa> findByEscritorioIdOrderByOrdemAsc(UUID escritorioId);

    List<PadraoTarefa> findByEscritorioIdAndAtivoOrderByOrdemAsc(UUID escritorioId, boolean ativo);

    List<PadraoTarefa> findByEscritorioIdAndIdIn(UUID escritorioId, Collection<UUID> ids);

    Optional<PadraoTarefa> findByIdAndEscritorioId(UUID id, UUID escritorioId);
}
