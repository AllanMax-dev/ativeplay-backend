package br.com.ativeplay.modules.escritorio.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.escritorio.domain.Escritorio;
import br.com.ativeplay.modules.escritorio.domain.PermissaoPerfil;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.escritorio.repository.PermissaoPerfilRepository;
import br.com.ativeplay.modules.escritorio.web.dto.EscritorioDtos.AtualizarEscritorioRequest;
import br.com.ativeplay.modules.escritorio.web.dto.EscritorioDtos.EscritorioResposta;
import br.com.ativeplay.modules.escritorio.web.dto.EscritorioDtos.NivelPermissao;
import br.com.ativeplay.modules.escritorio.web.dto.EscritorioDtos.PermissaoItem;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.Modulo;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cnpj;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EscritorioService {

    /** Perfis configuráveis na matriz (ADMIN tem acesso total e não aparece). */
    private static final List<Perfil> PERFIS_CONFIGURAVEIS = List.of(Perfil.GESTOR, Perfil.OPERADOR, Perfil.CLIENTE);

    private final EscritorioRepository escritorios;
    private final PermissaoPerfilRepository permissoes;
    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public EscritorioResposta atual(UsuarioAutenticado u) {
        return resposta(buscar(u));
    }

    public EscritorioResposta atualizar(UsuarioAutenticado u, AtualizarEscritorioRequest req) {
        var e = buscar(u);
        try {
            ZoneId.of(req.fusoHorario());
        } catch (DateTimeException ex) {
            throw ApiException.invalido("Fuso horário inválido: " + req.fusoHorario());
        }
        e.setNome(req.nome().trim());
        e.setCnpj(req.cnpj() == null || req.cnpj().isBlank() ? null : Cnpj.normalizar(req.cnpj()));
        e.setFusoHorario(req.fusoHorario());
        auditoria.registrar(u, "escritorio.editar", "escritorio", e.getId(), null, "Dados do escritório atualizados");
        return resposta(e);
    }

    /** Matriz completa perfil × módulo, com NENHUM onde não há linha. */
    @Transactional(readOnly = true)
    public List<PermissaoItem> permissoes(UsuarioAutenticado u) {
        var existentes = new HashMap<String, PermissaoPerfil>();
        permissoes.findByEscritorioId(u.escritorioId()).forEach(p -> existentes.put(p.getPerfil() + ":" + p.getModulo(), p));
        var matriz = new ArrayList<PermissaoItem>();
        for (var perfil : PERFIS_CONFIGURAVEIS) {
            for (var modulo : Modulo.values()) {
                var p = existentes.get(perfil + ":" + modulo);
                var nivel = p == null ? NivelPermissao.NENHUM : p.isEscrita() ? NivelPermissao.ESCRITA : NivelPermissao.LEITURA;
                matriz.add(new PermissaoItem(perfil, modulo, nivel));
            }
        }
        return matriz;
    }

    /** Substitui toda a matriz do escritório. Itens ausentes ficam sem acesso. */
    public List<PermissaoItem> substituirPermissoes(UsuarioAutenticado u, List<PermissaoItem> itens) {
        var vistos = new HashSet<String>();
        var linhas = new ArrayList<PermissaoPerfil>();
        for (var item : itens) {
            if (item.perfil() == Perfil.ADMIN) throw ApiException.invalido("O perfil ADMIN tem acesso total e não é configurável");
            if (!vistos.add(item.perfil() + ":" + item.modulo())) {
                throw ApiException.invalido("Permissão duplicada: " + item.perfil() + " / " + item.modulo());
            }
            if (item.nivel() != NivelPermissao.NENHUM) {
                linhas.add(new PermissaoPerfil(u.escritorioId(), item.perfil(), item.modulo(),
                        item.nivel() == NivelPermissao.ESCRITA));
            }
        }
        permissoes.deleteByEscritorioId(u.escritorioId());
        permissoes.flush();
        permissoes.saveAll(linhas);
        auditoria.registrar(u.escritorioId(), u.id(), "permissao.editar", "escritorio", u.escritorioId(), null,
                "Matriz de permissões atualizada", Map.of("linhas", linhas.size()));
        return permissoes(u);
    }

    private Escritorio buscar(UsuarioAutenticado u) {
        return escritorios.findById(u.escritorioId()).orElseThrow(() -> ApiException.naoEncontrado("Escritório"));
    }

    private EscritorioResposta resposta(Escritorio e) {
        return new EscritorioResposta(e.getId(), e.getNome(), e.getSlug(), e.getCnpj(),
                e.getCnpj() == null ? null : Cnpj.formatar(e.getCnpj()), e.getPlano(), e.getFusoHorario(), e.isAtivo(),
                e.getCriadoEm(), usuarios.countByEscritorioId(e.getId()), empresas.countByEscritorioId(e.getId()));
    }
}
