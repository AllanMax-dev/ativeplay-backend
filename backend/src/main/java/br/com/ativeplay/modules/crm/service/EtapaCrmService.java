package br.com.ativeplay.modules.crm.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.crm.domain.EtapaCrm;
import br.com.ativeplay.modules.crm.domain.TipoEtapaCrm;
import br.com.ativeplay.modules.crm.repository.EtapaCrmRepository;
import br.com.ativeplay.modules.crm.repository.OportunidadeCrmRepository;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.EtapaRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.EtapaResponse;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Colunas do funil de vendas. */
@Service
@RequiredArgsConstructor
@Transactional
public class EtapaCrmService {

    private final EtapaCrmRepository etapas;
    private final OportunidadeCrmRepository oportunidades;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<EtapaResponse> listar(UsuarioAutenticado u) {
        Map<UUID, OportunidadeCrmRepository.ResumoEtapa> resumo = oportunidades.resumoPorEtapa(u.escritorioId())
                .stream()
                .collect(Collectors.toMap(OportunidadeCrmRepository.ResumoEtapa::getEtapaId, Function.identity()));
        return etapas.findByEscritorioIdOrderByOrdemAsc(u.escritorioId()).stream()
                .map(e -> {
                    var r = resumo.get(e.getId());
                    return EtapaResponse.de(e, r == null ? 0 : r.getQuantidade(),
                            r == null ? BigDecimal.ZERO : r.getValorTotal());
                })
                .toList();
    }

    public EtapaResponse criar(UsuarioAutenticado u, EtapaRequest req) {
        var nome = req.nome().trim();
        if (etapas.existsByEscritorioIdAndNomeIgnoreCase(u.escritorioId(), nome)) {
            throw ApiException.conflito("Já existe uma etapa com este nome");
        }
        var e = new EtapaCrm();
        e.setEscritorioId(u.escritorioId());
        e.setNome(nome);
        e.setCor(req.cor());
        e.setTipo(req.tipo() == null ? TipoEtapaCrm.ABERTA : req.tipo());
        e.setOrdem(etapas.findByEscritorioIdOrderByOrdemAsc(u.escritorioId()).size());
        etapas.save(e);
        auditoria.registrar(u, "crm.etapa.criar", "etapa_crm", e.getId(), null, "Etapa " + nome + " criada");
        return EtapaResponse.de(e, 0, BigDecimal.ZERO);
    }

    public EtapaResponse atualizar(UsuarioAutenticado u, UUID id, EtapaRequest req) {
        var e = buscar(u, id);
        var nome = req.nome().trim();
        if (!e.getNome().equalsIgnoreCase(nome) && etapas.existsByEscritorioIdAndNomeIgnoreCase(u.escritorioId(), nome)) {
            throw ApiException.conflito("Já existe uma etapa com este nome");
        }
        e.setNome(nome);
        e.setCor(req.cor());
        if (req.tipo() != null) e.setTipo(req.tipo());
        auditoria.registrar(u, "crm.etapa.editar", "etapa_crm", e.getId(), null, "Etapa " + nome + " alterada");
        return listar(u).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    public void excluir(UsuarioAutenticado u, UUID id) {
        var e = buscar(u, id);
        if (oportunidades.existsByEtapaId(id)) {
            throw ApiException.conflito("A etapa possui oportunidades; mova-as antes de excluir");
        }
        etapas.delete(e);
        auditoria.registrar(u, "crm.etapa.excluir", "etapa_crm", id, null, "Etapa " + e.getNome() + " excluída");
    }

    /** Reordena as etapas na ordem dos ids informados (todos precisam ser do escritório). */
    public List<EtapaResponse> reordenar(UsuarioAutenticado u, List<UUID> ids) {
        var todas = etapas.findByEscritorioIdOrderByOrdemAsc(u.escritorioId());
        var porId = todas.stream().collect(Collectors.toMap(EtapaCrm::getId, Function.identity()));
        if (new HashSet<>(ids).size() != ids.size() || !porId.keySet().containsAll(ids)) {
            throw ApiException.invalido("Lista de etapas inválida");
        }
        int ordem = 0;
        for (var id : ids) porId.get(id).setOrdem(ordem++);
        for (var e : todas) if (!ids.contains(e.getId())) e.setOrdem(ordem++);
        auditoria.registrar(u, "crm.etapa.reordenar", "etapa_crm", null, null, "Funil reordenado");
        return listar(u);
    }

    EtapaCrm buscar(UsuarioAutenticado u, UUID id) {
        return etapas.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Etapa"));
    }
}
