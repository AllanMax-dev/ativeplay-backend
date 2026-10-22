package br.com.ativeplay.modules.documento.service;

import br.com.ativeplay.modules.auditoria.service.AuditoriaService;
import br.com.ativeplay.modules.documento.domain.Documento;
import br.com.ativeplay.modules.documento.repository.DocumentoRepository;
import br.com.ativeplay.modules.documento.web.dto.DocumentoResposta;
import br.com.ativeplay.modules.notificacao.domain.TipoNotificacao;
import br.com.ativeplay.modules.notificacao.service.NotificacaoService;
import br.com.ativeplay.modules.tarefa.domain.OcorrenciaTarefa;
import br.com.ativeplay.modules.usuario.service.UsuarioService;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import br.com.ativeplay.shared.armazenamento.ArmazenamentoService;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.web.Pagina;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Documentos de empresas e anexos de tarefas. Também usado pelo portal do cliente. */
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentoService {

    public static final int MAX_ARQUIVOS_POR_ENVIO = 10;
    private static final Set<String> EXTENSOES_BLOQUEADAS = Set.of("exe", "bat", "cmd", "sh", "js", "msi", "ps1");

    private final DocumentoRepository documentos;
    private final ArmazenamentoService armazenamento;
    private final EscopoService escopo;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;
    private final EntityManager em;

    /**
     * Grava os arquivos e registra os documentos. Se a transação for desfeita, os arquivos
     * já gravados em disco são removidos.
     */
    public List<DocumentoResposta> salvarArquivos(UsuarioAutenticado u, UUID empresaId, List<MultipartFile> arquivos,
            String categoria, UUID ocorrenciaId, boolean visivelCliente) {
        escopo.garantirEmpresa(u, empresaId);
        if (arquivos == null || arquivos.isEmpty()) throw ApiException.invalido("Envie ao menos um arquivo");
        if (arquivos.size() > MAX_ARQUIVOS_POR_ENVIO) {
            throw ApiException.invalido("Envie no máximo " + MAX_ARQUIVOS_POR_ENVIO + " arquivos por vez");
        }
        arquivos.forEach(DocumentoService::validarArquivo);
        var ocorrencia = ocorrenciaId == null ? null : ocorrenciaDaEmpresa(u, empresaId, ocorrenciaId);

        var gravados = new ArrayList<String>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) gravados.forEach(armazenamento::remover);
            }
        });

        var salvos = new ArrayList<Documento>();
        for (var arquivo : arquivos) {
            var nome = nomeSeguro(arquivo.getOriginalFilename());
            ArmazenamentoService.ArquivoSalvo salvo;
            try (var entrada = arquivo.getInputStream()) {
                salvo = armazenamento.salvar(u.escritorioId(), nome, entrada);
            } catch (IOException e) {
                throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
            }
            gravados.add(salvo.storageKey());

            var d = new Documento();
            d.setEscritorioId(u.escritorioId());
            d.setEmpresaId(empresaId);
            d.setOcorrenciaId(ocorrenciaId);
            d.setEnviadoPorId(u.id());
            d.setNome(nome);
            d.setCategoria(categoria == null || categoria.isBlank() ? null : truncar(categoria.trim(), 60));
            d.setMimeType(tipo(arquivo.getContentType()));
            d.setTamanho(salvo.tamanho());
            d.setStorageKey(salvo.storageKey());
            d.setSha256(salvo.sha256());
            d.setVisivelCliente(visivelCliente);
            salvos.add(documentos.save(d));
            auditoria.registrar(u, "documento.enviar", "documento", d.getId(), empresaId, "Documento " + nome + " enviado");
        }

        if (ocorrencia != null && ocorrencia.getResponsavelId() != null && !ocorrencia.getResponsavelId().equals(u.id())) {
            notificacoes.notificar(u.escritorioId(), ocorrencia.getResponsavelId(), TipoNotificacao.DOCUMENTO,
                    salvos.size() + " novo(s) anexo(s) em: " + ocorrencia.getTitulo(), null,
                    "tarefas?occ=" + ocorrencia.getId());
        }
        return respostas(salvos);
    }

    @Transactional(readOnly = true)
    public Pagina<DocumentoResposta> listar(UsuarioAutenticado u, UUID empresaId, UUID ocorrenciaId, String categoria,
            String busca, Pageable pageable) {
        if (empresaId != null) escopo.garantirAcesso(u, empresaId);
        var permitidas = escopo.empresasPermitidas(u);
        if (empresaId == null && permitidas.isPresent() && permitidas.get().isEmpty()) {
            return new Pagina<>(List.of(), 0, pageable.getPageNumber() + 1, pageable.getPageSize());
        }
        Specification<Documento> spec = (r, q, cb) -> {
            var filtros = new ArrayList<Predicate>();
            filtros.add(cb.equal(r.get("escritorioId"), u.escritorioId()));
            if (empresaId != null) filtros.add(cb.equal(r.get("empresaId"), empresaId));
            else permitidas.ifPresent(ids -> filtros.add(r.get("empresaId").in(ids)));
            if (ocorrenciaId != null) filtros.add(cb.equal(r.get("ocorrenciaId"), ocorrenciaId));
            if (categoria != null && !categoria.isBlank()) filtros.add(cb.equal(r.get("categoria"), categoria.trim()));
            if (busca != null && !busca.isBlank()) {
                filtros.add(cb.like(cb.lower(r.get("nome")), "%" + busca.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(filtros.toArray(Predicate[]::new));
        };
        var page = documentos.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "criadoEm")));
        var nomes = nomesDosAutores(page.getContent());
        return Pagina.de(page, d -> resposta(d, nomes));
    }

    /** Documento do escritório acessível ao usuário (respeita a carteira). */
    @Transactional(readOnly = true)
    public Documento buscar(UsuarioAutenticado u, UUID id) {
        var d = documentos.findByIdAndEscritorioId(id, u.escritorioId())
                .orElseThrow(() -> ApiException.naoEncontrado("Documento"));
        escopo.garantirAcesso(u, d.getEmpresaId());
        return d;
    }

    /** Resposta de download (attachment, nome em UTF-8). A autorização deve ser feita antes. */
    public ResponseEntity<Resource> download(Documento d) {
        return ResponseEntity.ok()
                .contentType(mediaType(d.getMimeType()))
                .contentLength(d.getTamanho())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.getNome(), StandardCharsets.UTF_8).build().toString())
                .body(armazenamento.abrir(d.getStorageKey()));
    }

    public void excluir(UsuarioAutenticado u, UUID id) {
        var d = buscar(u, id);
        documentos.delete(d);
        auditoria.registrar(u, "documento.excluir", "documento", d.getId(), d.getEmpresaId(),
                "Documento " + d.getNome() + " excluído");
        var chave = d.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                armazenamento.remover(chave);
            }
        });
    }

    public List<DocumentoResposta> respostas(List<Documento> lista) {
        var nomes = nomesDosAutores(lista);
        return lista.stream().map(d -> resposta(d, nomes)).toList();
    }

    // ---------- auxiliares ----------

    private OcorrenciaTarefa ocorrenciaDaEmpresa(UsuarioAutenticado u, UUID empresaId, UUID ocorrenciaId) {
        var o = em.find(OcorrenciaTarefa.class, ocorrenciaId);
        if (o == null || !o.getEscritorioId().equals(u.escritorioId()) || !o.getEmpresaId().equals(empresaId)) {
            throw ApiException.naoEncontrado("Tarefa da empresa");
        }
        return o;
    }

    private Map<UUID, String> nomesDosAutores(List<Documento> lista) {
        return usuarioService.nomes(lista.stream().map(Documento::getEnviadoPorId).filter(Objects::nonNull)
                .distinct().toList());
    }

    private static DocumentoResposta resposta(Documento d, Map<UUID, String> nomes) {
        var autor = d.getEnviadoPorId() == null ? null : new UsuarioRef(d.getEnviadoPorId(), nomes.get(d.getEnviadoPorId()));
        return new DocumentoResposta(d.getId(), d.getEmpresaId(), d.getOcorrenciaId(), d.getNome(), d.getCategoria(),
                d.getMimeType(), d.getTamanho(), d.getSha256(), d.isVisivelCliente(), autor, d.getCriadoEm());
    }

    private static void validarArquivo(MultipartFile arquivo) {
        if (arquivo.isEmpty()) throw ApiException.invalido("Arquivo vazio: " + arquivo.getOriginalFilename());
        var nome = nomeSeguro(arquivo.getOriginalFilename());
        var ponto = nome.lastIndexOf('.');
        var ext = ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        if (EXTENSOES_BLOQUEADAS.contains(ext)) throw ApiException.invalido("Tipo de arquivo não permitido: ." + ext);
    }

    /** Remove caminhos enviados pelo navegador e limita o tamanho do nome. */
    private static String nomeSeguro(String original) {
        if (original == null || original.isBlank()) return "arquivo";
        var semBarras = original.replace('\\', '/');
        var nome = semBarras.substring(semBarras.lastIndexOf('/') + 1).replaceAll("[\\x00-\\x1F]", "").trim();
        return nome.isEmpty() ? "arquivo" : truncar(nome, 255);
    }

    private static String tipo(String contentType) {
        return contentType == null || contentType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : truncar(contentType, 120);
    }

    private static MediaType mediaType(String valor) {
        try {
            return MediaType.parseMediaType(valor);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String truncar(String valor, int max) {
        return valor.length() > max ? valor.substring(0, max) : valor;
    }
}
