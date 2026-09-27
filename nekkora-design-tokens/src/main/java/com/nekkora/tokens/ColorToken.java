package com.nekkora.tokens;

/**
 * Tokens semânticos de cor.
 *
 * <p>Componentes e telas pedem cores por <b>papel</b> ("fundo de superfície", "texto secundário",
 * "ação primária"), nunca por valor. Cada tema resolve todos os papéis — trocar de tema troca
 * tudo de uma vez.
 *
 * <p>O {@link #key()} segue a nomenclatura do design system (ex.: {@code background.canvas}).
 */
public enum ColorToken {
    // ---------------------------------------------------------------- Fundos
    /** Fundo da janela / área mais externa. */
    BACKGROUND_CANVAS("background.canvas"),
    /** Painéis, cards, áreas de trabalho. */
    BACKGROUND_SURFACE("background.surface"),
    /** Elementos acima da superfície: popups, menus, diálogos, tooltips. */
    BACKGROUND_ELEVATED("background.elevated"),
    /** Áreas rebaixadas: trilhos, poços, fundos de código. */
    BACKGROUND_SUNKEN("background.sunken"),
    /** Camada translúcida de hover sobre qualquer fundo. */
    BACKGROUND_HOVER("background.hover"),
    /** Camada translúcida de pressionado sobre qualquer fundo. */
    BACKGROUND_PRESSED("background.pressed"),
    /** Fundo de campos de entrada. */
    FIELD_BACKGROUND("field.background"),

    // ---------------------------------------------------------------- Texto
    TEXT_PRIMARY("text.primary"),
    TEXT_SECONDARY("text.secondary"),
    TEXT_MUTED("text.muted"),
    TEXT_DISABLED("text.disabled"),
    /** Texto sobre fundos invertidos (ex.: sobre a cor de destaque). */
    TEXT_INVERSE("text.inverse"),
    TEXT_LINK("text.link"),

    // ---------------------------------------------------------------- Bordas
    BORDER_SUBTLE("border.subtle"),
    BORDER_DEFAULT("border.default"),
    BORDER_STRONG("border.strong"),
    BORDER_FOCUS("border.focus"),

    // ---------------------------------------------------------------- Ações
    ACTION_PRIMARY("action.primary"),
    ACTION_PRIMARY_HOVER("action.primaryHover"),
    ACTION_PRIMARY_PRESSED("action.primaryPressed"),
    ACTION_PRIMARY_TEXT("action.primaryText"),
    ACTION_SECONDARY("action.secondary"),
    ACTION_SECONDARY_HOVER("action.secondaryHover"),
    ACTION_SECONDARY_PRESSED("action.secondaryPressed"),
    ACTION_SECONDARY_TEXT("action.secondaryText"),
    ACTION_SECONDARY_BORDER("action.secondaryBorder"),
    ACTION_DANGER("action.danger"),
    ACTION_DANGER_HOVER("action.dangerHover"),
    ACTION_DANGER_PRESSED("action.dangerPressed"),
    ACTION_DANGER_TEXT("action.dangerText"),

    // ---------------------------------------------------------------- Destaque da marca
    ACCENT("accent"),
    /** Destaque translúcido (fundos de ênfase leve). */
    ACCENT_SOFT("accent.soft"),
    /** Destaque sólido que suporta texto branco com contraste AA. */
    ACCENT_SOLID("accent.solid"),

    // ---------------------------------------------------------------- Status
    STATUS_SUCCESS("status.success"),
    STATUS_SUCCESS_SOFT("status.successSoft"),
    STATUS_SUCCESS_SOLID("status.successSolid"),
    STATUS_WARNING("status.warning"),
    STATUS_WARNING_SOFT("status.warningSoft"),
    STATUS_WARNING_SOLID("status.warningSolid"),
    STATUS_ERROR("status.error"),
    STATUS_ERROR_SOFT("status.errorSoft"),
    STATUS_ERROR_SOLID("status.errorSolid"),
    STATUS_INFO("status.info"),
    STATUS_INFO_SOFT("status.infoSoft"),
    STATUS_INFO_SOLID("status.infoSolid"),
    STATUS_NEUTRAL("status.neutral"),
    STATUS_NEUTRAL_SOFT("status.neutralSoft"),
    STATUS_NEUTRAL_SOLID("status.neutralSolid"),
    /** Texto/ícone sobre qualquer fundo sólido de status. */
    STATUS_ON_SOLID("status.onSolid"),

    // ---------------------------------------------------------------- Seleção e foco
    SELECTION_BACKGROUND("selection.background"),
    SELECTION_TEXT("selection.text"),
    /** Seleção quando o componente não tem foco. */
    SELECTION_INACTIVE("selection.inactive"),
    FOCUS_RING("focus.ring"),

    // ---------------------------------------------------------------- Sobreposição
    OVERLAY_SCRIM("overlay.scrim"),
    SHADOW("shadow"),

    // ---------------------------------------------------------------- Marca (logo)
    BRAND_MARK("brand.mark"),
    BRAND_LENS("brand.lens"),
    BRAND_PUPIL("brand.pupil"),
    BRAND_WORDMARK("brand.wordmark");

    private final String key;

    ColorToken(String key) {
        this.key = key;
    }

    /** Nome do token no design system, ex.: {@code background.canvas}. */
    public String key() {
        return key;
    }

    /** Procura um token pelo nome ({@code background.canvas}); lança se não existir. */
    public static ColorToken fromKey(String key) {
        for (ColorToken t : values()) {
            if (t.key.equals(key)) return t;
        }
        throw new IllegalArgumentException("Token de cor desconhecido: " + key);
    }
}
