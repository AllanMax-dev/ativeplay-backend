package br.com.ativeplay.modules.auth.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.auth.domain.SessaoAcesso;
import br.com.ativeplay.modules.auth.repository.RefreshTokenRepository;
import br.com.ativeplay.modules.auth.repository.SessaoAcessoRepository;
import br.com.ativeplay.modules.escritorio.domain.Escritorio;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Datas;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
@RequiredArgsConstructor
public class SessaoAcessoService {

    private final SessaoAcessoRepository repository;
    private final RefreshTokenRepository refreshTokens;
    private final UsuarioRepository usuarios;
    private final EscritorioRepository escritorios;
    private final AuditoriaService auditoria;

    public record SessaoAcessoResponse(
            UUID id,
            UUID usuarioId,
            String usuarioNome,
            Instant inicio,
            Instant fim,
            Instant ultimoSinal,
            boolean pendente,
            long duracaoMs
    ) {}

    public record DiaResponse(LocalDate data, long totalMs) {}

    public record EquipeResponse(
            UUID usuarioId,
            String usuarioNome,
            Perfil perfil,
            long totalMesMs,
            long pendentes,
            List<DiaResponse> dias
    ) {}

    private record Periodo(Instant de, Instant ate) {}

    /**
     * Abre o registro de ponto do usuário (login/cadastro). O registro aberto anterior é encerrado
     * normalmente e os refresh tokens ligados a ele são revogados (aparelho antigo deslogado).
     * CLIENTE não usa o ponto: devolve null.
     */
    @Transactional
    public UUID iniciar(Usuario usuario) {
        if (usuario.getPerfil() == Perfil.CLIENTE) return null;

        var agora = Instant.now();

        repository.findByUsuarioIdAndFimIsNullAndPendenteFalse(usuario.getId())
                .forEach(anterior -> {
                    anterior.setFim(agora);
                    refreshTokens.revogarPorSessaoAcesso(anterior.getId(), agora);
                });
        // o índice único parcial exige o fechamento gravado antes do novo insert
        repository.flush();

        var sessao = new SessaoAcesso();

        sessao.setUsuarioId(usuario.getId());
        sessao.setEscritorioId(usuario.getEscritorioId());
        sessao.setInicio(agora);

        return repository.save(sessao).getId();
    }

    /** True se o registro existe, não tem fim e não está pendente. */
    @Transactional(readOnly = true)
    public boolean aberta(UUID id) {
        return repository.findById(id)
                .map(sessao -> sessao.getFim() == null && !sessao.isPendente())
                .orElse(false);
    }

    @Transactional
    public void encerrar(UUID id) {
        if (id == null) return;

        repository.findById(id).ifPresent(sessao -> {
            if (sessao.getFim() == null && !sessao.isPendente()) {
                sessao.setFim(Instant.now());
            }
        });
    }

    @Transactional
    public void encerrarTodas(UUID usuarioId) {
        var agora = Instant.now();

        repository.findByUsuarioIdAndFimIsNullAndPendenteFalse(usuarioId)
                .forEach(sessao -> sessao.setFim(agora));
    }

    /** Sinal de vida do navegador; 409 se o registro não estiver aberto. */
    @Transactional
    public void sinal(UsuarioAutenticado usuario, UUID sessaoId) {
        if (sessaoId == null || repository.registrarSinal(
                sessaoId,
                usuario.id(),
                usuario.escritorioId(),
                Instant.now()) == 0) {
            throw ApiException.conflito("Seu ponto foi encerrado. Entre novamente.");
        }
    }

    /** Marca como pendentes os registros abertos sem sinal desde o limite; devolve quantos. */
    @Transactional
    public int marcarPendentes(Instant limite) {
        return repository.marcarPendentes(limite);
    }

    @Transactional(readOnly = true)
    public Optional<SessaoAcessoResponse> atual(
            UsuarioAutenticado usuario,
            UUID sessaoId
    ) {
        if (sessaoId == null) {
            return Optional.empty();
        }

        return repository
                .findByIdAndUsuarioIdAndEscritorioId(
                        sessaoId,
                        usuario.id(),
                        usuario.escritorioId()
                )
                .filter(sessao -> sessao.getFim() == null && !sessao.isPendente())
                .map(sessao -> montar(sessao, nome(sessao.getUsuarioId()), Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<SessaoAcessoResponse> historico(
            UsuarioAutenticado usuario
    ) {
        var agora = Instant.now();
        var nome = nome(usuario.id());

        return repository
                .findByUsuarioIdAndEscritorioIdOrderByInicioDesc(
                        usuario.id(),
                        usuario.escritorioId(),
                        PageRequest.of(0, 100)
                )
                .stream()
                .map(sessao -> montar(sessao, nome, agora))
                .toList();
    }

    /** Carga horária da equipe (todos os não CLIENTE) no mês, com total por dia no fuso do escritório. */
    @Transactional(readOnly = true)
    public List<EquipeResponse> equipe(UsuarioAutenticado gestor, String mes) {
        var zona = zona(gestor);
        var periodo = periodo(mes, zona);
        var agora = Instant.now();

        Map<UUID, List<SessaoAcesso>> porUsuario = new HashMap<>();
        repository.findDoEscritorioNaJanela(gestor.escritorioId(), periodo.de(), periodo.ate(), agora)
                .forEach(s -> porUsuario.computeIfAbsent(s.getUsuarioId(), k -> new ArrayList<>()).add(s));

        // pendentes aguardando ajuste, de qualquer mês
        Map<UUID, Long> pendentes = new HashMap<>();
        repository.findByEscritorioIdAndFimIsNullAndPendenteTrue(gestor.escritorioId())
                .forEach(s -> pendentes.merge(s.getUsuarioId(), 1L, Long::sum));

        return usuarios.findByEscritorioIdAndPerfilNot(gestor.escritorioId(), Perfil.CLIENTE).stream()
                .sorted(Comparator.comparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(u -> {
                    var dias = totaisPorDia(porUsuario.getOrDefault(u.getId(), List.of()), periodo, zona, agora);
                    long total = dias.stream().mapToLong(DiaResponse::totalMs).sum();
                    return new EquipeResponse(u.getId(), u.getNome(), u.getPerfil(), total,
                            pendentes.getOrDefault(u.getId(), 0L), dias);
                })
                .toList();
    }

    /** Registros de um usuário do escritório no mês, mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<SessaoAcessoResponse> doUsuario(UsuarioAutenticado gestor, UUID usuarioId, String mes) {
        var alvo = usuarios.findByIdAndEscritorioId(usuarioId, gestor.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
        var periodo = periodo(mes, zona(gestor));
        var agora = Instant.now();

        return repository.findDoUsuarioNaJanela(gestor.escritorioId(), alvo.getId(), periodo.de(), periodo.ate(), agora)
                .stream()
                .map(sessao -> montar(sessao, alvo.getNome(), agora))
                .toList();
    }

    /** Gestor informa a saída de um registro pendente (auditado). */
    @Transactional
    public SessaoAcessoResponse informarSaida(UsuarioAutenticado gestor, UUID id, Instant fim) {
        var sessao = repository.findByIdAndEscritorioId(id, gestor.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Registro de ponto"));
        if (sessao.getFim() != null || !sessao.isPendente()) {
            throw ApiException.conflito("Só é possível informar a saída de um registro pendente");
        }

        var agora = Instant.now();
        if (fim.isBefore(sessao.getInicio())) {
            throw campoFimInvalido("A saída não pode ser anterior ao início do registro");
        }
        if (fim.isAfter(agora)) {
            throw campoFimInvalido("A saída não pode estar no futuro");
        }

        sessao.setFim(fim);
        sessao.setPendente(false);

        var nome = nome(sessao.getUsuarioId());
        auditoria.registrar(gestor.escritorioId(), gestor.id(), "ponto.ajustar_saida", "sessao_acesso",
                sessao.getId(), null, "Saída de " + nome + " informada: " + fim,
                Map.of("usuarioId", sessao.getUsuarioId().toString(), "fim", fim.toString()));
        return montar(sessao, nome, agora);
    }

    /** 422 no mesmo formato do 400 de validação: campos = {fim: mensagem}. */
    private static ErrorResponseException campoFimInvalido(String mensagem) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Dados inválidos");
        problema.setProperty("campos", Map.of("fim", mensagem));
        return new ErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY, problema, null);
    }

    private static Periodo periodo(String mes, ZoneId zona) {
        YearMonth ym;
        try {
            ym = mes == null || mes.isBlank() ? YearMonth.now(zona) : YearMonth.parse(mes.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.invalido("Mês inválido: use AAAA-MM");
        }
        return new Periodo(
                ym.atDay(1).atStartOfDay(zona).toInstant(),
                ym.plusMonths(1).atDay(1).atStartOfDay(zona).toInstant());
    }

    private ZoneId zona(UsuarioAutenticado usuario) {
        return Datas.zona(escritorios.findById(usuario.escritorioId())
                .map(Escritorio::getFusoHorario).orElse(null));
    }

    /** Soma por dia (fuso do escritório), dividindo na meia-noite os registros que a atravessam. */
    private static List<DiaResponse> totaisPorDia(
            List<SessaoAcesso> registros,
            Periodo periodo,
            ZoneId zona,
            Instant agora
    ) {
        var dias = new TreeMap<LocalDate, Long>();

        for (var sessao : registros) {
            var cursor = sessao.getInicio().isBefore(periodo.de()) ? periodo.de() : sessao.getInicio();
            var fimEfetivo = fimEfetivo(sessao, agora);
            var fim = fimEfetivo.isAfter(periodo.ate()) ? periodo.ate() : fimEfetivo;

            while (cursor.isBefore(fim)) {
                var dia = cursor.atZone(zona).toLocalDate();
                var meiaNoite = dia.plusDays(1).atStartOfDay(zona).toInstant();
                var trecho = fim.isBefore(meiaNoite) ? fim : meiaNoite;
                dias.merge(dia, Duration.between(cursor, trecho).toMillis(), Long::sum);
                cursor = trecho;
            }
        }

        return dias.entrySet().stream()
                .map(e -> new DiaResponse(e.getKey(), e.getValue()))
                .toList();
    }

    /** Fechado: fim; pendente: último sinal (ou início); aberto: agora. */
    private static Instant fimEfetivo(SessaoAcesso sessao, Instant agora) {
        if (sessao.getFim() != null) return sessao.getFim();
        if (sessao.isPendente()) {
            return sessao.getUltimoSinal() != null ? sessao.getUltimoSinal() : sessao.getInicio();
        }
        return agora;
    }

    private String nome(UUID usuarioId) {
        return usuarios.findById(usuarioId).map(Usuario::getNome).orElse(null);
    }

    private static SessaoAcessoResponse montar(
            SessaoAcesso sessao,
            String usuarioNome,
            Instant agora
    ) {
        long duracao = Math.max(
                0,
                Duration.between(
                        sessao.getInicio(),
                        fimEfetivo(sessao, agora)
                ).toMillis()
        );

        return new SessaoAcessoResponse(
                sessao.getId(),
                sessao.getUsuarioId(),
                usuarioNome,
                sessao.getInicio(),
                sessao.getFim(),
                sessao.getUltimoSinal(),
                sessao.isPendente(),
                duracao
        );
    }
}
