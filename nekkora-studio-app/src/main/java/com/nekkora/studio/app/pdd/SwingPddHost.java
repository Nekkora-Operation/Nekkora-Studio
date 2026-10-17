package com.nekkora.studio.app.pdd;

import com.nekkora.studio.ui.pdd.PddPanel;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.file.NekkoraFileDialogs;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** STUDIO-010 — janela real do PDD visual: não modal, do tamanho da área útil (cabe em 1366×768). */
public final class SwingPddHost implements PddPresenter.Host {
    private final Supplier<JFrame> owner;
    private final PddPanel panel;
    private JDialog dialog;

    public SwingPddHost(Supplier<JFrame> owner, PddPanel panel) {
        this.owner = owner;
        this.panel = panel;
    }

    @Override
    public void show() {
        if (dialog == null) {
            dialog = new JDialog(owner.get(), "PDD visual — preparar documentação", Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            NekkoraStack root = NekkoraStack.vertical(Spacing.SPACE_2);
            root.setPadding(Spacing.SPACE_4);
            root.add(panel);
            dialog.setContentPane(root);
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            dialog.setSize(Math.min(1260, usable.width - 40), Math.min(820, usable.height - 40));
            dialog.setLocation(usable.x + (usable.width - dialog.getWidth()) / 2, usable.y + (usable.height - dialog.getHeight()) / 2);
            dialog.getAccessibleContext().setAccessibleDescription("Revisar seções, blocos, instruções e imagens sanitizadas e exportar o PDF.");
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    @Override
    public void hide() {
        if (dialog != null) dialog.setVisible(false);
    }

    @Override
    public Optional<Path> chooseFolder() {
        return NekkoraFileDialogs.selectFolder(dialog, "Pasta para o PDF (versões anteriores nunca são substituídas)");
    }

    public JDialog dialog() {
        return dialog;
    }
}
