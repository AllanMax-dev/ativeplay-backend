package br.com.ativeplay.modules.painel.service;

import br.com.ativeplay.modules.escritorio.repository.EscritorioRepository;
import br.com.ativeplay.modules.painel.web.dto.PainelDtos.EtapaPipeline;
import br.com.ativeplay.modules.painel.web.dto.PainelDtos.ProdutividadeResponse;
import br.com.ativeplay.modules.painel.web.dto.PainelDtos.ProdutividadeUsuario;
import br.com.ativeplay.modules.painel.web.dto.PainelDtos.ResumoResponse;
import br.com.ativeplay.modules.painel.web.dto.PainelDtos.TempoResponsavel;
import br.com.ativeplay.shared.erro.ApiException;
import br.com.ativeplay.shared.escopo.EscopoService;
import br.com.ativeplay.shared.seguranca.UsuarioAutenticado;
import br.com.ativeplay.shared.util.Datas;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores calculados com SQL agregado direto no banco. Todas as consultas são
 * filtradas pelo escritório e, para OPERADOR, pela carteira de empresas.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PainelService {

    private static final List<String> STATUS_ABERTOS = List.of("PENDENTE", "EM_ANDAMENTO", "PAUSADA");

    private final NamedParameterJdbcTemplate jdbc;
    private final EscopoService escopo;
    private final EscritorioRepository escritorios;

    public ResumoResponse resumo(UsuarioAutenticado u, LocalDate data) {
        var fuso = escritorios.findById(u.escritorioId()).map(e -> e.getFusoHorario()).orElse(null);
        var dia = data != null ? data : Datas.hoje(fuso);
        var agora = Instant.now();
        var p = parametros(u)
                .addValue("dia", dia)
                .addValue("agora", Timestamp.from(agora))
                .addValue("abertos", STATUS_ABERTOS)
                .addValue("desde", Timestamp.from(agora.minus(Duration.ofDays(30))))
                .addValue("desdeData", dia.minusDays(30));
        var filtro = filtroEmpresas(u, "o", p);

        var porStatus = new LinkedHashMap<String, Long>();
        jdbc.query("select o.status, count(*) from ocorrencias_tarefa o where o.escritorio_id = :esc and o.data = :dia"
                + filtro + " group by o.status order by o.status",
                p, rs -> {
                    porStatus.put(rs.getString(1), rs.getLong(2));
                });
        long totalDia = porStatus.values().stream().mapToLong(Long::longValue).sum();

        long atrasadas = numero("select count(*) from ocorrencias_tarefa o where o.escritorio_id = :esc"
                + " and o.status in (:abertos) and o.prazo < :agora" + filtro, p);

        var noPrazo = jdbc.queryForMap("select count(*) as concluidas, count(*) filter (where not o.sla_violado) as no_prazo"
                + " from ocorrencias_tarefa o where o.escritorio_id = :esc and o.status = 'CONCLUIDA'"
                + " and o.concluida_em >= :desde" + filtro, p);
        long concluidas = ((Number) noPrazo.get("concluidas")).longValue();
        Double percentual = concluidas == 0 ? null
                : Math.round(((Number) noPrazo.get("no_prazo")).doubleValue() * 1000.0 / concluidas) / 10.0;

        long tempoTotal = numero("select coalesce(sum(o.tempo_gasto_ms), 0) from ocorrencias_tarefa o"
                + " where o.escritorio_id = :esc and o.data >= :desdeData" + filtro, p);
        var porResponsavel = jdbc.query("select u.id, u.nome, sum(o.tempo_gasto_ms) as tempo"
                + " from ocorrencias_tarefa o join usuarios u on u.id = o.responsavel_id"
                + " where o.escritorio_id = :esc and o.data >= :desdeData" + filtro
                + " group by u.id, u.nome having sum(o.tempo_gasto_ms) > 0 order by tempo desc limit 10",
                p, (rs, i) -> new TempoResponsavel(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)));

        var empresasPorStatus = new LinkedHashMap<String, Long>();
        jdbc.query("select e.status, count(*) from empresas e where e.escritorio_id = :esc"
                + filtroEmpresas(u, "e", "id", p) + " group by e.status order by e.status",
                p, rs -> {
                    empresasPorStatus.put(rs.getString(1), rs.getLong(2));
                });

        BigDecimal mrr = null;
        List<EtapaPipeline> pipeline = null;
        if (u.veTodasEmpresas()) {
            mrr = jdbc.queryForObject("select coalesce(sum(honorario_mensal), 0) from empresas"
                    + " where escritorio_id = :esc and status = 'ATIVA'", p, BigDecimal.class);
            pipeline = jdbc.query("select et.id, et.nome, et.tipo, count(op.id), coalesce(sum(op.valor_mensal), 0)"
                    + " from etapas_crm et left join oportunidades_crm op on op.etapa_id = et.id"
                    + " where et.escritorio_id = :esc group by et.id, et.nome, et.tipo, et.ordem order by et.ordem",
                    p, (rs, i) -> new EtapaPipeline(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getLong(4), rs.getBigDecimal(5)));
        }

        return new ResumoResponse(dia, totalDia, porStatus, atrasadas, percentual, tempoTotal, porResponsavel,
                empresasPorStatus, mrr, pipeline);
    }

    /** Produtividade por responsável no período (por data de competência das ocorrências concluídas). */
    public ProdutividadeResponse produtividade(UsuarioAutenticado u, LocalDate de, LocalDate ate) {
        var fuso = escritorios.findById(u.escritorioId()).map(e -> e.getFusoHorario()).orElse(null);
        var fim = ate != null ? ate : Datas.hoje(fuso);
        var inicio = de != null ? de : fim.minusDays(30);
        if (inicio.isAfter(fim)) throw ApiException.invalido("Período inválido: 'de' é posterior a 'ate'");
        var p = parametros(u).addValue("de", inicio).addValue("ate", fim);
        var filtro = filtroEmpresas(u, "o", p);
        var usuarios = jdbc.query("select u.id, u.nome, count(*) as concluidas,"
                + " count(*) filter (where not o.sla_violado) as no_prazo,"
                + " coalesce(sum(o.tempo_gasto_ms), 0) as tempo_total,"
                + " coalesce(avg(o.tempo_gasto_ms), 0) as tempo_medio"
                + " from ocorrencias_tarefa o join usuarios u on u.id = o.responsavel_id"
                + " where o.escritorio_id = :esc and o.status = 'CONCLUIDA' and o.data between :de and :ate" + filtro
                + " group by u.id, u.nome order by concluidas desc, u.nome",
                p, (rs, i) -> new ProdutividadeUsuario(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), Math.round(rs.getDouble(6))));
        return new ProdutividadeResponse(inicio, fim, usuarios);
    }

    // ---------- apoio ----------

    private MapSqlParameterSource parametros(UsuarioAutenticado u) {
        return new MapSqlParameterSource("esc", u.escritorioId());
    }

    private String filtroEmpresas(UsuarioAutenticado u, String alias, MapSqlParameterSource p) {
        return filtroEmpresas(u, alias, "empresa_id", p);
    }

    /** Restringe à carteira do usuário; vazio quando ele enxerga todas as empresas. */
    private String filtroEmpresas(UsuarioAutenticado u, String alias, String coluna, MapSqlParameterSource p) {
        var permitidas = escopo.empresasPermitidas(u);
        if (permitidas.isEmpty()) return "";
        if (permitidas.get().isEmpty()) return " and false";
        p.addValue("empresas", permitidas.get());
        return " and " + alias + "." + coluna + " in (:empresas)";
    }

    private long numero(String sql, MapSqlParameterSource p) {
        var valor = jdbc.queryForObject(sql, p, Long.class);
        return valor == null ? 0 : valor;
    }
}
