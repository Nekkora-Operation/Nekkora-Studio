package com.nekkora.studio.ui;

import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.icon.IconName;

/**
 * Intenção visual (ícone e tom do design system) de tipos de ação e origens. Tipos novos caem no
 * padrão e continuam apresentáveis pelo rótulo do domínio — nenhuma tela precisa mudar.
 */
public final class ActionTypeStyle {
    private ActionTypeStyle() {}

    public static IconName icon(ActionType type) {
        if (type == null) return IconName.CIRCLE_DASHED;
        return switch (type) {
            case CLICK -> IconName.ARROW_RIGHT;
            case FILL -> IconName.EDIT;
            case SELECT_OPTION -> IconName.LIST;
            case CHECK -> IconName.CHECK;
            case UNCHECK -> IconName.MINUS;
            case NAVIGATE -> IconName.EXTERNAL_LINK;
            case WAIT_FOR -> IconName.CLOCK;
            case OBSERVE -> IconName.EYE;
            default -> IconName.CIRCLE_DASHED;
        };
    }

    public static Tone tone(ActionType type) {
        return type == null || type == ActionType.UNKNOWN ? Tone.NEUTRAL : Tone.ACCENT;
    }

    public static IconName icon(StepOrigin origin) {
        if (origin == null) return IconName.CIRCLE_DASHED;
        return switch (origin) {
            case MANUAL -> IconName.USER;
            case RECORDED -> IconName.PLAY_CIRCLE;
            case IMPORTED -> IconName.DOWNLOAD;
            case GENERATED -> IconName.AGENT;
        };
    }
}
