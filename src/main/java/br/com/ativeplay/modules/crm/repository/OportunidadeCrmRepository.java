package br.com.ativeplay.modules.crm.repository;

import br.com.ativeplay.modules.crm.domain.OportunidadeCrm;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface OportunidadeCrmRepository
        extends JpaRepository<OportunidadeCrm, UUID>, JpaSpecificationExecutor<OportunidadeCrm> {

    /** Resumo por etapa: [etapaId, quantidade, soma do valor mensal]. */
    interface ResumoEtapa {
        UUID getEtapaId();

        long getQuantidade();

        BigDecimal getValorTotal();
    }

    Optional<OportunidadeCrm> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    List<OportunidadeCrm> findByEscritorioIdOrderByOrdemAsc(UUID escritorioId);

    List<OportunidadeCrm> findByEscritorioIdAndEtapaIdOrderByOrdemAsc(UUID escritorioId, UUID etapaId);

    boolean existsByEtapaId(UUID etapaId);

    @Query("""
            select o.etapaId as etapaId, count(o) as quantidade, coalesce(sum(o.valorMensal), 0) as valorTotal
            from OportunidadeCrm o where o.escritorioId = :escritorioId group by o.etapaId
            """)
    List<ResumoEtapa> resumoPorEtapa(UUID escritorioId);
}
