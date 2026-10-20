package com.nekkora.studio.ui.recorder;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.recorder.RecorderSnapshot;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.data.NekkoraKeyValueView;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import java.util.Objects;
import javax.swing.JPanel;

/**
 * Conteúdo do Floating Recorder: estado CONFIRMADO, automação, sessão (trocável), aba, modo, captura e cobertura,
 * comandos e os avisos desta versão. É uma visão: recebe fotografias do Recorder e devolve intenções do usuário.
 * Somente componentes e tokens do Nekkora Swing UI.
 */
public final class FloatingRecorderPanel extends JPanel {

    /** Intenções do usuário. */
    public interface Actions {
        void pause();

        void resume();

        void finish();

        void cancel();

        void newSession();

        void changeSession(SessionId session);

        void changeMode(RecordingMode mode);

        /** Desistir de esperar o navegador (ligação perdida): encerra só no Studio. */
        void abandon();

        void collapse();

        void alwaysOnTop(boolean onTop);

        /** Desfecho visto: voltar ao Studio. */
        void done();

        /**
         * Pedido da usuária (19/10/2026): depois de finalizar, capturar a tela para o PDD — a imagem vai para o bloco das
         * etapas que acabaram de ser gravadas (gesto Alt+Shift+K no Chrome, como na janela do PDD).
         */
        default void capturePdd() {}
    }

    private static final String LIVE = "live";
    private static final String SUMMARY = "summary";

    private final Actions actions;
    private final NekkoraStatusBadge badge = new NekkoraStatusBadge(com.nekkora.ui.core.Status.PENDING, "");
    private final NekkoraInlineAlert problem = new NekkoraInlineAlert(Severity.WARNING, "", "");
    private final NekkoraText automationName = new NekkoraText("—");
    private final NekkoraComboBox<LogicalSession> sessions = new NekkoraComboBox<>();
    private final NekkoraText tab = new NekkoraText("—");
    private final NekkoraText captureText = new NekkoraText("—");
    private final NekkoraText coverageText = new NekkoraText("—");
    private final NekkoraText stepsText = new NekkoraText("—");
    private final NekkoraText evidenceText = new NekkoraText("—");
    private final NekkoraText lastObservationText = new NekkoraText("—");
    private final NekkoraParagraph sessionNote = note("", TextTone.SECONDARY);
    private final NekkoraParagraph stepsNotice = note(RecorderText.ACTION_CAPTURE_NOTICE, TextTone.SECONDARY);
    private final NekkoraRadioButton captureOnly = new NekkoraRadioButton(RecordingMode.CAPTURE_ONLY.label());
    private final NekkoraRadioButton captureAndInteract = new NekkoraRadioButton(RecordingMode.CAPTURE_AND_INTERACT.label());
    private final NekkoraParagraph modeNote = note(RecorderText.MODE_NOTICE, TextTone.MUTED);
    private final NekkoraButton pauseResume = NekkoraButton.secondary("Pausar").withIcon(IconName.PAUSE).withSize(ControlSize.SMALL);
    private final NekkoraButton finish = NekkoraButton.primary("Finalizar").withIcon(IconName.STOP).withSize(ControlSize.SMALL);
    private final NekkoraButton newSession = NekkoraButton.ghost("Nova sessão").withIcon(IconName.LAYERS).withSize(ControlSize.SMALL);
    private final NekkoraButton cancel = NekkoraButton.ghost("Cancelar").withIcon(IconName.CLOSE).withSize(ControlSize.SMALL);
    private final NekkoraButton abandon = NekkoraButton.danger("Encerrar no Studio").withSize(ControlSize.SMALL);
    private final NekkoraCheckBox onTop = new NekkoraCheckBox("Manter acima das outras janelas", true);
    private final NekkoraButton collapse = NekkoraButton.ghost("Recolher").withIcon(IconName.MINUS).withSize(ControlSize.SMALL);
    private final NekkoraStack summaryLines = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraText summaryTitle = new NekkoraText("");
    private final NekkoraButton capturePdd = NekkoraButton.secondary("Capturar tela").withIcon(IconName.PLAY).withSize(ControlSize.SMALL);
    private final NekkoraParagraph pddStatus = note("", TextTone.SECONDARY);
    private final CardLayout cards = new CardLayout();
    private RecorderSnapshot last;
    private boolean adjusting;

    public FloatingRecorderPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = Objects.requireNonNull(actions);
        setOpaque(false);
        setLayout(cards);

        NekkoraStack live = NekkoraStack.vertical(Spacing.SPACE_4);
        NekkoraStack head = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        head.add(badge);
        head.addGlue();
        live.add(head);
        problem.setVisible(false);
        live.add(problem);

        sessions.setLabelProvider(s -> s == null ? "" : s.name());
        sessions.getAccessibleContext().setAccessibleName("Sessão lógica da gravação");
        sessions.addActionListener(e -> {
            LogicalSession s = sessions.getSelectedValue();
            if (!adjusting && s != null && last != null && !s.id().equals(last.session())) {
                actions.changeSession(s.id());
            }
        });
        NekkoraKeyValueView kv = new NekkoraKeyValueView();
        kv.add("Automação", automationName);
        kv.add("Sessão", sessions);
        kv.add("Aba", tab);
        kv.add("Captura", captureText);
        kv.add("Cobertura", coverageText);
        kv.add("Etapas", stepsText);
        stepsText.getAccessibleContext().setAccessibleName("Etapas registradas");
        kv.add("Evidências", evidenceText); // STUDIO-005: observadas na página — não são validação de negócio
        evidenceText.getAccessibleContext().setAccessibleName("Evidências observadas");
        kv.add("Última observação", lastObservationText);
        lastObservationText.getAccessibleContext().setAccessibleName("Última observação");
        live.add(kv);
        sessionNote.setVisible(false);
        live.add(sessionNote); // troca de sessão pedida e ainda não confirmada pela extensão

        NekkoraRadioButton.group(captureOnly, captureAndInteract);
        captureOnly.addActionListener(e -> {
            if (!adjusting) {
                actions.changeMode(RecordingMode.CAPTURE_ONLY);
            }
        });
        captureAndInteract.addActionListener(e -> {
            if (!adjusting) {
                actions.changeMode(RecordingMode.CAPTURE_AND_INTERACT);
            }
        });
        captureOnly.setToolTipText(RecordingMode.CAPTURE_ONLY.description());
        captureAndInteract.setToolTipText(RecordingMode.CAPTURE_AND_INTERACT.description());
        NekkoraStack modes = NekkoraStack.vertical(Spacing.SPACE_2);
        NekkoraStack radios = NekkoraStack.horizontal(Spacing.SPACE_5);
        radios.add(new NekkoraText("Modo:"));
        radios.add(captureOnly);
        radios.add(captureAndInteract);
        radios.addGlue();
        modes.add(radios);
        modes.add(modeNote);
        live.add(modes);

        pauseResume.onClick(e -> onPauseResume());
        finish.onClick(e -> actions.finish());
        cancel.onClick(e -> actions.cancel());
        newSession.onClick(e -> actions.newSession());
        abandon.onClick(e -> actions.abandon());
        abandon.getAccessibleContext().setAccessibleDescription("Encerra a gravação apenas no Studio, sem confirmação do navegador.");
        NekkoraStack row1 = NekkoraStack.horizontal(Spacing.SPACE_3);
        row1.add(pauseResume);
        row1.add(finish);
        row1.addGlue();
        NekkoraStack row2 = NekkoraStack.horizontal(Spacing.SPACE_3);
        row2.add(newSession);
        row2.add(cancel);
        row2.add(abandon);
        row2.addGlue();
        live.add(row1);
        live.add(row2);
        live.add(stepsNotice); // por inteiro: limitações e falhas de entrega precisam ser lidas

        onTop.addActionListener(e -> actions.alwaysOnTop(onTop.isSelected()));
        collapse.onClick(e -> actions.collapse());
        collapse.getAccessibleContext().setAccessibleDescription("Esconde o Recorder; ele volta pelo botão Gravar do Studio.");
        NekkoraStack foot = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        foot.add(onTop);
        foot.addGlue();
        foot.add(collapse);
        live.add(foot);

        NekkoraStack sum = NekkoraStack.vertical(Spacing.SPACE_4);
        sum.add(summaryTitle);
        sum.add(summaryLines);
        capturePdd.onClick(e -> actions.capturePdd());
        capturePdd.getAccessibleContext().setAccessibleDescription(
                "Captura a tela para o PDD: a imagem vai para o bloco das etapas desta gravação. Pressione Alt+Shift+K no Chrome depois.");
        pddStatus.setVisible(false);
        sum.add(pddStatus);
        NekkoraStack sumFoot = NekkoraStack.horizontal(Spacing.SPACE_3);
        sumFoot.add(capturePdd);
        sumFoot.addGlue();
        sumFoot.add(NekkoraButton.primary("Voltar ao Studio").withSize(ControlSize.SMALL).onClick(e -> actions.done()));
        sum.add(sumFoot);

        add(live, LIVE);
        add(sum, SUMMARY);
        getAccessibleContext().setAccessibleName("Nekkora Recorder");
    }

    /** Texto pequeno com quebra de linha (avisos nunca truncados com "…"). */
    private static NekkoraParagraph note(String text, TextTone tone) {
        NekkoraParagraph p = new NekkoraParagraph(text, tone);
        p.setTypeStyle(TypeStyle.CAPTION);
        return p;
    }

    private void onPauseResume() {
        if (last == null) {
            return;
        }
        if (last.state() == RecorderState.RECORDING) {
            actions.pause();
        } else {
            actions.resume();
        }
    }

    /** Mostra uma fotografia do Recorder (na EDT). {@code automation} resolve nomes; pode ser nula. */
    public void render(RecorderSnapshot s, Automation automation) {
        last = s;
        adjusting = true;
        try {
            RecorderState st = s.state();
            badge.setStatus(RecorderText.status(st), RecorderText.stateLabel(s));
            badge.getAccessibleContext().setAccessibleName("Estado da gravação: " + RecorderText.stateLabel(s));
            badge.getAccessibleContext().setAccessibleDescription(st.pending() ? "Aguardando a confirmação da extensão" : "Estado confirmado pela extensão");
            if (s.problem() != null) {
                problem.setSeverity(st == RecorderState.ERROR ? Severity.ERROR : Severity.WARNING);
                problem.setTitle(s.problem().title());
                problem.setMessage(s.problem().message());
                problem.setVisible(true);
            } else if (st == RecorderState.DEGRADED && s.linkLost()) {
                problem.setSeverity(Severity.WARNING);
                problem.setTitle("Conexão com o navegador perdida");
                problem.setMessage("Aguardando a extensão voltar. Nada foi dado como finalizado.");
                problem.setVisible(true);
            } else {
                problem.setVisible(false);
            }
            automationName.setText(automation == null ? "—" : automation.name());
            List<LogicalSession> list = automation == null ? List.of() : automation.sessions();
            sessions.removeAllItems();
            LogicalSession current = null;
            for (LogicalSession ls : list) {
                sessions.addItem(ls);
                if (ls.id().equals(s.session())) {
                    current = ls;
                }
            }
            sessions.setSelectedItem(current); // sempre a sessão CONFIRMADA
            boolean stable = st == RecorderState.RECORDING || st == RecorderState.PAUSED || st == RecorderState.DEGRADED;
            sessions.setEnabled(stable && !s.linkLost() && s.pendingSession() == null);
            String pendingName = null;
            for (LogicalSession ls : list) {
                if (ls.id().equals(s.pendingSession())) {
                    pendingName = ls.name();
                }
            }
            sessionNote.setText(s.pendingSession() == null ? "" : RecorderText.sessionPending(pendingName));
            sessionNote.setVisible(s.pendingSession() != null);
            tab.setText(s.tabTitle() == null ? "—" : s.tabTitle() + (s.tabHost() != null && !s.tabHost().equals(s.tabTitle()) ? " · " + s.tabHost() : ""));
            captureText.setText(RecorderText.capture(s));
            coverageText.setText(RecorderText.coverage(s.progress()));
            stepsText.setText(RecorderText.steps(s.steps()));
            evidenceText.setText(RecorderText.evidence(s.evidence()));
            lastObservationText.setText(RecorderText.lastObservation(s.evidence()));
            stepsNotice.setText(RecorderText.stepsNotice(s.steps()));
            RecordingMode shown = s.pendingMode() != null ? s.pendingMode() : s.mode();
            captureOnly.setSelected(shown == RecordingMode.CAPTURE_ONLY);
            captureAndInteract.setSelected(shown == RecordingMode.CAPTURE_AND_INTERACT);
            boolean modeEditable = stable && !s.linkLost() && s.pendingMode() == null;
            captureOnly.setEnabled(modeEditable);
            captureAndInteract.setEnabled(modeEditable);
            modeNote.setText(s.pendingMode() != null ? "Aguardando a confirmação da extensão… " + RecorderText.MODE_NOTICE : RecorderText.MODE_NOTICE);

            boolean canPause = st == RecorderState.RECORDING;
            boolean canResume = st == RecorderState.PAUSED || (st == RecorderState.DEGRADED && !s.linkLost());
            pauseResume.setText(st == RecorderState.DEGRADED ? "Tentar retomar" : canResume ? "Continuar" : "Pausar");
            pauseResume.setLeadingIcon(canResume ? (st == RecorderState.DEGRADED ? IconName.REFRESH : IconName.PLAY) : IconName.PAUSE);
            pauseResume.setEnabled(canPause || canResume);
            pauseResume.setLoading(st == RecorderState.PAUSING || st == RecorderState.RESUMING || st == RecorderState.RECOVERING);
            boolean canEnd = stable && !s.linkLost();
            finish.setEnabled(canEnd);
            finish.setLoading(st == RecorderState.FINALIZING);
            cancel.setEnabled(canEnd);
            newSession.setEnabled(stable && !s.linkLost());
            abandon.setVisible(s.linkLost());

            if (st == RecorderState.COMPLETED || st == RecorderState.CANCELLED || st == RecorderState.ERROR) {
                boolean actions = s.summary() != null && s.summary().actionsCaptured();
                summaryTitle.setText(st == RecorderState.COMPLETED ? (actions ? "Gravação concluída" : "Gravação técnica concluída")
                        : st == RecorderState.CANCELLED ? (actions ? "Gravação cancelada: as etapas desta gravação foram descartadas" : "Gravação técnica cancelada")
                        : (s.problem() != null ? s.problem().title() : "Gravação encerrada"));
                summaryLines.removeAll();
                if (s.summary() != null) {
                    for (String line : RecorderText.summary(s.summary())) {
                        summaryLines.add(note(line, TextTone.SECONDARY));
                    }
                } else if (s.problem() != null) {
                    summaryLines.add(note(s.problem().message(), TextTone.SECONDARY));
                }
                // só depois de uma gravação concluída com etapas registradas
                capturePdd.setVisible(st == RecorderState.COMPLETED && s.steps() != null && s.steps().accepted() > 0);
                cards.show(this, SUMMARY);
            } else {
                cards.show(this, LIVE);
            }
        } finally {
            adjusting = false;
        }
        revalidate();
        repaint();
    }

    /** Situação da captura para o PDD pedida daqui (vazio = esconde). */
    public void setPddStatus(String text, boolean busy) {
        pddStatus.setText(text == null ? "" : text);
        pddStatus.setVisible(text != null && !text.isEmpty());
        capturePdd.setEnabled(!busy);
        revalidate();
        repaint();
    }

    public NekkoraButton capturePddButton() {
        return capturePdd;
    }

    public NekkoraParagraph pddStatusText() {
        return pddStatus;
    }

    public void setAlwaysOnTopChecked(boolean value) {
        onTop.setSelected(value);
    }

    // ---- acesso para testes e evidências
    public NekkoraButton pauseResumeButton() {
        return pauseResume;
    }

    public NekkoraButton finishButton() {
        return finish;
    }

    public NekkoraButton cancelButton() {
        return cancel;
    }

    public NekkoraButton newSessionButton() {
        return newSession;
    }

    /** Aviso de troca de sessão aguardando a confirmação da extensão (oculto quando não há troca pendente). */
    public NekkoraParagraph sessionNote() {
        return sessionNote;
    }

    public NekkoraComboBox<LogicalSession> sessionCombo() {
        return sessions;
    }

    public NekkoraRadioButton modeButton(RecordingMode m) {
        return m == RecordingMode.CAPTURE_ONLY ? captureOnly : captureAndInteract;
    }

    public NekkoraStatusBadge badge() {
        return badge;
    }

    public NekkoraInlineAlert problemAlert() {
        return problem;
    }

    public NekkoraText captureLabel() {
        return captureText;
    }

    public NekkoraButton abandonButton() {
        return abandon;
    }

    public NekkoraCheckBox alwaysOnTopBox() {
        return onTop;
    }

    public NekkoraButton collapseButton() {
        return collapse;
    }

    public boolean showingSummary() {
        for (java.awt.Component c : getComponents()) {
            if (c.isVisible()) {
                return c == getComponent(1);
            }
        }
        return false;
    }
}
