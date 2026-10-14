package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.OptionChoice;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.domain.origin.StepProvenance;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.ui.form.FormSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S001-013 — identificadores internos nunca chegam a componentes destinados ao usuário. */
class StudioPrivacyTest {
    private final StudioFixture fx = new StudioFixture();

    @AfterEach
    void tearDown() {
        fx.dispose();
    }

    @Test
    @DisplayName("S001-013 nenhum UUID/ID interno em rótulos, dicas, nomes acessíveis, listas, propriedades e diálogos")
    void semIdentificadoresInternos() {
        Automation a = fx.missionExample();
        List<String> ids = new ArrayList<>();
        ids.add(a.id().value().toString());
        for (LogicalSession s : a.sessions()) {
            ids.add(s.id().value().toString());
            for (AutomationStep st : s.steps()) ids.add(st.id().value().toString());
        }
        // etapa "gravada" (simulando a futura entrega do Recorder) com chaves técnicas de correlação
        String targetId = "tgt-" + UUID.randomUUID();
        String recording = "rec-" + UUID.randomUUID();
        CorrelationKeys keys = CorrelationKeys.none().with(CorrelationKeys.Key.TARGET_ID, targetId)
                .with(CorrelationKeys.Key.RECORDING_ID, recording).with(CorrelationKeys.Key.NODE_ID, "n-412")
                .with(CorrelationKeys.Key.PAGE_SESSION_ID, UUID.randomUUID().toString());
        ElementReference estado = ElementReference.builder().accessibleName("Estado").semanticKind("SELECT").tag("select")
                .htmlId("estado").htmlName("uf").privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY)
                .addLocator(new LocatorCandidate(LocatorStrategy.ID, "campo de seleção \"Estado\"", "#estado", LocatorStability.HEURISTIC_STABLE, 1, true))
                .preferLastLocator()
                .addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "Documento principal"))
                .addAccessStep(new AccessStep(AccessStep.Type.IFRAME, "iframe#cadastro", "#cadastro",
                        CorrelationKeys.none().with(CorrelationKeys.Key.FRAME_ID, UUID.randomUUID().toString())))
                .addAccessStep(AccessStep.of(AccessStep.Type.ELEMENT, "select#estado"))
                .correlation(keys).build();
        ElementReference dynamic = ElementReference.builder().accessibleName("Campo " + UUID.randomUUID()).semanticKind("TEXT_INPUT")
                .htmlId("ember-" + UUID.randomUUID()).privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY).correlation(keys).build();
        AutomationStep recorded = Edt.call(() -> fx.studio.stepIntake().appendStep(a.id(), a.sessions().get(1).id(), StepDraft.builder()
                .name("Selecionar São Paulo no campo Estado").actionType(ActionType.SELECT_OPTION)
                .provenance(StepProvenance.of(StepOrigin.RECORDED, keys)).element(estado)
                .parameters(ActionParameters.withOption(new OptionChoice("São Paulo", "SP")))
                .context(new StepContext("Navegador", "Cadastro", "https://sistema.exemplo/cadastro?sid=" + UUID.randomUUID()))
                .build()));
        AutomationStep weird = Edt.call(() -> fx.studio.stepIntake().appendStep(a.id(), a.sessions().get(1).id(), StepDraft.builder()
                .name("Preencher campo dinâmico").actionType(ActionType.FILL).provenance(StepProvenance.of(StepOrigin.RECORDED, keys))
                .element(dynamic).build()));
        ids.add(recorded.id().value().toString());
        ids.add(weird.id().value().toString());

        StringBuilder all = new StringBuilder();
        all.append(Edt.call(() -> StudioFixture.texts(fx.studio.view().component())));
        for (AutomationStep st : List.of(recorded, weird)) {
            Edt.run(() -> fx.studio.controller().selectStep(a.id(), a.sessions().get(1).id(), st.id()));
            all.append(fx.inspectorText());
            fx.prompts.thenCancel();
            Edt.run(() -> fx.studio.controller().openStepDetails(st.id()));
            FormSpec<?> spec = fx.prompts.last();
            all.append(spec.title()).append('\n').append(spec.description()).append('\n');
            all.append(Edt.call(() -> StudioFixture.texts(spec.form())));
        }
        fx.studio.state().lastNotice().ifPresent(n -> all.append(n.title()).append(n.message()));
        String text = all.toString();

        assertTrue(text.contains("Selecionar São Paulo no campo Estado"));
        assertTrue(text.contains("iframe#cadastro"), "trajetória de acesso apresentada");
        assertTrue(text.contains("estado"), "id HTML seguro apresentado");
        assertTrue(text.contains("São Paulo (SP)"), "opção subordinada à mesma etapa");
        assertFalse(DisplaySafety.UUID.matcher(text).find(), "UUID visível na interface");
        for (String id : ids) assertFalse(text.contains(id), "identificador interno visível: " + id);
        for (String secret : List.of(targetId, recording, "n-412", "ember-")) assertFalse(text.contains(secret), "dado técnico visível: " + secret);
        assertFalse(text.contains("sid="), "endereço com parâmetro de sessão não é exibido");
    }
}
