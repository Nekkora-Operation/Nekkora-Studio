package com.nekkora.studio.app;

import com.nekkora.studio.app.recorder.RecorderPresenter;
import com.nekkora.studio.app.recorder.RecorderPrompts;
import com.nekkora.studio.app.recorder.RecorderWindowHost;
import com.nekkora.studio.app.ui.StudioController;
import com.nekkora.studio.app.ui.StudioPrompts;
import com.nekkora.studio.app.ui.StudioView;
import com.nekkora.studio.app.workspace.StudioState;
import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.domain.AutomationCatalog;
import com.nekkora.studio.domain.port.StepIntake;
import com.nekkora.studio.recorder.RecorderController;
import com.nekkora.studio.ui.recorder.FloatingRecorderPanel;
import java.time.Clock;
import java.util.function.Function;

/**
 * Montagem da aplicação (composition root): catálogo em memória → estado → casos de uso → controlador →
 * tela → Recorder (STUDIO-002: ligação com o navegador, RecorderController e o Floating Recorder). Independe de
 * janela, para que a mesma montagem seja usada pela aplicação real e pelos testes. Deve ser criada na EDT.
 */
public final class Studio {
    private final StudioState state;
    private final WorkspaceService service;
    private final StudioController controller;
    private final StudioView view;
    private final BrowserLink link;
    private final RecorderController recorder;
    private final RecorderPresenter recorderPresenter;

    private Studio(Clock clock, StudioPrompts prompts, Runnable exitAction, BrowserLink link, RecorderPrompts recorderPrompts,
            Function<FloatingRecorderPanel, RecorderWindowHost> hostFactory) {
        state = new StudioState(new AutomationCatalog(clock));
        service = new WorkspaceService(state);
        controller = new StudioController(service, prompts, exitAction);
        view = new StudioView(state, controller);
        this.link = link;
        // STUDIO-003: as etapas gravadas entram pelo StepIntake do WorkspaceService (na EDT).
        recorder = new RecorderController(link, clock, RecorderController.Options.defaults(), new com.nekkora.studio.app.recorder.WorkspaceStepSink(service));
        recorderPresenter = new RecorderPresenter(service, controller, recorder, view, recorderPrompts, hostFactory);
        // STUDIO-004: edição bloqueada (no caso de uso) na automação que recebe uma gravação — estado vivo do Recorder
        service.setRecordingGuard(id -> RecorderPresenter.locks(recorder.snapshot(), id));
    }

    /** Studio sem ligação com o navegador (testes e uso sem Bridge): Gravar explica que o navegador não está conectado. */
    public static Studio create(Clock clock, StudioPrompts prompts, Runnable exitAction) {
        return create(clock, prompts, exitAction, BrowserLink.offline("DISABLED"), RecorderPrompts.none(), p -> RecorderWindowHost.headless());
    }

    public static Studio create(Clock clock, StudioPrompts prompts, Runnable exitAction, BrowserLink link, RecorderPrompts recorderPrompts,
            Function<FloatingRecorderPanel, RecorderWindowHost> hostFactory) {
        return new Studio(clock, prompts, exitAction, link, recorderPrompts, hostFactory);
    }

    public BrowserLink link() {
        return link;
    }

    public RecorderController recorder() {
        return recorder;
    }

    public RecorderPresenter recorderPresenter() {
        return recorderPresenter;
    }

    /** Encerra observadores, o Recorder e a janela flutuante (ao fechar). */
    public void dispose() {
        recorderPresenter.dispose();
        recorder.close();
        view.dispose();
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
