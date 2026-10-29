package br.com.ativeplay.modules.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.ApiDeTeste;
import br.com.ativeplay.IntegracaoTest;
import br.com.ativeplay.modules.senha.repository.SenhaEmpresaRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Recursos "dentro do cliente": filtro por empresa em Tarefas do dia, contador geral
 * (sessões), checklist de observações pontuais e senhas salvas.
 */
class AreaDoClienteIntegracaoTest extends IntegracaoTest {

    private static final String DATA = "2030-01-07";

    @Autowired
    private SenhaEmpresaRepository senhasRepo;

    private ApiDeTeste api;
    private String token;
    private String empresaA;
    private String empresaB;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(http);
        token = api.registrar(ApiDeTeste.novoEmail()).accessToken();
        empresaA = api.criarEmpresa(token, "Alfa Ltda", "11444777000161");
        empresaB = api.criarEmpresa(token, "Beta Ltda", "11222333000181");
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtroPorEmpresaMostraApenasACarteiraDoOperadorComContagens() {
        criarAvulsa(empresaA, "Conciliação A");
        criarAvulsa(empresaA, "Pagamentos A");
        criarAvulsa(empresaB, "Conciliação B");
        var operador = api.criarOperador(token, List.of(UUID.fromString(empresaA)));

        var doOperador = api.getLista("/api/v1/tarefas/ocorrencias/empresas?data=" + DATA, operador);
        assertThat(doOperador.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(doOperador.getBody()).hasSize(1);
        assertThat(doOperador.getBody().getFirst())
                .containsEntry("id", empresaA)
                .containsEntry("naCarteira", true)
                .containsEntry("total", 2)
                .containsEntry("abertas", 2);

        // admin vê todas; com carteira=true, só as da própria carteira (nenhuma)
        assertThat(api.getLista("/api/v1/tarefas/ocorrencias/empresas?data=" + DATA, token).getBody()).hasSize(2);
        assertThat(api.getLista("/api/v1/tarefas/ocorrencias/empresas?carteira=true&data=" + DATA, token).getBody())
                .isEmpty();

        var filtradas = api.get("/api/v1/tarefas/ocorrencias?data=" + DATA + "&empresaId=" + empresaB, token);
        assertThat((List<Map<String, Object>>) filtradas.getBody().get("dados"))
                .extracting(m -> m.get("titulo")).containsExactly("Conciliação B");

        // empresa fora da carteira do operador é proibida no filtro
        assertThat(api.get("/api/v1/tarefas/ocorrencias?data=" + DATA + "&empresaId=" + empresaB, operador)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void contadorGeralSomaAsSessoesDoDiaETrocaDeEmpresaEncerraAAnterior() {
        assertThat(api.post("/api/v1/sessoes/iniciar", token, Map.of("empresaId", empresaA)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var segunda = api.post("/api/v1/sessoes/iniciar", token, Map.of("empresaId", empresaB));
        assertThat(segunda.getBody()).containsEntry("empresaId", empresaB);

        var resumo = api.get("/api/v1/sessoes/resumo", token);
        assertThat(resumo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<String, Object>) resumo.getBody().get("atual")).containsEntry("empresaId", empresaB);
        assertThat((List<?>) resumo.getBody().get("empresas")).hasSize(2);

        api.post("/api/v1/sessoes/encerrar", token, null);
        assertThat(api.get("/api/v1/sessoes/resumo", token).getBody().get("atual")).isNull();
        assertThat(api.get("/api/v1/sessoes/rentabilidade", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checklistCriaComSubitensConcluiEReabre() {
        var criado = api.post("/api/v1/empresas/" + empresaA + "/checklist", token, Map.of(
                "titulo", "Pendente de envio do acesso bancário", "prazo", "2030-01-10", "prioridade", "ALTA",
                "subitens", List.of(Map.of("texto", "Token Santander", "concluido", false),
                        Map.of("texto", "Senha internet banking", "concluido", false))));
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var id = (String) criado.getBody().get("id");
        var subitens = (List<Map<String, Object>>) criado.getBody().get("subitens");
        assertThat(subitens).hasSize(2);

        var marcado = api.patch("/api/v1/empresas/" + empresaA + "/checklist/" + id + "/subitens/"
                + subitens.getFirst().get("id"), token, Map.of("concluido", true));
        assertThat(marcado.getBody()).containsEntry("subitensConcluidos", 1);

        // PUT mantém o sub-item com id, remove o ausente e cria o novo
        var editado = api.put("/api/v1/empresas/" + empresaA + "/checklist/" + id, token, Map.of(
                "titulo", "Pendente de acesso bancário",
                "subitens", List.of(Map.of("id", subitens.getFirst().get("id"), "texto", "Token Santander",
                        "concluido", true), Map.of("texto", "Token Itaú", "concluido", false))));
        assertThat((List<Map<String, Object>>) editado.getBody().get("subitens"))
                .extracting(m -> m.get("texto")).containsExactly("Token Santander", "Token Itaú");

        assertThat(api.post("/api/v1/empresas/" + empresaA + "/checklist/" + id + "/concluir", token, null).getBody())
                .containsEntry("concluido", true);
        assertThat(api.getLista("/api/v1/empresas/" + empresaA + "/checklist?situacao=PENDENTES", token).getBody())
                .isEmpty();
        assertThat(api.post("/api/v1/empresas/" + empresaA + "/checklist/" + id + "/reabrir", token, null).getBody())
                .containsEntry("concluido", false);

        // checklist da empresa A não aparece na B
        assertThat(api.getLista("/api/v1/empresas/" + empresaB + "/checklist", token).getBody()).isEmpty();
        assertThat(api.delete("/api/v1/empresas/" + empresaA + "/checklist/" + id, token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void senhaFicaCifradaNaoApareceNaListagemESoSaiAoRevelar() {
        var criada = api.post("/api/v1/empresas/" + empresaA + "/senhas", token, Map.of(
                "acesso", "Banco Itaú", "categoria", "BANCO", "usuario", "Ag 1234 / CC 5678-9",
                "senha", "S3nh@Secreta", "url", "https://www.itau.com.br"));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criada.getBody()).containsEntry("possuiSenha", true).doesNotContainKey("senha");
        var id = (String) criada.getBody().get("id");

        var gravada = senhasRepo.findById(UUID.fromString(id)).orElseThrow();
        assertThat(gravada.getSenhaCifrada()).isNotBlank().doesNotContain("S3nh@Secreta");

        assertThat(api.getLista("/api/v1/empresas/" + empresaA + "/senhas?busca=itau", token).getBody()).hasSize(1);
        assertThat(api.get("/api/v1/empresas/" + empresaA + "/senhas/" + id + "/senha", token).getBody())
                .containsEntry("senha", "S3nh@Secreta");

        // editar sem senha mantém a atual
        api.put("/api/v1/empresas/" + empresaA + "/senhas/" + id, token, Map.of("acesso", "Itaú Empresas"));
        assertThat(api.get("/api/v1/empresas/" + empresaA + "/senhas/" + id + "/senha", token).getBody())
                .containsEntry("senha", "S3nh@Secreta");

        // operador sem a empresa na carteira não acessa
        var operador = api.criarOperador(token, List.of(UUID.fromString(empresaB)));
        assertThat(api.get("/api/v1/empresas/" + empresaA + "/senhas/" + id + "/senha", operador).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // URL inválida é recusada
        assertThat(api.post("/api/v1/empresas/" + empresaA + "/senhas", token,
                Map.of("acesso", "X", "url", "javascript:alert(1)")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void criarAvulsa(String empresaId, String titulo) {
        var r = api.post("/api/v1/tarefas/ocorrencias", token, Map.of("empresaId", empresaId, "titulo", titulo,
                "data", DATA));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
