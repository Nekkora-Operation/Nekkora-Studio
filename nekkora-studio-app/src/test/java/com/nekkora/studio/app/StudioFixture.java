package com.nekkora.studio.app;

import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.ui.AutomationNavigator;
import com.nekkora.studio.ui.AutomationOverview;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.Component;
import java.awt.Container;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;

/** Studio montado como na aplicação real (sem janela), com usuário roteirizado. */
final class StudioFixture {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    final ScriptedPrompts prompts = new ScriptedPrompts();
    final AtomicInteger exits = new AtomicInteger();
    final Studio studio;

    StudioFixture() {
        Edt.theme(NekkoraThemes.SAKURA_DARK);
        studio = Edt.call(() -> Studio.create(CLOCK, prompts, exits::incrementAndGet));
        Edt.render(studio.view().component(), 1440, 900);
    }

    void dispose() {
        Edt.run(() -> studio.view().dispose());
    }

    // ------------------------------------------------------------------ ações do usuário (pela interface)

    Automation newAutomation(String name, String description) {
        prompts.then(ScriptedPrompts.name(name, description));
        Edt.run(() -> studio.controller().newAutomation());
        return active();
    }

    LogicalSession newSession(String name) {
        prompts.then(ScriptedPrompts.name(name, null));
        Edt.run(() -> studio.controller().newSession());
        return active().sessions().get(active().sessions().size() - 1);
    }

    AutomationStep newStep(String session, String name, ActionType type) {
        prompts.then(ScriptedPrompts.step(session, name, type));
        Edt.run(() -> studio.controller().newStep(null));
        Selection.OfStep s = (Selection.OfStep) studio.state().selection();
        return active().step(s.step()).orElseThrow();
    }

    /** "Resultado esperado" da missão (seção 34), criado pelos fluxos da interface. */
    Automation missionExample() {
        newAutomation("Consulta de clientes", "Consulta o cadastro de clientes.");
        newSession("Autenticação");
        newSession("Consulta");
        newSession("Resultado");
        newStep("Autenticação", "Preencher usuário", ActionType.FILL);
        newStep("Autenticação", "Preencher senha", ActionType.FILL);
        newStep("Autenticação", "Clicar em Entrar", ActionType.CLICK);
        newStep("Consulta", "Abrir menu Clientes", ActionType.CLICK);
        newStep("Consulta", "Preencher campo de pesquisa", ActionType.FILL);
        newStep("Consulta", "Clicar em Pesquisar", ActionType.CLICK);
        newStep("Resultado", "Consultar detalhes", ActionType.OBSERVE);
        return active();
    }

    // ------------------------------------------------------------------ leitura da interface

    Automation active() {
        return studio.state().activeAutomation().orElseThrow();
    }

    AutomationNavigator navigator() {
        return studio.view().navigator();
    }

    AutomationOverview overview() {
        return Edt.call(() -> studio.view().overview(active().id()).orElseThrow());
    }

    List<LogicalSessionView> sessionViews() {
        return Edt.call(() -> overview().sessionViews());
    }

    /** Linhas principais exibidas nas listas de etapas, na ordem da tela. */
    List<String> stepLines() {
        return Edt.call(() -> {
            List<String> out = new ArrayList<>();
            for (LogicalSessionView v : overview().sessionViews()) for (AutomationStep s : v.itemsInOrder()) out.add(v.labelOf(s));
            return out;
        });
    }

    String inspectorText() {
        return Edt.call(() -> texts(studio.view().inspector()));
    }

    /** Todo texto destinado ao usuário: rótulos, botões, campos, dicas, nomes acessíveis e linhas das listas. */
    static String texts(Component root) {
        StringBuilder b = new StringBuilder();
        collect(root, b);
        return b.toString();
    }

    private static void collect(Component c, StringBuilder b) {
        if (c instanceof JLabel l && l.getText() != null) b.append(l.getText()).append('\n');
        if (c instanceof AbstractButton ab && ab.getText() != null) b.append(ab.getText()).append('\n');
        if (c instanceof JTextComponent t) b.append(t.getText()).append('\n');
        if (c instanceof JComponent jc && jc.getToolTipText() != null) b.append(jc.getToolTipText()).append('\n');
        AccessibleContext ac = c.getAccessibleContext();
        if (ac != null && ac.getAccessibleName() != null) b.append(ac.getAccessibleName()).append('\n');
        if (ac != null && ac.getAccessibleDescription() != null) b.append(ac.getAccessibleDescription()).append('\n');
        if (c instanceof LogicalSessionView v) {
            for (AutomationStep s : v.itemsInOrder()) b.append(v.labelOf(s)).append('\n').append(LogicalSessionView.detailOf(s)).append('\n');
        }
        if (c instanceof AutomationNavigator n) {
            for (Automation a : n.items()) b.append(a.name()).append('\n').append(com.nekkora.studio.ui.StudioText.summary(a)).append('\n');
        }
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collect(ch, b);
    }
}
