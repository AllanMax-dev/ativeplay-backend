package br.com.ativeplay.modules.tarefa.repository;

import br.com.ativeplay.modules.tarefa.domain.OcorrenciaTarefa;
import br.com.ativeplay.modules.tarefa.domain.StatusOcorrencia;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OcorrenciaTarefaRepository
        extends JpaRepository<OcorrenciaTarefa, UUID>, JpaSpecificationExecutor<OcorrenciaTarefa> {

    Optional<OcorrenciaTarefa> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    /** Ocorrências com cronômetro rodando para o usuário (para parar antes de iniciar outra). */
    List<OcorrenciaTarefa> findByEscritorioIdAndResponsavelIdAndCronometroInicioIsNotNull(UUID escritorioId,
            UUID responsavelId);

    @Query("""
            select o from OcorrenciaTarefa o
            where o.status in :abertas and o.slaViolado = false and o.prazo < :agora
            """)
    List<OcorrenciaTarefa> vencidasSemSla(Collection<StatusOcorrencia> abertas, Instant agora);

    @Modifying
    @Query("update OcorrenciaTarefa o set o.slaViolado = true where o.id in :ids and o.slaViolado = false")
    int marcarSlaViolado(Collection<UUID> ids);
}
