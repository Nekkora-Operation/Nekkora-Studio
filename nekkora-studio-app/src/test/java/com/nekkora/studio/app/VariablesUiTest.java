package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.SemanticStatus;
import com.nekkora.studio.domain.variable.SemanticType;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.TextFormat;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import com.nekkora.studio.ui.VariablesPanel;
import com.nekkora.studio.ui.form.RenameVariableForm;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.ui.form.StudioForm;
import com.nekkora.studio.ui.form.VariableForm;
import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.FindingCode;
import com.nekkora.studio.validation.FindingScope;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STUDIO-008: "Variáveis e dependências" pela interface — formulários reais, histórico, editor de etapa, temas e acessibilidade. */
class VariablesUiTest {
    private final StudioFixture fx = new StudioFixture();

    @AfterEach
    void tearDown() {
        fx.dispose();
    }

    private VariablesPanel panel() {
        return fx.studio.view().variablesPanel();
    }

    /** Preenche o formulário de variável: nome, tipo, significado (confirmado ou não), sensibilidade e origem. */
    private static Consumer<StudioForm<?>> variable(String name, VariableType type, SemanticType semantic, boolean confirmed, Sensitivity sensitivity,
            VariableOrigin.Kind origin, String constant) {
        return f -> {
            VariableForm v = (VariableForm) f;
            v.nameInput().setText(name);
            v.typeInput().setSelectedItem(type);
            v.semanticInput().setSelectedItem(semantic);
            v.confirmedInput().setSelected(confirmed);
            v.sensitivityInput().setSelectedItem(sensitivity);
            v.originInput().setSelectedItem(origin);
            if (constant != null) v.constantInput().setText(constant);
        };
    }

    private VariableDefinition declareCpf() {
        fx.prompts.then(variable("cliente.cpf", VariableType.STRING, SemanticType.BRAZILIAN_CPF, true, Sensitivity.PERSONAL_DATA, VariableOrigin.Kind.INPUT,
                null));
        Edt.run(() -> fx.studio.controller().newVariable());
        return fx.active().variableNamed("cliente.cpf").orElseThrow();
    }

    @Test
    @DisplayName("S008 — abrir Variáveis e dependências: aba própria do painel inferior, vazia, sem valores, ação habilitada só com automação")
    void abrirPainel() {
        assertFalse(Edt.call(() -> fx.studio.view().actions().get(StudioActions.VARIABLES).isEnabled()));
        fx.missionExample();
        assertTrue(Edt.call(() -> fx.studio.view().actions().get(StudioActions.VARIABLES).isEnabled()));
        Edt.run(() -> fx.studio.view().actions().get(StudioActions.VARIABLES).actionPerformed(null));
        assertTrue(Edt.call(() -> fx.studio.view().variablesVisible()));
        assertFalse(Edt.call(() -> fx.studio.view().validationVisible()), "uma aba por vez");
        assertEquals("0 variáveis · Consulta de clientes", Edt.call(() -> panel().counts().getText()));
        assertEquals(VariablesPanel.TITLE, Edt.call(() -> panel().getAccessibleContext().getAccessibleName()));
        assertTrue(Edt.call(() -> panel().createButton().isEnabled()));
        assertFalse(Edt.call(() -> panel().editButton().isEnabled()), "nada selecionado");
        Edt.run(() -> fx.studio.view().hideValidation());
        assertFalse(Edt.call(() -> fx.studio.view().variablesVisible()));
    }

    @Test
    @DisplayName("S008 — declarar CPF sem valor: só metadados; linha com origem, escopo, uso e sensibilidade")
    void declararCpf() {
        fx.missionExample();
        VariableDefinition cpf = declareCpf();
        assertEquals(SemanticStatus.USER_CONFIRMED, cpf.authority());
        assertEquals(VariableOrigin.Kind.INPUT, cpf.origin().kind());
        assertTrue(Edt.call(() -> fx.studio.view().variablesVisible()), "a nova variável aparece selecionada no painel");
        VariablesPanel.Row row = Edt.call(() -> panel().variableList().getSelectedValue());
        assertEquals("cliente.cpf · Texto · CPF", row.title());
        assertEquals("Entrada · Automação inteira · não usada · Dados pessoais", row.summary());
        assertTrue(Edt.call(() -> panel().originLine().getText()).startsWith("Origem: Entrada"));
        assertTrue(Edt.call(() -> fx.studio.state().project(fx.active().id()).orElseThrow().dirty()));
        assertEquals("Declarar variável", Edt.call(() -> fx.studio.service().history().undoLabel(fx.active().id()).orElse("")));
    }

    @Test
    @DisplayName("S008 — valor fixo: aviso de não criptografado; dado pessoal com valor fixo é recusado no campo, nada é declarado")
    void constanteSensivelRecusada() {
        fx.missionExample();
        VariableForm[] seen = new VariableForm[1];
        fx.prompts.then(f -> {
            seen[0] = (VariableForm) f;
            variable("cliente.cpf", VariableType.STRING, SemanticType.BRAZILIAN_CPF, true, Sensitivity.PERSONAL_DATA, VariableOrigin.Kind.CONSTANT,
                    "00000000000").accept(f);
        });
        Edt.run(() -> fx.studio.controller().newVariable());
        assertTrue(fx.active().variables().isEmpty());
        assertFalse(fx.prompts.results.get(fx.prompts.results.size() - 1));
        assertTrue(Edt.call(() -> seen[0].constantWarning().isVisible()));
        assertTrue(Edt.call(() -> seen[0].visibleErrors()).contains("não podem ter valor fixo"), Edt.call(() -> seen[0].visibleErrors()));
        assertEquals(0, Edt.call(() -> seen[0].tabs().getSelectedIndex()), "a aba com o campo do problema (Nome e valor) fica em primeiro plano");

        fx.prompts.then(variable("portal.endereco", VariableType.STRING, SemanticType.UNSPECIFIED, false, Sensitivity.PUBLIC, VariableOrigin.Kind.CONSTANT,
                "https://lab.exemplo.test"));
        Edt.run(() -> fx.studio.controller().newVariable());
        assertEquals("https://lab.exemplo.test", fx.active().variableNamed("portal.endereco").orElseThrow().origin().constant());
        VariablesPanel.Row row = Edt.call(() -> panel().variableList().getSelectedValue());
        assertFalse(row.summary().contains("lab.exemplo.test") || row.title().contains("lab.exemplo.test"), "a lista não mostra o valor fixo");
    }

    @Test
    @DisplayName("revisão 2 — formato que contradiz o significado (e-mail com máscara de CPF): recusado no campo de formato, nada é declarado")
    void formatoIncoerenteRecusado() {
        fx.missionExample();
        VariableForm[] seen = new VariableForm[1];
        fx.prompts.then(f -> {
            seen[0] = (VariableForm) f;
            variable("cliente.email", VariableType.STRING, SemanticType.EMAIL, true, Sensitivity.PERSONAL_DATA, VariableOrigin.Kind.INPUT, null).accept(f);
            seen[0].textFormatInput().setSelectedItem(TextFormat.BRAZILIAN_CPF_MASKED);
        });
        Edt.run(() -> fx.studio.controller().newVariable());
        assertTrue(fx.active().variables().isEmpty(), "nada é declarado");
        assertFalse(fx.prompts.results.get(fx.prompts.results.size() - 1));
        assertTrue(Edt.call(() -> seen[0].visibleErrors()).contains("contradiz o significado"), Edt.call(() -> seen[0].visibleErrors()));
        assertEquals(2, Edt.call(() -> seen[0].tabs().getSelectedIndex()), "a aba Restrições, com o campo de formato, fica em primeiro plano");
    }

    @Test
    @DisplayName("S008 — editor de etapa: lista as variáveis declaradas visíveis; escolher preenche a referência; nome não declarado é avisado")
    void editorDeEtapa() {
        Automation a = fx.missionExample();
        declareCpf();
        AutomationStep usuario = a.sessions().get(0).steps().get(0);
        StepDetailsForm[] seen = new StepDetailsForm[1];
        fx.prompts.then(f -> {
            StepDetailsForm d = (StepDetailsForm) f;
            seen[0] = d;
            d.modeInput(StepDetailsForm.ValueMode.VARIABLE).doClick();
            assertEquals(List.of(StepDetailsForm.CHOOSE_VARIABLE, "cliente.cpf"), items(d));
            d.valueInput().setText("cliente.cpfx");
            assertTrue(d.declaredVariableInfo().startsWith("Não declarada"), d.declaredVariableInfo());
            d.declaredVariableInput().setSelectedItem("cliente.cpf");
            assertEquals("cliente.cpf", d.valueInput().getText());
            assertTrue(d.declaredVariableInfo().startsWith("Declarada: Texto · CPF · origem entrada"), d.declaredVariableInfo());
        });
        Edt.run(() -> fx.studio.controller().openStepDetails(usuario.id()));
        assertEquals(new ValueSource.VariableReference("cliente.cpf"), fx.active().step(usuario.id()).orElseThrow().parameters().value().orElseThrow());
        Edt.run(() -> fx.studio.controller().showVariables());
        assertEquals("Entrada · Automação inteira · usada em 1 etapa · Dados pessoais", Edt.call(() -> panel().variableList().getSelectedValue().summary()));
        assertTrue(Edt.call(() -> panel().usageLine().getText()).contains("Sessão 1 · Etapa 01 — Preencher usuário"));
        Edt.run(() -> panel().goToButton().doClick());
        assertEquals(usuario.id(), ((com.nekkora.studio.app.workspace.Selection.OfStep) fx.studio.state().selection()).step());
    }

    @Test
    @DisplayName("STUDIO-012 — Finalidade é descrição: palavra com cara de valor (senha, usuário de segredo) é recusada no campo; nada é declarado")
    void finalidadeNaoEValor() {
        fx.missionExample();
        VariableForm[] seen = new VariableForm[1];
        for (String[] c : new String[][] {{"portal.usuario", "SECRET", "operador01"}, {"portal.senha", "SECRET", "Abc!2026xyz"}, {"lab.codigo", "PUBLIC", "Teste#2026"}}) {
            fx.prompts.then(f -> {
                seen[0] = (VariableForm) f;
                variable(c[0], VariableType.STRING, SemanticType.UNSPECIFIED, false, Sensitivity.valueOf(c[1]), VariableOrigin.Kind.INPUT, null).accept(f);
                seen[0].purposeInput().setText(c[2]);
            });
            Edt.run(() -> fx.studio.controller().newVariable());
            assertTrue(fx.active().variables().isEmpty(), "nada é declarado: " + c[0]);
            assertTrue(Edt.call(() -> seen[0].visibleErrors()).contains("finalidade parece conter um dado sensível"), Edt.call(() -> seen[0].visibleErrors()));
            assertEquals(1, Edt.call(() -> seen[0].tabs().getSelectedIndex()), "a aba com a Finalidade fica em primeiro plano");
        }
        // descrição de verdade passa, inclusive para um segredo
        fx.prompts.then(f -> {
            variable("portal.senha", VariableType.STRING, SemanticType.UNSPECIFIED, false, Sensitivity.SECRET, VariableOrigin.Kind.INPUT, null).accept(f);
            ((VariableForm) f).purposeInput().setText("Senha de acesso ao portal de auditoria");
        });
        Edt.run(() -> fx.studio.controller().newVariable());
        assertEquals("Senha de acesso ao portal de auditoria", fx.active().variableNamed("portal.senha").orElseThrow().purpose());
    }

    @Test
    @DisplayName("STUDIO-012 — origem do valor explícita: segredo sem campo de valor (fonte de segredo na execução); constante pública editável, Desfazer/Refazer")
    void origemDoValorExplicita() {
        Automation a = fx.missionExample();
        VariableForm[] seen = new VariableForm[1];
        fx.prompts.then(f -> {
            seen[0] = (VariableForm) f;
            variable("portal.senha", VariableType.STRING, SemanticType.UNSPECIFIED, false, Sensitivity.SECRET, VariableOrigin.Kind.INPUT, null).accept(f);
            assertTrue(javax.swing.SwingUtilities.isDescendingFrom(seen[0].originInput(), seen[0].tabs().getComponentAt(0)), "a origem do valor fica na primeira aba");
            assertTrue(javax.swing.SwingUtilities.isDescendingFrom(seen[0].purposeInput(), seen[0].tabs().getComponentAt(1)), "a finalidade fica com a descrição");
            assertTrue(seen[0].originHelp().getText().contains("NK_SECRET_"), seen[0].originHelp().getText());
            assertTrue(seen[0].originHelp().getText().contains("Não fica gravado no projeto"), seen[0].originHelp().getText());
            seen[0].originInput().setSelectedItem(VariableOrigin.Kind.CONSTANT);
            assertFalse(seen[0].constantInput().isEnabled(), "segredo: nenhum valor digitável no projeto");
            assertTrue(seen[0].originHelp().getText().contains("não podem ter valor constante"), seen[0].originHelp().getText());
            seen[0].originInput().setSelectedItem(VariableOrigin.Kind.INPUT);
            assertTrue(seen[0].usage().getText().contains("não a vincula"), seen[0].usage().getText());
        });
        Edt.run(() -> fx.studio.controller().newVariable());
        assertEquals(VariableOrigin.Kind.INPUT, fx.active().variableNamed("portal.senha").orElseThrow().origin().kind());
        assertEquals("Entrada na execução — fornecido ao rodar", VariableForm.originLabel(VariableOrigin.Kind.INPUT));

        // constante pública: valor gravado, editável, desfazível; o vínculo com a etapa é explícito
        fx.prompts.then(variable("auditoria.nivel", VariableType.STRING, SemanticType.UNSPECIFIED, false, Sensitivity.PUBLIC, VariableOrigin.Kind.CONSTANT,
                "Nível A"));
        Edt.run(() -> fx.studio.controller().newVariable());
        VariableDefinition nivel = fx.active().variableNamed("auditoria.nivel").orElseThrow();
        assertEquals("Nível A", nivel.origin().constant());
        fx.prompts.then(f -> {
            VariableForm v = (VariableForm) f;
            assertEquals("Nível A", v.constantInput().getText(), "reabrir mostra o valor constante para editar");
            assertTrue(v.constantInput().isEnabled());
            assertTrue(v.usage().getText().startsWith("Ainda não usada"), v.usage().getText());
            v.constantInput().setText("Nível B");
        });
        Edt.run(() -> fx.studio.controller().editVariable(nivel.id()));
        assertEquals("Nível B", fx.active().variable(nivel.id()).orElseThrow().origin().constant());
        Edt.run(() -> fx.studio.controller().undo());
        assertEquals("Nível A", fx.active().variable(nivel.id()).orElseThrow().origin().constant());
        Edt.run(() -> fx.studio.controller().redo());
        assertEquals("Nível B", fx.active().variable(nivel.id()).orElseThrow().origin().constant());
        AutomationStep usuario = a.sessions().get(0).steps().get(0);
        Edt.run(() -> fx.studio.service().setStepParameters(a.id(), usuario.id(),
                com.nekkora.studio.domain.action.ActionParameters.withValue(new ValueSource.VariableReference("auditoria.nivel"))));
        assertTrue(VariableForm.usageText(fx.active(), fx.active().variable(nivel.id()).orElseThrow()).startsWith("Usada em 1 etapa: Sessão 1 · Etapa 01"),
                VariableForm.usageText(fx.active(), fx.active().variable(nivel.id()).orElseThrow()));
    }

    private static List<String> items(StepDetailsForm d) {
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < d.declaredVariableInput().getItemCount(); i++) out.add(d.declaredVariableInput().getItemAt(i));
        return out;
    }

    @Test
    @DisplayName("S008 — renomear com prévia, Desfazer e Refazer; excluir em uso só com confirmação, desfazível")
    void renomearEExcluir() {
        Automation a = fx.missionExample();
        VariableDefinition cpf = declareCpf();
        AutomationStep usuario = a.sessions().get(0).steps().get(0);
        Edt.run(() -> fx.studio.service().setStepParameters(a.id(), usuario.id(),
                com.nekkora.studio.domain.action.ActionParameters.withValue(new ValueSource.VariableReference("cliente.cpf"))));
        String[] preview = new String[1];
        fx.prompts.then(f -> {
            RenameVariableForm r = (RenameVariableForm) f;
            preview[0] = r.previewText();
            r.nameInput().setText("titular.cpf");
        });
        Edt.run(() -> fx.studio.controller().renameVariable(cpf.id()));
        assertEquals("1 etapa passa a usar o novo nome, na mesma operação.", preview[0]);
        assertEquals(new ValueSource.VariableReference("titular.cpf"), fx.active().step(usuario.id()).orElseThrow().parameters().value().orElseThrow());
        assertEquals("Variável renomeada", fx.studio.state().lastNotice().orElseThrow().title());
        Edt.run(() -> fx.studio.controller().undo());
        assertEquals("cliente.cpf", fx.active().variable(cpf.id()).orElseThrow().name());
        assertEquals(new ValueSource.VariableReference("cliente.cpf"), fx.active().step(usuario.id()).orElseThrow().parameters().value().orElseThrow());
        Edt.run(() -> fx.studio.controller().redo());
        assertEquals("titular.cpf", fx.active().variable(cpf.id()).orElseThrow().name());

        fx.prompts.thenConfirm(false);
        Edt.run(() -> fx.studio.controller().deleteVariable(cpf.id()));
        assertEquals("Excluir e desligar 1 etapa", fx.prompts.confirmations.get(fx.prompts.confirmations.size() - 1).confirmLabel());
        assertEquals(1, fx.active().variables().size(), "cancelar não exclui");
        fx.prompts.thenConfirm(true);
        Edt.run(() -> fx.studio.controller().deleteVariable(cpf.id()));
        assertTrue(fx.active().variables().isEmpty());
        assertTrue(fx.active().step(usuario.id()).orElseThrow().parameters().value().isEmpty(), "a etapa fica sem valor (apontado na validação)");
        Edt.run(() -> fx.studio.controller().undo());
        assertEquals(1, fx.active().variables().size());
        assertEquals(new ValueSource.VariableReference("titular.cpf"), fx.active().step(usuario.id()).orElseThrow().parameters().value().orElseThrow());
    }

    @Test
    @DisplayName("S008 — apontamento de variável na validação: \"Ir para variável\" abre a aba Variáveis com ela selecionada")
    void apontamentoLevaAVariavel() {
        fx.missionExample();
        VariableDefinition cpf = declareCpf();
        Edt.run(() -> fx.studio.controller().validateActive());
        assertTrue(Edt.call(() -> fx.studio.view().validationVisible()));
        Finding unused = Edt.call(() -> fx.studio.view().validationPanel().report().orElseThrow().findings().stream()
                .filter(f -> f.code() == FindingCode.VARIABLE_UNUSED).findFirst().orElseThrow());
        assertEquals(FindingScope.VARIABLE, unused.scope());
        String place = Edt.call(() -> {
            var list = fx.studio.view().validationPanel().findingList();
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (list.getModel().getElementAt(i).finding().equals(unused)) {
                    list.setSelectedIndex(i);
                    return list.getModel().getElementAt(i).place();
                }
            }
            return "";
        });
        assertEquals("Variável — cliente.cpf", place);
        assertEquals("Ir para variável", Edt.call(() -> fx.studio.view().validationPanel().goToButton().getText()));
        Edt.run(() -> fx.studio.view().validationPanel().goToButton().doClick());
        assertTrue(Edt.call(() -> fx.studio.view().variablesVisible()));
        assertEquals(cpf.id(), Edt.call(() -> panel().variableList().getSelectedValue().variable().id()));
    }

    @Test
    @DisplayName("S008 — quatro temas e acessibilidade: painel e formulário renderizam; nomes acessíveis; sem cor como único sinal")
    void temasEAcessibilidade() {
        fx.missionExample();
        declareCpf();
        Edt.run(() -> fx.studio.controller().showVariables());
        for (NekkoraTheme t : NekkoraThemes.all()) {
            Edt.theme(t);
            Edt.call(() -> {
                javax.swing.SwingUtilities.updateComponentTreeUI(fx.studio.view().component());
                Edt.render(fx.studio.view().component(), 1366, 768);
                VariableForm form = new VariableForm(fx.active(), fx.active().variables().get(0));
                javax.swing.SwingUtilities.updateComponentTreeUI(form);
                Edt.render(form, 720, 640);
                return null;
            });
            assertTrue(Edt.call(() -> panel().getWidth() > 0 && panel().getHeight() > 0), t.displayName());
        }
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        assertEquals("Variáveis declaradas", Edt.call(() -> panel().variableList().getAccessibleContext().getAccessibleName()));
        String row = Edt.call(() -> {
            var list = panel().variableList();
            list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false);
            return list.getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleName();
        });
        assertTrue(row.contains("cliente.cpf · Texto · CPF") && row.contains("Dados pessoais"), "linha lida por tecnologia assistiva: " + row);
        assertEquals("Painel inferior: validação e variáveis", Edt.call(() -> fx.studio.view().bottomTabs().getAccessibleContext().getAccessibleName()));
        assertEquals("Variáveis", Edt.call(() -> fx.studio.view().bottomTabs().getTitleAt(1)));
    }

    @Test
    @DisplayName("S008 — painel de propriedades da automação: botão Variáveis e dependências; versão do modelo 1.1.0 não é \"anterior\"")
    void inspetor() {
        fx.missionExample();
        Edt.run(() -> fx.studio.controller().selectAutomation(fx.active().id()));
        String before = StudioFixture.texts(fx.studio.view().inspector());
        assertTrue(before.contains("1.0.0") && !before.contains("(anterior)"), before);
        declareCpf();
        Edt.run(() -> fx.studio.controller().selectAutomation(fx.active().id()));
        String after = StudioFixture.texts(fx.studio.view().inspector());
        assertTrue(after.contains("1.1.0") && !after.contains("(anterior)"), "1.1.0 é atual, não anterior: " + after);
        javax.swing.AbstractButton b = Edt.call(() -> find(fx.studio.view().inspector(), "Variáveis e dependências"));
        assertEquals("1 variável declarada", Edt.call(() -> b.getAccessibleContext().getAccessibleDescription()));
        Edt.run(() -> fx.studio.view().hideValidation());
        Edt.run(b::doClick);
        assertTrue(Edt.call(() -> fx.studio.view().variablesVisible()));
    }

    private static javax.swing.AbstractButton find(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.AbstractButton b && text.equals(b.getText())) return b;
            if (c instanceof java.awt.Container ct) {
                javax.swing.AbstractButton b = find(ct, text);
                if (b != null) return b;
            }
        }
        return null;
    }

    @Test
    @DisplayName("S008 — durante a gravação: consultar sim, editar não (mesmo bloqueio das outras edições)")
    void bloqueioDeGravacao() {
        fx.missionExample();
        declareCpf();
        Edt.run(() -> fx.studio.service().setRecordingGuard(id -> true));
        Edt.run(() -> fx.studio.service().editLockChanged(fx.active().id()));
        Edt.run(() -> fx.studio.controller().showVariables());
        assertTrue(Edt.call(() -> fx.studio.view().variablesVisible()));
        assertFalse(Edt.call(() -> panel().createButton().isEnabled()));
        assertFalse(Edt.call(() -> panel().renameButton().isEnabled()));
        int shown = fx.prompts.shown.size();
        Edt.run(() -> fx.studio.controller().newVariable());
        assertEquals(shown, fx.prompts.shown.size(), "nenhum formulário aberto");
        assertEquals("Edição indisponível durante a gravação", fx.studio.state().lastNotice().orElseThrow().title());
    }
}
