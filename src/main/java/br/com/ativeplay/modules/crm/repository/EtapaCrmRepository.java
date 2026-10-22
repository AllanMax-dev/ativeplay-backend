package br.com.ativeplay.modules.crm.repository;

import br.com.ativeplay.modules.crm.domain.EtapaCrm;
import br.com.ativeplay.modules.crm.domain.TipoEtapaCrm;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtapaCrmRepository extends JpaRepository<EtapaCrm, UUID> {

    List<EtapaCrm> findByEscritorioIdOrderByOrdemAsc(UUID escritorioId);

    Optional<EtapaCrm> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    Optional<EtapaCrm> findFirstByEscritorioIdAndTipoOrderByOrdemAsc(UUID escritorioId, TipoEtapaCrm tipo);

    boolean existsByEscritorioIdAndNomeIgnoreCase(UUID escritorioId, String nome);
}
