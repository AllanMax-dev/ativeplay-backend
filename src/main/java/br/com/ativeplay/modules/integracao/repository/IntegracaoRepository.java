package br.com.ativeplay.modules.integracao.repository;

import br.com.ativeplay.modules.integracao.domain.Integracao;
import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IntegracaoRepository extends JpaRepository<Integracao, UUID>, JpaSpecificationExecutor<Integracao> {

    Optional<Integracao> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    boolean existsByEmpresaIdAndProvedor(UUID empresaId, ProvedorIntegracao provedor);
}
