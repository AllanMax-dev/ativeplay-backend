package br.com.ativeplay.modules.notificacao.service;

import br.com.ativeplay.modules.notificacao.domain.Notificacao;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.repository.NotificacaoRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

    public record NotificacaoResposta(UUID id, TipoNotificacao tipo, String titulo, String mensagem, String link,
            Instant lidaEm, Instant criadoEm) {
        static NotificacaoResposta de(Notificacao n) {
            return new NotificacaoResposta(n.getId(), n.getTipo(), n.getTitulo(), n.getMensagem(), n.getLink(),
                    n.getLidaEm(), n.getCriadoEm());
        }
    }

    public record PaginaNotificacoes(Pagina<NotificacaoResposta> pagina, long naoLidas) {
    }

    private final NotificacaoRepository repositorio;

    /** Cria uma notificação para o usuário (ignora destinatário nulo). */
    @Transactional
    public void notificar(UUID escritorioId, UUID usuarioId, TipoNotificacao tipo, String titulo, String mensagem,
            String link) {
        if (usuarioId == null) return;
        var n = new Notificacao();
        n.setEscritorioId(escritorioId);
        n.setUsuarioId(usuarioId);
        n.setTipo(tipo);
        n.setTitulo(titulo.length() > 160 ? titulo.substring(0, 157) + "..." : titulo);
        n.setMensagem(mensagem);
        n.setLink(link);
        repositorio.save(n);
    }

    @Transactional(readOnly = true)
    public PaginaNotificacoes listar(UsuarioAutenticado u, boolean apenasNaoLidas, Pageable pageable) {
        var page = apenasNaoLidas
                ? repositorio.findByUsuarioIdAndLidaEmIsNullOrderByCriadoEmDesc(u.id(), pageable)
                : repositorio.findByUsuarioIdOrderByCriadoEmDesc(u.id(), pageable);
        return new PaginaNotificacoes(Pagina.de(page, NotificacaoResposta::de),
                repositorio.countByUsuarioIdAndLidaEmIsNull(u.id()));
    }

    @Transactional
    public void marcarLida(UsuarioAutenticado u, UUID id) {
        var n = repositorio.findByIdAndUsuarioId(id, u.id()).orElseThrow(() -> ApiException.naoEncontrado("Notificação"));
        if (n.getLidaEm() == null) n.setLidaEm(Instant.now());
    }

    @Transactional
    public int marcarTodasLidas(UsuarioAutenticado u) {
        return repositorio.marcarTodasLidas(u.id(), Instant.now());
    }
}
