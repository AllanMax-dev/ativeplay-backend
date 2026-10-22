package br.com.ativeplay.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Envelope das listagens paginadas: ?pagina=1&porPagina=20 → { dados, total, pagina, porPagina }. */
public record Pagina<T>(List<T> dados, long total, int pagina, int porPagina) {

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> mapeador) {
        return new Pagina<>(page.getContent().stream().map(mapeador).toList(), page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }

    public static <T> Pagina<T> de(Page<T> page) {
        return de(page, Function.identity());
    }
}
