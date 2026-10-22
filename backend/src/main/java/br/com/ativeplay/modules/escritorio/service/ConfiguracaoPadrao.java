package br.com.ativeplay.modules.escritorio.service;

import br.com.ativeplay.modules.crm.domain.EtapaCrm;
import br.com.ativeplay.modules.crm.domain.TipoEtapaCrm;
import br.com.ativeplay.modules.crm.repository.EtapaCrmRepository;
import br.com.ativeplay.modules.escritorio.domain.PermissaoPerfil;
import br.com.ativeplay.modules.escritorio.repository.PermissaoPerfilRepository;
import br.com.ativeplay.modules.setor.domain.Setor;
import br.com.ativeplay.modules.setor.repository.SetorRepository;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuração aplicada a todo escritório novo (e pelo seed): matriz de permissões,
 * setores e etapas do funil de CRM. Cada escritório pode ajustá-las depois.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracaoPadrao {

    private enum Nivel { LEITURA, ESCRITA }

    /** ADMIN não precisa de linhas: tem acesso total. */
    private static final Map<Perfil, Map<Modulo, Nivel>> PERMISSOES = Map.of(
            Perfil.GESTOR, Map.of(
                    Modulo.PAINEL, Nivel.ESCRITA, Modulo.EMPRESAS, Nivel.ESCRITA, Modulo.TAREFAS, Nivel.ESCRITA,
                    Modulo.CRM, Nivel.ESCRITA, Modulo.DOCUMENTOS, Nivel.ESCRITA, Modulo.RELATORIOS, Nivel.ESCRITA,
                    Modulo.INTEGRACOES, Nivel.ESCRITA, Modulo.AUDITORIA, Nivel.LEITURA, Modulo.USUARIOS, Nivel.LEITURA,
                    Modulo.CONFIGURACOES, Nivel.LEITURA),
            Perfil.OPERADOR, Map.of(
                    Modulo.PAINEL, Nivel.LEITURA, Modulo.EMPRESAS, Nivel.LEITURA, Modulo.TAREFAS, Nivel.ESCRITA,
                    Modulo.DOCUMENTOS, Nivel.ESCRITA, Modulo.CRM, Nivel.LEITURA));

    private static final List<String[]> SETORES = List.of(
            new String[] {"Financeiro", "#2563EB"},
            new String[] {"Contábil", "#7C3AED"},
            new String[] {"Fiscal", "#D97706"},
            new String[] {"Departamento Pessoal", "#059669"});

    private static final List<Object[]> ETAPAS_CRM = List.of(
            new Object[] {"Lead", "#64748B", TipoEtapaCrm.ABERTA},
            new Object[] {"Qualificação", "#0EA5E9", TipoEtapaCrm.ABERTA},
            new Object[] {"Proposta", "#6366F1", TipoEtapaCrm.ABERTA},
            new Object[] {"Negociação", "#F59E0B", TipoEtapaCrm.ABERTA},
            new Object[] {"Ganho", "#10B981", TipoEtapaCrm.GANHA},
            new Object[] {"Perdido", "#EF4444", TipoEtapaCrm.PERDIDA});

    private final PermissaoPerfilRepository permissoes;
    private final SetorRepository setores;
    private final EtapaCrmRepository etapas;

    @Transactional
    public void aplicar(UUID escritorioId) {
        var linhas = new ArrayList<PermissaoPerfil>();
        PERMISSOES.forEach((perfil, modulos) -> modulos.forEach((modulo, nivel) ->
                linhas.add(new PermissaoPerfil(escritorioId, perfil, modulo, nivel == Nivel.ESCRITA))));
        permissoes.saveAll(linhas);

        for (int i = 0; i < SETORES.size(); i++) {
            var s = new Setor();
            s.setEscritorioId(escritorioId);
            s.setNome(SETORES.get(i)[0]);
            s.setCor(SETORES.get(i)[1]);
            s.setOrdem(i);
            setores.save(s);
        }
        for (int i = 0; i < ETAPAS_CRM.size(); i++) {
            var e = new EtapaCrm();
            e.setEscritorioId(escritorioId);
            e.setNome((String) ETAPAS_CRM.get(i)[0]);
            e.setCor((String) ETAPAS_CRM.get(i)[1]);
            e.setTipo((TipoEtapaCrm) ETAPAS_CRM.get(i)[2]);
            e.setOrdem(i);
            etapas.save(e);
        }
    }
}
