package br.com.ativeplay.modules.auth.service;

import br.com.ativeplay.config.AtivePlayProperties;
import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.auth.domain.RefreshToken;
import br.com.ativeplay.modules.auth.repository.RefreshTokenRepository;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.AlterarSenhaRequest;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.LoginRequest;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.RegistrarEscritorioRequest;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.SessaoResponse;
import br.com.ativeplay.modules.auth.web.dto.AuthDtos.UsuarioResumo;
import br.com.ativeplay.modules.escritorio.domain.Escritorio;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.escritorio.service.ConfiguracaoPadrao;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cnpj;
import br.com.ativeplay.shared.util.Cripto;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Contexto da requisição gravado na sessão e na auditoria. */
    public record Contexto(String ip, String userAgent) {
    }

    private final UsuarioRepository usuarios;
    private final EscritorioRepository escritorios;
    private final RefreshTokenRepository refreshTokens;
    private final ConfiguracaoPadrao configuracaoPadrao;
    private final AuditoriaService auditoria;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AtivePlayProperties props;

    /** Hash usado quando o e-mail não existe, para o login levar o mesmo tempo nos dois casos. */
    private String hashFicticio;

    /** Onboarding self-service: cria o escritório (tenant), o administrador e a configuração padrão. */
    @Transactional
    public SessaoResponse registrar(RegistrarEscritorioRequest req, Contexto ctx) {
        var email = req.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmail(email)) throw ApiException.conflito("E-mail já cadastrado");

        var escritorio = new Escritorio();
        escritorio.setNome(req.nomeEscritorio().trim());
        escritorio.setCnpj(req.cnpj() == null || req.cnpj().isBlank() ? null : Cnpj.normalizar(req.cnpj()));
        escritorio.setSlug(slugDisponivel(req.nomeEscritorio()));
        escritorios.save(escritorio);
        configuracaoPadrao.aplicar(escritorio.getId());

        var admin = new Usuario();
        admin.setEscritorioId(escritorio.getId());
        admin.setNome(req.nome().trim());
        admin.setEmail(email);
        admin.setSenhaHash(passwordEncoder.encode(req.senha()));
        admin.setPerfil(Perfil.ADMIN);
        usuarios.save(admin);

        auditoria.registrar(escritorio.getId(), admin.getId(), "escritorio.criar", "escritorio", escritorio.getId(),
                null, "Escritório " + escritorio.getNome() + " cadastrado", null);
        return emitirSessao(admin, ctx);
    }

    @Transactional
    public SessaoResponse login(LoginRequest req, Contexto ctx) {
        var usuario = usuarios.findByEmail(req.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        var senhaOk = passwordEncoder.matches(req.senha(), usuario != null ? usuario.getSenhaHash() : hashFicticio());
        if (usuario == null || !senhaOk || !usuario.isAtivo() || !escritorioAtivo(usuario)) {
            throw ApiException.naoAutorizado("E-mail ou senha inválidos");
        }
        usuario.setUltimoAcesso(Instant.now());
        auditoria.registrar(usuario.getEscritorioId(), usuario.getId(), "auth.login", "usuario", usuario.getId(), null,
                null, null);
        return emitirSessao(usuario, ctx);
    }

    /** Rotação: o refresh token usado é revogado e um novo par é emitido. */
    @Transactional(noRollbackFor = ApiException.class)
    public SessaoResponse renovar(String refreshToken, Contexto ctx) {
        var registro = refreshTokens.findByTokenHash(Cripto.sha256(refreshToken))
                .orElseThrow(() -> ApiException.naoAutorizado("Sessão inválida"));
        var agora = Instant.now();
        if (registro.getRevogadoEm() != null) {
            // Reuso de um token já rotacionado indica vazamento: encerra todas as sessões do usuário.
            refreshTokens.revogarTodos(registro.getUsuarioId(), agora);
            throw ApiException.naoAutorizado("Sessão inválida");
        }
        if (registro.getExpiraEm().isBefore(agora)) throw ApiException.naoAutorizado("Sessão expirada");

        var usuario = usuarios.findById(registro.getUsuarioId()).orElseThrow();
        if (!usuario.isAtivo() || !escritorioAtivo(usuario)) throw ApiException.naoAutorizado("Usuário inativo");
        registro.setRevogadoEm(agora);
        return emitirSessao(usuario, ctx);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(Cripto.sha256(refreshToken))
                .filter(t -> t.getRevogadoEm() == null)
                .ifPresent(t -> t.setRevogadoEm(Instant.now()));
    }

    @Transactional
    public void alterarSenha(UsuarioAutenticado u, AlterarSenhaRequest req) {
        var usuario = usuarios.findById(u.id()).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
        if (!passwordEncoder.matches(req.senhaAtual(), usuario.getSenhaHash())) {
            throw ApiException.invalido("Senha atual incorreta");
        }
        usuario.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        refreshTokens.revogarTodos(u.id(), Instant.now());
        auditoria.registrar(u, "auth.alterar_senha", "usuario", u.id(), null, null);
    }

    /** Revoga todas as sessões do usuário (desativação, redefinição de senha). */
    @Transactional
    public void revogarSessoes(UUID usuarioId) {
        refreshTokens.revogarTodos(usuarioId, Instant.now());
    }

    private SessaoResponse emitirSessao(Usuario usuario, Contexto ctx) {
        var agora = Instant.now();
        var ttl = props.jwt().accessTtl();
        var claims = JwtClaimsSet.builder()
                .issuer("ativeplay")
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(ttl))
                .claim("esc", usuario.getEscritorioId().toString())
                .claim("perfil", usuario.getPerfil().name());
        if (usuario.getEmpresaId() != null) claims.claim("emp", usuario.getEmpresaId().toString());
        var accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();

        var refresh = Cripto.tokenAleatorio(48);
        var registro = new RefreshToken();
        registro.setUsuarioId(usuario.getId());
        registro.setTokenHash(Cripto.sha256(refresh));
        registro.setExpiraEm(agora.plus(Duration.ofDays(props.jwt().refreshTtlDias())));
        registro.setIp(ctx.ip());
        registro.setUserAgent(ctx.userAgent() == null ? null : ctx.userAgent().substring(0, Math.min(255, ctx.userAgent().length())));
        refreshTokens.save(registro);

        return new SessaoResponse(accessToken, refresh, "Bearer", ttl.toSeconds(), new UsuarioResumo(usuario.getId(),
                usuario.getNome(), usuario.getEmail(), usuario.getPerfil(), usuario.getEscritorioId(),
                usuario.getEmpresaId()));
    }

    private boolean escritorioAtivo(Usuario usuario) {
        return escritorios.findById(usuario.getEscritorioId()).map(Escritorio::isAtivo).orElse(false);
    }

    private String hashFicticio() {
        if (hashFicticio == null) hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
        return hashFicticio;
    }

    private String slugDisponivel(String nome) {
        var base = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isEmpty()) base = "escritorio";
        if (base.length() > 60) base = base.substring(0, 60);
        var slug = base;
        for (int i = 2; escritorios.existsBySlug(slug); i++) slug = base + "-" + i;
        return slug;
    }
}
