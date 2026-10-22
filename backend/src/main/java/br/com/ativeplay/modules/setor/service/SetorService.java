package br.com.ativeplay.modules.setor.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.setor.domain.Setor;
import br.com.ativeplay.modules.setor.repository.SetorRepository;
import br.com.ativeplay.modules.setor.web.dto.SetorDtos.SetorRequest;
import br.com.ativeplay.modules.setor.web.dto.SetorDtos.SetorResposta;
import br.com.ativeplay.modules.usuario.repository.UsuarioSetorRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SetorService {

    private final SetorRepository setores;
    private final UsuarioSetorRepository usuarioSetores;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<SetorResposta> listar(UsuarioAutenticado u) {
        return setores.findByEscritorioIdOrderByOrdemAsc(u.escritorioId()).stream().map(this::resposta).toList();
    }

    public SetorResposta criar(UsuarioAutenticado u, SetorRequest req) {
        var nome = req.nome().trim();
        if (setores.existsByEscritorioIdAndNomeIgnoreCase(u.escritorioId(), nome)) {
            throw ApiException.conflito("Já existe um setor com este nome");
        }
        var s = new Setor();
        s.setEscritorioId(u.escritorioId());
        s.setNome(nome);
        s.setCor(req.cor());
        s.setOrdem(setores.findByEscritorioIdOrderByOrdemAsc(u.escritorioId()).size());
        setores.save(s);
        auditoria.registrar(u, "setor.criar", "setor", s.getId(), null, "Setor " + nome + " criado");
        return resposta(s);
    }

    public SetorResposta atualizar(UsuarioAutenticado u, UUID id, SetorRequest req) {
        var s = buscar(u, id);
        var nome = req.nome().trim();
        if (!s.getNome().equalsIgnoreCase(nome) && setores.existsByEscritorioIdAndNomeIgnoreCase(u.escritorioId(), nome)) {
            throw ApiException.conflito("Já existe um setor com este nome");
        }
        s.setNome(nome);
        s.setCor(req.cor());
        s.setAtivo(true);
        auditoria.registrar(u, "setor.editar", "setor", s.getId(), null, "Setor " + nome + " atualizado");
        return resposta(s);
    }

    /** Exclusão lógica: o setor some das seleções, mas o histórico continua referenciando-o. */
    public void desativar(UsuarioAutenticado u, UUID id) {
        var s = buscar(u, id);
        s.setAtivo(false);
        auditoria.registrar(u, "setor.desativar", "setor", s.getId(), null, "Setor " + s.getNome() + " desativado");
    }

    public List<SetorResposta> reordenar(UsuarioAutenticado u, List<UUID> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw ApiException.invalido("Lista de setores com itens repetidos");
        var encontrados = setores.findByIdInAndEscritorioId(ids, u.escritorioId());
        if (encontrados.size() != ids.size()) throw ApiException.naoEncontrado("Setor");
        encontrados.forEach(s -> s.setOrdem(ids.indexOf(s.getId())));
        auditoria.registrar(u, "setor.reordenar", "setor", null, null, "Ordem dos setores alterada");
        return listar(u);
    }

    private Setor buscar(UsuarioAutenticado u, UUID id) {
        return setores.findByIdAndEscritorioId(id, u.escritorioId()).orElseThrow(() -> ApiException.naoEncontrado("Setor"));
    }

    private SetorResposta resposta(Setor s) {
        return new SetorResposta(s.getId(), s.getNome(), s.getCor(), s.getOrdem(), s.isAtivo(),
                usuarioSetores.countBySetorId(s.getId()));
    }
}
