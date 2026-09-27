package com.nekkora.tokens;

import java.util.List;
import java.util.Optional;

/** Os temas oficiais da Nekkora. O primeiro da lista é o padrão. */
public final class NekkoraThemes {
    private NekkoraThemes() {}

    /** Identidade/autoral: o tema mais "Nekkora", rosa elegante sobre ameixa profunda. */
    public static final NekkoraTheme SAKURA_DARK = ThemeFactory.create("sakura-dark", "Sakura Dark", ThemeMode.DARK,
            Palette.of("#1b0c1a", "#2d222f", "#4b2138", "#6d3c52", "#765d67", "#fadcd5", "#f8f2f7",
                    "#f0a3b8", "#f5bacb", "#e48ca5", "#b8486f"));

    /** Técnico/profissional: azul noturno, corporativo sem ser "SaaS genérico". */
    public static final NekkoraTheme MIDNIGHT_DARK = ThemeFactory.create("midnight-dark", "Midnight Dark", ThemeMode.DARK,
            Palette.of("#0d1117", "#161b22", "#212936", "#526d9e", "#7189b5", "#afc8f2", "#f2f5f8",
                    "#8fb0ea", "#a8c3f1", "#7897d6", "#4a6fb5"));

    /** Neutro/institucional, claro. */
    public static final NekkoraTheme PORCELAIN_LIGHT = ThemeFactory.create("porcelain-light", "Porcelain Light", ThemeMode.LIGHT,
            Palette.of("#f7f6f4", "#ffffff", "#eceae6", "#4e5d78", "#76839a", "#d7cbbf", "#24262b",
                    "#4e5d78", "#414e66", "#36415a", "#4e5d78"));

    /** Calmo e sofisticado, claro. */
    public static final NekkoraTheme SAGE_LIGHT = ThemeFactory.create("sage-light", "Sage Light", ThemeMode.LIGHT,
            Palette.of("#f4f6f1", "#ffffff", "#e6eae2", "#506b62", "#80958d", "#c7d6ce", "#202724",
                    "#506b62", "#435a52", "#384c45", "#506b62"));

    private static final List<NekkoraTheme> ALL = List.of(SAKURA_DARK, MIDNIGHT_DARK, PORCELAIN_LIGHT, SAGE_LIGHT);

    /** Tema padrão da marca. */
    public static NekkoraTheme defaultTheme() {
        return SAKURA_DARK;
    }

    /** Todos os temas oficiais, na ordem de exibição. */
    public static List<NekkoraTheme> all() {
        return ALL;
    }

    /** Busca por id ({@code sakura-dark}, {@code midnight-dark}, ...). */
    public static Optional<NekkoraTheme> byId(String id) {
        return ALL.stream().filter(t -> t.id().equals(id)).findFirst();
    }
}
