package com.nekkora.studio.domain;

import com.nekkora.studio.domain.action.ActionType;
import java.util.List;

/** Estrutura do "Resultado esperado" da missão (seção 34), criada pelas operações de domínio. */
final class MissionExample {
    static final List<String> STEP_NAMES = List.of("Preencher usuário", "Preencher senha", "Clicar em Entrar",
            "Abrir menu Clientes", "Preencher campo de pesquisa", "Clicar em Pesquisar", "Consultar detalhes");

    private MissionExample() {}

    static Automation build(AutomationCatalog catalog) {
        Automation a = catalog.create("Consulta de clientes", "Consulta o cadastro de clientes.");
        LogicalSession auth = a.addSession("Autenticação", null);
        a.appendStep(auth.id(), StepDraft.manual("Preencher usuário", ActionType.FILL, null));
        a.appendStep(auth.id(), StepDraft.manual("Preencher senha", ActionType.FILL, null));
        a.appendStep(auth.id(), StepDraft.manual("Clicar em Entrar", ActionType.CLICK, null));
        LogicalSession query = a.addSession("Consulta", null);
        a.appendStep(query.id(), StepDraft.manual("Abrir menu Clientes", ActionType.CLICK, null));
        a.appendStep(query.id(), StepDraft.manual("Preencher campo de pesquisa", ActionType.FILL, null));
        a.appendStep(query.id(), StepDraft.manual("Clicar em Pesquisar", ActionType.CLICK, null));
        LogicalSession result = a.addSession("Resultado", null);
        a.appendStep(result.id(), StepDraft.manual("Consultar detalhes", ActionType.OBSERVE, null));
        return a;
    }
}
