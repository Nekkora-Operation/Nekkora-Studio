package com.nekkora.studio.ai.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStability;
import com.nekkora.studio.domain.element.LocatorStrategy;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** V2: o que o framework 0.2.0 e o gerador NÃO fazem vira pedido de ajuda ou bloqueio — nunca uma aproximação. */
class CapabilityCheckTest {

    static List<String> codes(CapabilityCheck.Result r) {
        return r.issues().stream().map(QualityIssue::code).toList();
    }

    @Test
    @DisplayName("caso completo: só capacidades do catálogo, ambiente lab, nada faltando")
    void limpo() {
        ConsultaCase c = ConsultaCase.confirmed();
        CapabilityCheck.Result r = CapabilityCheck.check(c.a, ProjectContext.of(c.a, Optional.empty()), FrameworkCapabilityCatalog.bundled());
        assertEquals(List.of(), codes(r));
        assertEquals("lab", r.environment());
    }

    @Test
    @DisplayName("alvo em iframe → ajuda com o caminho de extensão; ação sem Lib → UNSUPPORTED_CAPABILITY; alvo sem decisão → bloqueio")
    void lacunas() {
        ConsultaCase c = ConsultaCase.confirmed();
        StepContext p = new StepContext("Navegador", "Consulta de registro", ConsultaCase.ORIGIN + "/{…}/{…}/consulta.html");
        ElementReference framed = ElementReference.builder().semanticKind("LINK").tag("a").accessibleName("Início").privacyPolicy(CapturePrivacy.VERSION)
                .sourceContract("nekkora-pagemap/0.3.0").addLocator(new LocatorCandidate(LocatorStrategy.ID, "", "#inicio", LocatorStability.OBSERVED_STABLE, 1, true))
                .preferLastLocator().addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                .addAccessStep(AccessStep.of(AccessStep.Type.IFRAME, "Menu")).correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-f")))
                .build();
        AutomationStep f = c.a.appendStep(c.a.sessions().get(1).id(), StepDraft.builder().name("Abrir início").actionType(ActionType.CLICK).context(p)
                .element(framed).build());
        c.decide(f.id());
        AutomationStep sel = c.a.appendStep(c.a.sessions().get(1).id(), StepDraft.builder().name("Escolher").actionType(ActionType.SELECT_OPTION).context(p)
                .element(framed).build());
        AutomationStep undecided = c.a.appendStep(c.a.sessions().get(1).id(), StepDraft.builder().name("Clicar").actionType(ActionType.CLICK).context(p)
                .element(ElementReference.builder().semanticKind("BUTTON").tag("button").accessibleName("Sair").privacyPolicy(CapturePrivacy.VERSION)
                        .sourceContract("nekkora-pagemap/0.3.0").addLocator(new LocatorCandidate(LocatorStrategy.ID, "", "#sair2", LocatorStability.OBSERVED_STABLE,
                                1, true)).preferLastLocator().addAccessStep(AccessStep.of(AccessStep.Type.MAIN_DOCUMENT, "documento principal"))
                        .correlation(CorrelationKeys.of(Map.of(CorrelationKeys.Key.TARGET_ID, "tgt-s2"))).build()).build());
        CapabilityCheck.Result r = CapabilityCheck.check(c.a, ProjectContext.of(c.a, Optional.empty()), FrameworkCapabilityCatalog.bundled());
        QualityIssue frame = r.issues().stream().filter(i -> i.code().equals("TARGET_IN_FRAME_NOT_SUPPORTED")).findFirst().orElseThrow();
        assertEquals(f.id(), frame.step());
        assertEquals(QualityIssue.Kind.HELP_REQUIRED, frame.kind());
        assertTrue(frame.assistance().orElseThrow().options().stream().anyMatch(o -> "SEE_EXTENDING".equals(o.studioAction())));
        assertTrue(r.issues().stream().anyMatch(i -> i.code().equals("UNSUPPORTED_CAPABILITY") && sel.id().equals(i.step())), codes(r).toString());
        assertTrue(r.issues().stream().anyMatch(i -> i.code().equals("TARGET_NOT_CONFIRMED") && undecided.id().equals(i.step()) && i.kind() == QualityIssue.Kind.BLOCKER));
    }

    @Test
    @DisplayName("origem fora do laboratório → bloqueio de SEGURANÇA (sem ambiente de produção no 0.2.0)")
    void origemReal() {
        ConsultaCase c = ConsultaCase.confirmed();
        c.a.appendStep(c.a.sessions().get(1).id(), StepDraft.builder().name("Observar").actionType(ActionType.WAIT_FOR)
                .context(new StepContext("Navegador", "Consulta de registro", "https://sistema.exemplo.test/{…}")).build());
        CapabilityCheck.Result r = CapabilityCheck.check(c.a, ProjectContext.of(c.a, Optional.empty()), FrameworkCapabilityCatalog.bundled());
        assertTrue(r.issues().stream().anyMatch(i -> i.code().equals("ORIGIN_NOT_AUTHORIZED_FOR_EXECUTION") && i.kind() == QualityIssue.Kind.SECURITY_BLOCKED),
                codes(r).toString());
        assertEquals("", r.environment());
    }

    @Test
    @DisplayName("site público https AUTORIZADO nas opções do projeto → benchmark; misturado com laboratório → bloqueado sem autorização possível")
    void benchmarkAutorizado() {
        ConsultaCase c = ConsultaCase.confirmedAt("https://treino.exemplo.test");
        ProjectContext ctx = ProjectContext.of(c.a, Optional.empty());
        CapabilityCheck.Result blocked = CapabilityCheck.check(c.a, ctx, FrameworkCapabilityCatalog.bundled());
        QualityIssue q = blocked.issues().stream().filter(i -> i.code().equals("ORIGIN_NOT_AUTHORIZED_FOR_EXECUTION")).findFirst().orElseThrow();
        assertTrue(q.assistance().orElseThrow().options().stream().anyMatch(o -> "AUTHORIZE_PUBLIC_SITE".equals(o.studioAction())));
        CapabilityCheck.Result ok = CapabilityCheck.check(c.a, ctx.withAuthorizedPublicOrigins(List.of("https://treino.exemplo.test")),
                FrameworkCapabilityCatalog.bundled());
        assertEquals("benchmark", ok.environment());
        assertEquals(List.of(), codes(ok));
        CapabilityCheck.Result other = CapabilityCheck.check(c.a, ctx.withAuthorizedPublicOrigins(List.of("https://outro.exemplo.test")),
                FrameworkCapabilityCatalog.bundled());
        assertEquals("", other.environment(), "autorização de OUTRA origem não vale");
    }
}
