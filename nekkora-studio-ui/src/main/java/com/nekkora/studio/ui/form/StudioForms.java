package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.ui.StepPresentation;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.icon.IconName;

/** Catálogo dos diálogos de formulário do Studio. */
public final class StudioForms {
    private StudioForms() {}

    public static FormSpec<NameDescriptionForm.Values> newAutomation() {
        return new FormSpec<>("Nova automação", "Dê um nome ao processo. As sessões e etapas vêm em seguida.", IconName.WORKFLOW,
                new NameDescriptionForm("Nome", "Ex.: Consulta de clientes", "Aparece na navegação e nos documentos do processo.", true),
                "Criar automação", NekkoraDialog.Size.MEDIUM);
    }

    public static FormSpec<NameDescriptionForm.Values> newSession(Automation automation) {
        return new FormSpec<>("Nova sessão", "A sessão entra no final de \"" + automation.name()
                + "\". Use sessões para dividir o processo em partes compreensíveis.", IconName.LAYERS,
                new NameDescriptionForm("Nome da sessão", "Ex.: Autenticação", "Por exemplo: Autenticação, Consulta, Resultado.", true),
                "Criar sessão", NekkoraDialog.Size.MEDIUM);
    }

    public static FormSpec<NameDescriptionForm.Values> renameSession(String currentName) {
        return new FormSpec<>("Renomear sessão", null, IconName.EDIT,
                new NameDescriptionForm("Nome da sessão", "Ex.: Autenticação", null, false).withValues(currentName, null),
                "Renomear", NekkoraDialog.Size.SMALL);
    }

    public static FormSpec<StepForm.Values> newStep(Automation automation, SessionId preselected) {
        return new FormSpec<>("Nova etapa", "Etapa manual: descreva a instrução. A gravação de etapas chega em uma próxima versão.",
                IconName.PLUS, StepForm.forCreation(automation, preselected), "Adicionar etapa", NekkoraDialog.Size.MEDIUM);
    }

    public static FormSpec<StepForm.Values> stepDetails(Automation automation, AutomationStep step) {
        return new FormSpec<>(step.name(), StepPresentation.positionTitle(automation, step) + " · "
                + automation.sessionOf(step.id()).orElseThrow().name(), null,
                new StepDetailsForm(automation, step), "Aplicar alterações", NekkoraDialog.Size.LARGE);
    }
}
