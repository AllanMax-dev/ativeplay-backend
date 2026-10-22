package br.com.ativeplay.modules.integracao.service;

import br.com.ativeplay.config.AtivePlayProperties;
import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.empresa.domain.Empresa;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.integracao.domain.Integracao;
import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import br.com.ativeplay.modules.integracao.domain.SincronizacaoIntegracao;
import br.com.ativeplay.modules.integracao.domain.StatusIntegracao;
import br.com.ativeplay.modules.integracao.domain.StatusSincronizacao;
import br.com.ativeplay.modules.integracao.provedor.ProvedoresErp;
import br.com.ativeplay.modules.integracao.repository.IntegracaoRepository;
import br.com.ativeplay.modules.integracao.repository.SincronizacaoIntegracaoRepository;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.AtualizarIntegracaoRequest;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.CriarIntegracaoRequest;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.IntegracaoResponse;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.SincronizacaoResponse;
import br.com.ativeplay.modules.integracao.web.dto.IntegracaoDtos.TesteResponse;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cripto;
import br.com.ativeplay.shared.web.Pagina;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conexões das empresas com ERPs. As credenciais são cifradas (AES-256-GCM) antes de
 * gravar e nunca aparecem em respostas nem na auditoria.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IntegracaoService {

    private static final TypeReference<Map<String, String>> MAPA = new TypeReference<>() {
    };

    private final IntegracaoRepository integracoes;
    private final SincronizacaoIntegracaoRepository sincronizacoes;
    private final EmpresaRepository empresas;
    private final ProvedoresErp provedores;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;
    private final AtivePlayProperties props;
    private final ObjectMapper json;
    private final PlatformTransactionManager transacoes;

    @Transactional(readOnly = true)
    public Pagina<IntegracaoResponse> listar(UsuarioAutenticado u, UUID empresaId, ProvedorIntegracao provedor,
            StatusIntegracao status, Pageable pageable) {
        if (empresaId != null) escopo.garantirAcesso(u, empresaId);
        Specification<Integracao> spec = (r, q, cb) -> cb.equal(r.get("escritorioId"), u.escritorioId());
        var permitidas = escopo.empresasPermitidas(u);
        if (permitidas.isPresent()) {
            var ids = permitidas.get();
            spec = spec.and((r, q, cb) -> ids.isEmpty() ? cb.disjunction() : r.get("empresaId").in(ids));
        }
        if (empresaId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("empresaId"), empresaId));
        if (provedor != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("provedor"), provedor));
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        var page = integracoes.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "criadoEm")));
        var nomes = empresas.findByIdInAndEscritorioId(
                        page.getContent().stream().map(Integracao::getEmpresaId).collect(Collectors.toSet()),
                        u.escritorioId())
                .stream().collect(Collectors.toMap(Empresa::getId, Empresa::getRazaoSocial));
        return Pagina.de(page, i -> IntegracaoResponse.de(i, nomes.get(i.getEmpresaId())));
    }

    @Transactional(readOnly = true)
    public IntegracaoResponse obter(UsuarioAutenticado u, UUID id) {
        return resposta(buscar(u, id));
    }

    public IntegracaoResponse criar(UsuarioAutenticado u, CriarIntegracaoRequest req) {
        var empresa = escopo.garantirEmpresa(u, req.empresaId());
        if (integracoes.existsByEmpresaIdAndProvedor(empresa.getId(), req.provedor())) {
            throw ApiException.conflito("A empresa já possui integração com " + req.provedor());
        }
        provedores.de(req.provedor()).validarCredenciais(req.credenciais());

        var i = new Integracao();
        i.setEscritorioId(u.escritorioId());
        i.setEmpresaId(empresa.getId());
        i.setProvedor(req.provedor());
        i.setStatus(StatusIntegracao.PENDENTE);
        i.setCredenciaisCifradas(cifrar(req.credenciais()));
        if (req.configuracoes() != null) i.setConfiguracoes(new HashMap<>(req.configuracoes()));
        integracoes.save(i);
        auditoria.registrar(u, "integracao.criar", "integracao", i.getId(), empresa.getId(),
                "Integração " + i.getProvedor() + " cadastrada");
        return IntegracaoResponse.de(i, empresa.getRazaoSocial());
    }

    public IntegracaoResponse atualizar(UsuarioAutenticado u, UUID id, AtualizarIntegracaoRequest req) {
        var i = buscar(u, id);
        if (req.credenciais() != null && !req.credenciais().isEmpty()) {
            provedores.de(i.getProvedor()).validarCredenciais(req.credenciais());
            i.setCredenciaisCifradas(cifrar(req.credenciais()));
            // novas credenciais precisam ser testadas novamente
            i.setStatus(StatusIntegracao.PENDENTE);
            i.setUltimoErro(null);
        }
        if (req.configuracoes() != null) i.setConfiguracoes(new HashMap<>(req.configuracoes()));
        auditoria.registrar(u, "integracao.editar", "integracao", i.getId(), i.getEmpresaId(),
                req.credenciais() != null && !req.credenciais().isEmpty() ? "Credenciais substituídas" : "Configurações alteradas");
        return resposta(i);
    }

    public IntegracaoResponse alterarStatus(UsuarioAutenticado u, UUID id, StatusIntegracao status) {
        if (status != StatusIntegracao.ATIVA && status != StatusIntegracao.DESATIVADA) {
            throw ApiException.invalido("Status permitido: ATIVA ou DESATIVADA");
        }
        var i = buscar(u, id);
        if (status == StatusIntegracao.ATIVA && i.getCredenciaisCifradas() == null) {
            throw ApiException.invalido("Cadastre as credenciais antes de ativar a integração");
        }
        i.setStatus(status);
        auditoria.registrar(u, "integracao.status", "integracao", i.getId(), i.getEmpresaId(), "Status " + status);
        return resposta(i);
    }

    public void excluir(UsuarioAutenticado u, UUID id) {
        var i = buscar(u, id);
        integracoes.delete(i);
        auditoria.registrar(u, "integracao.excluir", "integracao", id, i.getEmpresaId(),
                "Integração " + i.getProvedor() + " removida");
    }

    /** Testa a conexão e atualiza o status (ATIVA ou ERRO); o resultado é gravado mesmo em falha. */
    public TesteResponse testar(UsuarioAutenticado u, UUID id) {
        var i = buscar(u, id);
        if (i.getCredenciaisCifradas() == null) throw ApiException.invalido("Integração sem credenciais");
        try {
            provedores.de(i.getProvedor()).testarConexao(decifrar(i));
            i.setStatus(StatusIntegracao.ATIVA);
            i.setUltimoErro(null);
        } catch (RuntimeException e) {
            i.setStatus(StatusIntegracao.ERRO);
            i.setUltimoErro(mensagem(e));
        }
        auditoria.registrar(u, "integracao.testar", "integracao", i.getId(), i.getEmpresaId(),
                i.getStatus() == StatusIntegracao.ATIVA ? "Conexão OK" : "Falha: " + i.getUltimoErro());
        return new TesteResponse(i.getStatus() == StatusIntegracao.ATIVA,
                i.getStatus() == StatusIntegracao.ATIVA ? "Conexão verificada" : i.getUltimoErro(), i.getStatus());
    }

    /**
     * Executa a sincronização registrando o histórico em transações próprias: o registro
     * EM_EXECUCAO é gravado antes da chamada externa e o resultado (SUCESSO/ERRO) depois,
     * de modo que falhas também ficam no histórico.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SincronizacaoResponse sincronizar(UsuarioAutenticado u, UUID id, String recurso) {
        var tx = new TransactionTemplate(transacoes);
        tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        var preparo = tx.execute(s -> {
            var i = buscar(u, id);
            if (i.getStatus() == StatusIntegracao.DESATIVADA) throw ApiException.invalido("Integração desativada");
            if (i.getCredenciaisCifradas() == null) throw ApiException.invalido("Integração sem credenciais");
            var sync = new SincronizacaoIntegracao();
            sync.setEscritorioId(u.escritorioId());
            sync.setIntegracaoId(i.getId());
            sync.setRecurso(recurso.trim());
            sync.setIniciadaEm(Instant.now());
            sincronizacoes.save(sync);
            return new Object[] {sync.getId(), decifrar(i), i.getUltimaSincronizacao(), i.getProvedor()};
        });
        var syncId = (UUID) preparo[0];
        @SuppressWarnings("unchecked")
        var credenciais = (Map<String, String>) preparo[1];
        var desde = (Instant) preparo[2];
        var provedor = (ProvedorIntegracao) preparo[3];

        int registros = 0;
        String erro = null;
        try {
            registros = provedores.de(provedor).sincronizar(credenciais, recurso.trim(), desde);
        } catch (RuntimeException e) {
            erro = mensagem(e);
        }

        final int total = registros;
        final String falha = erro;
        return tx.execute(s -> {
            var agora = Instant.now();
            var sync = sincronizacoes.findById(syncId).orElseThrow();
            sync.setStatus(falha == null ? StatusSincronizacao.SUCESSO : StatusSincronizacao.ERRO);
            sync.setRegistros(total);
            sync.setErro(falha);
            sync.setFinalizadaEm(agora);
            var i = integracoes.findById(id).orElseThrow();
            i.setUltimoErro(falha);
            if (falha == null) i.setUltimaSincronizacao(agora);
            auditoria.registrar(u, "integracao.sincronizar", "integracao", i.getId(), i.getEmpresaId(),
                    recurso + ": " + (falha == null ? total + " registro(s)" : "erro — " + falha));
            if (falha != null) {
                var responsavel = empresas.findById(i.getEmpresaId()).map(Empresa::getResponsavelId).orElse(null);
                notificacoes.notificar(u.escritorioId(), responsavel, TipoNotificacao.INTEGRACAO,
                        "Falha na sincronização " + i.getProvedor() + " (" + recurso + ")", falha,
                        "integracoes?id=" + i.getId());
            }
            return SincronizacaoResponse.de(sync);
        });
    }

    @Transactional(readOnly = true)
    public Pagina<SincronizacaoResponse> historico(UsuarioAutenticado u, UUID id, Pageable pageable) {
        var i = buscar(u, id);
        return Pagina.de(sincronizacoes.findByIntegracaoIdOrderByIniciadaEmDesc(i.getId(), pageable),
                SincronizacaoResponse::de);
    }

    // ---------- apoio ----------

    private Integracao buscar(UsuarioAutenticado u, UUID id) {
        var i = integracoes.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Integração"));
        escopo.garantirAcesso(u, i.getEmpresaId());
        return i;
    }

    private IntegracaoResponse resposta(Integracao i) {
        return IntegracaoResponse.de(i, empresas.findById(i.getEmpresaId()).map(Empresa::getRazaoSocial).orElse(null));
    }

    private String cifrar(Map<String, String> credenciais) {
        try {
            return Cripto.cifrar(json.writeValueAsString(credenciais), props.encryptionKey());
        } catch (JsonProcessingException e) {
            throw ApiException.invalido("Credenciais inválidas");
        }
    }

    private Map<String, String> decifrar(Integracao i) {
        try {
            return json.readValue(Cripto.decifrar(i.getCredenciaisCifradas(), props.encryptionKey()), MAPA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Credenciais corrompidas na integração " + i.getId(), e);
        }
    }

    private static String mensagem(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
