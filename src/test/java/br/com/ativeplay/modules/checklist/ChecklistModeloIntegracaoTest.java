package br.com.ativeplay.modules.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ativeplay.ApiDeTeste;
import br.com.ativeplay.IntegracaoTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** Checklist modelo do escritório, cópia para empresas novas e regras de conclusão do checklist. */
class ChecklistModeloIntegracaoTest extends IntegracaoTest {

    private static final String MODELO = "/api/v1/checklist-modelo";

    private ApiDeTeste api;
    private String admin;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(http);
        admin = api.registrar(ApiDeTeste.novoEmail()).accessToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminMantemOModeloComSubitensEReordena() {
        var a = api.post(MODELO, admin, corpo("Contrato assinado", null, "Ler", "Assinar"));
        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(a.getBody()).containsEntry("titulo", "Contrato assinado").containsEntry("prioridade", "MEDIA");
        assertThat((List<Map<String, Object>>) a.getBody().get("subitens")).extracting(s -> s.get("texto"))
                .containsExactly("Ler", "Assinar");
        var b = api.post(MODELO, admin, corpo("Acessos", 5));
        var idA = (String) a.getBody().get("id");
        var idB = (String) b.getBody().get("id");

        assertThat(api.getLista(MODELO, admin).getBody()).extracting(i -> i.get("id")).containsExactly(idA, idB);

        var reordenado = http.exchange(MODELO + "/ordem", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("ids", List.of(idB, idA)), cabecalho(admin)), Object.class);
        assertThat(reordenado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.getLista(MODELO, admin).getBody()).extracting(i -> i.get("id")).containsExactly(idB, idA);

        // PUT substitui o item e os sub-itens (o que não vem é removido)
        var edicao = api.put(MODELO + "/" + idA, admin, corpo("Contrato revisado", 3, "Só um passo"));
        assertThat(edicao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edicao.getBody()).containsEntry("titulo", "Contrato revisado").containsEntry("prazoDias", 3);
        assertThat((List<Map<String, Object>>) edicao.getBody().get("subitens")).hasSize(1);

        assertThat(api.delete(MODELO + "/" + idB, admin).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.getLista(MODELO, admin).getBody()).hasSize(1);
        assertThat(api.put(MODELO + "/" + idB, admin, corpo("x", null)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void prazoDiasForaDoLimiteERejeitado() {
        assertThat(api.post(MODELO, admin, corpo("Item", 366)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.post(MODELO, admin, corpo("Item", -1)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void gestorEditaMasOperadorEClienteSaoBarrados() {
        var gestor = usuario("GESTOR", Map.of());
        assertThat(api.post(MODELO, gestor, corpo("Do gestor", null)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(api.getLista(MODELO, gestor).getStatusCode()).isEqualTo(HttpStatus.OK);

        var empresa = api.criarEmpresa(admin, "Cliente Ltda", "11444777000161");
        var operador = api.criarOperador(admin, List.of(java.util.UUID.fromString(empresa)));
        assertThat(api.get(MODELO, operador).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post(MODELO, operador, corpo("Do operador", null)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var cliente = usuario("CLIENTE", Map.of("empresaId", empresa));
        assertThat(api.get(MODELO, cliente).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post(MODELO, cliente, corpo("Do cliente", null)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void modeloDeOutroEscritorioNaoApareceNemEEditavel() {
        var id = (String) api.post(MODELO, admin, corpo("Meu item", null)).getBody().get("id");
        var outro = api.registrar(ApiDeTeste.novoEmail()).accessToken();

        assertThat(api.getLista(MODELO, outro).getBody()).isEmpty();
        assertThat(api.put(MODELO + "/" + id, outro, corpo("Invadido", null)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.delete(MODELO + "/" + id, outro).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void empresaNovaNasceComCopiaDoModeloEMudarOModeloNaoAlteraACopia() {
        var idComPrazo = (String) api.post(MODELO, admin, corpo("Acessos bancários", 5, "Itaú", "Santander"))
                .getBody().get("id");
        api.post(MODELO, admin, corpo("Contrato assinado", null));

        var empresa = api.criarEmpresa(admin, "Padaria Ltda", "11444777000161");
        var copia = api.getLista(checklist(empresa), admin).getBody();

        assertThat(copia).hasSize(2);
        var acessos = copia.stream().filter(i -> "Acessos bancários".equals(i.get("titulo"))).findFirst().orElseThrow();
        assertThat(acessos.get("prazo")).isEqualTo(hoje().plusDays(5).toString());
        assertThat(acessos.get("responsavel")).isNull();
        assertThat(acessos.get("concluido")).isEqualTo(false);
        assertThat((List<Map<String, Object>>) acessos.get("subitens")).extracting(s -> s.get("texto"))
                .containsExactly("Itaú", "Santander");
        var contrato = copia.stream().filter(i -> "Contrato assinado".equals(i.get("titulo"))).findFirst().orElseThrow();
        assertThat(contrato.get("prazo")).isNull();

        // alterar ou apagar o modelo depois não muda a cópia, e empresa anterior não recebe nada novo
        api.put(MODELO + "/" + idComPrazo, admin, corpo("Título novo", 30));
        api.delete(MODELO + "/" + idComPrazo, admin);
        assertThat(api.getLista(checklist(empresa), admin).getBody()).extracting(i -> i.get("titulo"))
                .containsExactlyInAnyOrder("Acessos bancários", "Contrato assinado");
        api.post(MODELO, admin, corpo("Item posterior", null));
        assertThat(api.getLista(checklist(empresa), admin).getBody()).hasSize(2);
    }

    @Test
    void empresaSemModeloNasceComChecklistVazio() {
        var empresa = api.criarEmpresa(admin, "Sem Modelo Ltda", "11444777000161");
        assertThat(api.getLista(checklist(empresa), admin).getBody()).isEmpty();
    }

    @Test
    void conversaoDoCrmTambemCopiaOModelo() {
        api.post(MODELO, admin, corpo("Certificado digital", 7, "Conferir validade"));
        var etapa = api.getLista("/api/v1/crm/etapas", admin).getBody().stream()
                .filter(e -> "Lead".equals(e.get("nome"))).findFirst().orElseThrow();
        var oportunidade = api.post("/api/v1/crm/oportunidades", admin, Map.of("etapaId", etapa.get("id"),
                "titulo", "Implantação", "razaoSocial", "Lucro Azul Ltda", "cnpj", "11222333000181",
                "contatoEmail", "contato@exemplo.com.br", "valorMensal", 1500.00));
        var conversao = api.post("/api/v1/crm/oportunidades/" + oportunidade.getBody().get("id") + "/converter",
                admin, null);
        assertThat(conversao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var copia = api.getLista(checklist((String) conversao.getBody().get("empresaId")), admin).getBody();
        assertThat(copia).hasSize(1);
        assertThat(copia.getFirst()).containsEntry("titulo", "Certificado digital")
                .containsEntry("prazo", hoje().plusDays(7).toString());
        assertThat(copia.getFirst().get("responsavel")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void concluirComSubitemAbertoDaConflitoESemSubitensConclui() {
        var empresa = api.criarEmpresa(admin, "Cliente Ltda", "11444777000161");
        var comSubitens = (Map<String, Object>) api.post(checklist(empresa), admin, Map.of("titulo", "Extratos",
                "subitens", List.of(Map.of("texto", "Itaú", "concluido", false),
                        Map.of("texto", "Santander", "concluido", false)))).getBody();
        var id = (String) comSubitens.get("id");
        var subs = (List<Map<String, Object>>) comSubitens.get("subitens");
        var sub1 = (String) subs.get(0).get("id");
        var sub2 = (String) subs.get(1).get("id");

        var bloqueado = api.post(checklist(empresa) + "/" + id + "/concluir", admin, null);
        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(bloqueado.getBody()).containsEntry("detail", "Conclua todos os sub-itens antes de concluir o item");

        api.patch(checklist(empresa) + "/" + id + "/subitens/" + sub1, admin, Map.of("concluido", true));
        assertThat(api.post(checklist(empresa) + "/" + id + "/concluir", admin, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        api.patch(checklist(empresa) + "/" + id + "/subitens/" + sub2, admin, Map.of("concluido", true));
        var concluido = api.post(checklist(empresa) + "/" + id + "/concluir", admin, null);
        assertThat(concluido.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(concluido.getBody()).containsEntry("concluido", true);

        // item concluído: marcar ou desmarcar sub-item exige reabrir antes
        var desmarcar = api.patch(checklist(empresa) + "/" + id + "/subitens/" + sub1, admin, Map.of("concluido", false));
        assertThat(desmarcar.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(desmarcar.getBody()).containsEntry("detail", "Reabra o item antes de alterar os sub-itens");
        var marcar = api.patch(checklist(empresa) + "/" + id + "/subitens/" + sub1, admin, Map.of("concluido", true));
        assertThat(marcar.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(marcar.getBody()).containsEntry("detail", "Reabra o item antes de alterar os sub-itens");

        // PUT em item concluído: sub-item aberto = 409; só título/prazo com sub-itens concluídos é permitido
        var abrindo = api.put(checklist(empresa) + "/" + id, admin, Map.of("titulo", "Extratos",
                "subitens", List.of(Map.of("id", sub1, "texto", "Itaú", "concluido", false),
                        Map.of("id", sub2, "texto", "Santander", "concluido", true))));
        assertThat(abrindo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(abrindo.getBody()).containsEntry("detail", "Reabra o item antes de alterar os sub-itens");
        var novoAberto = api.put(checklist(empresa) + "/" + id, admin, Map.of("titulo", "Extratos",
                "subitens", List.of(Map.of("id", sub1, "texto", "Itaú", "concluido", true),
                        Map.of("texto", "Novo", "concluido", false))));
        assertThat(novoAberto.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var editado = api.put(checklist(empresa) + "/" + id, admin, Map.of("titulo", "Extratos de setembro",
                "prazo", "2031-01-10",
                "subitens", List.of(Map.of("id", sub1, "texto", "Itaú", "concluido", true),
                        Map.of("id", sub2, "texto", "Santander", "concluido", true))));
        assertThat(editado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(editado.getBody()).containsEntry("titulo", "Extratos de setembro").containsEntry("concluido", true);

        api.post(checklist(empresa) + "/" + id + "/reabrir", admin, null);
        assertThat(api.patch(checklist(empresa) + "/" + id + "/subitens/" + sub1, admin, Map.of("concluido", false))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        var simples = (String) api.post(checklist(empresa), admin, Map.of("titulo", "Sem passos")).getBody().get("id");
        assertThat(api.post(checklist(empresa) + "/" + simples + "/concluir", admin, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---------- apoio ----------

    private static HttpHeaders cabecalho(String token) {
        var h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private static String checklist(String empresaId) {
        return "/api/v1/empresas/" + empresaId + "/checklist";
    }

    /** Fuso padrão do escritório recém-registrado. */
    private LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private static Map<String, Object> corpo(String titulo, Integer prazoDias, String... subitens) {
        var corpo = new java.util.HashMap<String, Object>();
        corpo.put("titulo", titulo);
        corpo.put("prazoDias", prazoDias);
        corpo.put("subitens", java.util.Arrays.stream(subitens).map(t -> Map.of("texto", t)).toList());
        return corpo;
    }

    /** Cria um usuário do perfil informado no escritório do admin e devolve o token dele. */
    private String usuario(String perfil, Map<String, Object> extras) {
        var email = ApiDeTeste.novoEmail();
        var dados = new java.util.HashMap<String, Object>(extras);
        dados.putAll(Map.of("nome", perfil, "email", email, "perfil", perfil, "senha", ApiDeTeste.SENHA));
        assertThat(api.post("/api/v1/usuarios", admin, dados).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return api.login(email);
    }
}
