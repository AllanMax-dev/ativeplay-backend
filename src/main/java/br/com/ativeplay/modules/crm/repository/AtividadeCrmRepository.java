package br.com.ativeplay.modules.crm.repository;

import br.com.ativeplay.modules.crm.domain.AtividadeCrm;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtividadeCrmRepository extends JpaRepository<AtividadeCrm, UUID> {

    List<AtividadeCrm> findByOportunidadeIdOrderByCriadoEmDesc(UUID oportunidadeId);

    Optional<AtividadeCrm> findByIdAndEscritorioId(UUID id, UUID escritorioId);
}
