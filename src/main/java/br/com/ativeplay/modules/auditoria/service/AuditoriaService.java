package br.com.ativeplay.modules.auditoria.service;

import br.com.ativeplay.modules.auditoria.domain.Auditoria;
import br.com.ativeplay.modules.auditoria.repository.AuditoriaRepository;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro da trilha de auditoria. Participa da transação da operação auditada: se a
 * operação for desfeita, o registro também é, e a trilha só contém o que aconteceu.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository repositorio;

    /** Ex.: registrar(usuario, "empresa.criar", "empresa", id, empresaId, "Empresa X cadastrada") */
    @Transactional
    public void registrar(UsuarioAutenticado u, String acao, String entidade, Object entidadeId, UUID empresaId,
            String descricao) {
        registrar(u.escritorioId(), u.id(), acao, entidade, entidadeId, empresaId, descricao, null);
    }

    @Transactional
    public void registrar(UUID escritorioId, UUID usuarioId, String acao, String entidade, Object entidadeId,
            UUID empresaId, String descricao, Map<String, Object> dados) {
        var a = new Auditoria();
        a.setEscritorioId(escritorioId);
        a.setUsuarioId(usuarioId);
        a.setEmpresaId(empresaId);
        a.setAcao(acao);
        a.setEntidade(entidade);
        a.setEntidadeId(entidadeId == null ? null : entidadeId.toString());
        a.setDescricao(descricao);
        a.setDados(dados);
        repositorio.save(a);
    }
}
