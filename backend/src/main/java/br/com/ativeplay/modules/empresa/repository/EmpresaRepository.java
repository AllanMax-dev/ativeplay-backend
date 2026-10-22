package br.com.ativeplay.modules.empresa.repository;

import br.com.ativeplay.modules.empresa.domain.Empresa;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID>, JpaSpecificationExecutor<Empresa> {

    Optional<Empresa> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    Optional<Empresa> findByEscritorioIdAndCnpj(UUID escritorioId, String cnpj);

    boolean existsByEscritorioIdAndCnpj(UUID escritorioId, String cnpj);

    List<Empresa> findByIdInAndEscritorioId(Collection<UUID> ids, UUID escritorioId);

    long countByEscritorioId(UUID escritorioId);
}
