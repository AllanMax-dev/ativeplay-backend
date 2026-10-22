package br.com.ativeplay.modules.integracao.repository;

import br.com.ativeplay.modules.integracao.domain.SincronizacaoIntegracao;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SincronizacaoIntegracaoRepository extends JpaRepository<SincronizacaoIntegracao, UUID> {

    Page<SincronizacaoIntegracao> findByIntegracaoIdOrderByIniciadaEmDesc(UUID integracaoId, Pageable pageable);
}
