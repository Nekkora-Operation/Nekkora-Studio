package com.nekkora.studio.persistence;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepField;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.StepProvenance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Descrição COMPLETA de uma automação feita só com a API pública do domínio — independente do codec de persistência,
 * para que a comparação antes × depois não seja circular. Inclui identidades, ordem, valores, procedência, elemento
 * capturado e efetivo, parâmetros, comentários, localizadores, revisão manual, instantes, evidências e o resumo derivado.
 */
final class ModelDump {
    private ModelDump() {}

    static List<String> of(Automation a) {
        List<String> out = new ArrayList<>();
        out.add("automation id=" + a.id().value() + " name=" + a.name() + " description=" + a.description() + " status=" + a.status() + " created="
                + a.createdAt() + " updated=" + a.updatedAt() + " model=" + a.modelVersion() + " revision=" + a.revision() + " steps=" + a.stepCount());
        for (LogicalSession s : a.sessions()) {
            out.add(" session id=" + s.id().value() + " name=" + s.name() + " description=" + s.description() + " notes=" + s.notes() + " steps="
                    + s.steps().stream().map(x -> x.id().value().toString()).toList());
            for (AutomationStep st : s.steps()) step(out, st);
        }
        out.add(" sequence=" + a.sequence().stream().map(p -> p.number() + ":" + p.stepId().value()).toList());
        for (ObservedEvidence e : a.unattributedEvidence()) out.add(" unattributed " + e);
        return out;
    }

    private static void step(List<String> out, AutomationStep st) {
        out.add("  step id=" + st.id().value() + " created=" + st.createdAt() + " name=" + st.name() + " description=" + st.description() + " type="
                + st.actionType() + " params=" + params(st.parameters()));
        out.add("   provenance " + provenance(st.provenance()));
        out.add("   captured " + st.capturedElement().map(ModelDump::element).orElse("-"));
        out.add("   effective " + st.element().map(ModelDump::element).orElse("-"));
        out.add("   context " + st.context().map(Object::toString).orElse("-") + " outcome=" + st.observedOutcome());
        out.add("   baseline " + st.baseline().map(b -> b.name() + "|" + b.description() + "|" + b.actionType() + "|" + params(b.parameters()) + "|"
                + b.preferredLocator()).orElse("-") + " derivedFrom=" + st.derivedFrom().map(x -> x.value().toString()).orElse("-"));
        out.add("   manualLocators " + st.manualLocators().stream().map(m -> m + "/" + m.verification()).toList());
        out.add("   comments " + st.comments());
        TreeMap<String, String> edits = new TreeMap<>();
        for (StepField f : StepField.values()) st.editedAt(f).ifPresent(i -> edits.put(f.name(), i.toString()));
        TreeMap<String, Boolean> modified = new TreeMap<>();
        for (StepField f : StepField.values()) modified.put(f.name(), st.modified(f));
        out.add("   edits " + edits + " modified=" + modified + " lastManualChange=" + st.lastManualChange().map(Object::toString).orElse("-")
                + " revision=" + st.manualRevision().map(r -> new TreeSet<>(r.fields().stream().map(Enum::name).toList()) + "|" + r.manualLocators() + "|"
                        + r.comments() + "|" + r.lastChangedAt()).orElse("-"));
        for (ObservedEvidence e : st.evidence()) out.add("   evidence " + e);
        ObservationSummary o = st.observation();
        out.add("   observation " + o.status() + " counts=" + new TreeMap<>(o.counts()) + " error=" + o.error() + " suppressed=" + o.suppressed() + " cut="
                + o.cut() + " limitations=" + new TreeSet<>(o.limitations()) + " last=" + o.lastObservedAt() + " business=" + o.businessOutcome());
    }

    private static String provenance(StepProvenance p) {
        return p.origin() + " " + keys(p.correlation()) + " " + p.recordingObservation().map(Object::toString).orElse("-");
    }

    private static String keys(CorrelationKeys k) {
        return new TreeMap<>(k.asMap()).toString();
    }

    private static String params(ActionParameters p) {
        return p.value().map(Object::toString).orElse("-") + "/" + p.option().map(Object::toString).orElse("-");
    }

    private static String element(ElementReference e) {
        List<String> path = new ArrayList<>();
        for (AccessStep s : e.accessPath()) path.add(s.type() + "|" + s.label() + "|" + s.locator() + "|" + keys(s.correlation()));
        return "name=" + e.accessibleName() + " kind=" + e.semanticKind() + " tag=" + e.tag() + " id=" + opt(e.htmlId()) + " nameAttr=" + opt(e.htmlName())
                + " sensitive=" + e.sensitive() + " policy=" + opt(e.privacyPolicy()) + " source=" + opt(e.sourceContract()) + " preferred="
                + e.preferredLocatorIndex() + " locators=" + e.locators() + " path=" + path + " context=" + e.context() + " notes=" + e.stabilityNotes()
                + " obstacles=" + e.obstacles() + " correlation=" + keys(e.correlation()) + " displayId=" + opt(e.displayableHtmlId());
    }

    private static String opt(Optional<String> o) {
        return o.orElse("∅");
    }
}
