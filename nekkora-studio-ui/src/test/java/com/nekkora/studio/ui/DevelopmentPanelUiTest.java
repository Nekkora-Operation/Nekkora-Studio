package com.nekkora.studio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ui.develop.DevelopmentPanel;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.Nekkora;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NEKKORA-E2E-002: as opções de uma pergunta ficam todas dentro da janela, mesmo com uma opção por coluna da planilha. */
class DevelopmentPanelUiTest {

    @BeforeAll
    static void install() throws Exception {
        edt(() -> {
            if (!ThemeManager.isInstalled()) Nekkora.install(NekkoraThemes.SAKURA_DARK);
            return null;
        });
    }

    @Test
    @DisplayName("pergunta de coluna com 9 opções: botões em linhas, nenhum cortado fora do painel")
    void opcoesNaoSaemDaJanela() throws Exception {
        List<AssistanceRequest.Option> opts = new ArrayList<>();
        opts.add(AssistanceRequest.Option.answer("COLUMN:Ocupação", "Manter a coluna “Ocupação”", "o rótulo da linha nomeia a coluna"));
        for (String h : List.of("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade", "Título")) {
            opts.add(AssistanceRequest.Option.answer(FunctionalSpec.FALLBACK + java.util.UUID.randomUUID(), "Usar quando faltar “" + h + "”", "fonte alternativa"));
        }
        opts.add(AssistanceRequest.Option.answer(FunctionalSpec.NOT_IN_OUTPUT, "Não vai para a planilha", "fora das colunas"));
        opts.add(AssistanceRequest.Option.action("GO_TO_STEP", "Ir para a etapa", "rever o dado apontado"));
        QualityIssue q = new QualityIssue("SPEC_OUTPUT_COLUMN_REVIEW", Layer.V1_PDD, QualityIssue.Kind.WARNING, null, null, "Sessão 3 · Etapa 8",
                "Coluna derivada do rótulo da linha.", "Confirme a coluna.", new AssistanceRequest("Para qual coluna da planilha vai esta leitura?", "por quê",
                opts, FunctionalSpec.Field.OUTPUT_COLUMN, "Especificação funcional · colunas da planilha"), null, null);
        DevelopmentPanel.Model empty = DevelopmentPanel.Model.empty("Biografias", true);
        DevelopmentPanel.Model m = new DevelopmentPanel.Model(empty.subject(), true, false, "", "", empty.severity(), List.of(), List.of(), List.of(), List.of(),
                List.of(q), List.of(), "", "", false, false);
        DevelopmentPanel.Actions actions = (DevelopmentPanel.Actions) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {DevelopmentPanel.Actions.class}, (p, method, args) -> null);
        AtomicReference<List<String>> outside = new AtomicReference<>();
        AtomicReference<Integer> count = new AtomicReference<>();
        edt(() -> {
            DevelopmentPanel panel = new DevelopmentPanel(actions);
            panel.show(m);
            panel.tabs().setSelectedIndex(1);
            panel.pendingList().setSelectedIndex(0);
            panel.setSize(1100, 760);
            layout(panel);
            List<String> bad = new ArrayList<>();
            List<AbstractButton> buttons = new ArrayList<>();
            collect(panel.pendingButtons(), buttons);
            for (AbstractButton b : buttons) {
                Rectangle r = SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), panel);
                if (r.x < 0 || r.x + r.width > panel.getWidth()) bad.add(b.getText() + " " + r);
            }
            outside.set(bad);
            count.set(buttons.size());
            return null;
        });
        assertEquals(10, count.get(), "as 9 opções + Abrir PDD (pendência da camada V1)");
        assertTrue(outside.get().isEmpty(), "botões fora do painel: " + outside.get());
    }

    private static void layout(Container c) {
        c.doLayout();
        for (Component x : c.getComponents()) if (x instanceof Container k) layout(k);
    }

    private static void collect(Container c, List<AbstractButton> out) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton b) out.add(b);
            else if (x instanceof Container k) collect(k, out);
        }
    }

    private static <T> T edt(Callable<T> c) throws Exception {
        AtomicReference<T> v = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                v.set(c.call());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
        return v.get();
    }
}
