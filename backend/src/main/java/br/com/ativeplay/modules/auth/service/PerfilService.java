package br.com.ativeplay.modules.auth.service;

import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.escritorio.repository.PermissaoPerfilRepository;
import br.com.ativeplay.modules.setor.repository.SetorRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioSetorRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta o "quem sou eu" consumido pelo frontend logo após o login. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfilService {

    private final UsuarioRepository usuarios;
    private final EscritorioRepository escritorios;
    private final EmpresaRepository empresas;
    private final PermissaoPerfilRepository permissoes;
    private final UsuarioSetorRepository usuarioSetores;
    private final SetorRepository setores;

    public Map<String, Object> perfil(UsuarioAutenticado u) {
        var usuario = usuarios.findById(u.id()).orElseThrow(() -> ApiException.naoEncontrado("Usuário"));
        var escritorio = escritorios.findById(u.escritorioId()).orElseThrow();

        var resposta = new LinkedHashMap<String, Object>();
        resposta.put("id", usuario.getId());
        resposta.put("nome", usuario.getNome());
        resposta.put("email", usuario.getEmail());
        resposta.put("perfil", usuario.getPerfil());
        resposta.put("cargo", usuario.getCargo());
        resposta.put("escritorio", Map.of("id", escritorio.getId(), "nome", escritorio.getNome(), "slug",
                escritorio.getSlug(), "plano", escritorio.getPlano(), "fusoHorario", escritorio.getFusoHorario()));
        if (usuario.getEmpresaId() != null) {
            empresas.findById(usuario.getEmpresaId()).ifPresent(e -> resposta.put("empresa",
                    Map.of("id", e.getId(), "razaoSocial", e.getRazaoSocial())));
        }
        var setorIds = usuarioSetores.findSetorIdsByUsuarioId(u.id());
        resposta.put("setores", setores.findAllById(setorIds).stream()
                .map(s -> Map.of("id", s.getId(), "nome", s.getNome()))
                .toList());
        resposta.put("permissoes", u.isAdmin()
                ? "TODAS"
                : permissoes.findByEscritorioIdAndPerfil(u.escritorioId(), u.perfil()).stream()
                        .map(p -> Map.of("modulo", p.getModulo(), "escrita", p.isEscrita()))
                        .toList());
        return resposta;
    }
}
