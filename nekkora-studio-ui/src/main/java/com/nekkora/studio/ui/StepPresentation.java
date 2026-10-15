package com.nekkora.studio.ui;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepPosition;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.ui.ElementPresentation.Row;
import java.util.ArrayList;
import java.util.List;

/** Linhas apresentáveis de uma etapa — as mesmas no painel de propriedades e no diálogo de detalhes. */
public final class StepPresentation {
    private StepPresentation() {}

    /** Título curto da posição: "Etapa 04 de 07". */
    public static String positionTitle(Automation a, AutomationStep step) {
        StepPosition p = a.positionOf(step.id()).orElseThrow();
        return "Etapa " + StudioText.number(p.number()) + " de " + StudioText.number(a.stepCount());
    }

    /** Dados gerais (sem nome/descrição, que têm campos próprios). */
    public static List<Row> overview(Automation a, AutomationStep step) {
        StepPosition p = a.positionOf(step.id()).orElseThrow();
        LogicalSession s = a.session(p.sessionId()).orElseThrow();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Tipo de ação", step.actionType().label()));
        rows.add(new Row("Sessão", s.name()));
        rows.add(new Row("Posição", StudioText.number(p.number()) + " na automação · " + p.numberInSession() + "ª da sessão"));
        rows.add(new Row("Origem", step.provenance().origin().label()));
        rows.add(new Row("Estado", StudioText.stepState(step)));
        return rows;
    }

    /** Parâmetros e contexto técnico (só o que existe). */
    public static List<Row> technical(AutomationStep step) {
        List<Row> rows = new ArrayList<>();
        step.parameters().value().ifPresent(v -> rows.add(new Row("Valor", value(v))));
        step.parameters().option().ifPresent(o -> rows.add(new Row("Opção", option(o))));
        step.context().ifPresent(c -> rows.addAll(context(c)));
        if (!step.observedOutcome().isBlank()) rows.add(new Row("Resultado observado", StudioText.technical(step.observedOutcome())));
        if (!step.comments().isEmpty()) rows.add(new Row("Comentários", Integer.toString(step.comments().size())));
        rows.add(new Row("Criada em", StudioText.dateTime(step.createdAt())));
        return rows;
    }

    private static final java.util.Map<String, String> EVIDENCE = java.util.Map.of(
            "FORM_SUBMITTED", "envio de formulário", "NAVIGATION_FOLLOWED", "navegação em seguida", "DOUBLE_CLICK", "clique duplo",
            "VIA_KEYBOARD", "pelo teclado", "VIA_LABEL", "pelo rótulo", "FOCUS_CLICK_ABSORBED", "clique de foco incorporado",
            "CUSTOM_COMBOBOX", "lista personalizada", "LOCATORS_VERIFIED_LIVE", "localizador verificado na página real");
    private static final java.util.Map<String, String> LIMITS = java.util.Map.ofEntries(
            java.util.Map.entry("AMBIGUOUS_TARGET", "alvo ambíguo"), java.util.Map.entry("OPTION_NOT_IDENTIFIED", "opção não identificada"),
            java.util.Map.entry("OPTION_NOT_CHOSEN", "opção não escolhida (proposta)"),
            java.util.Map.entry("OPTION_WITHOUT_PROVEN_COMBOBOX", "opção sem lista comprovada"),
            java.util.Map.entry("ELEMENT_UNRESOLVED", "elemento não correlacionado"), java.util.Map.entry("NO_UNIQUE_LOCATOR", "nenhum localizador único"),
            java.util.Map.entry("INDISTINGUISHABLE_LOOKALIKES", "elementos idênticos na página"),
            java.util.Map.entry("ACCESS_RESTRICTED", "acesso restrito na trajetória"),
            java.util.Map.entry("LIVE_CHECK_UNAVAILABLE", "sem verificação na página real"),
            java.util.Map.entry("CROSS_FRAME_ORDER_BY_RECEPTION", "ordem entre quadros pela recepção"),
            java.util.Map.entry("RESENT", "reenviada"));

    /** STUDIO-003: como a etapa gravada foi observada (vazio para etapas que não são gravadas). */
    public static List<Row> recording(AutomationStep step) {
        List<Row> rows = new ArrayList<>();
        step.provenance().recordingObservation().ifPresent(o -> {
            rows.add(new Row("Modo de captura", o.mode().label()));
            rows.add(new Row("Interação", o.observation().label()));
            rows.add(new Row("Resultado de negócio", o.businessOutcome().label()));
            List<String> ev = o.evidence().stream().map(EVIDENCE::get).filter(java.util.Objects::nonNull).toList();
            if (!ev.isEmpty()) rows.add(new Row("Evidências", String.join(", ", ev)));
            List<String> lim = o.limitations().stream().map(LIMITS::get).filter(java.util.Objects::nonNull).distinct().toList();
            rows.add(new Row("Limitações", lim.isEmpty() ? "Nenhuma identificada" : String.join(", ", lim)));
        });
        return rows;
    }

    private static String value(ValueSource v) {
        return v instanceof ValueSource.LiteralValue ? StudioText.technical(v.display()) : v.display();
    }

    private static String option(OptionChoice o) {
        String label = StudioText.technical(o.label());
        return o.value().isBlank() ? label : label + " (" + StudioText.technical(o.value()) + ")";
    }

    private static List<Row> context(StepContext c) {
        List<Row> rows = new ArrayList<>();
        if (!c.application().isBlank()) rows.add(new Row("Aplicação", StudioText.technical(c.application())));
        if (!c.screenTitle().isBlank()) rows.add(new Row("Tela", StudioText.technical(c.screenTitle())));
        if (!c.location().isBlank()) rows.add(new Row("Endereço", StudioText.technical(c.location())));
        return rows;
    }
}
