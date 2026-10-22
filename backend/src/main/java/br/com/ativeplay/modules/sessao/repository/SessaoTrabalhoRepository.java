package br.com.ativeplay.modules.sessao.repository;

import br.com.ativeplay.modules.sessao.domain.SessaoTrabalho;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SessaoTrabalhoRepository
        extends JpaRepository<SessaoTrabalho, UUID>, JpaSpecificationExecutor<SessaoTrabalho> {

    Optional<SessaoTrabalho> findByUsuarioIdAndFimIsNull(UUID usuarioId);
}
