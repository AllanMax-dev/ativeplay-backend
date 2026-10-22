package br.com.ativeplay.modules.crm.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.crm.domain.AtividadeCrm;
import br.com.ativeplay.modules.crm.domain.EtapaCrm;
import br.com.ativeplay.modules.crm.domain.OportunidadeCrm;
import br.com.ativeplay.modules.crm.domain.TipoAtividadeCrm;
import br.com.ativeplay.modules.crm.domain.TipoEtapaCrm;
import br.com.ativeplay.modules.crm.repository.AtividadeCrmRepository;
import br.com.ativeplay.modules.crm.repository.EtapaCrmRepository;
import br.com.ativeplay.modules.crm.repository.OportunidadeCrmRepository;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.AtividadeRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.AtividadeResponse;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.ConversaoResponse;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.EtapaFunil;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.MoverRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.OportunidadeRequest;
import br.com.ativeplay.modules.crm.web.dto.CrmDtos.OportunidadeResponse;
import br.com.ativeplay.modules.empresa.domain.Empresa;
import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cnpj;
import br.com.ativeplay.shared.web.Pagina;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Oportunidades do funil, movimentação entre etapas, conversão em empresa e atividades. */
@Service
@RequiredArgsConstructor
@Transactional
public class OportunidadeCrmService {

    private final OportunidadeCrmRepository oportunidades;
    private final EtapaCrmRepository etapas;
    private final AtividadeCrmRepository atividades;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;

    // ---------- consultas ----------

    @Transactional(readOnly = true)
    public Pagina<OportunidadeResponse> listar(UsuarioAutenticado u, UUID etapaId, UUID responsavelId, String busca,
            Boolean abertas, Pageable pageable) {
        Specification<OportunidadeCrm> spec = (r, q, cb) -> cb.equal(r.get("escritorioId"), u.escritorioId());
        if (etapaId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("etapaId"), etapaId));
        if (responsavelId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("responsavelId"), responsavelId));
        if (Boolean.TRUE.equals(abertas)) spec = spec.and((r, q, cb) -> cb.isNull(r.get("fechadaEm")));
        if (Boolean.FALSE.equals(abertas)) spec = spec.and((r, q, cb) -> cb.isNotNull(r.get("fechadaEm")));
        if (busca != null && !busca.isBlank()) {
            var termo = "%" + busca.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> {
                List<Predicate> ps = new ArrayList<>();
                for (var campo : List.of("titulo", "razaoSocial", "contatoNome", "contatoEmail", "cnpj")) {
                    ps.add(cb.like(cb.lower(r.get(campo)), termo));
                }
                return cb.or(ps.toArray(Predicate[]::new));
            });
        }
        var ordenado = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "atualizadoEm"));
        var page = oportunidades.findAll(spec, ordenado);
        var nomes = nomes(u, page.getContent().stream().map(OportunidadeCrm::getResponsavelId).toList());
        return Pagina.de(page, o -> OportunidadeResponse.de(o, nomes.get(o.getResponsavelId())));
    }

    /** Quadro kanban: etapas na ordem, cada uma com suas oportunidades ordenadas. */
    @Transactional(readOnly = true)
    public List<EtapaFunil> funil(UsuarioAutenticado u) {
        var todas = oportunidades.findByEscritorioIdOrderByOrdemAsc(u.escritorioId());
        var nomes = nomes(u, todas.stream().map(OportunidadeCrm::getResponsavelId).toList());
        var porEtapa = todas.stream().collect(Collectors.groupingBy(OportunidadeCrm::getEtapaId));
        return etapas.findByEscritorioIdOrderByOrdemAsc(u.escritorioId()).stream()
                .map(e -> {
                    var lista = porEtapa.getOrDefault(e.getId(), List.of());
                    var total = lista.stream().map(OportunidadeCrm::getValorMensal).filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new EtapaFunil(e.getId(), e.getNome(), e.getCor(), e.getOrdem(), e.getTipo(), lista.size(),
                            total, lista.stream().map(o -> OportunidadeResponse.de(o, nomes.get(o.getResponsavelId())))
                                    .toList());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public OportunidadeResponse obter(UsuarioAutenticado u, UUID id) {
        return resposta(u, buscar(u, id));
    }

    // ---------- escrita ----------

    public OportunidadeResponse criar(UsuarioAutenticado u, OportunidadeRequest req) {
        var etapa = req.etapaId() != null ? etapa(u, req.etapaId())
                : etapas.findFirstByEscritorioIdAndTipoOrderByOrdemAsc(u.escritorioId(), TipoEtapaCrm.ABERTA)
                        .orElseThrow(() -> ApiException.invalido("Cadastre uma etapa aberta no funil antes"));
        escopo.garantirUsuarioDaEquipe(u, req.responsavelId());

        var o = new OportunidadeCrm();
        o.setEscritorioId(u.escritorioId());
        o.setEtapaId(etapa.getId());
        o.setResponsavelId(req.responsavelId() != null ? req.responsavelId() : u.id());
        preencher(o, req);
        o.setOrdem(oportunidades.findByEscritorioIdAndEtapaIdOrderByOrdemAsc(u.escritorioId(), etapa.getId()).size());
        if (etapa.getTipo() != TipoEtapaCrm.ABERTA) o.setFechadaEm(Instant.now());
        oportunidades.save(o);

        auditoria.registrar(u, "crm.criar", "oportunidade_crm", o.getId(), null, "Oportunidade " + o.getTitulo());
        notificarAtribuicao(u, o);
        return resposta(u, o);
    }

    public OportunidadeResponse atualizar(UsuarioAutenticado u, UUID id, OportunidadeRequest req) {
        var o = buscar(u, id);
        var responsavelAnterior = o.getResponsavelId();
        escopo.garantirUsuarioDaEquipe(u, req.responsavelId());
        preencher(o, req);
        if (req.responsavelId() != null) o.setResponsavelId(req.responsavelId());
        if (req.etapaId() != null && !req.etapaId().equals(o.getEtapaId())) {
            mover(u, o, etapa(u, req.etapaId()), null, null);
        }
        auditoria.registrar(u, "crm.editar", "oportunidade_crm", o.getId(), o.getEmpresaId(),
                "Oportunidade " + o.getTitulo() + " alterada");
        if (!Objects.equals(responsavelAnterior, o.getResponsavelId())) notificarAtribuicao(u, o);
        return resposta(u, o);
    }

    public void excluir(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        oportunidades.delete(o);
        reindexar(u, o.getEtapaId(), null);
        auditoria.registrar(u, "crm.excluir", "oportunidade_crm", id, null, "Oportunidade " + o.getTitulo() + " excluída");
    }

    public OportunidadeResponse mover(UsuarioAutenticado u, UUID id, MoverRequest req) {
        var o = buscar(u, id);
        var destino = etapa(u, req.etapaId());
        mover(u, o, destino, req.ordem(), req.motivoPerda());
        auditoria.registrar(u, "crm.mover", "oportunidade_crm", o.getId(), o.getEmpresaId(),
                o.getTitulo() + " → " + destino.getNome());
        return resposta(u, o);
    }

    /** Oportunidade ganha vira empresa (cliente) em onboarding. */
    public ConversaoResponse converter(UsuarioAutenticado u, UUID id) {
        var o = buscar(u, id);
        if (o.getEmpresaId() != null) throw ApiException.conflito("Oportunidade já convertida na empresa " + o.getEmpresaId());
        if (o.getRazaoSocial() == null || o.getRazaoSocial().isBlank()) {
            throw ApiException.invalido("Informe a razão social antes de converter");
        }
        if (o.getCnpj() == null || !Cnpj.valido(o.getCnpj())) throw ApiException.invalido("Informe um CNPJ válido antes de converter");
        empresas.findByEscritorioIdAndCnpj(u.escritorioId(), o.getCnpj()).ifPresent(e -> {
            throw ApiException.conflito("Já existe uma empresa com este CNPJ (id " + e.getId() + ")");
        });
        var ganha = etapas.findFirstByEscritorioIdAndTipoOrderByOrdemAsc(u.escritorioId(), TipoEtapaCrm.GANHA)
                .orElseThrow(() -> ApiException.invalido("Cadastre uma etapa do tipo GANHA no funil"));

        var empresa = new Empresa();
        empresa.setEscritorioId(u.escritorioId());
        empresa.setRazaoSocial(o.getRazaoSocial());
        empresa.setCnpj(o.getCnpj());
        empresa.setStatus(StatusEmpresa.ONBOARDING);
        empresa.setEmail(o.getContatoEmail());
        empresa.setTelefone(o.getContatoTelefone());
        empresa.setHonorarioMensal(o.getValorMensal());
        empresa.setResponsavelId(o.getResponsavelId());
        empresas.save(empresa);

        o.setEmpresaId(empresa.getId());
        mover(u, o, ganha, null, null);
        auditoria.registrar(u, "crm.converter", "oportunidade_crm", o.getId(), empresa.getId(),
                "Oportunidade " + o.getTitulo() + " convertida na empresa " + empresa.getRazaoSocial());
        auditoria.registrar(u, "empresa.criar", "empresa", empresa.getId(), empresa.getId(),
                "Empresa " + empresa.getRazaoSocial() + " criada a partir do CRM");
        return new ConversaoResponse(o.getId(), empresa.getId(), ganha.getId());
    }

    // ---------- atividades ----------

    @Transactional(readOnly = true)
    public List<AtividadeResponse> atividades(UsuarioAutenticado u, UUID oportunidadeId) {
        buscar(u, oportunidadeId);
        var lista = atividades.findByOportunidadeIdOrderByCriadoEmDesc(oportunidadeId);
        var nomes = nomes(u, lista.stream().map(AtividadeCrm::getAutorId).toList());
        return lista.stream().map(a -> AtividadeResponse.de(a, nomes.get(a.getAutorId()))).toList();
    }

    public AtividadeResponse criarAtividade(UsuarioAutenticado u, UUID oportunidadeId, AtividadeRequest req) {
        var o = buscar(u, oportunidadeId);
        var a = new AtividadeCrm();
        a.setEscritorioId(u.escritorioId());
        a.setOportunidadeId(o.getId());
        a.setAutorId(u.id());
        a.setTipo(req.tipo() == null ? TipoAtividadeCrm.NOTA : req.tipo());
        a.setDescricao(req.descricao().trim());
        a.setAgendadaPara(req.agendadaPara());
        atividades.save(a);
        auditoria.registrar(u, "crm.atividade.criar", "atividade_crm", a.getId(), o.getEmpresaId(),
                a.getTipo() + " em " + o.getTitulo());
        return AtividadeResponse.de(a, nomes(u, List.of(u.id())).get(u.id()));
    }

    public AtividadeResponse concluirAtividade(UsuarioAutenticado u, UUID atividadeId) {
        var a = atividade(u, atividadeId);
        if (a.getConcluidaEm() == null) a.setConcluidaEm(Instant.now());
        auditoria.registrar(u, "crm.atividade.concluir", "atividade_crm", a.getId(), null, null);
        return AtividadeResponse.de(a, nomes(u, List.of(a.getAutorId())).get(a.getAutorId()));
    }

    public void excluirAtividade(UsuarioAutenticado u, UUID atividadeId) {
        var a = atividade(u, atividadeId);
        atividades.delete(a);
        auditoria.registrar(u, "crm.atividade.excluir", "atividade_crm", atividadeId, null, null);
    }

    // ---------- apoio ----------

    /**
     * Move a oportunidade para a etapa destino na posição informada (fim, se nula),
     * reindexando as duas colunas. Etapas GANHA/PERDIDA fecham a oportunidade.
     */
    private void mover(UsuarioAutenticado u, OportunidadeCrm o, EtapaCrm destino, Integer ordem, String motivoPerda) {
        if (destino.getTipo() == TipoEtapaCrm.PERDIDA) {
            var motivo = motivoPerda != null && !motivoPerda.isBlank() ? motivoPerda.trim() : o.getMotivoPerda();
            if (motivo == null || motivo.isBlank()) throw ApiException.invalido("Informe o motivo da perda");
            o.setMotivoPerda(motivo);
        }
        var origem = o.getEtapaId();
        o.setEtapaId(destino.getId());
        o.setFechadaEm(destino.getTipo() == TipoEtapaCrm.ABERTA ? null
                : (o.getFechadaEm() != null && origem.equals(destino.getId()) ? o.getFechadaEm() : Instant.now()));
        if (!origem.equals(destino.getId())) reindexar(u, origem, o.getId());
        var coluna = new ArrayList<>(oportunidades
                .findByEscritorioIdAndEtapaIdOrderByOrdemAsc(u.escritorioId(), destino.getId()).stream()
                .filter(x -> !x.getId().equals(o.getId()))
                .toList());
        int pos = ordem == null ? coluna.size() : Math.min(ordem, coluna.size());
        coluna.add(pos, o);
        for (int i = 0; i < coluna.size(); i++) coluna.get(i).setOrdem(i);
    }

    private void reindexar(UsuarioAutenticado u, UUID etapaId, UUID ignorar) {
        int i = 0;
        for (var x : oportunidades.findByEscritorioIdAndEtapaIdOrderByOrdemAsc(u.escritorioId(), etapaId)) {
            if (!x.getId().equals(ignorar)) x.setOrdem(i++);
        }
    }

    private void preencher(OportunidadeCrm o, OportunidadeRequest req) {
        o.setTitulo(req.titulo().trim());
        o.setRazaoSocial(req.razaoSocial());
        o.setCnpj(req.cnpjNormalizado());
        o.setContatoNome(req.contatoNome());
        o.setContatoEmail(req.contatoEmail() == null ? null : req.contatoEmail().trim().toLowerCase());
        o.setContatoTelefone(req.contatoTelefone());
        o.setOrigem(req.origem());
        o.setValorMensal(req.valorMensal());
        o.setPrevisaoFechamento(req.previsaoFechamento());
    }

    private void notificarAtribuicao(UsuarioAutenticado u, OportunidadeCrm o) {
        if (o.getResponsavelId() != null && !o.getResponsavelId().equals(u.id())) {
            notificacoes.notificar(u.escritorioId(), o.getResponsavelId(), TipoNotificacao.CRM,
                    "Oportunidade atribuída a você: " + o.getTitulo(), null, "crm?oportunidade=" + o.getId());
        }
    }

    private OportunidadeResponse resposta(UsuarioAutenticado u, OportunidadeCrm o) {
        return OportunidadeResponse.de(o, nomes(u, List.of(o.getResponsavelId())).get(o.getResponsavelId()));
    }

    private Map<UUID, String> nomes(UsuarioAutenticado u, Collection<UUID> ids) {
        Set<UUID> unicos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unicos.isEmpty()) return Map.of();
        return usuarios.findByIdInAndEscritorioId(unicos, u.escritorioId()).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }

    private OportunidadeCrm buscar(UsuarioAutenticado u, UUID id) {
        return oportunidades.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Oportunidade"));
    }

    private EtapaCrm etapa(UsuarioAutenticado u, UUID id) {
        return etapas.findByIdAndEscritorioId(id, u.escritorioId()).orElseThrow(() -> ApiException.naoEncontrado("Etapa"));
    }

    private AtividadeCrm atividade(UsuarioAutenticado u, UUID id) {
        return atividades.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Atividade"));
    }
}
