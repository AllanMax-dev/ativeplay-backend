package br.com.ativeplay.modules.empresa.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Indicadores agregados por empresa, calculados em uma única consulta para a página listada. */
@Repository
@RequiredArgsConstructor
public class EmpresaIndicadoresRepository {

    public record Indicadores(long abertasHoje, long atrasadas) {
        public static final Indicadores ZERO = new Indicadores(0, 0);
    }

    private static final String SQL = """
            SELECT empresa_id,
                   count(*) FILTER (WHERE data = :hoje)     AS abertas_hoje,
                   count(*) FILTER (WHERE prazo < now())    AS atrasadas
              FROM ocorrencias_tarefa
             WHERE escritorio_id = :escritorioId
               AND empresa_id IN (:empresaIds)
               AND status IN ('PENDENTE', 'EM_ANDAMENTO', 'PAUSADA')
             GROUP BY empresa_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Map<UUID, Indicadores> tarefasAbertas(UUID escritorioId, Collection<UUID> empresaIds, LocalDate hoje) {
        var resultado = new HashMap<UUID, Indicadores>();
        if (empresaIds.isEmpty()) return resultado;
        var params = new MapSqlParameterSource()
                .addValue("escritorioId", escritorioId)
                .addValue("empresaIds", empresaIds)
                .addValue("hoje", hoje);
        jdbc.query(SQL, params, rs -> {
            resultado.put(rs.getObject("empresa_id", UUID.class),
                    new Indicadores(rs.getLong("abertas_hoje"), rs.getLong("atrasadas")));
        });
        return resultado;
    }
}
