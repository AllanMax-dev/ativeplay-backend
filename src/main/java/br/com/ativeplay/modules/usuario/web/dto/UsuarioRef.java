package br.com.ativeplay.modules.usuario.web.dto;

import java.util.UUID;

/** Referência resumida a um usuário (responsável, autor, membro da equipe). */
public record UsuarioRef(UUID id, String nome) {
}
