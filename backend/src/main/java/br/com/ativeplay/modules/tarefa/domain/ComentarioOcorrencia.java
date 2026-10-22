package br.com.ativeplay.modules.tarefa.domain;

import br.com.ativeplay.shared.dominio.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "comentarios_ocorrencia")
public class ComentarioOcorrencia extends EntidadeBase {

    @Column(name = "escritorio_id", nullable = false, updatable = false)
    private UUID escritorioId;

    @Column(name = "ocorrencia_id", nullable = false)
    private UUID ocorrenciaId;

    @Column(name = "autor_id")
    private UUID autorId;

    @Column(nullable = false, columnDefinition = "text")
    private String texto;
}
