package br.com.ativeplay.modules.senha.service;

import br.com.ativeplay.config.AtivePlayProperties;
import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.senha.domain.CategoriaSenha;
import br.com.ativeplay.modules.senha.domain.SenhaEmpresa;
import br.com.ativeplay.modules.senha.repository.SenhaEmpresaRepository;
import br.com.ativeplay.modules.senha.web.dto.SenhaDtos.SenhaReveladaResponse;
import br.com.ativeplay.modules.senha.web.dto.SenhaDtos.SenhaRequest;
import br.com.ativeplay.modules.senha.web.dto.SenhaDtos.SenhaResponse;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cripto;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cofre de senhas de cada cliente (bancos, certificado, portais). A senha é cifrada com
 * AES-256-GCM (ENCRYPTION_KEY) antes de gravar, não aparece em listagens nem na auditoria,
 * e cada visualização fica registrada na trilha de auditoria.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SenhaEmpresaService {

    private final SenhaEmpresaRepository senhas;
    private final UsuarioRepository usuarios;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;
    private final AtivePlayProperties props;

    @Transactional(readOnly = true)
    public List<SenhaResponse> listar(UsuarioAutenticado u, UUID empresaId, String busca, CategoriaSenha categoria) {
        escopo.garantirEmpresa(u, empresaId);
        var termo = normalizar(busca);
        var lista = senhas.findByEscritorioIdAndEmpresaIdOrderByAcessoAsc(u.escritorioId(), empresaId).stream()
                .filter(s -> categoria == null || s.getCategoria() == categoria)
                .filter(s -> termo.isEmpty() || normalizar(s.getAcesso()).contains(termo)
                        || normalizar(s.getUsuario()).contains(termo) || normalizar(s.getUrl()).contains(termo))
                .toList();
        return montar(u, lista);
    }

    public SenhaResponse criar(UsuarioAutenticado u, UUID empresaId, SenhaRequest req) {
        escopo.garantirEmpresa(u, empresaId);
        var s = new SenhaEmpresa();
        s.setEscritorioId(u.escritorioId());
        s.setEmpresaId(empresaId);
        s.setCriadoPorId(u.id());
        s.setAtualizadoPorId(u.id());
        aplicar(s, req);
        if (req.senha() != null && !req.senha().isEmpty()) s.setSenhaCifrada(Cripto.cifrar(req.senha(), props.encryptionKey()));
        senhas.save(s);
        auditoria.registrar(u, "senha.criar", "senha", s.getId(), empresaId, "Acesso " + s.getAcesso() + " cadastrado");
        return montar(u, List.of(s)).getFirst();
    }

    public SenhaResponse atualizar(UsuarioAutenticado u, UUID empresaId, UUID id, SenhaRequest req) {
        var s = buscar(u, empresaId, id);
        aplicar(s, req);
        var trocouSenha = false;
        if (Boolean.TRUE.equals(req.removerSenha())) {
            s.setSenhaCifrada(null);
            trocouSenha = true;
        } else if (req.senha() != null && !req.senha().isEmpty()) {
            s.setSenhaCifrada(Cripto.cifrar(req.senha(), props.encryptionKey()));
            trocouSenha = true;
        }
        s.setAtualizadoPorId(u.id());
        auditoria.registrar(u, "senha.editar", "senha", s.getId(), empresaId,
                "Acesso " + s.getAcesso() + " editado" + (trocouSenha ? " (senha alterada)" : ""));
        senhas.saveAndFlush(s);
        return montar(u, List.of(s)).getFirst();
    }

    /** Devolve a senha em texto e registra quem a visualizou. */
    public SenhaReveladaResponse revelar(UsuarioAutenticado u, UUID empresaId, UUID id) {
        var s = buscar(u, empresaId, id);
        if (s.getSenhaCifrada() == null) throw ApiException.naoEncontrado("Senha deste acesso");
        auditoria.registrar(u, "senha.revelar", "senha", s.getId(), empresaId,
                "Senha do acesso " + s.getAcesso() + " visualizada");
        return new SenhaReveladaResponse(s.getId(), Cripto.decifrar(s.getSenhaCifrada(), props.encryptionKey()));
    }

    public void excluir(UsuarioAutenticado u, UUID empresaId, UUID id) {
        var s = buscar(u, empresaId, id);
        auditoria.registrar(u, "senha.excluir", "senha", s.getId(), empresaId, "Acesso " + s.getAcesso() + " excluído");
        senhas.delete(s);
    }

    // ------------------------------------------------------------------ apoio

    private SenhaEmpresa buscar(UsuarioAutenticado u, UUID empresaId, UUID id) {
        escopo.garantirEmpresa(u, empresaId);
        return senhas.findByIdAndEscritorioIdAndEmpresaId(id, u.escritorioId(), empresaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Acesso"));
    }

    private static void aplicar(SenhaEmpresa s, SenhaRequest req) {
        s.setAcesso(req.acesso().trim());
        s.setCategoria(req.categoria() != null ? req.categoria() : CategoriaSenha.OUTRO);
        s.setUsuario(vazioParaNulo(req.usuario()));
        s.setUrl(vazioParaNulo(req.url()));
        s.setObservacoes(vazioParaNulo(req.observacoes()));
    }

    private List<SenhaResponse> montar(UsuarioAutenticado u, List<SenhaEmpresa> lista) {
        if (lista.isEmpty()) return List.of();
        var ids = lista.stream().flatMap(s -> Stream.of(s.getCriadoPorId(), s.getAtualizadoPorId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> nomes = new HashMap<>();
        if (!ids.isEmpty()) {
            usuarios.findByIdInAndEscritorioId(ids, u.escritorioId()).forEach(x -> nomes.put(x.getId(), x.getNome()));
        }
        return lista.stream().map(s -> new SenhaResponse(s.getId(), s.getEmpresaId(), s.getAcesso(), s.getCategoria(),
                s.getUsuario(), s.getSenhaCifrada() != null, s.getUrl(), s.getObservacoes(),
                ref(s.getCriadoPorId(), nomes), ref(s.getAtualizadoPorId(), nomes), s.getCriadoEm(),
                s.getAtualizadoEm())).toList();
    }

    private static UsuarioRef ref(UUID id, Map<UUID, String> nomes) {
        return id == null ? null : new UsuarioRef(id, nomes.get(id));
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Minúsculas e sem acentos, para a busca ("itau" encontra "Itaú"). */
    private static String normalizar(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
