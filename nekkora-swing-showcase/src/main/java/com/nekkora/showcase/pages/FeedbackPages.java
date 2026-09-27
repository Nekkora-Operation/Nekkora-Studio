package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.kit.Page;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.feedback.NekkoraBanner;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.feedback.NekkoraProgressBar;
import com.nekkora.ui.feedback.NekkoraSkeleton;
import com.nekkora.ui.feedback.NekkoraSpinner;
import com.nekkora.ui.feedback.NekkoraToast;
import com.nekkora.ui.feedback.NekkoraTooltips;
import com.nekkora.ui.icon.IconName;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;

/** Feedback. */
public final class FeedbackPages {
    private FeedbackPages() {}

    public static JComponent feedback() {
        Page p = new Page("Feedback", "Alertas, banners, toasts, progresso, spinners, skeletons e tooltips — "
                + "sempre com ícone e texto (Info, Sucesso, Atenção, Erro).");
        NekkoraStack alerts = NekkoraStack.vertical(Spacing.SPACE_4);
        alerts.add(new NekkoraInlineAlert(Severity.INFO, "Nova versão disponível", "Reinicie para aplicar as melhorias."));
        alerts.add(new NekkoraInlineAlert(Severity.SUCCESS, "Projeto publicado", "Ele já está disponível para execução."));
        alerts.add(new NekkoraInlineAlert(Severity.WARNING, "Credencial expira em 3 dias", null)
                .addAction("Renovar agora", () -> { }).dismissible(null));
        alerts.add(new NekkoraInlineAlert(Severity.ERROR, "Falha ao salvar",
                "O arquivo está bloqueado por outro processo. Feche-o e tente novamente.").addAction("Tentar novamente", () -> { }));
        p.section("Alertas em linha", null, new Fixed(alerts, 620, 0));
        p.section("Banner", "Aviso de largura total no topo de uma área.",
                new NekkoraBanner(Severity.WARNING, "Você está offline", "As alterações serão sincronizadas quando a conexão voltar."));

        NekkoraStack toasts = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        for (Severity s : Severity.values()) {
            NekkoraButton b = NekkoraButton.secondary(s.label());
            b.addActionListener(e -> NekkoraToast.show(b, s, toastTitle(s), toastMessage(s)));
            toasts.add(b);
        }
        toasts.addGlue();
        p.section("Toasts", "Aparecem no canto inferior direito e somem sozinhos (o mouse em cima pausa).", toasts);

        NekkoraProgressBar p40 = new NekkoraProgressBar();
        p40.setValue(40);
        NekkoraProgressBar done = new NekkoraProgressBar();
        done.setValue(100);
        done.setTone(Tone.SUCCESS);
        NekkoraProgressBar fail = new NekkoraProgressBar();
        fail.setValue(70);
        fail.setTone(Tone.ERROR);
        p.section("Progresso", null, new Fixed(Page.column(
                Page.specimen("40%", p40), Page.specimen("concluído", done), Page.specimen("falhou", fail),
                Page.specimen("indeterminado", NekkoraProgressBar.indeterminate())), 420, 0));
        p.section("Spinners", null, Page.tightRow(new NekkoraSpinner(NekkoraSpinner.Size.SMALL),
                new NekkoraSpinner(NekkoraSpinner.Size.MEDIUM), new NekkoraSpinner(NekkoraSpinner.Size.LARGE)));
        NekkoraStack skel = NekkoraStack.horizontal(Spacing.SPACE_5).align(NekkoraStack.Align.START);
        skel.add(new NekkoraSkeleton(NekkoraSkeleton.Shape.CIRCLE));
        skel.add(new NekkoraSkeleton(NekkoraSkeleton.Shape.TEXT, 3));
        skel.add(new NekkoraSkeleton(NekkoraSkeleton.Shape.BLOCK));
        skel.addGlue();
        p.section("Skeleton", "Placeholder de carregamento que antecipa o formato do conteúdo.", skel);

        NekkoraIconButton save = new NekkoraIconButton(IconName.SAVE, "Salvar");
        NekkoraTooltips.install(save, "Salvar", NekkoraKeys.shortcut(KeyEvent.VK_S));
        NekkoraButton plain = NekkoraButton.secondary("Passe o mouse");
        plain.setToolTipText("Tooltip simples, com o visual do tema");
        p.section("Tooltips", "Rótulo + atalho de teclado. Aparecem após 500 ms.", Page.tightRow(save, plain));
        return p;
    }

    private static String toastTitle(Severity s) {
        return switch (s) {
            case INFO -> "Sincronização iniciada";
            case SUCCESS -> "Projeto salvo";
            case WARNING -> "Execução lenta";
            case ERROR -> "Não foi possível publicar";
        };
    }

    private static String toastMessage(Severity s) {
        return switch (s) {
            case SUCCESS -> null;
            case INFO -> "Você pode continuar trabalhando.";
            case WARNING -> "A última etapa levou mais que o esperado.";
            case ERROR -> "Verifique as credenciais e tente novamente.";
        };
    }
}
