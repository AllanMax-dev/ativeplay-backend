package br.com.ativeplay.modules.escritorio.repository;

import br.com.ativeplay.modules.escritorio.domain.Escritorio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscritorioRepository extends JpaRepository<Escritorio, UUID> {

    boolean existsBySlug(String slug);

    Optional<Escritorio> findBySlug(String slug);

    List<Escritorio> findByAtivoTrue();
}
