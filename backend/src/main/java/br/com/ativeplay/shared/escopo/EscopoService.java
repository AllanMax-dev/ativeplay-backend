package br.com.ativeplay.shared.escopo;

import br.com.ativeplay.modules.empresa.domain.Empresa;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.setor.repository.SetorRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioEmpresaRepository;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.Perfil;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escopo de dados do usuário dentro do tenant:
 * - ADMIN e GESTOR: todas as empresas do escritório;
 * - OPERADOR: apenas as empresas da sua carteira (usuario_empresas);
 * - CLIENTE: apenas a própria empresa.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EscopoService {

    private final EmpresaRepository empresas;
    private final UsuarioEmpresaRepository carteira;
    private final UsuarioRepository usuarios;
    private final SetorRepository setores;

    /** Empresas permitidas, ou vazio quando o usuário vê todas as do escritório. */
    public Optional<List<UUID>> empresasPermitidas(UsuarioAutenticado u) {
        if (u.veTodasEmpresas()) return Optional.empty();
        if (u.isCliente()) return Optional.of(u.empresaId() == null ? List.of() : List.of(u.empresaId()));
        return Optional.of(carteira.findEmpresaIdsByUsuarioId(u.id()));
    }

    /** Garante que a empresa existe no tenant e está acessível ao usuário. */
    public Empresa garantirEmpresa(UsuarioAutenticado u, UUID empresaId) {
        var empresa = empresas.findByIdAndEscritorioId(empresaId, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Empresa"));
        garantirAcesso(u, empresaId);
        return empresa;
    }

    /** Lança 403 se a empresa estiver fora da carteira do usuário. */
    public void garantirAcesso(UsuarioAutenticado u, UUID empresaId) {
        empresasPermitidas(u).ifPresent(permitidas -> {
            if (!permitidas.contains(empresaId)) throw ApiException.proibido("Empresa fora da sua carteira");
        });
    }

    /** Garante que um usuário referenciado (ex.: responsável) é da equipe do mesmo escritório. */
    public void garantirUsuarioDaEquipe(UsuarioAutenticado u, UUID usuarioId) {
        if (usuarioId == null) return;
        if (!usuarios.existsByIdAndEscritorioIdAndPerfilNot(usuarioId, u.escritorioId(), Perfil.CLIENTE)) {
            throw ApiException.naoEncontrado("Usuário responsável");
        }
    }

    public void garantirSetor(UsuarioAutenticado u, UUID setorId) {
        if (setorId == null) return;
        if (!setores.existsByIdAndEscritorioId(setorId, u.escritorioId())) {
            throw ApiException.naoEncontrado("Setor");
        }
    }
}
