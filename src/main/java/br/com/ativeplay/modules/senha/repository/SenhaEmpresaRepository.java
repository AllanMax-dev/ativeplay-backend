package br.com.ativeplay.modules.senha.repository;

import br.com.ativeplay.modules.senha.domain.SenhaEmpresa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenhaEmpresaRepository extends JpaRepository<SenhaEmpresa, UUID> {

    Optional<SenhaEmpresa> findByIdAndEscritorioIdAndEmpresaId(UUID id, UUID escritorioId, UUID empresaId);

    List<SenhaEmpresa> findByEscritorioIdAndEmpresaIdOrderByAcessoAsc(UUID escritorioId, UUID empresaId);
}
