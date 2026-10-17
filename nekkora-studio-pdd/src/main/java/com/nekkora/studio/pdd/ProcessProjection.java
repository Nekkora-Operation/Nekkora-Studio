package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.origin.StepOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * STUDIO-010 G1 — projeção documental SOMENTE LEITURA da automação: só os fatos que o PDD pode usar, já sanitizados.
 * Nunca chama uma operação que altere o domínio. Não carrega valores, nomes de variáveis, apelidos de credencial,
 * localizadores, chaves de correlação, URLs nem textos livres que não passem pela política de texto ({@link PddText}).
 *
 * <p>{@link #revision()} ({@code processRevision}) identifica o CONTEÚDO do processo relevante ao documento: ordem de
 * sessões e etapas, ações, tipo e nome seguro do alvo, tela e trocas de tela observadas. Não muda com revisões do
 * domínio, decisões da STUDIO-009 ou textos que o documento não usa.
 */
public final class ProcessProjection {

    /** De onde vem a etapa: observada numa gravação ou declarada pelo desenvolvedor. */
    public enum Origin { RECORDED, DECLARED }

    /** Teclas do vocabulário fechado (a própria ação, não um dado) — o mesmo vocabulário do plano da STUDIO-009. */
    public static final Set<String> KEY_VOCABULARY = Set.of("Enter", "Tab", "Escape");

    /**
     * Fatos de uma etapa.
     *
     * @param targetName nome do alvo apresentável (política de texto) ou vazio
     * @param screenKey  identificação OPACA da tela (resumo do modelo de URL e do título), vazia se desconhecida
     * @param screenChangeAfter a gravação observou troca de rota ou de documento depois desta etapa
     */
    public record StepFacts(StepId stepId, int number, Origin origin, ActionType action, boolean hasTarget, String targetKind, String targetName,
                            boolean sensitive, String key, String screenKey, boolean screenChangeAfter) {
        public StepFacts {
            Objects.requireNonNull(stepId);
            Objects.requireNonNull(origin);
            Objects.requireNonNull(action);
            targetKind = targetKind == null ? "" : targetKind;
            targetName = targetName == null ? "" : targetName;
            key = key == null ? "" : key;
            screenKey = screenKey == null ? "" : screenKey;
        }
    }

    /** Fatos de uma sessão (seção do documento). {@code safeName} vazio quando o nome não passa pela política. */
    public record SectionFacts(SessionId sessionId, int number, String safeName, List<StepFacts> steps) {
        public SectionFacts {
            Objects.requireNonNull(sessionId);
            safeName = safeName == null ? "" : safeName;
            steps = List.copyOf(steps);
        }
    }

    private final AutomationId automationId;
    private final List<SectionFacts> sections;
    private final String revision;

    private ProcessProjection(AutomationId automationId, List<SectionFacts> sections) {
        this.automationId = automationId;
        this.sections = List.copyOf(sections);
        this.revision = Canonical.sha256(canonical());
    }

    /** Projeta a automação. Pura: mesma automação → mesmos fatos e mesma revisão; nada é alterado. */
    public static ProcessProjection of(Automation a) {
        Objects.requireNonNull(a);
        List<SectionFacts> out = new ArrayList<>();
        int sn = 0;
        for (LogicalSession s : a.sessions()) {
            sn++;
            List<StepFacts> steps = new ArrayList<>();
            int n = 0;
            for (AutomationStep st : s.steps()) {
                n++;
                steps.add(facts(st, n));
            }
            out.add(new SectionFacts(s.id(), sn, PddText.safeLabel(s.name()).orElse(""), steps));
        }
        return new ProcessProjection(a.id(), out);
    }

    private static StepFacts facts(AutomationStep st, int number) {
        Origin origin = st.provenance().origin() == StepOrigin.RECORDED ? Origin.RECORDED : Origin.DECLARED;
        ElementReference e = st.element().orElse(null);
        String kind = e == null ? "" : e.semanticKind();
        String name = e == null ? "" : PddText.safeLabel(e.accessibleName()).orElse("");
        String key = "";
        if (st.actionType() == ActionType.PRESS_KEY && st.parameters().value().orElse(null) instanceof ValueSource.LiteralValue l
                && KEY_VOCABULARY.contains(l.text())) {
            key = l.text();
        }
        String screen = st.context().map(ProcessProjection::screenKey).orElse("");
        boolean change = st.evidence().stream().anyMatch(ev -> ev.type() == EvidenceType.ROUTE_CHANGED || ev.type() == EvidenceType.DOCUMENT_CHANGED);
        return new StepFacts(st.id(), number, origin, st.actionType(), e != null, kind, name, e != null && e.sensitive(), key, screen, change);
    }

    /** Resumo opaco da tela: o modelo de URL e o título nunca entram no documento, só se são iguais ou não. */
    static String screenKey(StepContext c) {
        String l = c.location() == null ? "" : c.location();
        String t = c.screenTitle() == null ? "" : c.screenTitle();
        if (l.isEmpty() && t.isEmpty()) return "";
        return "tela-" + Canonical.sha256(l + "\n" + t).substring(7, 23);
    }

    private String canonical() {
        StringBuilder b = new StringBuilder("nekkora-studio/pdd-visual/1.0.0 process\n").append(automationId.value()).append('\n');
        for (SectionFacts s : sections) {
            b.append("S|").append(s.number()).append('|').append(s.sessionId().value()).append('|').append(s.safeName()).append('\n');
            for (StepFacts t : s.steps()) {
                b.append("T|").append(t.number()).append('|').append(t.stepId().value()).append('|').append(t.origin()).append('|').append(t.action())
                        .append('|').append(t.hasTarget()).append('|').append(t.targetKind()).append('|').append(t.targetName()).append('|')
                        .append(t.sensitive()).append('|').append(t.key()).append('|').append(t.screenKey()).append('|').append(t.screenChangeAfter())
                        .append('\n');
            }
        }
        return b.toString();
    }

    public AutomationId automationId() {
        return automationId;
    }

    public List<SectionFacts> sections() {
        return sections;
    }

    /** {@code processRevision}: {@code sha256:…} do conteúdo do processo relevante ao documento. */
    public String revision() {
        return revision;
    }

    public int stepCount() {
        return sections.stream().mapToInt(s -> s.steps().size()).sum();
    }
}
