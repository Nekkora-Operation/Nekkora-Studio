package com.nekkora.ui.theme;

import com.nekkora.tokens.NekkoraTheme;

/** Notificado (na EDT) depois que o tema foi trocado e a árvore de componentes atualizada. */
@FunctionalInterface
public interface ThemeListener {
    void themeChanged(NekkoraTheme oldTheme, NekkoraTheme newTheme);
}
