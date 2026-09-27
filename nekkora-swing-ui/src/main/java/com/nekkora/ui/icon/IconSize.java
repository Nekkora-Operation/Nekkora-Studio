package com.nekkora.ui.icon;

/** Tamanhos de ícone (px lógicos). */
public enum IconSize {
    /** Densos: células de tabela, badges, breadcrumbs. */
    S(14),
    /** Padrão: botões, menus, itens de lista. */
    M(16),
    /** Toolbars grandes, cabeçalhos. */
    L(20),
    /** Estados vazios, marcadores. */
    XL(24),
    /** Ilustrativos (estados vazios grandes). */
    XXL(40);

    private final int px;

    IconSize(int px) {
        this.px = px;
    }

    public int px() {
        return px;
    }
}
