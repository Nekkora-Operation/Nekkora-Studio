package com.nekkora.ui.feedback;

import com.nekkora.ui.action.NekkoraKeys;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;

/**
 * Tooltips Nekkora. O visual vem do tema; aqui ficam o padrão de conteúdo (rótulo + atalho)
 * e os tempos globais.
 */
public final class NekkoraTooltips {
    private NekkoraTooltips() {}

    /** Tempos: aparece em 500 ms, some após 10 s. Chamado por {@code Nekkora.install()}. */
    public static void configure() {
        ToolTipManager m = ToolTipManager.sharedInstance();
        m.setInitialDelay(500);
        m.setReshowDelay(150);
        m.setDismissDelay(10_000);
    }

    /** Tooltip com rótulo e atalho de teclado, ex.: "Salvar  Ctrl+S". */
    public static void install(JComponent c, String label, KeyStroke shortcut) {
        c.setToolTipText(shortcut == null ? label
                : "<html>" + escape(label) + "&nbsp;&nbsp;<span style='opacity:0.7'><b>"
                        + escape(NekkoraKeys.format(shortcut)) + "</b></span></html>");
        if (c.getAccessibleContext().getAccessibleName() == null) c.getAccessibleContext().setAccessibleName(label);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
