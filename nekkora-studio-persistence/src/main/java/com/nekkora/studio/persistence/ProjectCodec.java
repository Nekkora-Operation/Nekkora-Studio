package com.nekkora.studio.persistence;

import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationRestoration;
import com.nekkora.studio.domain.AutomationStatus;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.CapturedBaseline;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepComment;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableConstraints;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableId;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableRules;
import com.nekkora.studio.domain.variable.VariableScope;
import com.nekkora.studio.domain.variable.VariableType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Documentos do projeto ↔ modelo (STUDIO-006, D-S006-03/04). Escrita: percorre o agregado (na thread que o possui) e
 * produz árvores JSON próprias — valores imutáveis, desligados dos objetos do domínio, prontos para serem serializados em
 * outra thread. Leitura: interpreta os documentos com {@link Doc} (campos exatos, tipos conferidos) e entrega os dados
 * da reconstrução ({@link AutomationRestoration.AutomationData}); as regras do domínio são reaplicadas na reconstrução.
 *
 * <p>Separação mantida em cada etapa: {@code original} (procedência, elemento capturado, contexto, resultado observado,
 * valores capturados, vínculo de duplicação) e {@code effective} (o que o usuário editou). O efetivo nunca substitui o
 * original.
 *
 * <p>STUDIO-008: o catálogo de variáveis vai em {@link ProjectFormat#VARIABLES} — só quando existe (modelo 1.1.0). Uma
 * automação sem variáveis produz exatamente os mesmos documentos de antes (modelo 1.0.0). Uma constante só existe para
 * variáveis não sensíveis (regra do domínio, reaplicada na leitura); nenhum valor de entrada ou de execução é gravado.
 */
public final class ProjectCodec {
    static final int MAX_LIST = 1_000;
    static final int MAX_CODES = 64;
    static final int MAX_KEYS = 16;

    private ProjectCodec() {}

    // ================================================================================================== escrita

    /** Documentos de conteúdo (sem manifesto e metadados) de uma automação, na ordem de escrita. */
    static Map<String, Object> documents(Automation a) {
        Map<String, Object> docs = new LinkedHashMap<>();
        Map<String, Object> automation = new LinkedHashMap<>();
        automation.put("schema", ModelVersion.SCHEMA);
        automation.put("modelVersion", a.modelVersion());
        automation.put("automationId", a.id().value().toString());
        automation.put("name", a.name());
        automation.put("description", a.description());
        automation.put("status", a.status().name());
        automation.put("createdAt", a.createdAt().toString());
        automation.put("updatedAt", a.updatedAt().toString());
        automation.put("revision", a.revision());
        List<Object> order = new ArrayList<>();
        List<Object> sessions = new ArrayList<>();
        for (LogicalSession s : a.sessions()) {
            order.add(s.id().value().toString());
            Map<String, Object> sd = new LinkedHashMap<>();
            sd.put("sessionId", s.id().value().toString());
            sd.put("name", s.name());
            sd.put("description", s.description());
            sd.put("notes", s.notes());
            List<Object> steps = new ArrayList<>();
            for (AutomationStep st : s.steps()) steps.add(st.id().value().toString());
            sd.put("steps", steps);
            sessions.add(sd);
        }
        automation.put("sessions", order);
        docs.put(ProjectFormat.AUTOMATION, automation);
        docs.put(ProjectFormat.SESSIONS, single("sessions", sessions));
        if (!a.variables().isEmpty()) {
            List<Object> vars = new ArrayList<>();
            for (VariableDefinition v : a.variables()) vars.add(variable(v));
            docs.put(ProjectFormat.VARIABLES, single("variables", vars));
        }
        boolean decisions = ModelVersion.V1_2.equals(a.modelVersion());
        for (LogicalSession s : a.sessions()) {
            for (AutomationStep st : s.steps()) docs.put(ProjectFormat.STEPS_DIR + st.id().value() + ".json", step(st, decisions));
        }
        for (LogicalSession s : a.sessions()) {
            for (AutomationStep st : s.steps()) {
                if (st.evidence().isEmpty()) continue;
                Map<String, Object> ed = new LinkedHashMap<>();
                ed.put("stepId", st.id().value().toString());
                ed.put("evidence", evidenceList(st.evidence()));
                docs.put(ProjectFormat.EVIDENCE_DIR + st.id().value() + ".json", ed);
            }
        }
        docs.put(ProjectFormat.UNATTRIBUTED, single("evidence", evidenceList(a.unattributedEvidence())));
        return docs;
    }

    private static Map<String, Object> variable(VariableDefinition v) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("variableId", v.id().value().toString());
        d.put("name", v.name());
        d.put("technicalType", v.type().name());
        d.put("semanticType", v.semantic().name());
        d.put("authority", v.authority().name());
        d.put("purpose", v.purpose());
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("kind", v.scope().kind().name());
        scope.put("sessionId", v.scope().sessionId().map(s -> s.value().toString()).orElse(null));
        d.put("scope", nulls(scope));
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("kind", v.origin().kind().name());
        origin.put("constant", v.origin().constant());
        origin.put("producerStepId", v.origin().producerStep().map(s -> s.value().toString()).orElse(null));
        d.put("origin", nulls(origin));
        d.put("sensitivity", v.sensitivity().name());
        VariableConstraints c = v.constraints();
        Map<String, Object> cd = new LinkedHashMap<>();
        cd.put("required", c.required());
        cd.put("minLength", c.minLength() == null ? null : (Object) c.minLength().longValue());
        cd.put("maxLength", c.maxLength() == null ? null : (Object) c.maxLength().longValue());
        cd.put("textFormat", c.textFormat().name());
        // decimais como TEXTO na forma escrita (sem passar por ponto flutuante)
        cd.put("minimum", c.minimum() == null ? null : c.minimum().toPlainString());
        cd.put("maximum", c.maximum() == null ? null : c.maximum().toPlainString());
        cd.put("scale", c.scale() == null ? null : (Object) c.scale().longValue());
        cd.put("currency", c.currency());
        d.put("constraints", nulls(cd));
        d.put("createdAt", v.createdAt().toString());
        d.put("updatedAt", v.updatedAt().toString());
        return d;
    }

    private static Map<String, Object> single(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    private static Map<String, Object> step(AutomationStep st, boolean decisions) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("stepId", st.id().value().toString());
        d.put("createdAt", st.createdAt().toString());
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("provenance", provenance(st.provenance()));
        original.put("capturedElement", st.capturedElement().map(ProjectCodec::element).orElse(null));
        original.put("context", st.context().map(ProjectCodec::context).orElse(null));
        original.put("observedOutcome", st.observedOutcome());
        original.put("baseline", st.baseline().map(ProjectCodec::baseline).orElse(null));
        original.put("derivedFrom", st.derivedFrom().map(x -> x.value().toString()).orElse(null));
        d.put("original", nulls(original));
        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("name", st.name());
        effective.put("description", st.description());
        effective.put("actionType", st.actionType().name());
        effective.put("parameters", parameters(st.parameters()));
        effective.put("preferredLocator", (long) st.element().map(ElementReference::preferredLocatorIndex).orElse(-1));
        List<Object> manual = new ArrayList<>();
        for (ManualLocator m : st.manualLocators()) {
            Map<String, Object> ml = new LinkedHashMap<>();
            ml.put("syntax", m.syntax().name());
            ml.put("expression", m.expression());
            ml.put("note", m.note());
            ml.put("proposedAt", m.proposedAt().toString());
            ml.put("verification", m.verification().name());
            manual.add(ml);
        }
        effective.put("manualLocators", manual);
        List<Object> comments = new ArrayList<>();
        for (StepComment c : st.comments()) {
            Map<String, Object> cd = new LinkedHashMap<>();
            cd.put("text", c.text());
            cd.put("createdAt", c.createdAt().toString());
            cd.put("editedAt", c.edited().map(Instant::toString).orElse(null));
            comments.add(nulls(cd));
        }
        effective.put("comments", comments);
        Map<String, Object> edits = new LinkedHashMap<>();
        for (StepField f : StepField.values()) st.editedAt(f).ifPresent(i -> edits.put(f.name(), i.toString()));
        effective.put("fieldEdits", edits);
        effective.put("lastManualChange", st.lastManualChange().map(Instant::toString).orElse(null));
        if (decisions) {
            // STUDIO-009 (modelo 1.2.0): alvo substituto e decisão — os dois campos existem em toda etapa (nulos se não houver)
            effective.put("targetReplacement", st.replacementTarget().map(ProjectCodec::element).orElse(null));
            effective.put("targetDecision", st.targetDecision().map(ProjectCodec::decision).orElse(null));
        }
        d.put("effective", nulls(effective));
        return d;
    }

    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]{0,59}");

    /** Decisão sobre o alvo: campos exatos, códigos fechados; as regras de autoridade e coerência são as do domínio. */
    private static TargetDecision decision(Doc t) throws ProjectException {
        Doc b = t.obj("basis", "identitySource", "status", "reasons", "observedAt", "location");
        Doc x = t.obj("expectation", "action", "valueKind", "variableRef", "targetId", "targetKind", "targetTag", "preferredExpression", "path");
        List<String> reasons = b.strings("reasons", 16);
        for (int i = 0; i < reasons.size(); i++) {
            if (!CODE.matcher(reasons.get(i)).matches()) throw Doc.invalid(b.at("reasons") + "[" + i + "]", "código inválido");
        }
        List<com.nekkora.studio.domain.element.AccessStep.Type> path = new ArrayList<>();
        List<String> types = x.strings("path", 12);
        for (int i = 0; i < types.size(); i++) path.add(Doc.enumValue(types.get(i), com.nekkora.studio.domain.element.AccessStep.Type.class, x.at("path")));
        TargetDecision.Kind kind = t.enumValue("kind", TargetDecision.Kind.class);
        DecisionActor actor = t.enumValue("actor", DecisionActor.class);
        Instant at = t.instant("decidedAt");
        String source = b.str("identitySource");
        String status = b.str("status");
        Instant observed = b.instantOrNull("observedAt");
        String location = b.str("location");
        ActionType action = x.enumValue("action", ActionType.class);
        String valueKind = x.str("valueKind");
        String variableRef = x.str("variableRef");
        String targetId = x.str("targetId");
        String targetKind = x.str("targetKind");
        String targetTag = x.str("targetTag");
        String expression = x.str("preferredExpression");
        try {
            return new TargetDecision(kind, actor, at, new TargetDecision.Basis(source, status, reasons, observed, location),
                    new TargetDecision.Expectation(action, valueKind, variableRef, targetId, targetKind, targetTag, expression, path));
        } catch (IllegalArgumentException ex) {
            throw Doc.invalid(t.where(), "decisão fora do contrato");
        }
    }

    private static Map<String, Object> decision(TargetDecision t) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kind", t.kind().name());
        d.put("actor", t.actor().name());
        d.put("decidedAt", t.decidedAt().toString());
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("identitySource", t.basis().identitySource());
        b.put("status", t.basis().status());
        b.put("reasons", new ArrayList<Object>(t.basis().reasons()));
        b.put("observedAt", t.basis().observedAt() == null ? null : t.basis().observedAt().toString());
        b.put("location", t.basis().location());
        d.put("basis", nulls(b));
        TargetDecision.Expectation x = t.expectation();
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("action", x.action().name());
        e.put("valueKind", x.valueKind());
        e.put("variableRef", x.variableRef());
        e.put("targetId", x.targetId());
        e.put("targetKind", x.targetKind());
        e.put("targetTag", x.targetTag());
        e.put("preferredExpression", x.preferredExpression());
        List<Object> path = new ArrayList<>();
        for (com.nekkora.studio.domain.element.AccessStep.Type type : x.path()) path.add(type.name());
        e.put("path", path);
        d.put("expectation", e);
        return d;
    }

    private static Map<String, Object> provenance(StepProvenance p) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("origin", p.origin().name());
        d.put("correlation", correlation(p.correlation()));
        d.put("recording", p.recordingObservation().map(o -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("mode", o.mode().name());
            r.put("observation", o.observation().name());
            r.put("businessOutcome", o.businessOutcome().name());
            r.put("evidence", new ArrayList<Object>(o.evidence()));
            r.put("limitations", new ArrayList<Object>(o.limitations()));
            return (Object) r;
        }).orElse(null));
        return nulls(d);
    }

    private static Map<String, Object> correlation(CorrelationKeys keys) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (CorrelationKeys.Key k : CorrelationKeys.Key.values()) keys.get(k).ifPresent(v -> m.put(k.name(), v));
        return m;
    }

    private static Map<String, Object> element(ElementReference e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("accessibleName", e.accessibleName());
        d.put("semanticKind", e.semanticKind());
        d.put("tag", e.tag());
        d.put("htmlId", e.htmlId().orElse(null));
        d.put("htmlName", e.htmlName().orElse(null));
        d.put("sensitive", e.sensitive());
        d.put("privacyPolicy", e.privacyPolicy().orElse(null));
        d.put("sourceContract", e.sourceContract().orElse(null));
        d.put("preferredLocator", (long) e.preferredLocatorIndex());
        List<Object> locators = new ArrayList<>();
        for (LocatorCandidate c : e.locators()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("strategy", c.strategy().name());
            l.put("description", c.description());
            l.put("expression", c.expression());
            l.put("stability", c.stability().name());
            l.put("matchCount", (long) c.matchCount());
            l.put("matchesTarget", c.matchesTarget());
            locators.add(l);
        }
        d.put("locators", locators);
        List<Object> path = new ArrayList<>();
        for (AccessStep s : e.accessPath()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", s.type().name());
            p.put("label", s.label());
            p.put("locator", s.locator());
            p.put("correlation", correlation(s.correlation()));
            path.add(p);
        }
        d.put("accessPath", path);
        List<Object> context = new ArrayList<>();
        for (ContextAnchor a : e.context()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("relation", a.relation());
            c.put("kind", a.kind());
            c.put("name", a.name());
            c.put("locator", a.locator());
            context.add(c);
        }
        d.put("context", context);
        d.put("stabilityNotes", new ArrayList<Object>(e.stabilityNotes()));
        List<Object> obstacles = new ArrayList<>();
        for (ObservedObstacle o : e.obstacles()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", o.type());
            m.put("severity", o.severity().name());
            m.put("description", o.description());
            obstacles.add(m);
        }
        d.put("obstacles", obstacles);
        d.put("correlation", correlation(e.correlation()));
        return nulls(d);
    }

    private static Map<String, Object> context(StepContext c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("application", c.application());
        d.put("screenTitle", c.screenTitle());
        d.put("location", c.location());
        return d;
    }

    private static Map<String, Object> baseline(CapturedBaseline b) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", b.name());
        d.put("description", b.description());
        d.put("actionType", b.actionType().name());
        d.put("parameters", parameters(b.parameters()));
        d.put("preferredLocator", (long) b.preferredLocator());
        return d;
    }

    private static Map<String, Object> parameters(ActionParameters p) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("value", p.value().map(v -> (Object) switch (v) {
            case ValueSource.LiteralValue l -> kind("LITERAL", "text", l.text());
            case ValueSource.VariableReference r -> kind("VARIABLE", "name", r.name());
            case ValueSource.CredentialReference c -> kind("CREDENTIAL", "alias", c.alias());
        }).orElse(null));
        d.put("option", p.option().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", o.label());
            m.put("value", o.value());
            return (Object) m;
        }).orElse(null));
        return nulls(d);
    }

    private static Map<String, Object> kind(String kind, String key, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put(key, value);
        return m;
    }

    private static List<Object> evidenceList(List<ObservedEvidence> list) {
        List<Object> out = new ArrayList<>(list.size());
        for (ObservedEvidence e : list) out.add(evidence(e));
        return out;
    }

    private static Map<String, Object> evidence(ObservedEvidence e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("evidenceId", e.evidenceId());
        d.put("sequence", e.sequence());
        d.put("recordingId", e.recordingId());
        d.put("eventId", e.eventId());
        d.put("observedAt", e.observedAt().toString());
        d.put("type", e.type().name());
        d.put("source", e.source().name());
        d.put("relation", e.relation().name());
        d.put("confidence", e.confidence().name());
        d.put("classification", e.classification() == null ? null : e.classification().name());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("frame", e.document().frame());
        doc.put("inShadow", e.document().inShadow());
        doc.put("url", e.document().url());
        d.put("document", nulls(doc));
        d.put("region", e.region());
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("availability", e.message().availability().name());
        msg.put("text", null); // modo estrito: nunca há texto da página
        d.put("message", nulls(msg));
        d.put("field", e.field());
        d.put("state", e.state() == null ? null : stateChange(e.state()));
        d.put("navigation", e.navigation() == null ? null : nulls(navigation(e.navigation())));
        d.put("signals", names(e.signals()));
        d.put("candidates", (long) e.candidates());
        d.put("signalsObserved", (long) e.signalsObserved());
        d.put("limitations", names(e.limitations()));
        return nulls(d);
    }

    private static Map<String, Object> stateChange(ObservedEvidence.StateChange s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attribute", s.attribute().name());
        m.put("from", s.from().name());
        m.put("to", s.to().name());
        return m;
    }

    private static Map<String, Object> navigation(ObservedEvidence.Navigation n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("newDocument", n.newDocument());
        m.put("url", n.url());
        return m;
    }

    private static List<Object> names(List<? extends Enum<?>> values) {
        List<Object> out = new ArrayList<>();
        for (Enum<?> v : values) out.add(v.name());
        return out;
    }

    /** {@code null} Java vira o {@code null} JSON (o campo existe; o leitor exige todos os campos). */
    private static Map<String, Object> nulls(Map<String, Object> m) {
        m.replaceAll((k, v) -> v == null ? Json.NULL : v);
        return m;
    }

    // ================================================================================================== leitura

    /** Lê os documentos de conteúdo (já conferidos pelo pacote) e devolve os dados da reconstrução. */
    static AutomationRestoration.AutomationData read(Map<String, Object> docs) throws ProjectException {
        Doc a = Doc.of(docs.get(ProjectFormat.AUTOMATION), "automation.json", "schema", "modelVersion", "automationId", "name", "description", "status",
                "createdAt", "updatedAt", "revision", "sessions");
        if (!ModelVersion.SCHEMA.equals(a.str("schema"))) throw new ProjectException(ProjectException.Error.UNSUPPORTED_VERSION, a.at("schema"));
        // o modelo acompanha o catálogo: 1.0.0 sem variables.json, 1.1.0 com ele (o pacote já conferiu formato ↔ entrada)
        boolean hasVariables = docs.containsKey(ProjectFormat.VARIABLES);
        // STUDIO-009: o modelo 1.2.0 (decisões sobre o alvo) acompanha o formato 1.2.x, com ou sem catálogo de variáveis
        boolean decisions = ModelVersion.V1_2.equals(a.str("modelVersion"));
        if (!decisions && !(hasVariables ? ModelVersion.V1_1 : ModelVersion.CURRENT).equals(a.str("modelVersion"))) {
            throw new ProjectException(ProjectException.Error.UNSUPPORTED_VERSION, a.at("modelVersion"));
        }
        AutomationId id = new AutomationId(uuid(a.str("automationId"), a.at("automationId")));
        List<String> sessionOrder = a.strings("sessions", ProjectFormat.MAX_SESSIONS);
        Doc sd = Doc.of(docs.get(ProjectFormat.SESSIONS), "sessions.json", "sessions");
        List<Object> sessionDocs = sd.list("sessions", ProjectFormat.MAX_SESSIONS);
        if (sessionDocs.size() != sessionOrder.size()) throw new ProjectException(ProjectException.Error.INTEGRITY, "sessions.json: ordem das sessões");
        List<AutomationRestoration.SessionData> sessions = new ArrayList<>();
        java.util.Set<String> referencedSteps = new java.util.HashSet<>();
        int totalSteps = 0;
        for (int i = 0; i < sessionDocs.size(); i++) {
            String w = "sessions.json: sessions[" + i + "]";
            Doc s = Doc.of(sessionDocs.get(i), w, "sessionId", "name", "description", "notes", "steps");
            String sid = s.str("sessionId");
            if (!sid.equals(sessionOrder.get(i))) throw new ProjectException(ProjectException.Error.INTEGRITY, w + ": ordem difere de automation.json");
            List<String> stepIds = s.strings("steps", ProjectFormat.MAX_STEPS);
            totalSteps += stepIds.size();
            if (totalSteps > ProjectFormat.MAX_STEPS) throw new ProjectException(ProjectException.Error.TOO_LARGE, "sessions.json: etapas");
            List<AutomationRestoration.StepData> steps = new ArrayList<>();
            for (int j = 0; j < stepIds.size(); j++) {
                String stepId = stepIds.get(j);
                uuid(stepId, w + ".steps[" + j + "]");
                if (!referencedSteps.add(stepId)) {
                    throw new ProjectException(ProjectException.Error.INTEGRITY, w + ".steps[" + j + "]: etapa repetida");
                }
                String path = ProjectFormat.STEPS_DIR + stepId + ".json";
                Object stepDoc = docs.get(path);
                if (stepDoc == null) throw new ProjectException(ProjectException.Error.INTEGRITY, w + ".steps[" + j + "]: etapa inexistente");
                Object evidenceDoc = docs.get(ProjectFormat.EVIDENCE_DIR + stepId + ".json");
                steps.add(step(stepDoc, path, stepId, evidenceDoc, decisions));
            }
            sessions.add(new AutomationRestoration.SessionData(new SessionId(uuid(sid, w + ".sessionId")), s.str("name"), s.str("description"),
                    s.str("notes"), steps));
        }
        // nenhuma etapa ou evidência solta: tudo que existe no pacote é referenciado
        for (String path : docs.keySet()) {
            java.util.regex.Matcher m = ProjectFormat.STEP_ENTRY.matcher(path);
            if (m.matches() && !referencedSteps.contains(m.group(1))) throw new ProjectException(ProjectException.Error.INTEGRITY, path + ": etapa sem sessão");
            m = ProjectFormat.EVIDENCE_ENTRY.matcher(path);
            if (m.matches() && !referencedSteps.contains(m.group(1))) {
                throw new ProjectException(ProjectException.Error.INTEGRITY, path + ": evidências de etapa inexistente");
            }
        }
        Doc u = Doc.of(docs.get(ProjectFormat.UNATTRIBUTED), ProjectFormat.UNATTRIBUTED, "evidence");
        List<ObservedEvidence> unattributed = u.each("evidence", com.nekkora.studio.domain.Automation.MAX_UNATTRIBUTED, ProjectCodec::evidence);
        List<VariableDefinition> variables = List.of();
        if (hasVariables) {
            Doc vd = Doc.of(docs.get(ProjectFormat.VARIABLES), ProjectFormat.VARIABLES, "variables");
            variables = vd.each("variables", VariableRules.MAX_VARIABLES, ProjectCodec::variable);
            // o escritor nunca produz catálogo vazio (sem variáveis o projeto é 1.0.0): nada de forma alternativa aceita
            if (variables.isEmpty()) throw Doc.invalid(vd.at("variables"), "catálogo vazio no formato 1.1");
        }
        // STUDIO-009: o escritor só usa o modelo 1.2.0 com ao menos uma decisão ou alvo substituto (menor versão capaz)
        if (decisions && sessions.stream().flatMap(x -> x.steps().stream()).noneMatch(x -> x.decision() != null || x.replacement() != null)) {
            throw Doc.invalid(a.at("modelVersion"), "modelo 1.2.0 sem decisões sobre o alvo");
        }
        return new AutomationRestoration.AutomationData(id, a.str("name"), a.str("description"), a.enumValue("status", AutomationStatus.class),
                a.instant("createdAt"), a.instant("updatedAt"), a.str("modelVersion"), a.lng("revision"), sessions, unattributed, variables);
    }

    private static final java.util.regex.Pattern DECIMAL_TEXT = java.util.regex.Pattern.compile("-?[0-9]{1,30}(\\.[0-9]{1,18})?");

    /** Uma variável: campos exatos; as regras de coerência (nome, significado, constante...) são as do domínio, na reconstrução. */
    private static VariableDefinition variable(Object value, String where) throws ProjectException {
        Doc d = Doc.of(value, where, "variableId", "name", "technicalType", "semanticType", "authority", "purpose", "scope", "origin", "sensitivity",
                "constraints", "createdAt", "updatedAt");
        VariableId id = new VariableId(uuid(d.str("variableId"), d.at("variableId")));
        Doc s = d.obj("scope", "kind", "sessionId");
        VariableScope.Kind scopeKind = s.enumValue("kind", VariableScope.Kind.class);
        SessionId session = s.isNull("sessionId") ? null : new SessionId(uuid(s.str("sessionId"), s.at("sessionId")));
        VariableScope scope = Doc.domain(s.where(), x -> new VariableScope(scopeKind, session));
        Doc o = d.obj("origin", "kind", "constant", "producerStepId");
        VariableOrigin.Kind originKind = o.enumValue("kind", VariableOrigin.Kind.class);
        String constant = o.strOrNull("constant");
        StepId producer = o.isNull("producerStepId") ? null : new StepId(uuid(o.str("producerStepId"), o.at("producerStepId")));
        VariableOrigin origin = Doc.domain(o.where(), x -> new VariableOrigin(originKind, constant, producer));
        Doc c = d.obj("constraints", "required", "minLength", "maxLength", "textFormat", "minimum", "maximum", "scale", "currency");
        VariableConstraints constraints = new VariableConstraints(c.bool("required"),
                c.isNull("minLength") ? null : c.integer("minLength", 0, VariableRules.MAX_DECLARED_LENGTH),
                c.isNull("maxLength") ? null : c.integer("maxLength", 0, VariableRules.MAX_DECLARED_LENGTH), c.enumValue("textFormat", TextFormat.class),
                decimal(c, "minimum"), decimal(c, "maximum"), c.isNull("scale") ? null : c.integer("scale", 0, VariableRules.MAX_SCALE),
                c.strOrNull("currency"));
        String name = d.str("name");
        VariableType type = d.enumValue("technicalType", VariableType.class);
        SemanticType semantic = d.enumValue("semanticType", SemanticType.class);
        SemanticStatus authority = d.enumValue("authority", SemanticStatus.class);
        String purpose = d.str("purpose");
        Sensitivity sensitivity = d.enumValue("sensitivity", Sensitivity.class);
        Instant created = d.instant("createdAt");
        Instant updated = d.instant("updatedAt");
        return Doc.domain(where, x -> new VariableDefinition(id, name, type, semantic, authority, purpose, scope, origin, sensitivity, constraints, created,
                updated));
    }

    private static BigDecimal decimal(Doc c, String field) throws ProjectException {
        if (c.isNull(field)) return null;
        String text = c.str(field);
        if (!DECIMAL_TEXT.matcher(text).matches()) throw Doc.invalid(c.at(field), "decimal fora da forma canônica");
        return new BigDecimal(text);
    }

    private static AutomationRestoration.StepData step(Object value, String path, String expectedId, Object evidenceDoc, boolean decisions)
            throws ProjectException {
        Doc d = Doc.of(value, path, "stepId", "createdAt", "original", "effective");
        if (!d.str("stepId").equals(expectedId)) throw new ProjectException(ProjectException.Error.INTEGRITY, path + ": identidade difere do nome");
        Doc o = d.obj("original", "provenance", "capturedElement", "context", "observedOutcome", "baseline", "derivedFrom");
        // STUDIO-009: os campos do alvo substituto e da decisão existem SÓ no modelo 1.2.0 (e nele, sempre)
        Doc e = decisions
                ? d.obj("effective", "name", "description", "actionType", "parameters", "preferredLocator", "manualLocators", "comments", "fieldEdits",
                        "lastManualChange", "targetReplacement", "targetDecision")
                : d.obj("effective", "name", "description", "actionType", "parameters", "preferredLocator", "manualLocators", "comments", "fieldEdits",
                        "lastManualChange");
        ElementReference replacement = decisions && !e.isNull("targetReplacement") ? element(e.raw("targetReplacement"), e.at("targetReplacement")) : null;
        TargetDecision decision = decisions && !e.isNull("targetDecision") ? decision(e.obj("targetDecision", "kind", "actor", "decidedAt", "basis",
                "expectation")) : null;
        StepProvenance provenance = provenance(o.obj("provenance", "origin", "correlation", "recording"));
        ElementReference captured = o.isNull("capturedElement") ? null : element(o.raw("capturedElement"), o.at("capturedElement"));
        StepContext context = null;
        if (!o.isNull("context")) {
            Doc c = o.obj("context", "application", "screenTitle", "location");
            context = new StepContext(c.str("application"), c.str("screenTitle"), c.str("location"));
        }
        CapturedBaseline baseline = null;
        if (!o.isNull("baseline")) {
            Doc b = o.obj("baseline", "name", "description", "actionType", "parameters", "preferredLocator");
            ActionParameters bp = parameters(b.raw("parameters"), b.at("parameters"));
            baseline = new CapturedBaseline(b.str("name"), b.str("description"), b.enumValue("actionType", ActionType.class), bp,
                    b.integer("preferredLocator", -1, MAX_LIST));
        }
        StepId derivedFrom = o.isNull("derivedFrom") ? null : new StepId(uuid(o.str("derivedFrom"), o.at("derivedFrom")));
        List<ManualLocator> manual = e.each("manualLocators", MAX_LIST, (v, w) -> {
            Doc m = Doc.of(v, w, "syntax", "expression", "note", "proposedAt", "verification");
            // um localizador manual nunca é verificado: o arquivo não pode declarar outra coisa
            if (Doc.enumValue(m.str("verification"), ManualLocator.Verification.class, m.at("verification")) != ManualLocator.Verification.NOT_VERIFIED) {
                throw Doc.invalid(m.at("verification"), "localizador manual verificado");
            }
            ManualLocator.Syntax syntax = m.enumValue("syntax", ManualLocator.Syntax.class);
            String expression = m.str("expression");
            String note = m.str("note");
            Instant at = m.instant("proposedAt");
            ManualLocator ml = Doc.domain(w, x -> new ManualLocator(syntax, expression, note, at));
            if (!ml.expression().equals(expression) || !ml.note().equals(note)) throw Doc.invalid(w, "texto fora da forma normalizada");
            return ml;
        });
        List<StepComment> comments = e.each("comments", MAX_LIST, (v, w) -> {
            Doc c = Doc.of(v, w, "text", "createdAt", "editedAt");
            String text = c.str("text");
            Instant created = c.instant("createdAt");
            Instant edited = c.instantOrNull("editedAt");
            return Doc.domain(w, x -> new StepComment(text, created, edited));
        });
        Map<StepField, Instant> edits = new EnumMap<>(StepField.class);
        // os campos de fieldEdits são variáveis (só os editados): lidos como mapa de texto, conferidos um a um
        for (Map.Entry<String, String> en : e.stringMap("fieldEdits", StepField.values().length).entrySet()) {
            String w = e.at("fieldEdits") + "." + en.getKey();
            StepField f = Doc.enumValue(en.getKey(), StepField.class, w);
            if (f == StepField.TARGET && !decisions) throw Doc.invalid(w, "campo do modelo 1.2.0 em versão anterior");
            edits.put(f, Doc.instant(en.getValue(), w));
        }
        List<ObservedEvidence> evidence = List.of();
        if (evidenceDoc != null) {
            String ep = ProjectFormat.EVIDENCE_DIR + expectedId + ".json";
            Doc ed = Doc.of(evidenceDoc, ep, "stepId", "evidence");
            if (!ed.str("stepId").equals(expectedId)) throw new ProjectException(ProjectException.Error.INTEGRITY, ep + ": etapa difere do nome");
            evidence = ed.each("evidence", Automation.MAX_EVIDENCE_PER_STEP, ProjectCodec::evidence);
            if (evidence.isEmpty()) throw new ProjectException(ProjectException.Error.INTEGRITY, ep + ": documento de evidências vazio");
        }
        return new AutomationRestoration.StepData(new StepId(uuid(expectedId, path)), d.instant("createdAt"), provenance, captured, context,
                o.str("observedOutcome"), baseline, derivedFrom, e.str("name"), e.str("description"), e.enumValue("actionType", ActionType.class),
                parameters(e.raw("parameters"), e.at("parameters")), e.integer("preferredLocator", -1, MAX_LIST), manual, comments, edits,
                e.instantOrNull("lastManualChange"), evidence, replacement, decision);
    }

    private static StepProvenance provenance(Doc p) throws ProjectException {
        StepOrigin origin = p.enumValue("origin", StepOrigin.class);
        CorrelationKeys correlation = correlation(p, "correlation");
        RecordingObservation recording = null;
        if (!p.isNull("recording")) {
            Doc r = p.obj("recording", "mode", "observation", "businessOutcome", "evidence", "limitations");
            RecordingObservation.CaptureMode mode = r.enumValue("mode", RecordingObservation.CaptureMode.class);
            RecordingObservation.Observation obs = r.enumValue("observation", RecordingObservation.Observation.class);
            RecordingObservation.BusinessOutcome outcome = r.enumValue("businessOutcome", RecordingObservation.BusinessOutcome.class);
            List<String> evidence = codes(r, "evidence");
            List<String> limitations = codes(r, "limitations");
            recording = Doc.domain(r.where(), x -> new RecordingObservation(mode, obs, outcome, evidence, limitations));
        }
        RecordingObservation rec = recording;
        return Doc.domain(p.where(), x -> new StepProvenance(origin, correlation, rec));
    }

    /** Códigos técnicos (evidência e limitação da gravação): só o formato de código, nunca texto livre. */
    private static List<String> codes(Doc d, String field) throws ProjectException {
        List<String> out = d.strings(field, MAX_CODES);
        for (int i = 0; i < out.size(); i++) {
            if (!out.get(i).matches("[A-Z_]{1,40}")) throw Doc.invalid(d.at(field) + "[" + i + "]", "código inválido");
        }
        return out;
    }

    private static final java.util.regex.Pattern CORRELATION_VALUE = java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{1,120}");

    private static CorrelationKeys correlation(Doc d, String field) throws ProjectException {
        Map<CorrelationKeys.Key, String> keys = new EnumMap<>(CorrelationKeys.Key.class);
        for (Map.Entry<String, String> en : d.stringMap(field, MAX_KEYS).entrySet()) {
            String w = d.at(field) + "." + en.getKey();
            if (!CORRELATION_VALUE.matcher(en.getValue()).matches()) throw Doc.invalid(w, "identificador interno inválido");
            keys.put(Doc.enumValue(en.getKey(), CorrelationKeys.Key.class, w), en.getValue());
        }
        return CorrelationKeys.of(keys);
    }

    private static ElementReference element(Object value, String where) throws ProjectException {
        Doc e = Doc.of(value, where, "accessibleName", "semanticKind", "tag", "htmlId", "htmlName", "sensitive", "privacyPolicy", "sourceContract",
                "preferredLocator", "locators", "accessPath", "context", "stabilityNotes", "obstacles", "correlation");
        ElementReference.Builder b = ElementReference.builder().accessibleName(e.str("accessibleName")).semanticKind(e.str("semanticKind"))
                .tag(e.str("tag")).htmlId(e.strOrNull("htmlId")).htmlName(e.strOrNull("htmlName")).sensitive(e.bool("sensitive"))
                .privacyPolicy(e.strOrNull("privacyPolicy")).sourceContract(e.strOrNull("sourceContract")).correlation(correlation(e, "correlation"));
        for (LocatorCandidate c : e.each("locators", MAX_LIST, (v, w) -> {
            Doc l = Doc.of(v, w, "strategy", "description", "expression", "stability", "matchCount", "matchesTarget");
            LocatorStrategy strategy = l.enumValue("strategy", LocatorStrategy.class);
            LocatorStability stability = l.enumValue("stability", LocatorStability.class);
            String description = l.str("description");
            String expression = l.str("expression");
            int count = l.integer("matchCount", 0, Integer.MAX_VALUE);
            boolean matches = l.bool("matchesTarget");
            return Doc.domain(w, x -> new LocatorCandidate(strategy, description, expression, stability, count, matches));
        })) {
            b.addLocator(c);
        }
        for (AccessStep s : e.each("accessPath", MAX_LIST, (v, w) -> {
            Doc a = Doc.of(v, w, "type", "label", "locator", "correlation");
            return new AccessStep(a.enumValue("type", AccessStep.Type.class), a.str("label"), a.str("locator"), correlation(a, "correlation"));
        })) {
            b.addAccessStep(s);
        }
        for (ContextAnchor c : e.each("context", MAX_LIST, (v, w) -> {
            Doc a = Doc.of(v, w, "relation", "kind", "name", "locator");
            return new ContextAnchor(a.str("relation"), a.str("kind"), a.str("name"), a.str("locator"));
        })) {
            b.addContext(c);
        }
        for (String note : e.strings("stabilityNotes", MAX_LIST)) b.addStabilityNote(note);
        for (ObservedObstacle o : e.each("obstacles", MAX_LIST, (v, w) -> {
            Doc a = Doc.of(v, w, "type", "severity", "description");
            return new ObservedObstacle(a.str("type"), a.enumValue("severity", ObservedObstacle.Severity.class), a.str("description"));
        })) {
            b.addObstacle(o);
        }
        ElementReference built = Doc.domain(where, x -> b.build());
        int preferred = e.integer("preferredLocator", -1, MAX_LIST);
        if (preferred >= built.locators().size() || (preferred >= 0 && !built.locators().get(preferred).validated())) {
            throw new ProjectException(ProjectException.Error.INTEGRITY, e.at("preferredLocator"));
        }
        return built.withPreferredLocator(preferred);
    }

    private static ActionParameters parameters(Object value, String where) throws ProjectException {
        Doc p = Doc.of(value, where, "value", "option");
        if (!p.isNull("value") && !p.isNull("option")) throw Doc.invalid(where, "valor e opção juntos");
        if (!p.isNull("value")) {
            Object raw = p.raw("value");
            Object k = raw instanceof Map<?, ?> m ? m.get("kind") : null;
            if (!(k instanceof String kind)) throw Doc.invalid(p.at("value") + ".kind", "esperado texto");
            ValueSource v = switch (kind) {
                case "LITERAL" -> {
                    String t = Doc.of(raw, p.at("value"), "kind", "text").str("text");
                    yield Doc.domain(p.at("value"), x -> new ValueSource.LiteralValue(t));
                }
                case "VARIABLE" -> {
                    String n = Doc.of(raw, p.at("value"), "kind", "name").str("name");
                    yield Doc.domain(p.at("value"), x -> new ValueSource.VariableReference(n));
                }
                case "CREDENTIAL" -> {
                    String n = Doc.of(raw, p.at("value"), "kind", "alias").str("alias");
                    yield Doc.domain(p.at("value"), x -> new ValueSource.CredentialReference(n));
                }
                default -> throw Doc.invalid(p.at("value") + ".kind", "valor não reconhecido");
            };
            return ActionParameters.withValue(v);
        }
        if (!p.isNull("option")) {
            Doc o = p.obj("option", "label", "value");
            String label = o.str("label");
            String val = o.str("value");
            return ActionParameters.withOption(Doc.domain(p.at("option"), x -> new OptionChoice(label, val)));
        }
        return ActionParameters.none();
    }

    private static ObservedEvidence evidence(Object value, String where) throws ProjectException {
        Doc d = Doc.of(value, where, "evidenceId", "sequence", "recordingId", "eventId", "observedAt", "type", "source", "relation", "confidence",
                "classification", "document", "region", "message", "field", "state", "navigation", "signals", "candidates", "signalsObserved",
                "limitations");
        Doc doc = d.obj("document", "frame", "inShadow", "url");
        Doc msg = d.obj("message", "availability", "text");
        if (!msg.isNull("text")) throw Doc.invalid(msg.at("text"), "texto da página em evidência");
        ObservedEvidence.Message message = Doc.domain(d.at("message"),
                x -> new ObservedEvidence.Message(Enum.valueOf(ObservedEvidence.Message.Availability.class, nameOf(msg, "availability")), null));
        ObservedEvidence.StateChange state = null;
        if (!d.isNull("state")) {
            Doc s = d.obj("state", "attribute", "from", "to");
            state = new ObservedEvidence.StateChange(s.enumValue("attribute", EvidenceVocabulary.StateAttribute.class),
                    s.enumValue("from", EvidenceVocabulary.StateValue.class), s.enumValue("to", EvidenceVocabulary.StateValue.class));
        }
        ObservedEvidence.Navigation navigation = null;
        if (!d.isNull("navigation")) {
            Doc n = d.obj("navigation", "newDocument", "url");
            navigation = new ObservedEvidence.Navigation(n.bool("newDocument"), n.strOrNull("url"));
            if (!java.util.Objects.equals(navigation.url(), n.strOrNull("url"))) throw Doc.invalid(n.at("url"), "endereço recusado pelo domínio");
        }
        String region = d.strOrNull("region");
        String field = d.strOrNull("field");
        String url = doc.strOrNull("url");
        List<EvidenceVocabulary.Signal> signals = new ArrayList<>();
        for (String s : d.strings("signals", 8)) signals.add(Doc.enumValue(s, EvidenceVocabulary.Signal.class, d.at("signals")));
        List<EvidenceVocabulary.Limitation> limitations = new ArrayList<>();
        for (String s : d.strings("limitations", 8)) limitations.add(Doc.enumValue(s, EvidenceVocabulary.Limitation.class, d.at("limitations")));
        ObservedEvidence.DocumentContext dc = new ObservedEvidence.DocumentContext(doc.bool("frame"), doc.bool("inShadow"), null, url);
        ObservedEvidence.StateChange st = state;
        ObservedEvidence.Navigation nav = navigation;
        String evidenceId = d.str("evidenceId");
        long sequence = d.lng("sequence");
        String recordingId = d.str("recordingId");
        String eventId = d.strOrNull("eventId");
        Instant observedAt = d.instant("observedAt");
        EvidenceType type = d.enumValue("type", EvidenceType.class);
        EvidenceVocabulary.Source source = d.enumValue("source", EvidenceVocabulary.Source.class);
        EvidenceVocabulary.Relation relation = d.enumValue("relation", EvidenceVocabulary.Relation.class);
        EvidenceVocabulary.Confidence confidence = d.enumValue("confidence", EvidenceVocabulary.Confidence.class);
        EvidenceVocabulary.Classification classification = d.enumOrNull("classification", EvidenceVocabulary.Classification.class);
        int candidates = d.integer("candidates", 0, 1000);
        int observed = d.integer("signalsObserved", 0, 1000);
        ObservedEvidence ev = Doc.domain(where, x -> new ObservedEvidence(evidenceId, sequence, recordingId, eventId, observedAt, type, source, relation,
                confidence, classification, dc, region, message, field, st, nav, signals, candidates, observed, limitations));
        // o domínio descarta (vira null) o que não aceita; no arquivo isso é conteúdo inválido, não algo a "consertar"
        if (!java.util.Objects.equals(ev.region(), region) || !java.util.Objects.equals(ev.field(), field)
                || !java.util.Objects.equals(ev.document().url(), url)) {
            throw Doc.invalid(where, "valor recusado pelo domínio");
        }
        return ev;
    }

    private static String nameOf(Doc d, String field) {
        try {
            return d.str(field);
        } catch (ProjectException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static UUID uuid(String text, String where) throws ProjectException {
        try {
            UUID u = UUID.fromString(text);
            if (!u.toString().equals(text)) throw Doc.invalid(where, "identidade fora da forma canônica");
            return u;
        } catch (IllegalArgumentException e) {
            throw Doc.invalid(where, "identidade inválida");
        }
    }
}
