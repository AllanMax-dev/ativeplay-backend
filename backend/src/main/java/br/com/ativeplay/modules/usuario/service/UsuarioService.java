package br.com.ativeplay.modules.usuario.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.auth.service.AuthService;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.setor.domain.Setor;
import br.com.ativeplay.modules.setor.repository.SetorRepository;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.modules.usuario.domain.UsuarioEmpresa;
import br.com.ativeplay.modules.usuario.domain.UsuarioSetor;
import br.com.ativeplay.modules.usuario.repository.UsuarioEmpresaRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioSetorRepository;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.AtualizarUsuarioRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.CriarUsuarioRequest;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.EmpresaResumo;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.SenhaTemporariaResposta;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.SetorResumo;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.UsuarioCriadoResposta;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.UsuarioDetalhe;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioDtos.UsuarioResposta;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cripto;
import br.com.ativeplay.shared.web.Pagina;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final UsuarioSetorRepository usuarioSetores;
    private final UsuarioEmpresaRepository carteira;
    private final SetorRepository setores;
    private final EmpresaRepository empresas;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public Pagina<UsuarioResposta> listar(UsuarioAutenticado u, Perfil perfil, Boolean ativo, UUID setorId,
            String busca, Pageable pageable) {
        Specification<Usuario> spec = (r, q, cb) -> {
            var filtros = new ArrayList<Predicate>();
            filtros.add(cb.equal(r.get("escritorioId"), u.escritorioId()));
            if (perfil != null) filtros.add(cb.equal(r.get("perfil"), perfil));
            if (ativo != null) filtros.add(cb.equal(r.get("ativo"), ativo));
            if (setorId != null) {
                var sub = q.subquery(UUID.class);
                var us = sub.from(UsuarioSetor.class);
                sub.select(us.get("usuarioId")).where(cb.equal(us.get("setorId"), setorId));
                filtros.add(r.get("id").in(sub));
            }
            if (busca != null && !busca.isBlank()) {
                var termo = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
                filtros.add(cb.or(cb.like(cb.lower(r.get("nome")), termo), cb.like(cb.lower(r.get("email")), termo)));
            }
            return cb.and(filtros.toArray(Predicate[]::new));
        };
        var page = usuarios.findAll(spec,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("nome")));

        var ids = page.getContent().stream().map(Usuario::getId).toList();
        var vinculosSetor = ids.isEmpty() ? List.<UsuarioSetor>of() : usuarioSetores.findByUsuarioIdIn(ids);
        var nomesSetor = setores.findAllById(vinculosSetor.stream().map(UsuarioSetor::getSetorId).distinct().toList())
                .stream().collect(Collectors.toMap(Setor::getId, Setor::getNome));
        var setoresPorUsuario = vinculosSetor.stream().collect(Collectors.groupingBy(UsuarioSetor::getUsuarioId,
                Collectors.mapping(v -> nomesSetor.get(v.getSetorId()), Collectors.toList())));
        var carteiraPorUsuario = (ids.isEmpty() ? List.<UsuarioEmpresa>of() : carteira.findByUsuarioIdIn(ids)).stream()
                .collect(Collectors.groupingBy(UsuarioEmpresa::getUsuarioId, Collectors.counting()));

        return Pagina.de(page, x -> new UsuarioResposta(x.getId(), x.getNome(), x.getEmail(), x.getPerfil(),
                x.getCargo(), x.getTelefone(), x.isAtivo(), x.getEmpresaId(), x.getUltimoAcesso(), x.getCriadoEm(),
                setoresPorUsuario.getOrDefault(x.getId(), List.of()),
                carteiraPorUsuario.getOrDefault(x.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public UsuarioDetalhe detalhe(UsuarioAutenticado u, UUID id) {
        return detalhe(buscar(u, id));
    }

    public UsuarioCriadoResposta criar(UsuarioAutenticado u, CriarUsuarioRequest req) {
        exigirPodeAtribuir(u, req.perfil());
        var email = normalizarEmail(req.email());
        if (usuarios.existsByEmail(email)) throw ApiException.conflito("E-mail já cadastrado");

        var novo = new Usuario();
        novo.setEscritorioId(u.escritorioId());
        novo.setNome(req.nome().trim());
        novo.setEmail(email);
        novo.setPerfil(req.perfil());
        novo.setCargo(req.cargo());
        novo.setTelefone(req.telefone());
        novo.setEmpresaId(validarEmpresaDoPerfil(u, req.perfil(), req.empresaId()));
        var senhaTemporaria = req.senha() == null || req.senha().isBlank() ? gerarSenhaTemporaria() : null;
        novo.setSenhaHash(passwordEncoder.encode(senhaTemporaria != null ? senhaTemporaria : req.senha()));
        usuarios.save(novo);

        if (req.setorIds() != null) definirSetores(u, novo, req.setorIds());
        if (req.empresaIds() != null) definirCarteira(u, novo, req.empresaIds());
        auditoria.registrar(u, "usuario.criar", "usuario", novo.getId(), novo.getEmpresaId(),
                "Usuário " + novo.getNome() + " (" + novo.getPerfil() + ") criado");
        return new UsuarioCriadoResposta(detalhe(novo), senhaTemporaria);
    }

    public UsuarioDetalhe atualizar(UsuarioAutenticado u, UUID id, AtualizarUsuarioRequest req) {
        var alvo = buscar(u, id);
        exigirPodeGerenciar(u, alvo);
        if (req.perfil() != alvo.getPerfil()) {
            if (alvo.getId().equals(u.id())) throw ApiException.invalido("Você não pode alterar o seu próprio perfil");
            exigirPodeAtribuir(u, req.perfil());
        }
        var email = normalizarEmail(req.email());
        if (!email.equals(alvo.getEmail()) && usuarios.existsByEmail(email)) {
            throw ApiException.conflito("E-mail já cadastrado");
        }
        var empresaId = validarEmpresaDoPerfil(u, req.perfil(), req.empresaId());
        var mudouAcesso = req.perfil() != alvo.getPerfil() || !Objects.equals(empresaId, alvo.getEmpresaId());

        alvo.setNome(req.nome().trim());
        alvo.setEmail(email);
        alvo.setPerfil(req.perfil());
        alvo.setCargo(req.cargo());
        alvo.setTelefone(req.telefone());
        alvo.setEmpresaId(empresaId);
        if (req.perfil() == Perfil.CLIENTE) {
            carteira.deleteByUsuarioId(alvo.getId());
            usuarioSetores.deleteByUsuarioId(alvo.getId());
        }
        // O token carrega perfil e empresa: força novo login quando o acesso muda.
        if (mudouAcesso) authService.revogarSessoes(alvo.getId());
        auditoria.registrar(u, "usuario.editar", "usuario", alvo.getId(), alvo.getEmpresaId(),
                "Usuário " + alvo.getNome() + " atualizado");
        return detalhe(alvo);
    }

    public UsuarioDetalhe alterarAtivo(UsuarioAutenticado u, UUID id, boolean ativo) {
        var alvo = buscar(u, id);
        exigirPodeGerenciar(u, alvo);
        if (!ativo && alvo.getId().equals(u.id())) throw ApiException.invalido("Você não pode desativar a si mesmo");
        alvo.setAtivo(ativo);
        if (!ativo) authService.revogarSessoes(alvo.getId());
        auditoria.registrar(u, ativo ? "usuario.ativar" : "usuario.desativar", "usuario", alvo.getId(),
                alvo.getEmpresaId(), "Usuário " + alvo.getNome() + (ativo ? " reativado" : " desativado"));
        return detalhe(alvo);
    }

    public SenhaTemporariaResposta redefinirSenha(UsuarioAutenticado u, UUID id) {
        var alvo = buscar(u, id);
        exigirPodeGerenciar(u, alvo);
        var senha = gerarSenhaTemporaria();
        alvo.setSenhaHash(passwordEncoder.encode(senha));
        authService.revogarSessoes(alvo.getId());
        auditoria.registrar(u, "usuario.redefinir_senha", "usuario", alvo.getId(), alvo.getEmpresaId(),
                "Senha de " + alvo.getNome() + " redefinida");
        return new SenhaTemporariaResposta(senha);
    }

    public UsuarioDetalhe atualizarSetores(UsuarioAutenticado u, UUID id, List<UUID> setorIds) {
        var alvo = buscar(u, id);
        exigirPodeGerenciar(u, alvo);
        definirSetores(u, alvo, setorIds);
        auditoria.registrar(u, "usuario.setores", "usuario", alvo.getId(), null,
                "Setores de " + alvo.getNome() + " atualizados");
        return detalhe(alvo);
    }

    public UsuarioDetalhe atualizarCarteira(UsuarioAutenticado u, UUID id, List<UUID> empresaIds) {
        var alvo = buscar(u, id);
        exigirPodeGerenciar(u, alvo);
        definirCarteira(u, alvo, empresaIds);
        auditoria.registrar(u, "usuario.carteira", "usuario", alvo.getId(), null,
                "Carteira de " + alvo.getNome() + ": " + empresaIds.size() + " empresa(s)");
        return detalhe(alvo);
    }

    // ---------- regras ----------

    private void definirSetores(UsuarioAutenticado u, Usuario alvo, List<UUID> setorIds) {
        if (alvo.getPerfil() == Perfil.CLIENTE && !setorIds.isEmpty()) {
            throw ApiException.invalido("Usuários do portal não pertencem a setores");
        }
        var unicos = new LinkedHashSet<>(setorIds);
        if (setores.findByIdInAndEscritorioId(unicos, u.escritorioId()).size() != unicos.size()) {
            throw ApiException.naoEncontrado("Setor");
        }
        usuarioSetores.deleteByUsuarioId(alvo.getId());
        usuarioSetores.flush();
        usuarioSetores.saveAll(unicos.stream().map(s -> new UsuarioSetor(alvo.getId(), s)).toList());
    }

    private void definirCarteira(UsuarioAutenticado u, Usuario alvo, List<UUID> empresaIds) {
        if (alvo.getPerfil() == Perfil.CLIENTE && !empresaIds.isEmpty()) {
            throw ApiException.invalido("Usuários do portal não têm carteira de empresas");
        }
        var unicos = new LinkedHashSet<>(empresaIds);
        if (empresas.findByIdInAndEscritorioId(unicos, u.escritorioId()).size() != unicos.size()) {
            throw ApiException.naoEncontrado("Empresa");
        }
        carteira.deleteByUsuarioId(alvo.getId());
        carteira.flush();
        carteira.saveAll(unicos.stream().map(e -> new UsuarioEmpresa(alvo.getId(), e)).toList());
    }

    /** CLIENTE exige uma empresa do escritório; a equipe interna não tem empresa vinculada. */
    private UUID validarEmpresaDoPerfil(UsuarioAutenticado u, Perfil perfil, UUID empresaId) {
        if (perfil != Perfil.CLIENTE) {
            if (empresaId != null) throw ApiException.invalido("Somente usuários CLIENTE são vinculados a uma empresa");
            return null;
        }
        if (empresaId == null) throw ApiException.invalido("Usuários CLIENTE precisam de uma empresa (empresaId)");
        empresas.findByIdAndEscritorioId(empresaId, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Empresa"));
        return empresaId;
    }

    private static void exigirPodeAtribuir(UsuarioAutenticado u, Perfil perfil) {
        if (perfil == Perfil.ADMIN && !u.isAdmin()) {
            throw ApiException.proibido("Somente administradores podem conceder o perfil ADMIN");
        }
    }

    private static void exigirPodeGerenciar(UsuarioAutenticado u, Usuario alvo) {
        if (alvo.getPerfil() == Perfil.ADMIN && !u.isAdmin()) {
            throw ApiException.proibido("Somente administradores podem alterar outro administrador");
        }
    }

    private Usuario buscar(UsuarioAutenticado u, UUID id) {
        return usuarios.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
    }

    private UsuarioDetalhe detalhe(Usuario x) {
        var setorIds = usuarioSetores.findSetorIdsByUsuarioId(x.getId());
        var listaSetores = setores.findAllById(setorIds).stream()
                .map(s -> new SetorResumo(s.getId(), s.getNome())).toList();
        var empresaIds = carteira.findEmpresaIdsByUsuarioId(x.getId());
        var listaEmpresas = empresas.findAllById(empresaIds).stream()
                .map(e -> new EmpresaResumo(e.getId(), e.getRazaoSocial())).toList();
        return new UsuarioDetalhe(x.getId(), x.getNome(), x.getEmail(), x.getPerfil(), x.getCargo(), x.getTelefone(),
                x.isAtivo(), x.getEmpresaId(), x.getUltimoAcesso(), x.getCriadoEm(), x.getAtualizadoEm(), listaSetores,
                listaEmpresas);
    }

    /** Nomes por id, para montar referências em outras respostas. */
    @Transactional(readOnly = true)
    public Map<UUID, String> nomes(Collection<UUID> ids) {
        if (ids.isEmpty()) return new HashMap<>();
        return usuarios.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome,
                (a, b) -> a, HashMap::new));
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** ~12 caracteres aleatórios, URL-safe. */
    private static String gerarSenhaTemporaria() {
        return Cripto.tokenAleatorio(9);
    }
}
