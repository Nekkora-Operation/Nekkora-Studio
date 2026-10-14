package com.nekkora.studio.app;

import com.nekkora.studio.app.ui.StudioController;
import com.nekkora.studio.app.ui.StudioPrompts;
import com.nekkora.studio.app.ui.StudioView;
import com.nekkora.studio.app.workspace.StudioState;
import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.domain.AutomationCatalog;
import com.nekkora.studio.domain.port.StepIntake;
import java.time.Clock;

/**
 * Montagem da aplicação (composition root): catálogo em memória → estado → casos de uso → controlador →
 * tela. Independe de janela, para que a mesma montagem seja usada pela aplicação real e pelos testes.
 * Deve ser criada na EDT.
 */
public final class Studio {
    private final StudioState state;
    private final WorkspaceService service;
    private final StudioController controller;
    private final StudioView view;

    private Studio(Clock clock, StudioPrompts prompts, Runnable exitAction) {
        state = new StudioState(new AutomationCatalog(clock));
        service = new WorkspaceService(state);
        controller = new StudioController(service, prompts, exitAction);
        view = new StudioView(state, controller);
    }

    public static Studio create(Clock clock, StudioPrompts prompts, Runnable exitAction) {
        return new Studio(clock, prompts, exitAction);
    }

    public StudioState state() {
        return state;
    }

    public WorkspaceService service() {
        return service;
    }

    /** Porta de entrada de etapas para produtores futuros (Recorder). */
    public StepIntake stepIntake() {
        return service;
    }

    public StudioController controller() {
        return controller;
    }

    public StudioView view() {
        return view;
    }
}
