package br.com.ativeplay.modules.tarefa.repository;

import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.tarefa.domain.DefinicaoTarefa;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DefinicaoTarefaRepository extends JpaRepository<DefinicaoTarefa, UUID> {

    List<DefinicaoTarefa> findByEscritorioIdAndEmpresaIdOrderByOrdemAsc(UUID escritorioId, UUID empresaId);

    List<DefinicaoTarefa> findByEscritorioIdAndEmpresaIdAndAtivaOrderByOrdemAsc(UUID escritorioId, UUID empresaId,
            boolean ativa);

    Optional<DefinicaoTarefa> findByIdAndEscritorioId(UUID id, UUID escritorioId);

    @Query("select coalesce(max(d.ordem), -1) from DefinicaoTarefa d where d.empresaId = :empresaId")
    int maiorOrdem(UUID empresaId);

    @Query("select d.padraoId from DefinicaoTarefa d where d.empresaId = :empresaId and d.padraoId is not null")
    List<UUID> findPadraoIdsByEmpresaId(UUID empresaId);

    /** Definições ativas de empresas em operação (status informados) do escritório. */
    @Query("""
            select d from DefinicaoTarefa d, Empresa e
            where e.id = d.empresaId and d.escritorioId = :escritorioId and d.ativa = true and e.status in :status
            """)
    List<DefinicaoTarefa> ativasParaGeracao(UUID escritorioId, Collection<StatusEmpresa> status);

    @Query("""
            select d from DefinicaoTarefa d, Empresa e
            where e.id = d.empresaId and d.escritorioId = :escritorioId and d.ativa = true and e.status in :status
              and d.empresaId = :empresaId
            """)
    List<DefinicaoTarefa> ativasParaGeracaoDaEmpresa(UUID escritorioId, UUID empresaId,
            Collection<StatusEmpresa> status);
}
