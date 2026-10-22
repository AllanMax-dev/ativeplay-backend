package br.com.ativeplay.modules.sessao.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.empresa.domain.Empresa;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.sessao.domain.SessaoTrabalho;
import br.com.ativeplay.modules.sessao.repository.SessaoTrabalhoRepository;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.SessaoResponse;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sessões de trabalho: o tempo em que o usuário esteve "dentro" de uma empresa. */
@Service
@RequiredArgsConstructor
@Transactional
public class SessaoTrabalhoService {

    private final SessaoTrabalhoRepository sessoes;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final EscopoService escopo;
    private final AuditoriaService auditoria;

    /** Abre uma sessão na empresa, encerrando antes a sessão aberta do usuário (só pode haver uma). */
    public SessaoResponse iniciar(UsuarioAutenticado u, UUID empresaId) {
        escopo.garantirEmpresa(u, empresaId);
        var agora = Instant.now();
        sessoes.findByUsuarioIdAndFimIsNull(u.id()).ifPresent(aberta -> {
            aberta.setFim(agora);
            sessoes.flush(); // libera o índice único parcial antes de inserir a nova
        });
        var s = new SessaoTrabalho();
        s.setEscritorioId(u.escritorioId());
        s.setUsuarioId(u.id());
        s.setEmpresaId(empresaId);
        s.setInicio(agora);
        sessoes.save(s);
        auditoria.registrar(u, "sessao.iniciar", "sessao", s.getId(), empresaId, "Sessão de trabalho iniciada");
        return montar(u, List.of(s), agora).getFirst();
    }

    public SessaoResponse encerrar(UsuarioAutenticado u) {
        var s = sessoes.findByUsuarioIdAndFimIsNull(u.id())
                .orElseThrow(() -> ApiException.conflito("Não há sessão de trabalho aberta"));
        var agora = Instant.now();
        s.setFim(agora);
        auditoria.registrar(u, "sessao.encerrar", "sessao", s.getId(), s.getEmpresaId(), "Sessão de trabalho encerrada");
        return montar(u, List.of(s), agora).getFirst();
    }

    @Transactional(readOnly = true)
    public Optional<SessaoResponse> atual(UsuarioAutenticado u) {
        return sessoes.findByUsuarioIdAndFimIsNull(u.id()).map(s -> montar(u, List.of(s), Instant.now()).getFirst());
    }

    /** Histórico. OPERADOR vê apenas as próprias sessões. */
    @Transactional(readOnly = true)
    public Pagina<SessaoResponse> listar(UsuarioAutenticado u, UUID usuarioId, UUID empresaId, Instant de, Instant ate,
            Pageable pageable) {
        var filtroUsuario = u.perfil() == Perfil.OPERADOR ? u.id() : usuarioId;
        if (empresaId != null) escopo.garantirAcesso(u, empresaId);
        Specification<SessaoTrabalho> spec = (r, q, cb) -> {
            var p = new ArrayList<Predicate>();
            p.add(cb.equal(r.get("escritorioId"), u.escritorioId()));
            if (filtroUsuario != null) p.add(cb.equal(r.get("usuarioId"), filtroUsuario));
            if (empresaId != null) p.add(cb.equal(r.get("empresaId"), empresaId));
            if (de != null) p.add(cb.greaterThanOrEqualTo(r.get("inicio"), de));
            if (ate != null) p.add(cb.lessThanOrEqualTo(r.get("inicio"), ate));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = sessoes.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "inicio")));
        var respostas = montar(u, page.getContent(), Instant.now());
        return Pagina.de(new PageImpl<>(respostas, page.getPageable(), page.getTotalElements()));
    }

    private List<SessaoResponse> montar(UsuarioAutenticado u, List<SessaoTrabalho> lista, Instant agora) {
        if (lista.isEmpty()) return List.of();
        var nomesEmpresas = empresas.findByIdInAndEscritorioId(
                        lista.stream().map(SessaoTrabalho::getEmpresaId).collect(Collectors.toSet()), u.escritorioId())
                .stream().collect(Collectors.toMap(Empresa::getId, Function.identity()));
        var nomesUsuarios = usuarios.findByIdInAndEscritorioId(
                        lista.stream().map(SessaoTrabalho::getUsuarioId).collect(Collectors.toSet()), u.escritorioId())
                .stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
        return lista.stream().map(s -> {
            var e = nomesEmpresas.get(s.getEmpresaId());
            var fim = s.getFim() != null ? s.getFim() : agora;
            return new SessaoResponse(s.getId(), s.getUsuarioId(), nomesUsuarios.get(s.getUsuarioId()),
                    s.getEmpresaId(), e == null ? null : nomeEmpresa(e), s.getInicio(), s.getFim(),
                    Math.max(0, Duration.between(s.getInicio(), fim).toMillis()));
        }).toList();
    }

    private static String nomeEmpresa(Empresa e) {
        return e.getNomeFantasia() != null && !e.getNomeFantasia().isBlank() ? e.getNomeFantasia() : e.getRazaoSocial();
    }
}
