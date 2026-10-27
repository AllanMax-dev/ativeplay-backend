package br.com.ativeplay.modules.tarefa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ativeplay.ApiDeTeste;
import br.com.ativeplay.IntegracaoTest;
import br.com.ativeplay.modules.tarefa.repository.OcorrenciaTarefaRepository;
import br.com.ativeplay.modules.tarefa.service.GeracaoOcorrenciasService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/** Fluxos de tarefas ponta a ponta: recorrência, geração, cronômetro, SLA e controle de concorrência. */
class TarefasIntegracaoTest extends IntegracaoTest {

    private static final String DATA = "2030-01-07";

    @Autowired
    private OcorrenciaTarefaRepository ocorrencias;
    @Autowired
    private GeracaoOcorrenciasService geracao;
    @Autowired
    private TransactionTemplate transacao;

    private ApiDeTeste api;
    private String token;
    private String empresaId;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(http);
        token = api.registrar(ApiDeTeste.novoEmail()).accessToken();
        empresaId = api.criarEmpresa(token, "Tarefas Ltda", "11444777000161");
    }

    @Test
    @SuppressWarnings("unchecked")
    void definicaoRecorrenteGeraOcorrenciaNaDataENaoDuplicaAoGerarDeNovo() {
        var definicao = api.post("/api/v1/tarefas/definicoes", token, Map.of("empresaId", empresaId,
                "nome", "Conciliação bancária", "recorrencia", "DIARIA", "apenasDiasUteis", false,
                "horarioLimite", "18:00", "prioridade", "ALTA", "tempoEstimadoMin", 30));
        assertThat(definicao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var definicaoId = (String) definicao.getBody().get("id");

        var primeira = api.post("/api/v1/tarefas/ocorrencias/gerar", token, Map.of("empresaId", empresaId, "data", DATA));
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(primeira.getBody()).containsEntry("criadas", 1);

        var listagem = api.get("/api/v1/tarefas/ocorrencias?empresaId=" + empresaId + "&data=" + DATA, token);
        var dados = listagem.getBody().get("dados");
        assertThat((List<?>) dados).hasSize(1);
        var ocorrencia = ((List<Map<String, Object>>) dados).getFirst();
        assertThat(ocorrencia)
                .containsEntry("definicaoId", definicaoId)
                .containsEntry("titulo", "Conciliação bancária")
                .containsEntry("status", "PENDENTE")
                .containsEntry("prioridade", "ALTA");

        // gerar de novo a mesma data é idempotente (restrição única definicao_id + data)
        assertThat(api.post("/api/v1/tarefas/ocorrencias/gerar", token, Map.of("empresaId", empresaId, "data", DATA))
                .getBody()).containsEntry("criadas", 0);
    }

    @Test
    void cronometroIniciaPausaEConcluiESoUmRodaPorPessoa() {
        var a = criarAvulsa("Tarefa A", null);
        var b = criarAvulsa("Tarefa B", null);

        assertThat(api.post("/api/v1/tarefas/ocorrencias/" + a + "/iniciar", token, null).getBody())
                .containsEntry("status", "EM_ANDAMENTO");

        // iniciar outra tarefa pausa a que estava rodando
        assertThat(api.post("/api/v1/tarefas/ocorrencias/" + b + "/iniciar", token, null).getBody())
                .containsEntry("status", "EM_ANDAMENTO");
        var pausadaAutomaticamente = ocorrencia(a);
        assertThat(pausadaAutomaticamente).containsEntry("status", "PAUSADA");
        assertThat(pausadaAutomaticamente.get("cronometroInicio")).isNull();

        var pausada = api.post("/api/v1/tarefas/ocorrencias/" + b + "/pausar", token, null);
        assertThat(pausada.getBody()).containsEntry("status", "PAUSADA");
        assertThat(pausada.getBody().get("cronometroInicio")).isNull();
        assertThat(((Number) pausada.getBody().get("tempoGastoAtualMs")).longValue()).isGreaterThanOrEqualTo(0);

        // pausar tarefa que não está em andamento é conflito
        assertThat(api.post("/api/v1/tarefas/ocorrencias/" + a + "/pausar", token, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        // iniciar tarefa que já está rodando é conflito
        api.post("/api/v1/tarefas/ocorrencias/" + b + "/iniciar", token, null);
        assertThat(api.post("/api/v1/tarefas/ocorrencias/" + b + "/iniciar", token, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        var concluida = api.post("/api/v1/tarefas/ocorrencias/" + b + "/concluir", token, null);
        assertThat(concluida.getBody()).containsEntry("status", "CONCLUIDA").containsEntry("slaViolado", false);
        assertThat(concluida.getBody().get("concluidaEm")).isNotNull();
        // tarefa concluída não pode voltar a rodar sem reabrir
        assertThat(api.post("/api/v1/tarefas/ocorrencias/" + b + "/iniciar", token, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void concluirDepoisDoPrazoMarcaSlaViolado() {
        var atrasada = criarAvulsa("Entrega atrasada", Instant.now().minus(2, ChronoUnit.HOURS));
        api.post("/api/v1/tarefas/ocorrencias/" + atrasada + "/iniciar", token, null);

        var concluida = api.post("/api/v1/tarefas/ocorrencias/" + atrasada + "/concluir", token, null);
        assertThat(concluida.getBody()).containsEntry("status", "CONCLUIDA").containsEntry("slaViolado", true);
    }

    @Test
    void varreduraDeSlaMarcaTarefaAbertaComPrazoVencido() {
        var aberta = criarAvulsa("Vence antes da varredura", Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(ocorrencia(aberta)).containsEntry("slaViolado", false);

        geracao.varrerSla();

        assertThat(ocorrencia(aberta))
                .containsEntry("slaViolado", true)
                .containsEntry("status", "PENDENTE");
    }

    @Test
    void edicaoComVersaoAntigaLancaConflitoDeVersao() {
        var id = criarAvulsa("Versionada", null);
        // leitura feita antes da outra edição: fica com a versão 0
        var lida = transacao.execute(s -> ocorrencias.findById(UUID.fromString(id)).orElseThrow());
        assertThat(lida).isNotNull();

        var editada = api.patch("/api/v1/tarefas/ocorrencias/" + id, token, Map.of("titulo", "Editada primeiro"));
        assertThat(editada.getStatusCode()).isEqualTo(HttpStatus.OK);

        // a segunda gravação, com a versão 0 já superada, é rejeitada pelo @Version (o handler global responde 409)
        lida.setTitulo("Editada por último, com versão antiga");
        assertThatThrownBy(() -> ocorrencias.save(lida))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void respostaDoPatchTraziaAVersaoPersistida() {
        var id = criarAvulsa("Versão da resposta", null);

        var editada = api.patch("/api/v1/tarefas/ocorrencias/" + id, token, Map.of("titulo", "Nova versão"));
        assertThat(editada.getBody()).containsEntry("versao", ocorrencia(id).get("versao"));
    }

    @Test
    void respostaDasAcoesDeStatusTraziaAVersaoPersistida() {
        var id = criarAvulsa("Versões das ações", null);
        var base = "/api/v1/tarefas/ocorrencias/" + id + "/";

        for (var acao : new String[] {"iniciar", "pausar", "concluir", "reabrir", "cancelar"}) {
            var resposta = api.post(base + acao, token, null);
            assertThat(resposta.getStatusCode()).as(acao).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).as(acao).containsEntry("versao", ocorrencia(id).get("versao"));
        }
    }

    // ---------- apoio ----------

    private String criarAvulsa(String titulo, Instant prazo) {
        var corpo = new HashMap<String, Object>(Map.of("empresaId", empresaId, "titulo", titulo, "data", DATA));
        if (prazo != null) corpo.put("prazo", prazo.toString());
        var resposta = api.post("/api/v1/tarefas/ocorrencias", token, corpo);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resposta.getBody().get("id");
    }

    /** O GET de detalhe devolve a tarefa aninhada em "ocorrencia" (junto com comentários e anexos). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> ocorrencia(String id) {
        var resposta = api.get("/api/v1/tarefas/ocorrencias/" + id, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resposta.getBody().get("ocorrencia");
    }
}
