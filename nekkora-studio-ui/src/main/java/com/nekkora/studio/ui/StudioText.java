package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Textos apresentados pelo Studio (contagens, numeração, datas). Concentrar a formatação aqui evita
 * strings soltas nas telas e mantém a regra: nada de identificadores internos no texto do usuário.
 */
public final class StudioText {
    /** Texto exibido quando um dado técnico existe mas não pode ser apresentado. */
    public static final String NOT_AVAILABLE = "—";

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).withZone(ZoneId.systemDefault());

    private StudioText() {}

    /** Número visual com dois dígitos (01, 02 … 10, 11). */
    public static String number(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }

    public static String steps(int n) {
        return n == 0 ? "Nenhuma etapa" : n == 1 ? "1 etapa" : n + " etapas";
    }

    public static String sessions(int n) {
        return n == 0 ? "Nenhuma sessão" : n == 1 ? "1 sessão" : n + " sessões";
    }

    public static String automations(int n) {
        return n == 0 ? "Nenhuma automação" : n == 1 ? "1 automação" : n + " automações";
    }

    /** Resumo "3 sessões · 7 etapas". */
    public static String summary(Automation a) {
        return sessions(a.sessions().size()) + " · " + steps(a.stepCount()).toLowerCase(Locale.ROOT);
    }

    public static String dateTime(Instant instant) {
        return DATE_TIME.format(instant);
    }

    /** Valor opcional: vazio vira {@link #NOT_AVAILABLE}. */
    public static String orDash(String text) {
        return text == null || text.isBlank() ? NOT_AVAILABLE : text;
    }

    /** Dado técnico vindo de produtores: só aparece se for seguro (sem IDs internos nem dados sensíveis). */
    public static String technical(String text) {
        return text == null || text.isBlank() || !DisplaySafety.isSafeToDisplay(text) ? NOT_AVAILABLE : text;
    }

    /** Estado básico da etapa para quem lê: vínculo com elemento (texto, não depende de cor). */
    public static String stepState(AutomationStep step) {
        if (step.element().isPresent()) return "Elemento vinculado";
        return switch (step.actionType().target()) {
            case EXPECTED -> "Elemento não vinculado";
            case OPTIONAL, NONE -> "Sem elemento";
        };
    }
}
