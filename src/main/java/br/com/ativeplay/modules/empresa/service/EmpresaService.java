package br.com.ativeplay.modules.empresa.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.documento.repository.DocumentoRepository;
import br.com.ativeplay.modules.empresa.domain.Empresa;
import br.com.ativeplay.modules.empresa.domain.RegimeTributario;
import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.empresa.repository.EmpresaIndicadoresRepository;
import br.com.ativeplay.modules.empresa.repository.EmpresaIndicadoresRepository.Indicadores;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.EmpresaDetalhe;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.EmpresaRequest;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.EmpresaResumoResposta;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.IntegracaoResumo;
import br.com.ativeplay.modules.empresa.web.dto.EmpresaDtos.UsuarioPortal;
import br.com.ativeplay.modules.escritorio.domain.Escritorio;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.integracao.domain.Integracao;
import br.com.ativeplay.modules.usuario.domain.UsuarioEmpresa;
import br.com.ativeplay.modules.usuario.repository.UsuarioEmpresaRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.modules.usuario.service.UsuarioService;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import br.com.ativeplay.shared.armazenamento.ArmazenamentoService;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cnpj;
import br.com.ativeplay.shared.util.Datas;
import br.com.ativeplay.shared.web.Pagina;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional
public class EmpresaService {

    private final EmpresaRepository empresas;
    private final EmpresaIndicadoresRepository indicadores;
    private final EscritorioRepository escritorios;
    private final UsuarioRepository usuarios;
    private final UsuarioEmpresaRepository carteira;
    private final UsuarioService usuarioService;
    private final DocumentoRepository documentos;
    private final ArmazenamentoService armazenamento;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public Pagina<EmpresaResumoResposta> listar(UsuarioAutenticado u, StatusEmpresa status,
            RegimeTributario regime, UUID responsavelId, String busca, Pageable pageable) {
        var permitidas = escopo.empresasPermitidas(u);
        if (permitidas.isPresent() && permitidas.get().isEmpty()) {
            return new Pagina<>(List.of(), 0, pageable.getPageNumber() + 1, pageable.getPageSize());
        }
        Specification<Empresa> spec = (r, q, cb) -> {
            var filtros = new ArrayList<Predicate>();
            filtros.add(cb.equal(r.get("escritorioId"), u.escritorioId()));
            permitidas.ifPresent(ids -> filtros.add(r.get("id").in(ids)));
            if (status != null) filtros.add(cb.equal(r.get("status"), status));
            if (regime != null) filtros.add(cb.equal(r.get("regimeTributario"), regime));
            if (responsavelId != null) filtros.add(cb.equal(r.get("responsavelId"), responsavelId));
            if (busca != null && !busca.isBlank()) {
                var termo = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
                var cnpj = "%" + Cnpj.normalizar(busca) + "%";
                filtros.add(cb.or(
                        cb.like(cb.lower(r.get("razaoSocial")), termo),
                        cb.like(cb.lower(cb.coalesce(r.get("nomeFantasia"), "")), termo),
                        cb.like(r.get("cnpj"), cnpj)));
            }
            return cb.and(filtros.toArray(Predicate[]::new));
        };
        var page = empresas.findAll(spec,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("razaoSocial")));

        var ids = page.getContent().stream().map(Empresa::getId).toList();
        var porEmpresa = indicadores.tarefasAbertas(u.escritorioId(), ids, Datas.hoje(fuso(u)));
        var nomes = usuarioService.nomes(page.getContent().stream().map(Empresa::getResponsavelId)
                .filter(Objects::nonNull).distinct().toList());

        return Pagina.de(page, e -> {
            var ind = porEmpresa.getOrDefault(e.getId(), Indicadores.ZERO);
            return new EmpresaResumoResposta(e.getId(), e.getRazaoSocial(), e.getNomeFantasia(), e.getCnpj(),
                    Cnpj.formatar(e.getCnpj()), e.getRegimeTributario(), e.getStatus(), e.getEmail(), e.getTelefone(),
                    ref(e.getResponsavelId(), nomes), e.getHonorarioMensal(), e.getDataInicio(), ind.abertasHoje(),
                    ind.atrasadas());
        });
    }

    @Transactional(readOnly = true)
    public EmpresaDetalhe detalhe(UsuarioAutenticado u, UUID id) {
        return detalhe(escopo.garantirEmpresa(u, id));
    }

    public EmpresaDetalhe criar(UsuarioAutenticado u, EmpresaRequest req) {
        var cnpj = Cnpj.normalizar(req.cnpj());
        if (empresas.existsByEscritorioIdAndCnpj(u.escritorioId(), cnpj)) {
            throw ApiException.conflito("Já existe uma empresa com este CNPJ");
        }
        var e = new Empresa();
        e.setEscritorioId(u.escritorioId());
        aplicar(u, e, req, cnpj);
        empresas.save(e);
        auditoria.registrar(u, "empresa.criar", "empresa", e.getId(), e.getId(),
                "Empresa " + e.getRazaoSocial() + " cadastrada");
        return detalhe(e);
    }

    public EmpresaDetalhe atualizar(UsuarioAutenticado u, UUID id, EmpresaRequest req) {
        var e = escopo.garantirEmpresa(u, id);
        var cnpj = Cnpj.normalizar(req.cnpj());
        if (!cnpj.equals(e.getCnpj()) && empresas.existsByEscritorioIdAndCnpj(u.escritorioId(), cnpj)) {
            throw ApiException.conflito("Já existe uma empresa com este CNPJ");
        }
        aplicar(u, e, req, cnpj);
        auditoria.registrar(u, "empresa.editar", "empresa", e.getId(), e.getId(),
                "Empresa " + e.getRazaoSocial() + " atualizada");
        return detalhe(e);
    }

    public EmpresaDetalhe alterarStatus(UsuarioAutenticado u, UUID id, StatusEmpresa status) {
        var e = escopo.garantirEmpresa(u, id);
        var anterior = e.getStatus();
        e.setStatus(status);
        auditoria.registrar(u.escritorioId(), u.id(), "empresa.status", "empresa", e.getId(), e.getId(),
                "Status de " + e.getRazaoSocial() + ": " + anterior + " → " + status,
                Map.of("de", anterior.name(), "para", status.name()));
        return detalhe(e);
    }

    /** Exclusão definitiva (cascata no banco). Só para empresas encerradas; remove também os arquivos. */
    public void excluir(UsuarioAutenticado u, UUID id) {
        var e = escopo.garantirEmpresa(u, id);
        if (e.getStatus() != StatusEmpresa.ENCERRADA) {
            throw ApiException.conflito("Encerre a empresa antes de excluí-la");
        }
        var arquivos = documentos.findStorageKeysByEmpresaId(id);
        empresas.delete(e);
        auditoria.registrar(u, "empresa.excluir", "empresa", id, null, "Empresa " + e.getRazaoSocial() + " excluída");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                arquivos.forEach(armazenamento::remover);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<UsuarioRef> equipe(UsuarioAutenticado u, UUID id) {
        escopo.garantirEmpresa(u, id);
        return equipe(id);
    }

    public List<UsuarioRef> definirEquipe(UsuarioAutenticado u, UUID id, List<UUID> usuarioIds) {
        var e = escopo.garantirEmpresa(u, id);
        var unicos = new LinkedHashSet<>(usuarioIds);
        var encontrados = usuarios.findByIdInAndEscritorioId(unicos, u.escritorioId());
        if (encontrados.size() != unicos.size() || encontrados.stream().anyMatch(x -> x.getPerfil() == Perfil.CLIENTE)) {
            throw ApiException.invalido("A equipe deve conter apenas usuários da equipe do escritório");
        }
        carteira.deleteByEmpresaId(id);
        carteira.flush();
        carteira.saveAll(unicos.stream().map(usuarioId -> new UsuarioEmpresa(usuarioId, id)).toList());
        auditoria.registrar(u, "empresa.equipe", "empresa", id, id,
                "Equipe de " + e.getRazaoSocial() + ": " + unicos.size() + " pessoa(s)");
        return equipe(id);
    }

    // ---------- auxiliares ----------

    private void aplicar(UsuarioAutenticado u, Empresa e, EmpresaRequest req, String cnpj) {
        escopo.garantirUsuarioDaEquipe(u, req.responsavelId());
        e.setRazaoSocial(req.razaoSocial().trim());
        e.setNomeFantasia(vazioParaNulo(req.nomeFantasia()));
        e.setCnpj(cnpj);
        if (req.regimeTributario() != null) e.setRegimeTributario(req.regimeTributario());
        e.setEmail(vazioParaNulo(req.email()) == null ? null : req.email().trim().toLowerCase(Locale.ROOT));
        e.setTelefone(vazioParaNulo(req.telefone()));
        e.setResponsavelId(req.responsavelId());
        e.setHonorarioMensal(req.honorarioMensal());
        e.setDataInicio(req.dataInicio());
        e.setObservacoes(vazioParaNulo(req.observacoes()));
    }

    private EmpresaDetalhe detalhe(Empresa e) {
        var nomes = usuarioService.nomes(e.getResponsavelId() == null ? List.of() : List.of(e.getResponsavelId()));
        var portal = usuarios.findByEmpresaId(e.getId()).stream()
                .map(x -> new UsuarioPortal(x.getId(), x.getNome(), x.getEmail(), x.isAtivo(), x.getUltimoAcesso()))
                .toList();
        var integracoes = em.createQuery(
                        "select i from Integracao i where i.empresaId = :empresaId and i.escritorioId = :escritorioId",
                        Integracao.class)
                .setParameter("empresaId", e.getId())
                .setParameter("escritorioId", e.getEscritorioId())
                .getResultList().stream()
                .map(i -> new IntegracaoResumo(i.getId(), i.getProvedor(), i.getStatus(), i.getUltimaSincronizacao()))
                .toList();
        return new EmpresaDetalhe(e.getId(), e.getRazaoSocial(), e.getNomeFantasia(), e.getCnpj(),
                Cnpj.formatar(e.getCnpj()), e.getRegimeTributario(), e.getStatus(), e.getEmail(), e.getTelefone(),
                ref(e.getResponsavelId(), nomes), e.getHonorarioMensal(), e.getDataInicio(), e.getObservacoes(),
                e.getCriadoEm(), e.getAtualizadoEm(), equipe(e.getId()), portal, integracoes);
    }

    private List<UsuarioRef> equipe(UUID empresaId) {
        var ids = carteira.findUsuarioIdsByEmpresaId(empresaId);
        return usuarios.findAllById(ids).stream().map(x -> new UsuarioRef(x.getId(), x.getNome())).toList();
    }

    private String fuso(UsuarioAutenticado u) {
        return escritorios.findById(u.escritorioId()).map(Escritorio::getFusoHorario).orElse(null);
    }

    private static UsuarioRef ref(UUID id, Map<UUID, String> nomes) {
        return id == null ? null : new UsuarioRef(id, nomes.get(id));
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
