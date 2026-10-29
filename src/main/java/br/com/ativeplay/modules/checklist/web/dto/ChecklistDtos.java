package br.com.ativeplay.modules.checklist.web.dto;

import br.com.ativeplay.modules.tarefa.domain.Prioridade;
import br.com.ativeplay.modules.usuario.web.dto.UsuarioRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs do checklist do cliente. */
public final class ChecklistDtos {

    private ChecklistDtos() {
    }

    public enum Situacao { PENDENTES, CONCLUIDOS, TODOS }

    /**
     * Criação e edição (PUT substitui o item inteiro, inclusive os sub-itens: os que vierem
     * com id são mantidos/atualizados, os sem id são criados e os ausentes são removidos).
     */
    public record ChecklistItemRequest(
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 10_000) String observacoes,
            LocalDate prazo,
            Prioridade prioridade,
            UUID responsavelId,
            @Valid @Size(max = 50, message = "no máximo 50 sub-itens") List<SubitemRequest> subitens) {
    }

    public record SubitemRequest(UUID id, @NotBlank @Size(max = 200) String texto, boolean concluido) {
    }

    public record SubitemStatusRequest(@NotNull Boolean concluido) {
    }

    public record SubitemResponse(UUID id, String texto, boolean concluido) {
    }

    /** @param atrasado pendente com prazo anterior a hoje (fuso do escritório) */
    public record ChecklistItemResponse(UUID id, UUID empresaId, String titulo, String observacoes, LocalDate prazo,
            Prioridade prioridade, boolean atrasado, UsuarioRef responsavel, UsuarioRef criadoPor, boolean concluido,
            Instant concluidoEm, UsuarioRef concluidoPor, List<SubitemResponse> subitens, int subitensConcluidos,
            Instant criadoEm, Instant atualizadoEm) {
    }
}
