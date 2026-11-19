package br.com.ativeplay.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.ApiDeTeste;
import br.com.ativeplay.ApiDeTeste.Sessao;
import br.com.ativeplay.IntegracaoTest;
import br.com.ativeplay.modules.auth.service.AgendadorPonto;
import br.com.ativeplay.modules.auth.service.SessaoAcessoService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ponto eletrônico: registro de acesso aberto no login, pendente sem sinal e carga horária da equipe. */
class PontoEletronicoIntegracaoTest extends IntegracaoTest {

    private static final String BASE = "/api/v1/sessoes-acesso";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessaoAcessoService servico;

    ApiDeTeste api;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(http);
    }

    // ---------- regras 1, 2 e 6: abrir, fechar, um aberto por vez ----------

    @Test
    void cadastroELoginAbremORegistro() {
        var admin = api.registrar(ApiDeTeste.novoEmail());

        var atual = api.get(BASE + "/atual", admin.accessToken());
        assertThat(atual.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atual.getBody()).containsEntry("pendente", false).containsEntry("usuarioNome", "Admin");
        assertThat(atual.getBody().get("fim")).isNull();
        assertThat(atual.getBody().get("ultimoSinal")).isNull();
    }

    @Test
    void logoutFechaORegistro() {
        var admin = api.registrar(ApiDeTeste.novoEmail());
        var id = atualId(admin.accessToken());

        var logout = api.post("/api/v1/auth/logout", null, Map.of("refreshToken", admin.refreshToken()));
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(api.get(BASE + "/atual", admin.accessToken()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var registro = registroDoHistorico(admin.accessToken(), id);
        assertThat(registro.get("fim")).isNotNull();
        assertThat(registro).containsEntry("pendente", false);
    }

    @Test
    void refreshMantemOMesmoRegistro() {
        var admin = api.registrar(ApiDeTeste.novoEmail());
        var id = atualId(admin.accessToken());

        var renovada = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", admin.refreshToken()));
        assertThat(renovada.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(atualId((String) renovada.getBody().get("accessToken"))).isEqualTo(id);
        assertThat(api.getLista(BASE, (String) renovada.getBody().get("accessToken")).getBody()).hasSize(1);
    }

    @Test
    void novoLoginFechaORegistroAnteriorERevogaORefreshDoAparelhoAntigo() {
        var email = ApiDeTeste.novoEmail();
        var antigo = api.registrar(email);
        var idAntigo = atualId(antigo.accessToken());

        var novo = loginCompleto(email);
        var idNovo = atualId(novo.accessToken());
        assertThat(idNovo).isNotEqualTo(idAntigo);

        // o registro antigo foi encerrado normalmente, não ficou pendente
        assertThat(api.get(BASE + "/atual", antigo.accessToken()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var fechado = registroDoHistorico(novo.accessToken(), idAntigo);
        assertThat(fechado.get("fim")).isNotNull();
        assertThat(fechado).containsEntry("pendente", false);

        // o refresh token do aparelho antigo não vale mais...
        var recusado = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", antigo.refreshToken()));
        assertThat(recusado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(recusado.getBody().get("detail")).isEqualTo("Sessão encerrada");

        // ...e a tentativa não derruba o login novo
        assertThat(api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", novo.refreshToken()))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atualId(novo.accessToken())).isEqualTo(idNovo);
    }

    @Test
    void sinalAtualizaOUltimoSinalEVirou409AposLogout() {
        var admin = api.registrar(ApiDeTeste.novoEmail());

        assertThat(api.post(BASE + "/sinal", admin.accessToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.get(BASE + "/atual", admin.accessToken()).getBody().get("ultimoSinal")).isNotNull();

        api.post("/api/v1/auth/logout", null, Map.of("refreshToken", admin.refreshToken()));
        var resposta = api.post(BASE + "/sinal", admin.accessToken(), null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().get("detail")).isEqualTo("Seu ponto foi encerrado. Entre novamente.");
    }

    @Test
    void alterarSenhaContinuaEncerrandoORegistro() {
        var admin = api.registrar(ApiDeTeste.novoEmail());

        var resposta = api.post("/api/v1/auth/senha", admin.accessToken(),
                Map.of("senhaAtual", ApiDeTeste.SENHA, "novaSenha", "novaSenha1234"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(api.get(BASE + "/atual", admin.accessToken()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void bancoNaoAceitaDoisRegistrosAbertosDoMesmoUsuario() {
        var admin = api.registrar(ApiDeTeste.novoEmail());
        var registro = atual(admin.accessToken());
        var usuarioId = UUID.fromString((String) registro.get("usuarioId"));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sessoes_acesso (escritorio_id, usuario_id) VALUES ((SELECT escritorio_id FROM usuarios WHERE id = ?), ?)",
                usuarioId, usuarioId)).isInstanceOf(DataIntegrityViolationException.class);

        // um pendente não conta como aberto
        jdbc.update("UPDATE sessoes_acesso SET pendente = true WHERE id = ?", UUID.fromString((String) registro.get("id")));
        assertThat(jdbc.update(
                "INSERT INTO sessoes_acesso (escritorio_id, usuario_id) VALUES ((SELECT escritorio_id FROM usuarios WHERE id = ?), ?)",
                usuarioId, usuarioId)).isEqualTo(1);
    }

    // ---------- regra 3: pendente ----------

    @Test
    void jobMarcaPendenteSoQuemPassouDoTempoSemSinal() {
        var parado = api.registrar(ApiDeTeste.novoEmail());
        var semSinalNenhum = api.registrar(ApiDeTeste.novoEmail());
        var comSinalRecente = api.registrar(ApiDeTeste.novoEmail());
        var idParado = atualId(parado.accessToken());
        var idSemSinal = atualId(semSinalNenhum.accessToken());
        var idRecente = atualId(comSinalRecente.accessToken());

        // parado: sinal há 20 min; semSinalNenhum: aberto há 20 min sem nenhum sinal (usa o início);
        // comSinalRecente: aberto há 20 min mas com sinal há 1 min
        var agora = Instant.now();
        voltarNoTempo(idParado, agora.minus(Duration.ofMinutes(30)), agora.minus(Duration.ofMinutes(20)));
        voltarNoTempo(idSemSinal, agora.minus(Duration.ofMinutes(20)), null);
        voltarNoTempo(idRecente, agora.minus(Duration.ofMinutes(20)), agora.minus(Duration.ofMinutes(1)));

        assertThat(servico.marcarPendentes(agora.minus(Duration.ofMinutes(15)))).isGreaterThanOrEqualTo(2);

        for (var token : List.of(parado.accessToken(), semSinalNenhum.accessToken())) {
            assertThat(api.get(BASE + "/atual", token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        var pendente = registroDoHistorico(parado.accessToken(), idParado);
        assertThat(pendente).containsEntry("pendente", true);
        assertThat(pendente.get("fim")).isNull();
        assertThat(registroDoHistorico(semSinalNenhum.accessToken(), idSemSinal)).containsEntry("pendente", true);

        assertThat(api.get(BASE + "/atual", comSinalRecente.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registroDoHistorico(comSinalRecente.accessToken(), idRecente)).containsEntry("pendente", false);
    }

    @Test
    void sinalDeRegistroPendenteDa409ERefreshDePendenteDa401() {
        var email = ApiDeTeste.novoEmail();
        var admin = api.registrar(email);
        var id = atualId(admin.accessToken());
        tornarPendente(id);

        assertThat(api.post(BASE + "/sinal", admin.accessToken(), null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var refresh = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", admin.refreshToken()));
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh.getBody().get("detail")).isEqualTo("Sessão encerrada");

        // o login abre um registro novo; o pendente continua aguardando o gestor
        var novo = loginCompleto(email);
        assertThat(api.get(BASE + "/atual", novo.accessToken()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registroDoHistorico(novo.accessToken(), id)).containsEntry("pendente", true);
    }

    @Test
    void tokenSemSidGanhaRegistroNoRefreshEFechaOAbertoAnterior() {
        var email = ApiDeTeste.novoEmail();
        var admin = api.registrar(email);
        var idAntigo = atualId(admin.accessToken());

        // sessão anterior à V5: refresh token sem registro de ponto
        jdbc.update("UPDATE refresh_tokens SET sessao_acesso_id = NULL WHERE usuario_id = (SELECT id FROM usuarios WHERE email = ?)",
                email);

        var renovada = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", admin.refreshToken()));
        assertThat(renovada.getStatusCode()).isEqualTo(HttpStatus.OK);
        var token = (String) renovada.getBody().get("accessToken");

        var idNovo = atualId(token);
        assertThat(idNovo).isNotEqualTo(idAntigo);
        assertThat(registroDoHistorico(token, idAntigo).get("fim")).isNotNull();
        assertThat(api.getLista(BASE, token).getBody()).hasSize(2);
    }

    @Test
    void agendadorTemTravaDeJobs() throws NoSuchMethodException {
        assertThat(AgendadorPonto.class.getMethod("marcarPendentes").isAnnotationPresent(SchedulerLock.class)).isTrue();
    }

    // ---------- CLIENTE ----------

    @Test
    void clienteNaoUsaOPontoEAsRotasDao403() {
        var admin = api.registrar(ApiDeTeste.novoEmail());
        var empresaId = api.criarEmpresa(admin.accessToken(), "Empresa Cliente Ltda", "11.222.333/0001-81");
        var emailCliente = ApiDeTeste.novoEmail();
        var criado = api.post("/api/v1/usuarios", admin.accessToken(), Map.of("nome", "Cliente", "email", emailCliente,
                "perfil", "CLIENTE", "senha", ApiDeTeste.SENHA, "empresaId", empresaId));
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var cliente = loginCompleto(emailCliente);
        assertThat(api.get(BASE + "/atual", cliente.accessToken()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post(BASE + "/sinal", cliente.accessToken(), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusDoGet(BASE, cliente.accessToken())).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusDoGet(BASE + "/equipe", cliente.accessToken())).isEqualTo(HttpStatus.FORBIDDEN);

        // nem o login nem o refresh abrem registro para o cliente
        assertThat(api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", cliente.refreshToken()))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessoes_acesso WHERE usuario_id = (SELECT id FROM usuarios WHERE email = ?)",
                Integer.class, emailCliente)).isZero();

        // a equipe só lista funcionários
        var equipe = api.getLista(BASE + "/equipe", admin.accessToken()).getBody();
        assertThat(equipe).extracting(l -> l.get("usuarioNome")).containsExactly("Admin");
    }

    // ---------- regra 5: equipe e ajuste ----------

    @Test
    void equipeEAjusteSoParaAdminEGestorEIsoladosPorEscritorio() {
        var adminA = api.registrar(ApiDeTeste.novoEmail());
        var adminB = api.registrar(ApiDeTeste.novoEmail());
        var operadorA = api.criarOperador(adminA.accessToken(), List.of());
        var gestorA = tokenDeNovoUsuario(adminA.accessToken(), "GESTOR");
        var operadorId = (String) atual(operadorA).get("usuarioId");
        var registroA = (String) atual(adminA.accessToken()).get("id");
        tornarPendente(UUID.fromString(registroA));

        // OPERADOR não vê equipe, nem histórico de outro, nem ajusta
        assertThat(statusDoGet(BASE + "/equipe", operadorA)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusDoGet(BASE + "/usuarios/" + operadorId, operadorA)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.patch(BASE + "/" + registroA + "/saida", operadorA, Map.of("fim", Instant.now().toString()))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN e GESTOR veem os três funcionários do escritório A, com o pendente contado
        for (var token : List.of(adminA.accessToken(), gestorA)) {
            var equipe = api.getLista(BASE + "/equipe", token);
            assertThat(equipe.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(equipe.getBody()).hasSize(3);
            assertThat(equipe.getBody()).filteredOn(l -> "Admin".equals(l.get("usuarioNome")))
                    .singleElement().satisfies(l -> assertThat(((Number) l.get("pendentes")).longValue()).isEqualTo(1));
            assertThat(api.getLista(BASE + "/usuarios/" + operadorId, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // escritório B não enxerga nem ajusta nada do A
        var equipeB = api.getLista(BASE + "/equipe", adminB.accessToken()).getBody();
        assertThat(equipeB).hasSize(1);
        assertThat(statusDoGet(BASE + "/usuarios/" + operadorId, adminB.accessToken()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.patch(BASE + "/" + registroA + "/saida", adminB.accessToken(),
                Map.of("fim", Instant.now().toString())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ajusteValidaOFimEAudita() {
        var admin = api.registrar(ApiDeTeste.novoEmail());
        var gestor = tokenDeNovoUsuario(admin.accessToken(), "GESTOR");
        var alvo = api.criarOperador(admin.accessToken(), List.of());
        var id = atualId(alvo);
        var alvoId = (String) atual(alvo).get("usuarioId");
        var gestorId = (String) atual(gestor).get("usuarioId");
        var inicio = Instant.now().minus(Duration.ofHours(3)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        voltarNoTempo(id, inicio, inicio.plus(Duration.ofMinutes(10)));
        var url = BASE + "/" + id + "/saida";

        // só pendente: o registro ainda está aberto
        assertThat(api.patch(url, gestor, Map.of("fim", Instant.now().minus(Duration.ofHours(1)).toString()))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        tornarPendente(id);

        var antes = api.patch(url, gestor, Map.of("fim", inicio.minusSeconds(1).toString()));
        assertThat(antes.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(campos(antes.getBody())).containsKey("fim");

        var futuro = api.patch(url, gestor, Map.of("fim", Instant.now().plus(Duration.ofHours(1)).toString()));
        assertThat(futuro.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(campos(futuro.getBody())).containsKey("fim");

        assertThat(api.patch(url, gestor, Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE acao = 'ponto.ajustar_saida' AND entidade_id = ?",
                Integer.class, id.toString())).isZero();

        var fim = inicio.plus(Duration.ofHours(2));
        var ok = api.patch(url, gestor, Map.of("fim", fim.toString()));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).containsEntry("pendente", false);
        assertThat(Instant.parse((String) ok.getBody().get("fim"))).isEqualTo(fim);
        assertThat(((Number) ok.getBody().get("duracaoMs")).longValue()).isEqualTo(Duration.ofHours(2).toMillis());

        var auditoria = jdbc.queryForMap(
                "SELECT usuario_id::text AS usuario, dados->>'usuarioId' AS alvo, dados->>'fim' AS fim FROM auditoria "
                        + "WHERE acao = 'ponto.ajustar_saida' AND entidade = 'sessao_acesso' AND entidade_id = ?",
                id.toString());
        assertThat(auditoria).containsEntry("usuario", gestorId).containsEntry("alvo", alvoId)
                .containsEntry("fim", fim.toString());

        // já não está mais pendente
        assertThat(api.patch(url, gestor, Map.of("fim", fim.toString())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void totaisPorDiaNoFusoDoEscritorioDividindoNaMeiaNoite() {
        var admin = api.registrar(ApiDeTeste.novoEmail());
        var usuarioId = UUID.fromString((String) atual(admin.accessToken()).get("usuarioId"));

        // escritório em America/Sao_Paulo (UTC-3): 10/03 22:00 → 11/03 02:00 locais = 4h divididas em 2h + 2h
        inserirFechado(usuarioId, Instant.parse("2026-03-11T01:00:00Z"), Instant.parse("2026-03-11T05:00:00Z"));
        // 31/03 23:00 → 01/04 01:00 locais: cruza a virada do mês (1h em cada mês)
        inserirFechado(usuarioId, Instant.parse("2026-04-01T02:00:00Z"), Instant.parse("2026-04-01T04:00:00Z"));
        // 11/03 08:00 → 09:30 locais: soma no mesmo dia do registro anterior
        inserirFechado(usuarioId, Instant.parse("2026-03-11T11:00:00Z"), Instant.parse("2026-03-11T12:30:00Z"));

        var marco = linhaDe(api.getLista(BASE + "/equipe?mes=2026-03", admin.accessToken()).getBody(), usuarioId);
        assertThat(totaisPorDia(marco)).containsExactly(
                Map.entry("2026-03-10", Duration.ofHours(2).toMillis()),
                Map.entry("2026-03-11", Duration.ofHours(2).plusMinutes(90).toMillis()),
                Map.entry("2026-03-31", Duration.ofHours(1).toMillis()));
        assertThat(((Number) marco.get("totalMesMs")).longValue())
                .isEqualTo(Duration.ofHours(6).plusMinutes(30).toMillis());

        var abril = linhaDe(api.getLista(BASE + "/equipe?mes=2026-04", admin.accessToken()).getBody(), usuarioId);
        assertThat(totaisPorDia(abril)).containsExactly(Map.entry("2026-04-01", Duration.ofHours(1).toMillis()));

        // a lista do usuário no mês traz os registros que tocam o mês, mais recentes primeiro
        var registros = api.getLista(BASE + "/usuarios/" + usuarioId + "?mes=2026-03", admin.accessToken()).getBody();
        assertThat(registros).hasSize(3);
        assertThat(registros.get(0).get("inicio")).isEqualTo("2026-04-01T02:00:00Z");

        assertThat(statusDoGet(BASE + "/equipe?mes=03-2026", admin.accessToken()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pendenteContaSoAteOUltimoSinalNoTotal() {
        var admin = api.registrar(ApiDeTeste.novoEmail());
        var registro = atual(admin.accessToken());
        var id = UUID.fromString((String) registro.get("id"));
        var usuarioId = UUID.fromString((String) registro.get("usuarioId"));
        var inicio = Instant.now().minus(Duration.ofHours(5));
        voltarNoTempo(id, inicio, inicio.plus(Duration.ofMinutes(45)));
        tornarPendente(id);

        var lista = api.getLista(BASE + "/usuarios/" + usuarioId, admin.accessToken()).getBody();
        assertThat(lista).hasSize(1);
        assertThat(((Number) lista.get(0).get("duracaoMs")).longValue()).isEqualTo(Duration.ofMinutes(45).toMillis());
    }

    // ---------- apoio ----------

    private Sessao loginCompleto(String email) {
        var resposta = api.post("/api/v1/auth/login", null, Map.of("email", email, "senha", ApiDeTeste.SENHA));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Sessao((String) resposta.getBody().get("accessToken"), (String) resposta.getBody().get("refreshToken"));
    }

    private String tokenDeNovoUsuario(String tokenAdmin, String perfil) {
        var email = ApiDeTeste.novoEmail();
        var resposta = api.post("/api/v1/usuarios", tokenAdmin, Map.of("nome", perfil, "email", email,
                "perfil", perfil, "senha", ApiDeTeste.SENHA));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return api.login(email);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> campos(Map<String, Object> problema) {
        return (Map<String, Object>) problema.get("campos");
    }

    private HttpStatusCode statusDoGet(String url, String token) {
        var h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Object.class).getStatusCode();
    }

    private Map<String, Object> atual(String token) {
        var resposta = api.get(BASE + "/atual", token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private UUID atualId(String token) {
        return UUID.fromString((String) atual(token).get("id"));
    }

    private Map<String, Object> registroDoHistorico(String token, UUID id) {
        return api.getLista(BASE, token).getBody().stream()
                .filter(r -> id.toString().equals(r.get("id"))).findFirst().orElseThrow();
    }

    private void voltarNoTempo(UUID id, Instant inicio, Instant ultimoSinal) {
        jdbc.update("UPDATE sessoes_acesso SET inicio = ?, ultimo_sinal = ? WHERE id = ?",
                Timestamp.from(inicio), ultimoSinal == null ? null : Timestamp.from(ultimoSinal), id);
    }

    private void tornarPendente(UUID id) {
        jdbc.update("UPDATE sessoes_acesso SET pendente = true WHERE id = ?", id);
    }

    private void inserirFechado(UUID usuarioId, Instant inicio, Instant fim) {
        jdbc.update("INSERT INTO sessoes_acesso (escritorio_id, usuario_id, inicio, fim) "
                + "VALUES ((SELECT escritorio_id FROM usuarios WHERE id = ?), ?, ?, ?)",
                usuarioId, usuarioId, Timestamp.from(inicio), Timestamp.from(fim));
    }

    private static Map<String, Object> linhaDe(List<Map<String, Object>> equipe, UUID usuarioId) {
        return equipe.stream().filter(l -> usuarioId.toString().equals(l.get("usuarioId"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map.Entry<String, Long>> totaisPorDia(Map<String, Object> linha) {
        return ((List<Map<String, Object>>) linha.get("dias")).stream()
                .map(d -> Map.entry((String) d.get("data"), ((Number) d.get("totalMs")).longValue()))
                .toList();
    }
}
