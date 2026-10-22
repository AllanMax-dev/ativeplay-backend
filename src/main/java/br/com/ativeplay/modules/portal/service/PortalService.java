package br.com.ativeplay.modules.portal.service;

import br.com.ativeplay.modules.documento.domain.Documento;
import br.com.ativeplay.modules.documento.repository.DocumentoRepository;
import br.com.ativeplay.modules.documento.service.DocumentoService;
import br.com.ativeplay.modules.documento.web.dto.DocumentoResposta;
import br.com.ativeplay.modules.empresa.repository.EmpresaRepository;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.portal.web.dto.PortalDtos.DocumentoPortal;
import br.com.ativeplay.modules.portal.web.dto.PortalDtos.EmpresaPortal;
import br.com.ativeplay.modules.portal.web.dto.PortalDtos.TarefaPortal;
import br.com.ativeplay.modules.tarefa.domain.StatusOcorrencia;
import br.com.ativeplay.modules.usuario.repository.UsuarioRepository;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Cnpj;
import br.com.ativeplay.shared.web.Pagina;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Portal do cliente final: tudo é limitado à empresa do usuário CLIENTE e aos itens
 * marcados como visíveis para o cliente.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalService {

    private static final String CATEGORIA_PADRAO = "Enviado pelo cliente";
    private static final int MAX_ARQUIVOS = 10;

    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final DocumentoRepository documentos;
    private final DocumentoService documentoService;
    private final NotificacaoService notificacoes;
    private final NamedParameterJdbcTemplate jdbc;

    public EmpresaPortal empresa(UsuarioAutenticado u) {
        var e = empresas.findByIdAndEscritorioId(empresaDo(u), u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Empresa"));
        var responsavel = e.getResponsavelId() == null ? null
                : usuarios.findById(e.getResponsavelId()).map(x -> x.getNome()).orElse(null);
        return new EmpresaPortal(e.getId(), e.getRazaoSocial(), e.getNomeFantasia(), e.getCnpj(),
                Cnpj.formatar(e.getCnpj()), e.getRegimeTributario().name(), e.getStatus().name(), e.getEmail(),
                e.getTelefone(), responsavel, e.getDataInicio());
    }

    public Pagina<TarefaPortal> tarefas(UsuarioAutenticado u, StatusOcorrencia status, LocalDate de, LocalDate ate,
            Pageable pageable) {
        var p = new MapSqlParameterSource("esc", u.escritorioId()).addValue("emp", empresaDo(u));
        var where = new StringBuilder(" from ocorrencias_tarefa o left join setores s on s.id = o.setor_id"
                + " where o.escritorio_id = :esc and o.empresa_id = :emp and o.visivel_cliente");
        if (status != null) {
            where.append(" and o.status = :status");
            p.addValue("status", status.name());
        }
        if (de != null) {
            where.append(" and o.data >= :de");
            p.addValue("de", de);
        }
        if (ate != null) {
            where.append(" and o.data <= :ate");
            p.addValue("ate", ate);
        }
        var total = jdbc.queryForObject("select count(*)" + where, p, Long.class);
        paginar(p, pageable);
        var dados = jdbc.query("select o.id, o.titulo, o.data, o.prazo, o.status, o.concluida_em, s.nome" + where
                + " order by o.data desc, o.prazo desc limit :limite offset :deslocamento", p,
                (rs, i) -> new TarefaPortal(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, LocalDate.class), instante(rs.getTimestamp(4)), rs.getString(5),
                        instante(rs.getTimestamp(6)), rs.getString(7)));
        return new Pagina<>(dados, total == null ? 0 : total, pageable.getPageNumber() + 1, pageable.getPageSize());
    }

    public Pagina<DocumentoPortal> documentos(UsuarioAutenticado u, Pageable pageable) {
        var p = new MapSqlParameterSource("esc", u.escritorioId()).addValue("emp", empresaDo(u));
        var where = " from documentos d left join usuarios us on us.id = d.enviado_por_id"
                + " where d.escritorio_id = :esc and d.empresa_id = :emp and d.visivel_cliente";
        var total = jdbc.queryForObject("select count(*)" + where, p, Long.class);
        paginar(p, pageable);
        var dados = jdbc.query("select d.id, d.nome, d.categoria, d.mime_type, d.tamanho,"
                + " coalesce(us.perfil = 'CLIENTE', false), d.criado_em" + where
                + " order by d.criado_em desc limit :limite offset :deslocamento", p,
                (rs, i) -> new DocumentoPortal(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getBoolean(6), instante(rs.getTimestamp(7))));
        return new Pagina<>(dados, total == null ? 0 : total, pageable.getPageNumber() + 1, pageable.getPageSize());
    }

    public ResponseEntity<Resource> download(UsuarioAutenticado u, UUID documentoId) {
        var empresaId = empresaDo(u);
        Documento d = documentos.findByIdAndEscritorioId(documentoId, u.escritorioId())
                .filter(x -> x.getEmpresaId().equals(empresaId) && x.isVisivelCliente())
                .orElseThrow(() -> ApiException.naoEncontrado("Documento"));
        return documentoService.download(d);
    }

    /** Upload feito pelo cliente: visível no portal e avisa o responsável pela empresa. */
    @Transactional
    public List<DocumentoPortal> enviar(UsuarioAutenticado u, List<MultipartFile> arquivos, String categoria) {
        var empresaId = empresaDo(u);
        if (arquivos == null || arquivos.isEmpty()) throw ApiException.invalido("Envie ao menos um arquivo");
        if (arquivos.size() > MAX_ARQUIVOS) throw ApiException.invalido("Envie no máximo " + MAX_ARQUIVOS + " arquivos por vez");
        var cat = categoria == null || categoria.isBlank() ? CATEGORIA_PADRAO : categoria.trim();
        var salvos = documentoService.salvarArquivos(u, empresaId, arquivos, cat, null, true);

        var empresa = empresas.findByIdAndEscritorioId(empresaId, u.escritorioId()).orElseThrow();
        notificacoes.notificar(u.escritorioId(), empresa.getResponsavelId(), TipoNotificacao.DOCUMENTO,
                empresa.getRazaoSocial() + " enviou " + salvos.size() + " documento(s)",
                String.join(", ", salvos.stream().map(DocumentoResposta::nome).toList()),
                "empresas/" + empresaId + "/documentos");
        return salvos.stream().map(d -> new DocumentoPortal(d.id(), d.nome(), d.categoria(), d.mimeType(),
                d.tamanho(), true, d.criadoEm())).toList();
    }

    // ---------- apoio ----------

    private static UUID empresaDo(UsuarioAutenticado u) {
        if (u.empresaId() == null) throw ApiException.proibido("Usuário do portal sem empresa vinculada");
        return u.empresaId();
    }

    private static void paginar(MapSqlParameterSource p, Pageable pageable) {
        p.addValue("limite", pageable.getPageSize()).addValue("deslocamento", pageable.getOffset());
    }

    private static java.time.Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
