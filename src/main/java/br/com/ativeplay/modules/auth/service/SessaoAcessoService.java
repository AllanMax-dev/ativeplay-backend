package br.com.ativeplay.modules.auth.service;

import br.com.ativeplay.modules.auth.domain.SessaoAcesso;
import br.com.ativeplay.modules.auth.repository.SessaoAcessoRepository;
import br.com.ativeplay.modules.usuario.domain.Usuario;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessaoAcessoService {

    private final SessaoAcessoRepository repository;

    public record SessaoAcessoResponse(
            UUID id,
            Instant inicio,
            Instant fim,
            long duracaoMs
    ) {}

    @Transactional
    public SessaoAcesso iniciar(Usuario usuario) {
        var sessao = new SessaoAcesso();

        sessao.setUsuarioId(usuario.getId());
        sessao.setEscritorioId(usuario.getEscritorioId());
        sessao.setInicio(Instant.now());

        return repository.save(sessao);
    }

    @Transactional
    public void encerrar(UUID id) {
        if (id == null) return;

        repository.findById(id).ifPresent(sessao -> {
            if (sessao.getFim() == null) {
                sessao.setFim(Instant.now());
            }
        });
    }

    @Transactional
    public void encerrarTodas(UUID usuarioId) {
        var agora = Instant.now();

        repository.findByUsuarioIdAndFimIsNull(usuarioId)
                .forEach(sessao -> sessao.setFim(agora));
    }

    @Transactional(readOnly = true)
    public Optional<SessaoAcessoResponse> atual(
            UsuarioAutenticado usuario,
            UUID sessaoId
    ) {
        if (sessaoId == null) {
            return Optional.empty();
        }

        return repository
                .findByIdAndUsuarioIdAndEscritorioId(
                        sessaoId,
                        usuario.id(),
                        usuario.escritorioId()
                )
                .filter(sessao -> sessao.getFim() == null)
                .map(sessao -> montar(sessao, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<SessaoAcessoResponse> historico(
            UsuarioAutenticado usuario
    ) {
        var agora = Instant.now();

        return repository
                .findByUsuarioIdAndEscritorioIdOrderByInicioDesc(
                        usuario.id(),
                        usuario.escritorioId(),
                        PageRequest.of(0, 100)
                )
                .stream()
                .map(sessao -> montar(sessao, agora))
                .toList();
    }

    private static SessaoAcessoResponse montar(
            SessaoAcesso sessao,
            Instant agora
    ) {
        var fim = sessao.getFim() == null
                ? agora
                : sessao.getFim();

        long duracao = Math.max(
                0,
                Duration.between(
                        sessao.getInicio(),
                        fim
                ).toMillis()
        );

        return new SessaoAcessoResponse(
                sessao.getId(),
                sessao.getInicio(),
                sessao.getFim(),
                duracao
        );
    }
}
