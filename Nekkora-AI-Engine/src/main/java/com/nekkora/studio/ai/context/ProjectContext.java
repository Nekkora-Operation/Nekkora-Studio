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
                             String startUrl, String startTemplate, List<String> declaredVariables, List<String> sessionNames,
                             List<String> authorizedPublicOrigins) {

    /** De onde vem o valor de um preenchimento — o tipo, nunca o valor. */
    public enum ValueKind { NONE, VARIABLE, CREDENTIAL, LITERAL }

    /**
     * Uma etapa.
     *
     * @param targetName  rótulo seguro (política de texto do PDD) ou vazio
     * @param locatorUsable o localizador preferencial é validado e não posicional (exportável)
     * @param fingerprint resumo de ação + alvo (uma decisão da especificação vale enquanto ele não muda)
     * @param anchor      NEKKORA-E2E-002: rótulo seguro da LINHA/TERMO que identifica o dado lido (ex.: "Nascimento"), ou vazio
     * @param navigates   a gravação viu o documento mudar logo depois desta etapa (ex.: Enter numa busca)
     */
    public record Step(StepId stepId, int session, int number, ActionType action, boolean hasTarget, String targetKind, String targetName,
                       boolean sensitive, boolean inFrame, boolean inShadow, boolean confirmed, boolean locatorUsable, ValueKind value,
                       String variableName, String screenKey, boolean screenChangeAfter, String blockId, String fingerprint, String anchor,
                       boolean navigates) {
        public Step {
            anchor = anchor == null ? "" : anchor;
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
        authorizedPublicOrigins = List.copyOf(authorizedPublicOrigins);
    }

    /** A mesma fotografia com as origens públicas de treinamento AUTORIZADAS pela pessoa (opções do projeto). */
    public ProjectContext withAuthorizedPublicOrigins(java.util.Collection<String> origins) {
        return new ProjectContext(automationId, automationRevision, processRevision, steps, this.origins, startUrl, startTemplate, declaredVariables,
                sessionNames, List.copyOf(origins));
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
        String template = null;
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
                if (template == null && origin != null) template = location;
                // endereço inicial: uma etapa Navegar com endereço literal; ou o endereço observado, se a política de privacidade o
                // guardou inteiro (sem trechos mascarados "{…}") — o endereço gravado costuma ser só um MODELO
                if (start == null && st.actionType() == ActionType.NAVIGATE && v.isPresent() && v.get() instanceof ValueSource.LiteralValue lit) {
                    String u = withoutQuery(lit.text());
                    if (!u.isEmpty()) start = u;
                }
                if (start == null && origin != null && !location.contains("{") && !location.contains("?") && !location.contains("#")) {
                    start = withoutQuery(location);
                }
                String kind = f == null ? "" : f.targetKind();
                String name = f == null ? "" : f.targetName();
                String anchor = e == null ? "" : anchorOf(e);
                // a âncora entra no resumo só quando existe: as decisões das versões anteriores continuam valendo
                String fp = Hashes.sha256(st.actionType() + "|" + kind + "|" + name + "|" + (e != null && e.sensitive()) + "|" + frame
                        + (anchor.isEmpty() ? "" : "|" + anchor));
                boolean navigates = st.provenance().recordingObservation().map(r -> r.evidence().contains("NAVIGATION_FOLLOWED")).orElse(false);
                out.add(new Step(st.id(), sn, n, st.actionType(), e != null, kind, name, e != null && e.sensitive(), frame, shadow,
                        st.targetDecision().map(d -> d.confirmed()).orElse(false), usable, vk, var, f == null ? "" : f.screenKey(),
                        f != null && f.screenChangeAfter(), blockOf.get(st.id()), fp, anchor, navigates));
            }
        }
        List<String> declared = a.variables().stream().map(x -> x.name()).toList();
        List<String> names = p.sections().stream().map(ProcessProjection.SectionFacts::safeName).toList();
        return new ProjectContext(a.id().value().toString(), a.revision(), p.revision(), out, origins, start == null ? "" : start,
                template == null ? "" : template, declared, names, List.of());
    }

    private static final java.util.regex.Pattern ORIGIN = java.util.regex.Pattern.compile("^(https?)://([A-Za-z0-9.-]+|\\[[0-9a-fA-F:.]+\\])(:[0-9]{1,5})?(?=[/?#]|$)");

    /** {@code esquema://host[:porta]} de um endereço observado (inclusive um MODELO com trechos "{…}"), ou {@code null}. */
    public static String origin(String location) {
        if (location == null || location.isBlank()) return null;
        java.util.regex.Matcher m = ORIGIN.matcher(location.strip());
        if (!m.find()) return null;
        return m.group(1).toLowerCase(java.util.Locale.ROOT) + "://" + m.group(2).toLowerCase(java.util.Locale.ROOT) + (m.group(3) == null ? "" : m.group(3));
    }

    /**
     * O endereço informado casa com o MODELO gravado? Mesma origem, mesmo número de trechos no caminho e os trechos
     * conhecidos iguais (os mascarados "{…}" aceitam qualquer valor); sem consulta nem fragmento (podem carregar dados).
     */
    public static boolean matchesTemplate(String url, String template) {
        if (url == null || url.isBlank() || url.contains("?") || url.contains("#") || url.length() > 300) return false;
        String o = origin(url);
        if (o == null || (template != null && !template.isBlank() && !o.equals(origin(template)))) return false;
        if (template == null || template.isBlank()) return true;
        String up = url.replaceFirst("^[^:]+://[^/?#]+", "");
        String tp = template.replaceFirst("^[^:]+://[^/?#]+", "").replaceFirst("[?#].*$", "");
        String[] a = up.split("/", -1);
        String[] b = tp.split("/", -1);
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!b[i].equals("{…}") && !b[i].equals(a[i])) return false;
        return true;
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

    private static final java.util.regex.Pattern ANCHOR = java.util.regex.Pattern.compile(
            "(?:th|dt\\[1\\])\\[normalize-space\\(\\.\\)=\"([^\"]{1,80})\"\\]");

    /**
     * NEKKORA-E2E-002: rótulo da LINHA (tr com th) ou do TERMO (dt) que ancora o alvo — tirado do localizador preferencial
     * por linha (já aprovado pela política de privacidade nas barreiras da extensão e do Studio). Vazio se não houver.
     */
    public static String anchorOf(ElementReference e) {
        Optional<LocatorCandidate> pref = e.preferredLocator();
        if (pref.isEmpty() || pref.get().strategy() != LocatorStrategy.ROW_CONTEXT || pref.get().expression() == null) return "";
        java.util.regex.Matcher m = ANCHOR.matcher(pref.get().expression());
        if (!m.find()) return "";
        String label = m.group(1).strip();
        return com.nekkora.studio.domain.privacy.CapturePrivacy.interfaceText(label) == null ? "" : label;
    }

    /** Candidato de localizador do alvo por rótulo (texto de interface) — usado só para nomear chaves e variáveis. */
    public static Optional<String> labelOf(AutomationStep st) {
        return st.element().flatMap(e -> e.locators().stream().filter(c -> c.strategy() == LocatorStrategy.LABEL).map(LocatorCandidate::expression)
                .findFirst());
    }
}
