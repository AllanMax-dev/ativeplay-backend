package br.com.ativeplay.modules.tarefa.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Chama o bean do agendador pelo proxy para aplicar a mesma trava usada pelo cron. */
@Component
@RequiredArgsConstructor
public class InicializacaoTarefas {

    private final AgendadorTarefas agendador;

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        agendador.gerarDoDia();
    }
}
