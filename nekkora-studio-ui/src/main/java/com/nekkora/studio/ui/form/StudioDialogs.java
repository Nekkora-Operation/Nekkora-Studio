package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.dialog.NekkoraConfirmationDialog;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.theme.Scale;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.JComponent;

/**
 * Diálogos do Studio sobre {@link NekkoraDialog}: Esc cancela, Enter confirma, foco no primeiro campo e
 * volta à origem ao fechar. A confirmação chama {@code submit}, que executa a operação de domínio; se o
 * domínio recusar, a mensagem aparece no campo e o diálogo continua aberto.
 */
public final class StudioDialogs {
    private StudioDialogs() {}

    /**
     * Mostra um formulário modal.
     *
     * @return {@code true} se a operação foi confirmada e aceita pelo domínio
     */
    public static <V> boolean showForm(Component parent, FormSpec<V> spec, Function<V, Optional<ValidationProblem>> submit) {
        StudioForm<V> form = spec.form();
        NekkoraDialog d = new NekkoraDialog(parent, spec.title());
        d.setDialogSize(spec.size());
        if (spec.description() != null) d.setDescription(spec.description());
        if (spec.marker() != null) d.setMarker(spec.marker(), Tone.ACCENT);
        d.setContent(boundedScroll(form));
        d.addSecondary("Cancelar");
        d.addPrimary(spec.confirmLabel(), () -> trySubmit(form, submit));
        // Ctrl+Enter confirma também quando o foco está em uma área de texto (onde Enter quebra a linha)
        d.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(NekkoraKeys.shortcut(KeyEvent.VK_ENTER), "studio.confirm");
        d.getRootPane().getActionMap().put("studio.confirm", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                d.getPrimaryButton().doClick();
            }
        });
        d.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                form.initialFocus().requestFocusInWindow();
            }
        });
        return d.showDialog() == NekkoraDialog.Result.CONFIRMED;
    }

    /**
     * Confirmação de um formulário: limpa erros, executa a operação e, se o domínio recusar, mostra a mensagem
     * no campo certo e devolve o foco ao formulário. {@code true} fecha o diálogo.
     */
    public static <V> boolean trySubmit(StudioForm<V> form, Function<V, Optional<ValidationProblem>> submit) {
        form.clearErrors();
        Optional<ValidationProblem> problem = submit.apply(form.values());
        problem.ifPresent(form::showProblem);
        if (problem.isPresent()) form.initialFocus().requestFocusInWindow();
        return problem.isEmpty();
    }

    /** Confirmação de descarte das alterações em memória ao fechar o Studio. */
    public static boolean confirmDiscardOnExit(Component parent, String message) {
        return NekkoraConfirmationDialog.confirmDanger(parent, "Fechar sem salvar?", message, "Fechar e descartar");
    }

    /**
     * Conteúdo rolável com altura limitada à tela — diálogos continuam acessíveis em 1366×768 e com 150% de
     * escala, mesmo quando o formulário é alto.
     */
    static JComponent boundedScroll(Component content) {
        NekkoraScrollPane scroll = NekkoraScrollPane.verticalOnly(content);
        NekkoraPanel holder = new NekkoraPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = content.getPreferredSize();
                return new Dimension(d.width, Math.min(d.height + Scale.px(4), maxContentHeight()));
            }
        };
        holder.add(scroll, BorderLayout.CENTER);
        return holder;
    }

    private static int maxContentHeight() {
        if (GraphicsEnvironment.isHeadless()) return Scale.px(560);
        int usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
        return Math.max(Scale.px(200), usable - Scale.px(220)); // cabeçalho, rodapé e margens do diálogo
    }
}
