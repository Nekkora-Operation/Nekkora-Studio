package com.nekkora.studio.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NEKKORA-E2E-002: etapa sem alvo e sem tela (criada no editor) roda na tela da etapa seguinte, depois da espera dela. */
class InheritedScreenTest {

    private static ProjectContext.Step step(int session, boolean target, String screen) {
        return new ProjectContext.Step(new StepId(UUID.randomUUID()), session, 1, target ? ActionType.OBSERVE : ActionType.OBSERVE, target, "", "", false,
                false, false, true, true, ProjectContext.ValueKind.NONE, "", screen, false, "", "fp", "", false);
    }

    @Test
    @DisplayName("herda a tela da PRÓXIMA etapa da sessão; senão a da anterior; nunca atravessa sessões; etapas com tela não mudam")
    void heranca() {
        List<ProjectContext.Step> in = List.of(step(1, true, "busca"), step(2, false, ""), step(2, true, "artigo"), step(3, true, "fim"),
                step(3, false, ""), step(4, false, ""));
        List<ProjectContext.Step> out = ProjectContext.withInheritedScreens(in);
        assertEquals(List.of("busca", "artigo", "artigo", "fim", "fim", ""), out.stream().map(ProjectContext.Step::screenKey).toList());
        assertEquals(in.get(1).stepId(), out.get(1).stepId(), "a mesma etapa, só com a tela herdada");
    }
}
