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
    private final com.nekkora.studio.app.workspace.ProjectService projects;
    private final com.nekkora.studio.app.workspace.ValidationService validation;
    private final java.util.concurrent.ExecutorService persistence;
    private final StudioEnvironment environment;
    private final StudioController controller;
    private final StudioView view;
    private final BrowserLink link;
    private final RecorderController recorder;
    private final RecorderPresenter recorderPresenter;

    private Studio(Clock clock, StudioPrompts prompts, Runnable exitAction, BrowserLink link, RecorderPrompts recorderPrompts,
            Function<FloatingRecorderPanel, RecorderWindowHost> hostFactory, StudioEnvironment environment) {
        state = new StudioState(new AutomationCatalog(clock));
        service = new WorkspaceService(state);
        this.environment = environment;
        // STUDIO-006: disco sempre fora da EDT, numa única thread (salvamentos e leituras em ordem)
        persistence = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nekkora-persistence");
            t.setDaemon(true);
            return t;
        });
        com.nekkora.studio.persistence.ProjectFiles files = new com.nekkora.studio.persistence.ProjectFiles(
                new com.nekkora.studio.persistence.ProjectRepository(environment.fileOps()), StudioApplication.VERSION);
        projects = new com.nekkora.studio.app.workspace.ProjectService(service, prompts, files, persistence, clock, environment.recents(),
                environment.settings(), environment.recovery(files));
        validation = new com.nekkora.studio.app.workspace.ValidationService(state); // STUDIO-007: diagnóstico estrutural (somente leitura)
        controller = new StudioController(service, projects, validation, prompts, exitAction);
        view = new StudioView(state, controller);
        this.link = link;
        // STUDIO-003: as etapas gravadas entram pelo StepIntake do WorkspaceService (na EDT).
        recorder = new RecorderController(link, clock, RecorderController.Options.defaults(), new com.nekkora.studio.app.recorder.WorkspaceStepSink(service),
                new com.nekkora.studio.app.recorder.WorkspaceEvidenceSink(service));
        recorderPresenter = new RecorderPresenter(service, controller, recorder, view, recorderPrompts, hostFactory);
        // STUDIO-004: edição bloqueada (no caso de uso) na automação que recebe uma gravação — estado vivo do Recorder
        service.setRecordingGuard(id -> RecorderPresenter.locks(recorder.snapshot(), id));
        // STUDIO-006: salvar só com a gravação quiescente (nunca finaliza nem cancela uma gravação para salvar)
        projects.setBusyGuard(id -> RecorderPresenter.busyForSave(recorder.snapshot(), id));
        projects.restartRecoveryTimer();
        this.clock = clock;
        // STUDIO-009: debug guiado (criado no primeiro uso, com a janela configurada pela aplicação)
        view.actions().onGuidedDebug(() -> guidedDebug().open());
        // STUDIO-010: PDD visual (rascunho e imagens sanitizadas no diretório de dados; nunca no .nekkora)
        view.actions().onVisualPdd(() -> visualPdd().open());
        // STUDIO-011: geração do código Python (janela dona configurada pela aplicação)
        view.actions().onPythonCodegen(() -> new com.nekkora.studio.app.codegen.CodegenPresenter(service, codegenOwner).open());
    }

    private java.util.function.Supplier<java.awt.Window> codegenOwner = () -> null;

    /** Janela dona do diálogo de geração (a aplicação configura). */
    public void useCodegenOwner(java.util.function.Supplier<java.awt.Window> owner) {
        this.codegenOwner = java.util.Objects.requireNonNull(owner);
    }

    private java.util.function.Function<com.nekkora.studio.ui.pdd.PddPanel, com.nekkora.studio.app.pdd.PddPresenter.Host> pddHosts =
            p -> com.nekkora.studio.app.pdd.PddPresenter.Host.headless(null);
    private com.nekkora.studio.app.pdd.PddPresenter visualPdd;

    /** Janela real do PDD (a aplicação configura; os testes ficam sem janela). Antes do primeiro uso. */
    public void useVisualPddHosts(java.util.function.Function<com.nekkora.studio.ui.pdd.PddPanel, com.nekkora.studio.app.pdd.PddPresenter.Host> factory) {
        this.pddHosts = java.util.Objects.requireNonNull(factory);
    }

    /** STUDIO-010: PDD visual da automação ativa. */
    public com.nekkora.studio.app.pdd.PddPresenter visualPdd() {
        if (visualPdd == null) {
            visualPdd = new com.nekkora.studio.app.pdd.PddPresenter(service, link, recorder, environment.decisionActor(), clock,
                    environment.dataDir().orElse(null), pddHosts);
        }
        return visualPdd;
    }

    private final Clock clock;
    private java.util.function.Function<com.nekkora.studio.ui.debug.GuidedDebugPanel, com.nekkora.studio.app.debug.GuidedDebugPresenter.Host> debugHosts =
            p -> com.nekkora.studio.app.debug.GuidedDebugPresenter.Host.headless();
    private com.nekkora.studio.app.debug.GuidedDebugPresenter guidedDebug;

    /** Janela real do debug guiado (a aplicação configura; os testes ficam sem janela). Antes do primeiro uso. */
    public void useGuidedDebugHosts(java.util.function.Function<com.nekkora.studio.ui.debug.GuidedDebugPanel,
            com.nekkora.studio.app.debug.GuidedDebugPresenter.Host> factory) {
        this.debugHosts = java.util.Objects.requireNonNull(factory);
    }

    /** STUDIO-009: debug guiado (inspecionar, destacar, confirmar/corrigir — nunca executar). */
    public com.nekkora.studio.app.debug.GuidedDebugPresenter guidedDebug() {
        if (guidedDebug == null) {
            guidedDebug = new com.nekkora.studio.app.debug.GuidedDebugPresenter(service, link, recorder, environment.decisionActor(), clock, debugHosts);
        }
        return guidedDebug;
    }

    /** Studio sem ligação com o navegador (testes e uso sem Bridge): Gravar explica que o navegador não está conectado. */
    public static Studio create(Clock clock, StudioPrompts prompts, Runnable exitAction) {
        return create(clock, prompts, exitAction, BrowserLink.offline("DISABLED"), RecorderPrompts.none(), p -> RecorderWindowHost.headless());
    }

    /** Sem dados locais (padrão dos testes): nada é gravado fora dos arquivos de projeto. */
    public static Studio create(Clock clock, StudioPrompts prompts, Runnable exitAction, BrowserLink link, RecorderPrompts recorderPrompts,
            Function<FloatingRecorderPanel, RecorderWindowHost> hostFactory) {
        return create(clock, prompts, exitAction, link, recorderPrompts, hostFactory, StudioEnvironment.inMemory());
    }

    public static Studio create(Clock clock, StudioPrompts prompts, Runnable exitAction, BrowserLink link, RecorderPrompts recorderPrompts,
            Function<FloatingRecorderPanel, RecorderWindowHost> hostFactory, StudioEnvironment environment) {
        return new Studio(clock, prompts, exitAction, link, recorderPrompts, hostFactory, environment);
    }

    /** Casos de uso de projeto (STUDIO-006). */
    public com.nekkora.studio.app.workspace.ProjectService projects() {
        return projects;
    }

    /** Validação estrutural (STUDIO-007). */
    public com.nekkora.studio.app.workspace.ValidationService validation() {
        return validation;
    }

    public StudioEnvironment environment() {
        return environment;
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
        projects.dispose();
        persistence.shutdown();
        if (guidedDebug != null) guidedDebug.dispose();
        if (visualPdd != null) visualPdd.dispose();
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
