package com.nekkora.studio.app.recorder;

import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.ui.StudioController;
import com.nekkora.studio.app.ui.StudioView;
import com.nekkora.studio.app.workspace.Notice;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.app.workspace.StudioChange;
import com.nekkora.studio.app.workspace.StudioEvent;
import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.recorder.RecorderController;
import com.nekkora.studio.recorder.RecorderSnapshot;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.recorder.RecordingTarget;
import com.nekkora.studio.ui.recorder.FloatingRecorderPanel;
import com.nekkora.studio.ui.recorder.RecorderText;
import com.nekkora.studio.ui.recorder.RecordingSetupPanel;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.studio.NekkoraStatusBar;
import com.nekkora.ui.typography.NekkoraCaption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.SwingUtilities;

/**
 * Liga o Recorder (thread própria) à interface (EDT): botão Gravar, diálogo de início, Floating Recorder, barra de
 * status e saída do Studio. Toda fotografia do Recorder chega por {@code invokeLater} (D-STUDIO-002-10) e só a parte
 * do Recorder é atualizada — a automação nunca é redesenhada por um evento de gravação.
 */
public final class RecorderPresenter {

    /** Rótulos do botão: "Gravando" só depois da confirmação da extensão. */
    static String recordLabel(RecorderState s) {
        return switch (s) {
            case RECORDING -> "Gravando";
            case PAUSED, PAUSING, RESUMING -> "Gravação pausada";
            case DEGRADED, RECOVERING -> "Gravação interrompida";
            case FINALIZING, CANCELLING -> "Encerrando gravação";
            default -> StudioActions.RECORD_LABEL;
        };
    }

    private final WorkspaceService service;
    private final StudioController controller;
    private final RecorderController recorder;
    private final RecorderPrompts prompts;
    private final StudioActions actions;
    private final FloatingRecorderPanel panel;
    private final RecorderWindowHost host;
    private final NekkoraCaption statusItem = NekkoraStatusBar.item(IconName.MONITOR, RecorderText.link(null));
    private final Consumer<RecorderSnapshot> listener = s -> SwingUtilities.invokeLater(() -> render(s));
    private final Consumer<StudioEvent> stateListener = this::onStudioEvent;
    private RecorderSnapshot current;
    private boolean disposed;

    public RecorderPresenter(WorkspaceService service, StudioController controller, RecorderController recorder, StudioView view,
            RecorderPrompts prompts, Function<FloatingRecorderPanel, RecorderWindowHost> hostFactory) {
        this.service = service;
        this.controller = controller;
        this.recorder = recorder;
        this.prompts = prompts;
        this.actions = view.actions();
        this.panel = new FloatingRecorderPanel(new PanelActions());
        this.host = hostFactory.apply(panel);
        statusItem.getAccessibleContext().setAccessibleName("Conexão com o navegador");
        view.addStatusItem(statusItem);
        actions.onRecord(this::record);
        controller.setExitGuard(this::guardExit);
        service.state().addListener(stateListener);
        recorder.addListener(listener);
        render(recorder.snapshot());
    }

    // ------------------------------------------------------------------ intenções do usuário

    /** Botão Gravar: inicia (diálogo) ou, com uma gravação existente, mostra o Recorder. */
    public void record() {
        RecorderSnapshot s = recorder.snapshot();
        if (s.state().active() || s.state().terminal()) {
            host.show();
            return;
        }
        Automation a = service.state().activeAutomation().orElse(null);
        if (a == null || a.sessions().isEmpty()) {
            return;
        }
        SessionId pre = switch (service.state().selection()) {
            case Selection.OfSession x -> x.session();
            case Selection.OfStep x -> x.session();
            default -> a.sessions().get(a.sessions().size() - 1).id();
        };
        boolean onTop = host.isAlwaysOnTop();
        host.setAlwaysOnTop(false); // nenhum diálogo fica escondido atrás do Recorder
        Optional<RecordingSetupPanel.Choice> choice;
        try {
            choice = prompts.chooseRecording(a, pre, RecorderPrompts.source(() -> recorder.snapshot().link(), recorder::listTabs));
        } finally {
            host.setAlwaysOnTop(onTop);
        }
        choice.ifPresent(c -> {
            recorder.start(new RecordingTarget(a.id(), c.session()), c.tab(), c.mode());
            host.show();
        });
    }

    private void requestClose() {
        RecorderSnapshot s = recorder.snapshot();
        if (!s.state().active()) {
            recorder.acknowledge();
            host.hide();
            return;
        }
        host.setAlwaysOnTop(false);
        RecorderPrompts.CloseDecision d;
        try {
            d = prompts.askClose();
        } finally {
            host.setAlwaysOnTop(panelOnTop);
        }
        switch (d) {
            case FINISH -> recorder.finish();
            case CANCEL -> recorder.cancel();
            default -> { } // a gravação continua: a janela permanece
        }
    }

    private boolean panelOnTop = true;

    private void newSession() {
        Automation a = recorder.snapshot().automation() == null ? null : service.state().catalog().find(recorder.snapshot().automation()).orElse(null);
        if (a == null) {
            return;
        }
        if (!a.id().equals(service.state().activeId().orElse(null))) {
            controller.openAutomation(a.id());
        }
        List<SessionId> before = a.sessions().stream().map(LogicalSession::id).toList();
        host.setAlwaysOnTop(false);
        try {
            controller.newSession(); // domínio existente: o mesmo formulário e as mesmas validações do Studio
        } finally {
            host.setAlwaysOnTop(panelOnTop);
        }
        a.sessions().stream().map(LogicalSession::id).filter(id -> !before.contains(id)).findFirst().ifPresent(recorder::associate);
    }

    /** Sair do Studio com gravação: o usuário decide finalizar ou cancelar; o navegador confirma antes de fechar. */
    private void guardExit(Runnable exit) {
        RecorderSnapshot s = recorder.snapshot();
        if (!s.state().active()) {
            exit.run();
            return;
        }
        host.setAlwaysOnTop(false);
        RecorderPrompts.ExitDecision d;
        try {
            d = prompts.askExit();
        } finally {
            host.setAlwaysOnTop(panelOnTop);
        }
        if (d == RecorderPrompts.ExitDecision.STAY) {
            return;
        }
        recorder.shutdown(d == RecorderPrompts.ExitDecision.FINISH, Duration.ofSeconds(5))
                .whenComplete((v, e) -> SwingUtilities.invokeLater(exit));
    }

    // ------------------------------------------------------------------ estado → tela

    /**
     * STUDIO-004 (D-S004-10): a automação está "em gravação" para fins de edição enquanto a gravação existir (início,
     * gravação, pausa, interrupção, encerramento ou reconciliação com resultado desconhecido) e enquanto houver etapas
     * recebidas ainda não entregues ao domínio.
     */
    public static boolean locks(RecorderSnapshot s, com.nekkora.studio.domain.AutomationId automation) {
        if (s == null || s.automation() == null || !s.automation().equals(automation)) {
            return false;
        }
        return s.state().active() || s.steps().pending() > 0;
    }

    private com.nekkora.studio.domain.AutomationId lockedAutomation;

    private void render(RecorderSnapshot s) {
        if (disposed) {
            return;
        }
        current = s;
        com.nekkora.studio.domain.AutomationId locked = s.automation() != null && locks(s, s.automation()) ? s.automation() : null;
        if (!java.util.Objects.equals(locked, lockedAutomation)) {
            com.nekkora.studio.domain.AutomationId before = lockedAutomation;
            lockedAutomation = locked;
            service.editLockChanged(locked != null ? locked : before); // a tela reavalia o que pode ser editado
        }
        Automation a = s.automation() == null ? null : service.state().catalog().find(s.automation()).orElse(null);
        panel.render(s, a);
        host.contentChanged();
        LinkStatus link = s.link();
        String text = RecorderText.link(link);
        if (s.state().active()) {
            text += " · " + s.state().label();
        }
        statusItem.setText(text);
        statusItem.setToolTipText(link != null && link.connected() ? null : RecorderText.connectHint(link));
        refreshAction();
        if (s.state() == RecorderState.IDLE && s.problem() != null) {
            // Início recusado (ex.: site não autorizado): explica no Studio e volta ao estado ocioso.
            host.hide();
            service.report(new Notice(Notice.Kind.WARNING, s.problem().title(), s.problem().message()));
            recorder.acknowledge();
        } else if (s.state().terminal() && !host.isShowing()) {
            host.show(); // o desfecho (resumo ou erro) sempre é mostrado
        }
    }

    private void onStudioEvent(StudioEvent e) {
        if (e.has(StudioChange.ACTIVE_AUTOMATION) || e.has(StudioChange.CONTENT)) {
            refreshAction();
        }
        if (e.has(StudioChange.CONTENT) && current != null && current.automation() != null && current.automation().equals(e.automation())) {
            render(current); // nomes de sessões no Recorder
        }
    }

    private void refreshAction() {
        RecorderSnapshot s = current == null ? recorder.snapshot() : current;
        boolean hasTarget = service.state().activeAutomation().map(x -> !x.sessions().isEmpty()).orElse(false);
        boolean bridge = s.link() == null || s.link().state() != LinkStatus.State.UNAVAILABLE;
        var action = actions.get(StudioActions.RECORD);
        action.setEnabled(s.state().active() || s.state().terminal() || (bridge && hasTarget));
        action.putValue(Action.NAME, recordLabel(s.state()));
        action.putValue(Action.SHORT_DESCRIPTION, s.state().active() ? "Mostrar o Recorder" : !bridge ? RecorderText.connectHint(s.link())
                : hasTarget ? "Iniciar uma gravação técnica numa aba autorizada do Chrome." : "Crie uma sessão na automação para gravar.");
    }

    public void dispose() {
        disposed = true;
        recorder.removeListener(listener);
        service.state().removeListener(stateListener);
        host.dispose();
    }

    // ------------------------------------------------------------------ acesso para testes/evidências

    public FloatingRecorderPanel panel() {
        return panel;
    }

    public RecorderWindowHost host() {
        return host;
    }

    public NekkoraCaption statusItem() {
        return statusItem;
    }

    private final class PanelActions implements FloatingRecorderPanel.Actions {
        @Override
        public void pause() {
            recorder.pause();
        }

        @Override
        public void resume() {
            recorder.resume();
        }

        @Override
        public void finish() {
            recorder.finish();
        }

        @Override
        public void cancel() {
            recorder.cancel();
        }

        @Override
        public void newSession() {
            RecorderPresenter.this.newSession();
        }

        @Override
        public void changeSession(SessionId session) {
            recorder.associate(session);
        }

        @Override
        public void changeMode(RecordingMode mode) {
            recorder.setMode(mode);
        }

        @Override
        public void abandon() {
            recorder.abandon();
        }

        @Override
        public void collapse() {
            host.hide();
        }

        @Override
        public void alwaysOnTop(boolean onTop) {
            panelOnTop = onTop;
            host.setAlwaysOnTop(onTop);
        }

        @Override
        public void done() {
            recorder.acknowledge();
            host.hide();
        }
    }

    /** Fecha a janela do Recorder pelo X: nunca encerra a gravação em silêncio. */
    public Runnable closeRequest() {
        return this::requestClose;
    }
}
