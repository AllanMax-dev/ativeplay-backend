package br.com.ativeplay.seed;

import br.com.ativeplay.config.AtivePlayProperties;
import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.checklist.domain.ChecklistItem;
import br.com.ativeplay.modules.checklist.domain.ChecklistModeloItem;
import br.com.ativeplay.modules.checklist.domain.ChecklistModeloSubitem;
import br.com.ativeplay.modules.checklist.domain.ChecklistSubitem;
import br.com.ativeplay.modules.checklist.repository.ChecklistItemRepository;
import br.com.ativeplay.modules.checklist.repository.ChecklistModeloItemRepository;
import br.com.ativeplay.modules.crm.domain.AtividadeCrm;
import br.com.ativeplay.modules.crm.domain.OportunidadeCrm;
import br.com.ativeplay.modules.crm.domain.TipoAtividadeCrm;
import br.com.ativeplay.modules.crm.domain.TipoEtapaCrm;
import br.com.ativeplay.modules.crm.repository.AtividadeCrmRepository;
import br.com.ativeplay.modules.crm.repository.EtapaCrmRepository;
import br.com.ativeplay.modules.crm.repository.OportunidadeCrmRepository;
import br.com.ativeplay.modules.empresa.domain.Empresa;
import br.com.ativeplay.modules.empresa.domain.RegimeTributario;
import br.com.ativeplay.modules.empresa.domain.StatusEmpresa;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.escritorio.domain.Escritorio;
import br.com.ativeplay.modules.escritorio.domain.Plano;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.escritorio.service.ConfiguracaoPadrao;
import br.com.ativeplay.modules.integracao.domain.Integracao;
import br.com.ativeplay.modules.integracao.domain.ProvedorIntegracao;
import br.com.ativeplay.modules.integracao.repository.IntegracaoRepository;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.senha.domain.CategoriaSenha;
import br.com.ativeplay.modules.senha.domain.SenhaEmpresa;
import br.com.ativeplay.modules.senha.repository.SenhaEmpresaRepository;
import br.com.ativeplay.modules.sessao.domain.SessaoTrabalho;
import br.com.ativeplay.modules.sessao.repository.SessaoTrabalhoRepository;
import br.com.ativeplay.modules.setor.domain.Setor;
import br.com.ativeplay.modules.setor.repository.SetorRepository;
import br.com.ativeplay.modules.tarefa.domain.ComentarioOcorrencia;
import br.com.ativeplay.modules.tarefa.domain.DefinicaoTarefa;
import br.com.ativeplay.modules.tarefa.domain.OcorrenciaTarefa;
import br.com.ativeplay.modules.tarefa.domain.PadraoTarefa;
import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.modules.tarefa.domain.Recorrencia;
import br.com.ativeplay.modules.tarefa.domain.StatusOcorrencia;
import br.com.ativeplay.modules.tarefa.repository.ComentarioOcorrenciaRepository;
import br.com.ativeplay.modules.tarefa.repository.DefinicaoTarefaRepository;
import br.com.ativeplay.modules.tarefa.repository.OcorrenciaTarefaRepository;
import br.com.ativeplay.modules.tarefa.repository.PadraoTarefaRepository;
import br.com.ativeplay.modules.tarefa.service.Recorrencias;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.modules.usuario.domain.UsuarioEmpresa;
import br.com.ativeplay.modules.usuario.domain.UsuarioSetor;
import br.com.ativeplay.modules.usuario.repository.UsuarioEmpresaRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioSetorRepository;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.util.Cnpj;
import br.com.ativeplay.shared.util.Cripto;
import br.com.ativeplay.shared.util.Datas;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de demonstração (fictícios). Ativado pelo perfil "seed": recria o escritório demo
 * a cada inicialização e depois a API segue rodando normalmente.
 *
 * <pre>mvn spring-boot:run -Dspring-boot.run.profiles=dev,seed</pre>
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class DemoSeeder implements ApplicationRunner {

    public static final String SLUG = "lucro-azul-demo";
    public static final String SENHA = "Ativeplay@123";
    private static final int DIAS_HISTORICO = 30;
    private static final String FUSO = "America/Sao_Paulo";

    private final EscritorioRepository escritorios;
    private final ConfiguracaoPadrao configuracaoPadrao;
    private final UsuarioRepository usuarios;
    private final UsuarioSetorRepository usuarioSetores;
    private final UsuarioEmpresaRepository carteira;
    private final SetorRepository setores;
    private final EmpresaRepository empresas;
    private final PadraoTarefaRepository padroes;
    private final DefinicaoTarefaRepository definicoes;
    private final OcorrenciaTarefaRepository ocorrencias;
    private final ComentarioOcorrenciaRepository comentarios;
    private final SessaoTrabalhoRepository sessoes;
    private final EtapaCrmRepository etapas;
    private final OportunidadeCrmRepository oportunidades;
    private final AtividadeCrmRepository atividades;
    private final IntegracaoRepository integracoes;
    private final NotificacaoService notificacoes;
    private final AuditoriaService auditoria;
    private final PasswordEncoder passwordEncoder;
    private final ChecklistItemRepository checklist;
    private final ChecklistModeloItemRepository checklistModelo;
    private final SenhaEmpresaRepository senhasEmpresa;
    private final AtivePlayProperties props;

    /** Gerador com semente fixa: o seed é reproduzível. */
    private final Random random = new Random(20261017);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        escritorios.findBySlug(SLUG).ifPresent(e -> {
            escritorios.delete(e);
            escritorios.flush();
        });

        var escritorio = new Escritorio();
        escritorio.setNome("Lucro Azul Consultoria (demo)");
        escritorio.setSlug(SLUG);
        escritorio.setCnpj(cnpj("112223330001"));
        escritorio.setPlano(Plano.PROFISSIONAL);
        escritorios.save(escritorio);
        var esc = escritorio.getId();
        configuracaoPadrao.aplicar(esc);
        var porNome = setores.findByEscritorioIdOrderByOrdemAsc(esc).stream()
                .collect(java.util.stream.Collectors.toMap(Setor::getNome, Setor::getId));

        // ---------- equipe ----------
        var senhaHash = passwordEncoder.encode(SENHA);
        var admin = usuario(esc, "admin@ativeplay.dev", "Marina Costa", Perfil.ADMIN, "Sócia-diretora", senhaHash, null);
        var gestor = usuario(esc, "gestor@ativeplay.dev", "Rafael Souza", Perfil.GESTOR, "Coordenador de operações", senhaHash, null);
        var op1 = usuario(esc, "operador@ativeplay.dev", "Juliana Lima", Perfil.OPERADOR, "Analista financeira", senhaHash, null);
        var op2 = usuario(esc, "operador2@ativeplay.dev", "Bruno Alves", Perfil.OPERADOR, "Analista contábil", senhaHash, null);
        var equipe = List.of(op1, op2);
        usuarioSetores.saveAll(List.of(
                new UsuarioSetor(op1.getId(), porNome.get("Financeiro")),
                new UsuarioSetor(op2.getId(), porNome.get("Contábil")),
                new UsuarioSetor(op2.getId(), porNome.get("Fiscal")),
                new UsuarioSetor(gestor.getId(), porNome.get("Financeiro"))));

        // ---------- empresas (clientes) ----------
        record DadosEmpresa(String razao, String fantasia, String base, RegimeTributario regime, int honorario) {
        }
        var dados = List.of(
                new DadosEmpresa("Padaria Pão Dourado Ltda", "Pão Dourado", "123456780001", RegimeTributario.SIMPLES_NACIONAL, 1800),
                new DadosEmpresa("Clínica Sorriso Pleno Ltda", "Sorriso Pleno", "234567890001", RegimeTributario.LUCRO_PRESUMIDO, 3200),
                new DadosEmpresa("Tech Norte Sistemas S.A.", "Tech Norte", "12ABC34501DE", RegimeTributario.LUCRO_REAL, 7500),
                new DadosEmpresa("Mercado Bom Preço Eireli", "Bom Preço", "345678900001", RegimeTributario.SIMPLES_NACIONAL, 2400),
                new DadosEmpresa("Construtora Alicerce Ltda", "Alicerce", "456789010001", RegimeTributario.LUCRO_PRESUMIDO, 5200),
                new DadosEmpresa("Studio Bem-Estar Pilates ME", "Bem-Estar", "567890120001", RegimeTributario.MEI, 650));
        var hoje = Datas.hoje(FUSO);
        var listaEmpresas = new ArrayList<Empresa>();
        for (int i = 0; i < dados.size(); i++) {
            var d = dados.get(i);
            var e = new Empresa();
            e.setEscritorioId(esc);
            e.setRazaoSocial(d.razao());
            e.setNomeFantasia(d.fantasia());
            e.setCnpj(cnpj(d.base()));
            e.setRegimeTributario(d.regime());
            e.setStatus(i == 5 ? StatusEmpresa.ONBOARDING : StatusEmpresa.ATIVA);
            e.setEmail("financeiro@" + java.text.Normalizer.normalize(d.fantasia(), java.text.Normalizer.Form.NFD)
                    .replaceAll("[^A-Za-z]", "").toLowerCase() + ".com.br");
            e.setResponsavelId(equipe.get(i % 2).getId());
            e.setHonorarioMensal(BigDecimal.valueOf(d.honorario()));
            e.setDataInicio(hoje.minusDays(400 - i * 60L));
            listaEmpresas.add(empresas.save(e));
            carteira.save(new UsuarioEmpresa(equipe.get(i % 2).getId(), e.getId()));
        }
        usuario(esc, "cliente@ativeplay.dev", "Carlos Mendes", Perfil.CLIENTE, "Proprietário", senhaHash,
                listaEmpresas.get(0).getId());

        // ---------- catálogo de tarefas ----------
        var catalogo = List.of(
                padrao(esc, 0, "Conciliação bancária", porNome.get("Financeiro"), Recorrencia.DIARIA, null, null, "12:00", Prioridade.ALTA, 40),
                padrao(esc, 1, "Contas a pagar do dia", porNome.get("Financeiro"), Recorrencia.DIARIA, null, null, "16:00", Prioridade.ALTA, 30),
                padrao(esc, 2, "Emissão de boletos", porNome.get("Financeiro"), Recorrencia.SEMANAL, List.of(1, 4), null, "17:00", Prioridade.MEDIA, 25),
                padrao(esc, 3, "Fechamento contábil", porNome.get("Contábil"), Recorrencia.MENSAL, null, 5, "18:00", Prioridade.ALTA, 180),
                padrao(esc, 4, "Envio do relatório gerencial", porNome.get("Contábil"), Recorrencia.MENSAL, null, 10, "18:00", Prioridade.MEDIA, 60),
                padrao(esc, 5, "Apuração do DAS", porNome.get("Fiscal"), Recorrencia.MENSAL, null, 18, "18:00", Prioridade.URGENTE, 45),
                padrao(esc, 6, "Folha de pagamento", porNome.get("Departamento Pessoal"), Recorrencia.MENSAL, null, 25, "18:00", Prioridade.ALTA, 120));

        var todasDefinicoes = new ArrayList<DefinicaoTarefa>();
        for (int i = 0; i < listaEmpresas.size(); i++) {
            var empresa = listaEmpresas.get(i);
            int ordem = 0;
            for (var p : catalogo) {
                if (empresa.getRegimeTributario() == RegimeTributario.MEI && p.getNome().equals("Fechamento contábil")) continue;
                var d = new DefinicaoTarefa();
                d.setEscritorioId(esc);
                d.setEmpresaId(empresa.getId());
                d.setPadraoId(p.getId());
                d.setSetorId(p.getSetorId());
                d.setResponsavelId(equipe.get(i % 2).getId());
                d.setNome(p.getNome());
                d.setRecorrencia(p.getRecorrencia());
                d.setDiasSemana(new ArrayList<>(p.getDiasSemana()));
                d.setDiaMes(p.getDiaMes());
                d.setApenasDiasUteis(p.isApenasDiasUteis());
                d.setHorarioLimite(p.getHorarioLimite());
                d.setPrioridade(p.getPrioridade());
                d.setTempoEstimadoMin(p.getTempoEstimadoMin());
                d.setVisivelCliente(p.getNome().equals("Envio do relatório gerencial") || p.getNome().equals("Apuração do DAS"));
                d.setOrdem(ordem++);
                todasDefinicoes.add(d);
            }
        }
        definicoes.saveAll(todasDefinicoes);

        // ---------- ocorrências: histórico de 30 dias + hoje ----------
        var agora = Instant.now();
        var lista = new ArrayList<OcorrenciaTarefa>();
        for (int dia = -DIAS_HISTORICO; dia <= 0; dia++) {
            var data = hoje.plusDays(dia);
            for (var def : todasDefinicoes) {
                if (!Recorrencias.deveOcorrer(def, data)) continue;
                var o = ocorrencia(esc, def, data);
                if (dia < 0) simularHistorico(o, def);
                lista.add(o);
            }
        }
        ocorrencias.saveAll(lista);

        // tarefa avulsa em andamento, com cronômetro rodando
        var avulsa = new OcorrenciaTarefa();
        avulsa.setEscritorioId(esc);
        avulsa.setEmpresaId(listaEmpresas.get(0).getId());
        avulsa.setSetorId(porNome.get("Financeiro"));
        avulsa.setResponsavelId(op1.getId());
        avulsa.setTitulo("Conferir notas de fornecedor em atraso");
        avulsa.setDescricao("Cliente pediu revisão das notas do mês anterior.");
        avulsa.setData(hoje);
        avulsa.setPrazo(Datas.instanteLocal(hoje, "18:00", FUSO));
        avulsa.setPrioridade(Prioridade.URGENTE);
        avulsa.setStatus(StatusOcorrencia.EM_ANDAMENTO);
        avulsa.setIniciadaEm(agora.minus(Duration.ofMinutes(50)));
        avulsa.setCronometroInicio(agora.minus(Duration.ofMinutes(20)));
        avulsa.setTempoGastoMs(Duration.ofMinutes(30).toMillis());
        avulsa.setVisivelCliente(true);
        ocorrencias.save(avulsa);
        comentarios.saveAll(List.of(
                comentario(esc, avulsa, gestor, "Prioridade alta: o cliente tem reunião com o banco amanhã."),
                comentario(esc, avulsa, op1, "Já separei as notas, falta conferir 3 fornecedores.")));

        var sessao = new SessaoTrabalho();
        sessao.setEscritorioId(esc);
        sessao.setUsuarioId(op1.getId());
        sessao.setEmpresaId(listaEmpresas.get(0).getId());
        sessao.setInicio(agora.minus(Duration.ofMinutes(55)));
        sessoes.save(sessao);

        // histórico do contador geral (sessões encerradas) para o comparativo tempo x honorário
        for (int dia = 1; dia <= 20; dia++) {
            var data = hoje.minusDays(dia);
            if (!Datas.diaUtil(data)) continue;
            for (int i = 0; i < listaEmpresas.size() - 1; i++) {
                var operador = equipe.get(i % 2);
                var inicio = Datas.instanteLocal(data, String.format("%02d:%02d", 8 + i, (dia * 7) % 50), FUSO);
                var historico = new SessaoTrabalho();
                historico.setEscritorioId(esc);
                historico.setUsuarioId(operador.getId());
                historico.setEmpresaId(listaEmpresas.get(i).getId());
                historico.setInicio(inicio);
                historico.setFim(inicio.plus(Duration.ofMinutes(25L + ((long) (i + 1) * dia * 13) % 70)));
                sessoes.save(historico);
            }
        }

        // ---------- checklist do cliente ----------
        var paoDourado = listaEmpresas.get(0).getId();
        var sorriso = listaEmpresas.get(1).getId();
        checklist.save(itemChecklist(esc, paoDourado, 0, "Pendente de envio do acesso bancário",
                "Cliente ficou de enviar o token do Santander. Sem ele não conseguimos fazer a conciliação.",
                hoje.plusDays(2), Prioridade.ALTA, op1, gestor, List.of("Token Santander", "Senha internet banking")));
        checklist.save(itemChecklist(esc, paoDourado, 1, "Faltam notas fiscais de entrada de setembro",
                "Fornecedores: Moinho Sul e Laticínios Serra.", hoje.minusDays(1), Prioridade.URGENTE, op1, op1,
                List.of()));
        var concluido = itemChecklist(esc, paoDourado, 2, "Atualizar contato do financeiro do cliente",
                "Novo responsável: Carla (financeiro@paodourado.com.br).", null, Prioridade.BAIXA, op1, op1, List.of());
        concluido.concluir(op1.getId(), agora.minus(Duration.ofDays(1)));
        checklist.save(concluido);
        checklist.save(itemChecklist(esc, sorriso, 0, "Certificado digital vence em 15 dias",
                "Avisar o cliente para renovar o e-CNPJ A1.", hoje.plusDays(15), Prioridade.MEDIA, op2, gestor,
                List.of("Avisar cliente", "Receber novo arquivo .pfx", "Atualizar na aba de senhas")));

        // ---------- checklist modelo (empresas novas nascem com uma cópia) ----------
        itemModelo(esc, 0, "Certificado digital válido", 7, List.of("Conferir validade", "Solicitar renovação se necessário"));
        itemModelo(esc, 1, "Acessos bancários recebidos", 5, List.of("Extrato Itaú", "Extrato Santander"));
        itemModelo(esc, 2, "Contrato assinado", null, List.of());

        // ---------- senhas salvas (fictícias) ----------
        String[][] acessos = {
            {"Banco Itaú", "BANCO", "Ag 1234 / CC 56789-0", "Demo@Itau2026", "https://www.itau.com.br"},
            {"Santander Empresas", "BANCO", "12.345.678/0001-90", "Demo#Sant321", "https://www.santander.com.br"},
            {"Certificado digital A1", "CERTIFICADO", "e-CNPJ Pão Dourado", "Cert@2026", null},
            {"Portal da energia", "PORTAL", "financeiro@paodourado.com.br", "Luz@2026", null},
            {"Prefeitura (NFS-e)", "GOVERNO", "123456", "Nfse@2026", null}};
        for (var a : acessos) {
            var senha = new SenhaEmpresa();
            senha.setEscritorioId(esc);
            senha.setEmpresaId(paoDourado);
            senha.setAcesso(a[0]);
            senha.setCategoria(CategoriaSenha.valueOf(a[1]));
            senha.setUsuario(a[2]);
            senha.setSenhaCifrada(Cripto.cifrar(a[3], props.encryptionKey()));
            senha.setUrl(a[4]);
            senha.setCriadoPorId(op1.getId());
            senha.setAtualizadoPorId(op1.getId());
            senhasEmpresa.save(senha);
        }

        // ---------- CRM ----------
        var etapasPorNome = etapas.findAll().stream().filter(e -> e.getEscritorioId().equals(esc))
                .collect(java.util.stream.Collectors.toMap(e -> e.getNome(), e -> e));
        String[][] funil = {
            {"Restaurante Sabor da Terra", "Lead", "1900", "Indicação"},
            {"Auto Peças Veloz", "Lead", "2600", "Site"},
            {"Escola Pequeno Saber", "Qualificação", "3800", "Evento"},
            {"Farmácia Vida Plena", "Proposta", "4200", "Indicação"},
            {"Transportadora Rota Sul", "Negociação", "9800", "Outbound"},
            {"Ótica Visão Clara", "Negociação", "1500", "Site"},
            {"Agência Pixel Criativo", "Ganho", "2900", "Indicação"},
            {"Hamburgueria Brasa", "Perdido", "1200", "Instagram"}};
        String[] contatos = {"Fernanda Rocha", "Paulo Nunes", "Aline Freitas", "Diego Martins"};
        for (int i = 0; i < funil.length; i++) {
            var etapa = etapasPorNome.get(funil[i][1]);
            var o = new OportunidadeCrm();
            o.setEscritorioId(esc);
            o.setEtapaId(etapa.getId());
            o.setResponsavelId(i % 2 == 0 ? admin.getId() : gestor.getId());
            o.setTitulo("BPO financeiro — " + funil[i][0]);
            o.setRazaoSocial(funil[i][0] + " Ltda");
            o.setCnpj(cnpj(String.valueOf(600000000001L + i * 111111L)));
            o.setContatoNome(contatos[i % contatos.length]);
            o.setContatoEmail("contato" + (i + 1) + "@exemplo.com.br");
            o.setOrigem(funil[i][3]);
            o.setValorMensal(new BigDecimal(funil[i][2]));
            o.setPrevisaoFechamento(hoje.plusDays(7 + i * 5L));
            o.setOrdem(i);
            if (etapa.getTipo() != TipoEtapaCrm.ABERTA) o.setFechadaEm(agora.minus(Duration.ofDays(i + 1L)));
            if (etapa.getTipo() == TipoEtapaCrm.PERDIDA) o.setMotivoPerda("Escolheu um concorrente com preço menor");
            oportunidades.save(o);
            atividades.save(atividade(esc, o, gestor, TipoAtividadeCrm.LIGACAO, "Primeiro contato realizado.", null));
            if (etapa.getTipo() == TipoEtapaCrm.ABERTA) {
                atividades.save(atividade(esc, o, gestor, TipoAtividadeCrm.REUNIAO, "Reunião de diagnóstico",
                        agora.plus(Duration.ofDays(i + 2L))));
            }
        }

        // ---------- integração, notificações e auditoria ----------
        var integracao = new Integracao();
        integracao.setEscritorioId(esc);
        integracao.setEmpresaId(listaEmpresas.get(2).getId());
        integracao.setProvedor(ProvedorIntegracao.OMIE);
        integracao.setConfiguracoes(Map.of("recursos", List.of("contas_pagar", "contas_receber")));
        integracoes.save(integracao);

        notificacoes.notificar(esc, op1.getId(), TipoNotificacao.TAREFA_ATRIBUIDA,
                "Nova tarefa: Conferir notas de fornecedor em atraso", null, "tarefas");
        notificacoes.notificar(esc, op1.getId(), TipoNotificacao.COMENTARIO, "Rafael Souza comentou em uma tarefa", null,
                "tarefas");
        notificacoes.notificar(esc, gestor.getId(), TipoNotificacao.CRM, "Proposta enviada para Farmácia Vida Plena",
                null, "crm");
        auditoria.registrar(esc, admin.getId(), "escritorio.criar", "escritorio", esc, null,
                "Escritório demo criado pelo seed", null);
        listaEmpresas.forEach(e -> auditoria.registrar(esc, admin.getId(), "empresa.criar", "empresa", e.getId(), e.getId(),
                "Empresa " + e.getRazaoSocial() + " cadastrada", null));

        log.info("Seed concluído: {} empresas, {} definições, {} ocorrências, {} oportunidades. Senha: {}",
                listaEmpresas.size(), todasDefinicoes.size(), lista.size() + 1, funil.length, SENHA);
    }

    private static String cnpj(String base) {
        return base + Cnpj.digitos(base);
    }

    private Usuario usuario(java.util.UUID esc, String email, String nome, Perfil perfil, String cargo, String senhaHash,
            java.util.UUID empresaId) {
        var u = usuarios.findByEmail(email).orElseGet(Usuario::new);
        if (u.getId() != null && !u.getEscritorioId().equals(esc)) {
            usuarios.delete(u); // e-mail demo preso a outro escritório: recria no demo
            usuarios.flush();
            u = new Usuario();
        }
        u.setEscritorioId(esc);
        u.setEmail(email);
        u.setNome(nome);
        u.setPerfil(perfil);
        u.setCargo(cargo);
        u.setSenhaHash(senhaHash);
        u.setEmpresaId(empresaId);
        u.setAtivo(true);
        return usuarios.save(u);
    }

    private PadraoTarefa padrao(java.util.UUID esc, int ordem, String nome, java.util.UUID setorId, Recorrencia recorrencia,
            List<Integer> diasSemana, Integer diaMes, String horario, Prioridade prioridade, int estimadoMin) {
        var p = new PadraoTarefa();
        p.setEscritorioId(esc);
        p.setOrdem(ordem);
        p.setNome(nome);
        p.setSetorId(setorId);
        p.setRecorrencia(recorrencia);
        p.setDiasSemana(diasSemana == null ? new ArrayList<>() : new ArrayList<>(diasSemana));
        p.setDiaMes(diaMes);
        p.setHorarioLimite(horario);
        p.setPrioridade(prioridade);
        p.setTempoEstimadoMin(estimadoMin);
        return padroes.save(p);
    }

    private OcorrenciaTarefa ocorrencia(java.util.UUID esc, DefinicaoTarefa def, LocalDate data) {
        var o = new OcorrenciaTarefa();
        o.setEscritorioId(esc);
        o.setEmpresaId(def.getEmpresaId());
        o.setDefinicaoId(def.getId());
        o.setSetorId(def.getSetorId());
        o.setResponsavelId(def.getResponsavelId());
        o.setTitulo(def.getNome());
        o.setData(data);
        o.setPrazo(Datas.instanteLocal(data, def.getHorarioLimite(), FUSO));
        o.setPrioridade(def.getPrioridade());
        o.setTempoEstimadoMin(def.getTempoEstimadoMin());
        o.setVisivelCliente(def.isVisivelCliente());
        return o;
    }

    /** Dias passados: a maioria concluída (parte com atraso) e algumas pendências esquecidas. */
    private void simularHistorico(OcorrenciaTarefa o, DefinicaoTarefa def) {
        double sorte = random.nextDouble();
        if (sorte < 0.06) {
            o.setSlaViolado(true);
            return;
        }
        long estimado = Duration.ofMinutes(def.getTempoEstimadoMin() == null ? 30 : def.getTempoEstimadoMin()).toMillis();
        long gasto = (long) (estimado * (0.5 + random.nextDouble()));
        boolean atrasou = sorte > 0.85;
        long deslocamento = (long) (random.nextDouble() * Duration.ofHours(4).toMillis()) + 60_000;
        var concluidaEm = atrasou ? o.getPrazo().plusMillis(deslocamento) : o.getPrazo().minusMillis(deslocamento);
        o.setStatus(StatusOcorrencia.CONCLUIDA);
        o.setTempoGastoMs(gasto);
        o.setIniciadaEm(concluidaEm.minusMillis(gasto + (long) (random.nextDouble() * 3_600_000)));
        o.setConcluidaEm(concluidaEm);
        o.setConcluidaPorId(def.getResponsavelId());
        o.setSlaViolado(atrasou);
    }

    private void itemModelo(java.util.UUID esc, int ordem, String titulo, Integer prazoDias, List<String> passos) {
        var item = new ChecklistModeloItem();
        item.setEscritorioId(esc);
        item.setOrdem(ordem);
        item.setTitulo(titulo);
        item.setPrazoDias(prazoDias);
        for (int i = 0; i < passos.size(); i++) {
            var sub = new ChecklistModeloSubitem();
            sub.setItem(item);
            sub.setTexto(passos.get(i));
            sub.setOrdem(i);
            item.getSubitens().add(sub);
        }
        checklistModelo.save(item);
    }

    private static ChecklistItem itemChecklist(java.util.UUID esc, java.util.UUID empresaId, int ordem, String titulo,
            String observacoes, LocalDate prazo, Prioridade prioridade, Usuario responsavel, Usuario autor,
            List<String> passos) {
        var item = new ChecklistItem();
        item.setEscritorioId(esc);
        item.setEmpresaId(empresaId);
        item.setOrdem(ordem);
        item.setTitulo(titulo);
        item.setObservacoes(observacoes);
        item.setPrazo(prazo);
        item.setPrioridade(prioridade);
        item.setResponsavelId(responsavel.getId());
        item.setCriadoPorId(autor.getId());
        for (int i = 0; i < passos.size(); i++) {
            var sub = new ChecklistSubitem();
            sub.setItem(item);
            sub.setTexto(passos.get(i));
            sub.setConcluido(i == 0 && passos.size() > 1);
            sub.setOrdem(i);
            item.getSubitens().add(sub);
        }
        return item;
    }

    private static ComentarioOcorrencia comentario(java.util.UUID esc, OcorrenciaTarefa o, Usuario autor, String texto) {
        var c = new ComentarioOcorrencia();
        c.setEscritorioId(esc);
        c.setOcorrenciaId(o.getId());
        c.setAutorId(autor.getId());
        c.setTexto(texto);
        return c;
    }

    private static AtividadeCrm atividade(java.util.UUID esc, OportunidadeCrm o, Usuario autor, TipoAtividadeCrm tipo,
            String descricao, Instant agendadaPara) {
        var a = new AtividadeCrm();
        a.setEscritorioId(esc);
        a.setOportunidadeId(o.getId());
        a.setAutorId(autor.getId());
        a.setTipo(tipo);
        a.setDescricao(descricao);
        a.setAgendadaPara(agendadaPara);
        return a;
    }
}
