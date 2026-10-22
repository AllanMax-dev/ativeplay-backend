package br.com.ativeplay.modules.documento.repository;

import br.com.ativeplay.modules.documento.domain.Documento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DocumentoRepository extends JpaRepository<Documento, UUID>, JpaSpecificationExecutor<Documento> {

    Optional<Documento> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    @Query("select d.storageKey from Documento d where d.empresaId = :empresaId")
    List<String> findStorageKeysByEmpresaId(UUID empresaId);
}
