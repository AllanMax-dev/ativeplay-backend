package br.com.ativeplay.modules.sessao.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.empresa.domain.Empresa;
import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.sessao.domain.SessaoTrabalho;
import br.com.ativeplay.modules.sessao.repository.SessaoTrabalhoRepository;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.RentabilidadeEmpresa;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.RentabilidadeResponse;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.ResumoDiaResponse;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.SessaoResponse;
import br.com.ativeplay.modules.sessao.web.dto.SessaoDtos.TempoEmpresa;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Datas;
import br.com.ativeplay.shared.web.Pagina;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
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
    private final EscritorioRepository escritorios;
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

    /**
     * Contador geral do dia: soma o tempo das sessões (entrou na empresa → saiu ou trocou)
     * dentro do dia no fuso do escritório. OPERADOR consulta apenas o próprio resumo.
     */
    @Transactional(readOnly = true)
    public ResumoDiaResponse resumoDia(UsuarioAutenticado u, LocalDate data, UUID usuarioId) {
        var alvo = u.perfil() == Perfil.OPERADOR || usuarioId == null ? u.id() : usuarioId;
        if (!alvo.equals(u.id())) escopo.garantirUsuarioDaEquipe(u, alvo);
        var zona = Datas.zona(fuso(u));
        var dia = data != null ? data : LocalDate.now(zona);
        var de = dia.atStartOfDay(zona).toInstant();
        var ate = dia.plusDays(1).atStartOfDay(zona).toInstant();
        var agora = Instant.now();

        var lista = sessoes.doUsuarioNoPeriodo(u.escritorioId(), alvo, de, ate);
        var porEmpresa = new LinkedHashMap<UUID, long[]>(); // ms, quantidade
        long total = 0;
        for (var s : lista) {
            var ms = sobreposicaoMs(s, de, ate, agora);
            total += ms;
            var c = porEmpresa.computeIfAbsent(s.getEmpresaId(), k -> new long[2]);
            c[0] += ms;
            c[1]++;
        }
        var nomes = nomesEmpresas(u, porEmpresa.keySet());
        var tempos = porEmpresa.entrySet().stream()
                .map(e -> new TempoEmpresa(e.getKey(), nomes.get(e.getKey()), e.getValue()[0], (int) e.getValue()[1]))
                .sorted(Comparator.comparingLong(TempoEmpresa::totalMs).reversed())
                .toList();
        var atual = sessoes.findByUsuarioIdAndFimIsNull(alvo).map(s -> montar(u, List.of(s), agora).getFirst())
                .orElse(null);
        return new ResumoDiaResponse(dia, alvo, total, atual, tempos, agora);
    }

    /**
     * Tempo da equipe por empresa x honorário (ADMIN e GESTOR): mostra quais clientes demandam
     * mais horas do que pagam. Padrão: últimos 30 dias até hoje.
     */
    @Transactional(readOnly = true)
    public RentabilidadeResponse rentabilidade(UsuarioAutenticado u, LocalDate de, LocalDate ate) {
        var zona = Datas.zona(fuso(u));
        var fimPeriodo = ate != null ? ate : LocalDate.now(zona);
        var inicioPeriodo = de != null ? de : fimPeriodo.minusDays(29);
        if (inicioPeriodo.isAfter(fimPeriodo)) throw ApiException.invalido("A data inicial deve ser anterior à final");
        if (ChronoUnit.DAYS.between(inicioPeriodo, fimPeriodo) > 366) {
            throw ApiException.invalido("O período máximo é de um ano");
        }
        var inicio = inicioPeriodo.atStartOfDay(zona).toInstant();
        var fim = fimPeriodo.plusDays(1).atStartOfDay(zona).toInstant();
        var agora = Instant.now();

        var tempo = new HashMap<UUID, Long>();
        var pessoas = new HashMap<UUID, Set<UUID>>();
        for (var s : sessoes.doEscritorioNoPeriodo(u.escritorioId(), inicio, fim)) {
            tempo.merge(s.getEmpresaId(), sobreposicaoMs(s, inicio, fim, agora), Long::sum);
            pessoas.computeIfAbsent(s.getEmpresaId(), k -> new HashSet<>()).add(s.getUsuarioId());
        }
        var dias = ChronoUnit.DAYS.between(inicioPeriodo, fimPeriodo) + 1;
        var proporcao = BigDecimal.valueOf(dias).divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP);

        var linhas = empresas.findByEscritorioId(u.escritorioId()).stream()
                .filter(e -> tempo.containsKey(e.getId()) || e.getStatus() == StatusEmpresa.ATIVA)
                .map(e -> {
                    long ms = tempo.getOrDefault(e.getId(), 0L);
                    BigDecimal valorHora = null;
                    if (e.getHonorarioMensal() != null && ms > 0) {
                        var horas = BigDecimal.valueOf(ms).divide(BigDecimal.valueOf(3_600_000L), 6, RoundingMode.HALF_UP);
                        valorHora = e.getHonorarioMensal().multiply(proporcao).divide(horas, 2, RoundingMode.HALF_UP);
                    }
                    return new RentabilidadeEmpresa(e.getId(), nomeEmpresa(e), ms,
                            pessoas.getOrDefault(e.getId(), Set.of()).size(), e.getHonorarioMensal(), valorHora);
                })
                .sorted(Comparator.comparingLong(RentabilidadeEmpresa::totalMs).reversed())
                .toList();
        long total = linhas.stream().mapToLong(RentabilidadeEmpresa::totalMs).sum();
        return new RentabilidadeResponse(inicioPeriodo, fimPeriodo, total, linhas);
    }

    /** Parte da sessão que cai dentro de [de, ate); a sessão aberta conta até agora. */
    private static long sobreposicaoMs(SessaoTrabalho s, Instant de, Instant ate, Instant agora) {
        var inicio = s.getInicio().isBefore(de) ? de : s.getInicio();
        var fimSessao = s.getFim() != null ? s.getFim() : agora;
        var fim = fimSessao.isAfter(ate) ? ate : fimSessao;
        return Math.max(0, Duration.between(inicio, fim).toMillis());
    }

    private java.util.Map<UUID, String> nomesEmpresas(UsuarioAutenticado u, Set<UUID> ids) {
        if (ids.isEmpty()) return java.util.Map.of();
        return empresas.findByIdInAndEscritorioId(ids, u.escritorioId()).stream()
                .collect(Collectors.toMap(Empresa::getId, SessaoTrabalhoService::nomeEmpresa));
    }

    private String fuso(UsuarioAutenticado u) {
        return escritorios.findById(u.escritorioId()).map(e -> e.getFusoHorario()).orElse(null);
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
