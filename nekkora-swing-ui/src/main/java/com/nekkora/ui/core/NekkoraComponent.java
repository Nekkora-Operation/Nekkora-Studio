package com.nekkora.ui.core;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JComponent;

/**
 * Base dos componentes pintados pela lib. Garante um {@link AccessibleContext} (nome, papel e
 * estados) para leitores de tela — {@code JComponent} puro não tem.
 */
public abstract class NekkoraComponent extends JComponent {

    /** Papel acessível (padrão: rótulo). */
    protected AccessibleRole accessibleRole() {
        return AccessibleRole.LABEL;
    }

    /** Acrescente estados (selecionado, expandido...) ao conjunto. */
    protected void accessibleStates(AccessibleStateSet states) {}

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return accessibleRole();
                }

                @Override
                public AccessibleStateSet getAccessibleStateSet() {
                    AccessibleStateSet s = super.getAccessibleStateSet();
                    accessibleStates(s);
                    return s;
                }
            };
        }
        return accessibleContext;
    }
}
