package br.com.ativeplay.modules.tarefa.repository;

import br.com.ativeplay.modules.tarefa.domain.ComentarioOcorrencia;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComentarioOcorrenciaRepository extends JpaRepository<ComentarioOcorrencia, UUID> {

    List<ComentarioOcorrencia> findByOcorrenciaIdOrderByCriadoEmAsc(UUID ocorrenciaId);

    /** Pares [ocorrenciaId, quantidade]. */
    @Query("select c.ocorrenciaId, count(c) from ComentarioOcorrencia c where c.ocorrenciaId in :ids group by c.ocorrenciaId")
    List<Object[]> contarPorOcorrencia(Collection<UUID> ids);
}
