package com.nekkora.ui.core;

import com.formdev.flatlaf.FlatClientProperties;
import java.util.function.Predicate;
import javax.swing.JComponent;

/** Constantes e preferências globais da Nekkora Swing UI. */
public final class NekkoraUI {
    private NekkoraUI() {}

    /**
     * Client property que força um {@link Interaction} na pintura — usado pelo Showcase e pelos
     * testes de screenshot para exibir hover/pressed/foco sem mouse. Não use em telas.
     */
    public static final String FORCED_INTERACTION = "nekkora.forcedInteraction";

    /** Client property: {@code Boolean.TRUE} desenha o anel de foco mesmo sem foco (showcase). */
    public static final String FORCED_FOCUS = "nekkora.forcedFocus";

    private static volatile boolean reducedMotion = Boolean.getBoolean("nekkora.reducedMotion");

    /** Movimento reduzido: animações viram trocas instantâneas ou ficam bem mais lentas. */
    public static boolean isReducedMotion() {
        return reducedMotion;
    }

    public static void setReducedMotion(boolean reduced) {
        reducedMotion = reduced;
    }

    /** Força um estado de interação (somente showcase/testes). */
    public static void forceInteraction(JComponent c, Interaction state) {
        c.putClientProperty(FORCED_INTERACTION, state);
        boolean focus = state == Interaction.FOCUSED;
        c.putClientProperty(FORCED_FOCUS, focus ? Boolean.TRUE : null);
        // componentes pintados pelo FlatLaf (campos, combos) consultam este predicado
        c.putClientProperty(FlatClientProperties.COMPONENT_FOCUS_OWNER,
                focus ? (Predicate<JComponent>) x -> true : null);
        c.repaint();
    }
}
