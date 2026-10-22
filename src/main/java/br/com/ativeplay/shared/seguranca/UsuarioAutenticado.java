package br.com.ativeplay.shared.seguranca;

import java.util.UUID;

/**
 * Usuário da requisição, extraído do access token. Basta declarar um parâmetro deste
 * tipo no controller para recebê-lo.
 *
 * @param empresaId empresa vinculada (somente perfil CLIENTE)
 */
public record UsuarioAutenticado(UUID id, UUID escritorioId, Perfil perfil, UUID empresaId) {

    public boolean isAdmin() {
        return perfil == Perfil.ADMIN;
    }

    /** ADMIN e GESTOR enxergam todas as empresas do escritório. */
    public boolean veTodasEmpresas() {
        return perfil == Perfil.ADMIN || perfil == Perfil.GESTOR;
    }

    public boolean isCliente() {
        return perfil == Perfil.CLIENTE;
    }
}
