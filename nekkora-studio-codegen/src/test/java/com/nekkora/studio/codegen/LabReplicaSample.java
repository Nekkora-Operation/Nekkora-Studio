package com.nekkora.studio.codegen;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.validation.AutomationValidator;
import java.nio.file.Path;
import java.util.List;

/**
 * FERRAMENTA DE TESTE (não é o fluxo do produto): monta, em código, um modelo equivalente ao que o Recorder produziria
 * na RÉPLICA SINTÉTICA do laboratório do framework ({@code /desafio/index.html}) e gera o projeto Python, para provar que
 * o código gerado executa. A prova do fluxo real (Recorder → debug → gerador pela GUI) é a homologação E2E-001.
 *
 * <p>Uso: {@code java ... LabReplicaSample <pasta-de-saida> <origem-do-lab>}
 */
public final class LabReplicaSample {
    static final List<String> LABELS = List.of("First Name", "Last Name", "Company Name", "Role in Company", "Address", "Email", "Phone Number");

    private LabReplicaSample() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        String origin = args[1];
        Automation a = Automation.create("Réplica do laboratório", "", FormCase.CLOCK);
        LogicalSession prep = a.addSession("Preparação", null);
        LogicalSession rec = a.addSession("Preenchimento por registro", null);
        LogicalSession end = a.addSession("Conclusão", null);
        StepContext page = new StepContext("Navegador", "Desafio", origin + "/{…}");
        AutomationStep dl = step(a, prep, ActionType.CLICK, page, FormCase.el("LINK", "a", "Baixar planilha",
                new LocatorCandidate(LocatorStrategy.ROLE_NAME, "", "", LocatorStability.HEURISTIC_STABLE, 1, true)), null);
        step(a, prep, ActionType.CLICK, page, FormCase.el("BUTTON", "button", "Start",
                new LocatorCandidate(LocatorStrategy.ROLE_NAME, "", "", LocatorStability.HEURISTIC_STABLE, 1, true)), null);
        StringBuilder cols = new StringBuilder();
        for (String label : LABELS) {
            String var = label.toLowerCase().replace(' ', '_');
            a.declareVariable(VariableDraft.of(var, VariableType.STRING).withSemantic(SemanticType.FREE_TEXT, SemanticStatus.USER_CONFIRMED)
                    .withSensitivity(Sensitivity.PERSONAL_DATA).withOrigin(VariableOrigin.input()).withPurpose("coluna da planilha"));
            step(a, rec, ActionType.FILL, page, FormCase.el("TEXT_INPUT", "input", "", new LocatorCandidate(LocatorStrategy.LABEL, "",
                    "//*[label[normalize-space(.)=\"" + label + "\"]]/input", LocatorStability.HEURISTIC_STABLE, 1, true)),
                    new ValueSource.VariableReference(var));
            if (!cols.isEmpty()) cols.append(", ");
            cols.append("{\"header\": \"").append(label).append("\", \"variable\": \"").append(var).append('"')
                    .append(label.equals("Phone Number") ? ", \"integerAsText\": true" : "").append('}');
        }
        step(a, rec, ActionType.CLICK, page, FormCase.el("BUTTON", "input", "Submit",
                new LocatorCandidate(LocatorStrategy.CONTEXTUAL, "", "#form-desafio input[type=\"submit\"]", LocatorStability.HEURISTIC_STABLE, 1, true)), null);
        AutomationStep obs = a.appendStep(end.id(), StepDraft.builder().name("Ler resultado").actionType(ActionType.OBSERVE).context(page).build());
        String config = """
                {"schema": "nekkora-studio/codegen-config", "version": "1.0.0", "processName": "replica_laboratorio",
                 "environment": "lab", "allowedOrigins": ["%s"], "startUrl": "%s/desafio/index.html",
                 "sessions": {"init": [1], "perRecord": 2, "end": [3]}, "initAdvance": "REPLACED", "observations": true,
                 "input": {"format": "xlsx", "sheet": null, "source": "DOWNLOAD", "downloadStep": "%s", "fileName": "entrada.xlsx",
                   "columns": [%s]},
                 "textObservations": [{"stepId": "%s",
                   "pattern": "^Congratulations! Your success rate is \\\\d+%% \\\\( \\\\d+ out of \\\\d+ fields\\\\) in \\\\d+ milliseconds$"}]}
                """.formatted(origin, origin, dl.id().value(), cols, obs.id().value());
        PythonGenerator.Result r = PythonGenerator.generate(a, new AutomationValidator().validate(a), CodegenConfig.parse(config));
        if (!r.ok()) throw new IllegalStateException(r.blockers().toString());
        System.out.println(OutputWriter.write(out, r.files()));
    }

    private static AutomationStep step(Automation a, LogicalSession s, ActionType type, StepContext page,
                                       com.nekkora.studio.domain.element.ElementReference el, ValueSource value) {
        StepDraft.Builder b = StepDraft.builder().name(type.label()).actionType(type).context(page).element(el);
        if (value != null) b.parameters(ActionParameters.withValue(value));
        AutomationStep st = a.appendStep(s.id(), b.build());
        a.decideTarget(st.id(), TargetDecision.Kind.CONFIRMED, DecisionActor.TEST_AGENT,
                new TargetDecision.Basis("TARGET_ID", "RESOLVED", List.of(), FormCase.CLOCK.instant(), ""));
        return st;
    }
}
