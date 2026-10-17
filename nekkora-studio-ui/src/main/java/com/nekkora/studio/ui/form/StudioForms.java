package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
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

    /** STUDIO-004: propriedades da automação (nome e descrição; a identidade não muda). */
    public static FormSpec<NameDescriptionForm.Values> editAutomation(Automation automation) {
        return new FormSpec<>("Propriedades da automação", "Sessões e etapas continuam como estão.", IconName.WORKFLOW,
                new NameDescriptionForm("Nome", "Ex.: Consulta de clientes", "Único entre as automações do Studio.", true)
                        .withValues(automation.name(), automation.description()),
                "Salvar alterações", NekkoraDialog.Size.MEDIUM);
    }

    /** STUDIO-004: nome, descrição e observações da sessão (etapas e ordem não mudam). */
    public static FormSpec<NameDescriptionForm.Values> editSession(LogicalSession session) {
        return new FormSpec<>("Sessão — " + session.name(), "As etapas da sessão e a ordem continuam como estão.", IconName.LAYERS,
                new NameDescriptionForm("Nome da sessão", "Ex.: Autenticação", "Único nesta automação.", true)
                        .withNotes("Até 2000 caracteres.").withValues(session.name(), session.description()).withNotesValue(session.notes()),
                "Salvar alterações", NekkoraDialog.Size.MEDIUM);
    }

    public static FormSpec<StepForm.Values> newStep(Automation automation, SessionId preselected) {
        return new FormSpec<>("Nova etapa", "Etapa manual: descreva a instrução. Para registrar interações do Chrome, use Gravar.",
                IconName.PLUS, StepForm.forCreation(automation, preselected), "Adicionar etapa", NekkoraDialog.Size.MEDIUM);
    }

    /** Detalhes e edição completa da etapa (STUDIO-004: Geral, Parâmetros, Elemento, Comentários, Origem e alterações). */
    public static FormSpec<StepDetailsForm.Values> stepDetails(Automation automation, AutomationStep step) {
        return new FormSpec<>(step.name(), StepPresentation.positionTitle(automation, step) + " · "
                + automation.sessionOf(step.id()).orElseThrow().name(), null,
                new StepDetailsForm(automation, step), "Salvar alterações", NekkoraDialog.Size.LARGE);
    }

    /** STUDIO-008: declarar variável (só metadados; nenhum valor de execução). */
    public static FormSpec<VariableForm.Values> newVariable(Automation automation) {
        return new FormSpec<>("Nova variável", "Declare um dado do processo de \"" + automation.name() + "\". O Studio guarda só a declaração: "
                + "nenhum valor de execução, nenhuma senha.", IconName.BRACES, new VariableForm(automation, null), "Declarar variável",
                NekkoraDialog.Size.LARGE);
    }

    public static FormSpec<VariableForm.Values> editVariable(Automation automation, com.nekkora.studio.domain.variable.VariableDefinition v) {
        return new FormSpec<>("Editar variável", "Mudar o nome aqui também atualiza as etapas que usam a variável, na mesma operação.", IconName.BRACES,
                new VariableForm(automation, v), "Salvar variável", NekkoraDialog.Size.LARGE);
    }

    /** Renomear com prévia das etapas atualizadas. */
    public static FormSpec<String> renameVariable(String currentName, java.util.List<String> consumers) {
        return new FormSpec<>("Renomear variável", "O nome e as referências das etapas mudam juntos: um Desfazer volta tudo.", IconName.EDIT,
                new RenameVariableForm(currentName, consumers), "Renomear", NekkoraDialog.Size.MEDIUM);
    }

    /** STUDIO-004: mover uma etapa para outra posição ou sessão da mesma automação. */
    public static FormSpec<MoveStepForm.Values> moveStep(Automation automation, AutomationStep step) {
        return new FormSpec<>("Mover etapa", "\"" + step.name() + "\" continua a mesma etapa: só muda de lugar.", IconName.SORT,
                new MoveStepForm(automation, step), "Mover etapa", NekkoraDialog.Size.MEDIUM);
    }
}
