package com.nekkora.studio.ai.context;

import com.nekkora.studio.ai.Hashes;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.ProcessProjection;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * STUDIO-014 — ProjectContextAssembler: fotografia TIPADA e MINIMIZADA do projeto para o planejamento, a validação e
 * (com consentimento) a IA. Só fatos estruturais: ação, tipo e nome SEGURO do alvo (o mesmo filtro do PDD), se é
 * sensível, se está em iframe/shadow, se o alvo foi confirmado no debug, de onde vem o valor (tipo da origem, nunca o
 * valor), tela opaca e o bloco do PDD. Nunca: valores, textos de campo, localizadores, URLs completas, credenciais,
 * pixels. A origem do site (esquema + host + porta) entra só onde a política de execução precisa dela.
 */
public record ProjectContext(String automationId, long automationRevision, String processRevision, List<Step> steps, List<String> origins,
                             String startUrl, List<String> declaredVariables, List<String> sessionNames) {

    /** De onde vem o valor de um preenchimento — o tipo, nunca o valor. */
    public enum ValueKind { NONE, VARIABLE, CREDENTIAL, LITERAL }

    /**
     * Uma etapa.
     *
     * @param targetName  rótulo seguro (política de texto do PDD) ou vazio
     * @param locatorUsable o localizador preferencial é validado e não posicional (exportável)
     * @param fingerprint resumo de ação + alvo (uma decisão da especificação vale enquanto ele não muda)
     */
    public record Step(StepId stepId, int session, int number, ActionType action, boolean hasTarget, String targetKind, String targetName,
                       boolean sensitive, boolean inFrame, boolean inShadow, boolean confirmed, boolean locatorUsable, ValueKind value,
                       String variableName, String screenKey, boolean screenChangeAfter, String blockId, String fingerprint) {
        public Step {
            Objects.requireNonNull(stepId);
            targetKind = targetKind == null ? "" : targetKind;
            targetName = targetName == null ? "" : targetName;
            variableName = variableName == null ? "" : variableName;
            screenKey = screenKey == null ? "" : screenKey;
            blockId = blockId == null ? "" : blockId;
        }
    }

    public ProjectContext {
        steps = List.copyOf(steps);
        origins = List.copyOf(origins);
        declaredVariables = List.copyOf(declaredVariables);
        sessionNames = List.copyOf(sessionNames);
    }

    public Optional<Step> step(StepId id) {
        return steps.stream().filter(s -> s.stepId().equals(id)).findFirst();
    }

    /** Monta a fotografia. {@code draft} pode faltar (PDD ainda não criado). */
    public static ProjectContext of(Automation a, Optional<DocumentDraft> draft) {
        ProcessProjection p = ProcessProjection.of(a);
        Map<StepId, ProcessProjection.StepFacts> facts = new LinkedHashMap<>();
        p.sections().forEach(s -> s.steps().forEach(f -> facts.put(f.stepId(), f)));
        Map<StepId, String> blockOf = new LinkedHashMap<>();
        draft.ifPresent(d -> d.blocks().forEach(b -> b.stepIds().forEach(id -> blockOf.put(id, b.blockId()))));
        List<Step> out = new ArrayList<>();
        List<String> origins = new ArrayList<>();
        String start = null;
        int sn = 0;
        for (LogicalSession s : a.sessions()) {
            sn++;
            int n = 0;
            for (AutomationStep st : s.steps()) {
                n++;
                ProcessProjection.StepFacts f = facts.get(st.id());
                ElementReference e = st.element().orElse(null);
                boolean frame = e != null && e.accessPath().stream().anyMatch(x -> x.type() == AccessStep.Type.IFRAME);
                boolean shadow = e != null && e.accessPath().stream().anyMatch(x -> x.type() == AccessStep.Type.SHADOW_ROOT);
                boolean usable = e != null && e.preferredLocator().map(l -> l.validated() && l.strategy() != LocatorStrategy.POSITIONAL).orElse(false);
                ValueKind vk = ValueKind.NONE;
                String var = "";
                Optional<ValueSource> v = st.parameters().value();
                if (v.isPresent()) {
                    switch (v.get()) {
                        case ValueSource.VariableReference r -> {
                            vk = ValueKind.VARIABLE;
                            var = r.name();
                        }
                        case ValueSource.CredentialReference c -> vk = ValueKind.CREDENTIAL;
                        case ValueSource.LiteralValue l -> vk = ValueKind.LITERAL;
                    }
                }
                String location = st.context().map(c -> c.location()).orElse("");
                String origin = origin(location);
                if (origin != null && !origins.contains(origin)) origins.add(origin);
                if (start == null && origin != null) start = withoutQuery(location);
                String kind = f == null ? "" : f.targetKind();
                String name = f == null ? "" : f.targetName();
                String fp = Hashes.sha256(st.actionType() + "|" + kind + "|" + name + "|" + (e != null && e.sensitive()) + "|" + frame);
                out.add(new Step(st.id(), sn, n, st.actionType(), e != null, kind, name, e != null && e.sensitive(), frame, shadow,
                        st.targetDecision().map(d -> d.confirmed()).orElse(false), usable, vk, var, f == null ? "" : f.screenKey(),
                        f != null && f.screenChangeAfter(), blockOf.get(st.id()), fp));
            }
        }
        List<String> declared = a.variables().stream().map(x -> x.name()).toList();
        List<String> names = p.sections().stream().map(ProcessProjection.SectionFacts::safeName).toList();
        return new ProjectContext(a.id().value().toString(), a.revision(), p.revision(), out, origins, start == null ? "" : start, declared, names);
    }

    /** {@code esquema://host[:porta]} de um endereço observado, ou {@code null}. */
    static String origin(String location) {
        if (location == null || location.isBlank()) return null;
        try {
            URI u = URI.create(location.strip());
            if (u.getScheme() == null || u.getHost() == null || !u.getScheme().matches("https?")) return null;
            return u.getScheme() + "://" + u.getHost().toLowerCase(java.util.Locale.ROOT) + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Endereço sem consulta nem fragmento (a consulta pode carregar dados). */
    static String withoutQuery(String location) {
        try {
            URI u = URI.create(location.strip());
            return u.getScheme() + "://" + u.getRawAuthority() + (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath());
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Candidato de localizador do alvo por rótulo (texto de interface) — usado só para nomear chaves e variáveis. */
    public static Optional<String> labelOf(AutomationStep st) {
        return st.element().flatMap(e -> e.locators().stream().filter(c -> c.strategy() == LocatorStrategy.LABEL).map(LocatorCandidate::expression)
                .findFirst());
    }
}
