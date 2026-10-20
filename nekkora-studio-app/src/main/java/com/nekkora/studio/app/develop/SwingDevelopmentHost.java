package com.nekkora.studio.app.develop;

import com.nekkora.studio.ui.develop.DevelopmentPanel;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.file.NekkoraFileDialogs;
import com.nekkora.ui.technical.NekkoraMonospaceTextArea;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** STUDIO-014 — janela real de "Desenvolver automação": não modal, cabe em 1366×768 e em 150%. */
public final class SwingDevelopmentHost implements DevelopmentPresenter.Host {
    private final Supplier<JFrame> owner;
    private final DevelopmentPanel panel;
    private JDialog dialog;

    public SwingDevelopmentHost(Supplier<JFrame> owner, DevelopmentPanel panel) {
        this.owner = owner;
        this.panel = panel;
    }

    @Override
    public void show() {
        if (dialog == null) {
            dialog = new JDialog(owner.get(), DevelopmentPanel.TITLE, Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            NekkoraPanel root = new NekkoraPanel(new BorderLayout());
            root.setPadding(Spacing.SPACE_5);
            root.add(panel, BorderLayout.CENTER);
            dialog.setContentPane(root);
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            dialog.setSize(Math.min(1180, usable.width - 40), Math.min(800, usable.height - 40));
            dialog.setLocation(usable.x + (usable.width - dialog.getWidth()) / 2, usable.y + (usable.height - dialog.getHeight()) / 2);
            dialog.getAccessibleContext().setAccessibleDescription(DevelopmentPanel.EXPLANATION);
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override
    public void hide() {
        if (dialog != null) dialog.setVisible(false);
    }

    @Override
    public Optional<Path> chooseFolder(String title) {
        return NekkoraFileDialogs.selectFolder(dialog, title);
    }

    @Override
    public Optional<Path> choosePython() {
        return NekkoraFileDialogs.openFile(dialog, "Python para verificação (com nekkora_rpa, ruff e mypy)", "Interpretador Python", "exe", "");
    }

    @Override
    public void showText(String title, String text) {
        NekkoraDialog d = new NekkoraDialog(dialog, title);
        d.setDialogSize(NekkoraDialog.Size.LARGE);
        NekkoraMonospaceTextArea area = new NekkoraMonospaceTextArea(text, 18);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.getAccessibleContext().setAccessibleName(title);
        d.setContent(new NekkoraScrollPane(area));
        d.addPrimary("Fechar");
        d.showDialog();
    }

    @Override
    public boolean consent(String title, List<String> sent, List<String> notSent, String payload) {
        NekkoraDialog d = new NekkoraDialog(dialog, title);
        d.setDialogSize(NekkoraDialog.Size.LARGE);
        d.setDescription("A análise por IA é opcional e usa um serviço externo. Só o que está abaixo é enviado; a resposta é tratada como sugestão.");
        NekkoraStack s = NekkoraStack.vertical(Spacing.SPACE_4);
        s.add(new NekkoraParagraph("Vai: " + String.join("; ", sent) + ".", TextTone.PRIMARY));
        s.add(new NekkoraParagraph("Não vai: " + String.join("; ", notSent) + ".", TextTone.SECONDARY));
        NekkoraMonospaceTextArea area = new NekkoraMonospaceTextArea(payload, 10);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setCaretPosition(0);
        area.getAccessibleContext().setAccessibleName("Prévia exata do envio");
        s.add(new NekkoraScrollPane(area));
        d.setContent(s);
        d.addSecondary("Não enviar");
        d.addPrimary("Enviar esta análise");
        return d.showDialog() == NekkoraDialog.Result.CONFIRMED;
    }

    @Override
    public boolean confirm(String title, String message, String action) {
        return com.nekkora.ui.dialog.NekkoraConfirmationDialog.confirmDanger(dialog, title, message, action);
    }

    @Override
    public void openFolder(Path p) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(p.toFile());
        } catch (IOException | UnsupportedOperationException e) {
            showText("Abrir código", "A pasta não pôde ser aberta pelo sistema. Caminho: " + p);
        }
    }

    public JDialog dialog() {
        return dialog;
    }
}
