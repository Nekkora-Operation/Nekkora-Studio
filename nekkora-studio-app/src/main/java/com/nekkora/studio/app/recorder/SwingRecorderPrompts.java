package com.nekkora.studio.app.recorder;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.ui.recorder.RecorderText;
import com.nekkora.studio.ui.recorder.RecordingSetupPanel;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.icon.IconName;
import java.awt.Component;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Diálogos reais do Recorder (NekkoraDialog), ancorados na janela do Studio. */
public final class SwingRecorderPrompts implements RecorderPrompts {
    private final Supplier<Component> parent;

    public SwingRecorderPrompts(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public Optional<RecordingSetupPanel.Choice> chooseRecording(Automation automation, SessionId preselected, SetupSource source) {
        NekkoraDialog d = new NekkoraDialog(parent.get(), "Iniciar gravação");
        d.setMarker(IconName.PLAY_CIRCLE, Tone.ACCENT);
        d.setDescription("Escolha a sessão e a aba do Chrome. A captura técnica acompanha a página; as ações ainda não viram etapas.");
        AtomicReference<RecordingSetupPanel> ref = new AtomicReference<>();
        Runnable reload = () -> {
            RecordingSetupPanel p = ref.get();
            p.showLink(source.link());
            if (source.link() == null || !source.link().connected()) {
                p.showError("Sem conexão com o navegador. " + RecorderText.connectHint(source.link()));
                return;
            }
            p.showLoading();
            source.tabs().whenComplete((tabs, e) -> SwingUtilities.invokeLater(() -> {
                if (e == null) {
                    p.showTabs(tabs);
                } else {
                    p.showError("Não foi possível consultar as abas: o navegador não confirmou a consulta.");
                }
            }));
        };
        RecordingSetupPanel real = new RecordingSetupPanel(automation, preselected, reload);
        ref.set(real);
        d.setContent(real);
        d.setDialogSize(NekkoraDialog.Size.LARGE);
        d.addSecondary("Cancelar");
        NekkoraButton start = d.addPrimary("Iniciar gravação", () -> real.choice().isPresent());
        start.setEnabled(false);
        real.onSelectionChange(() -> start.setEnabled(real.choice().isPresent()));
        reload.run();
        return d.showDialog() == NekkoraDialog.Result.CONFIRMED ? real.choice() : Optional.empty();
    }

    @Override
    public CloseDecision askClose() {
        NekkoraDialog d = new NekkoraDialog(parent.get(), "A gravação continua ativa");
        d.setMarker(IconName.ALERT, Tone.WARNING);
        d.setDescription("Fechar o Recorder não encerra a gravação técnica. O que deseja fazer?");
        AtomicReference<CloseDecision> out = new AtomicReference<>(CloseDecision.CONTINUE);
        d.setDialogSize(NekkoraDialog.Size.MEDIUM);
        d.addSecondary("Manter a gravação");
        d.addDanger("Cancelar gravação", () -> {
            out.set(CloseDecision.CANCEL);
            return true;
        });
        d.addPrimary("Finalizar", () -> {
            out.set(CloseDecision.FINISH);
            return true;
        });
        return d.showDialog() == NekkoraDialog.Result.CONFIRMED ? out.get() : CloseDecision.CONTINUE;
    }

    @Override
    public ExitDecision askExit() {
        NekkoraDialog d = new NekkoraDialog(parent.get(), "Há uma gravação técnica em andamento");
        d.setMarker(IconName.ALERT, Tone.WARNING);
        d.setDescription("Antes de fechar o Studio, finalize ou cancele a gravação. O navegador confirma antes do encerramento.");
        AtomicReference<ExitDecision> out = new AtomicReference<>(ExitDecision.STAY);
        d.setDialogSize(NekkoraDialog.Size.MEDIUM);
        d.addSecondary("Voltar ao Studio");
        d.addDanger("Cancelar e sair", () -> {
            out.set(ExitDecision.CANCEL);
            return true;
        });
        d.addPrimary("Finalizar e sair", () -> {
            out.set(ExitDecision.FINISH);
            return true;
        });
        return d.showDialog() == NekkoraDialog.Result.CONFIRMED ? out.get() : ExitDecision.STAY;
    }
}
