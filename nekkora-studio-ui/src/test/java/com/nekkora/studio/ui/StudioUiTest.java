package com.nekkora.studio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationCatalog;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.CorrelationKeys;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.ui.ElementPresentation.Row;
import com.nekkora.studio.ui.form.NameDescriptionForm;
import com.nekkora.studio.ui.form.StepForm;
import com.nekkora.studio.ui.form.StudioDialogs;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.Nekkora;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Componentes do Studio: visão do domínio, formulários, privacidade e pintura nos quatro temas. */
class StudioUiTest {

    @BeforeAll
    static void install() {
        edt(() -> {
            if (!ThemeManager.isInstalled()) Nekkora.install(NekkoraThemes.SAKURA_DARK);
            return null;
        });
    }

    private static Automation example() {
        Automation a = new AutomationCatalog(Clock.systemUTC()).create("Consulta de clientes", "Consulta o cadastro.");
        LogicalSession auth = a.addSession("Autenticação", null);
        a.appendStep(auth.id(), StepDraft.manual("Preencher usuário", ActionType.FILL, null));
        a.appendStep(auth.id(), StepDraft.manual("Clicar em Entrar", ActionType.CLICK, null));
        LogicalSession q = a.addSession("Consulta", "Pesquisa pelo nome.");
        a.appendStep(q.id(), StepDraft.manual("Abrir menu Clientes", ActionType.CLICK, null));
        a.addSession("Resultado", null);
        return a;
    }

    private static final AutomationOverview.Listener NOOP = new AutomationOverview.Listener() {
        @Override
        public void automationSelected() {}

        @Override
        public void addSessionRequested() {}

        @Override
        public void addStepRequested() {}

        @Override
        public void sessionSelected(SessionId session) {}

        @Override
        public void stepSelected(SessionId session, StepId step) {}

        @Override
        public void stepOpened(SessionId session, StepId step) {}

        @Override
        public void addStepRequested(SessionId session) {}

        @Override
        public void renameSessionRequested(SessionId session) {}
    };

    @Test
    @DisplayName("a visão reflete o domínio e reconcilia sem recriar as sessões existentes (P2, 16.1)")
    void visaoReconcilia() {
        Automation a = example();
        AutomationOverview o = edt(() -> new AutomationOverview(a, NOOP));
        List<LogicalSessionView> before = edt(o::sessionViews);
        assertEquals(3, before.size());
        assertEquals(List.of("01   Preencher usuário", "02   Clicar em Entrar"),
                edt(() -> before.get(0).itemsInOrder().stream().map(before.get(0)::labelOf).toList()));
        assertEquals("03   Abrir menu Clientes", edt(() -> before.get(1).labelOf(before.get(1).itemsInOrder().get(0))));
        assertTrue(edt(() -> before.get(2).isShowingEmptyState()));

        AutomationStep added = a.appendStep(a.sessions().get(0).id(), StepDraft.manual("Preencher senha", ActionType.FILL, null));
        edt(() -> {
            o.refresh();
            return null;
        });
        List<LogicalSessionView> after = edt(o::sessionViews);
        for (int i = 0; i < 3; i++) assertSame(before.get(i), after.get(i), "visão de sessão reaproveitada");
        assertEquals("03   Preencher senha", edt(() -> after.get(0).labelOf(added)));
        assertEquals("04   Abrir menu Clientes", edt(() -> after.get(1).labelOf(after.get(1).itemsInOrder().get(0))),
                "numeração recalculada a partir da ordem do domínio");
    }

    @Test
    @DisplayName("seleção na lista notifica a intenção; mostrar a seleção não dispara eventos (sem laços)")
    void selecaoSemLacos() {
        Automation a = example();
        List<String> events = new ArrayList<>();
        AutomationOverview o = edt(() -> new AutomationOverview(a, new AutomationOverview.Listener() {
            @Override
            public void automationSelected() {}

            @Override
            public void addSessionRequested() {}

            @Override
            public void addStepRequested() {}

            @Override
            public void sessionSelected(SessionId session) {}

            @Override
            public void stepSelected(SessionId session, StepId step) {
                events.add("step");
            }

            @Override
            public void stepOpened(SessionId session, StepId step) {}

            @Override
            public void addStepRequested(SessionId session) {}

            @Override
            public void renameSessionRequested(SessionId session) {}
        }));
        StepId target = a.sessions().get(1).steps().get(0).id();
        edt(() -> {
            o.showSelection(a.sessions().get(1).id(), target);
            return null;
        });
        assertTrue(events.isEmpty());
        assertFalse(edt(() -> o.sessionViews().get(1).stepList().isSelectionEmpty()));
        edt(() -> {
            o.sessionViews().get(0).stepList().setSelectedIndex(1);
            return null;
        });
        assertEquals(List.of("step"), events);
    }

    @Test
    @DisplayName("formulário mostra o problema do domínio no campo certo e não fecha")
    void formularioValidacao() {
        NameDescriptionForm f = edt(() -> new NameDescriptionForm("Nome", "", null, true));
        boolean closed = edt(() -> StudioDialogs.trySubmit(f, v -> Optional.of(ValidationProblem.NAME_REQUIRED)));
        assertFalse(closed);
        assertEquals("Informe um nome.", edt(f::visibleErrors));
        boolean ok = edt(() -> StudioDialogs.trySubmit(f, v -> Optional.empty()));
        assertTrue(ok);
        assertEquals("", edt(f::visibleErrors), "erros limpos ao confirmar novamente");
        edt(() -> {
            f.showProblem(ValidationProblem.DESCRIPTION_TOO_LONG);
            return null;
        });
        assertTrue(edt(f::visibleErrors).contains("descrição"));
    }

    @Test
    @DisplayName("formulário de etapa: sessões pelo nome (sem IDs), tipos do catálogo e edição sem trocar sessão")
    void formularioEtapa() {
        Automation a = example();
        StepForm create = edt(() -> StepForm.forCreation(a, a.sessions().get(1).id()));
        assertEquals("2 · Consulta", edt(() -> create.sessionInput().getSelectedItem().toString()));
        assertEquals(a.sessions().get(1).id(), edt(create::values).session());
        assertEquals(ActionType.manualChoices().size(), edt(() -> create.typeInput().getItemCount()));
        AutomationStep step = a.sessions().get(0).steps().get(0);
        StepForm edit = edt(() -> StepForm.forEdit(step));
        assertEquals(null, edit.sessionInput());
        assertEquals("Preencher usuário", edt(() -> edit.values().name()));
        assertEquals(ActionType.FILL, edt(() -> edit.values().actionType()));
    }

    @Test
    @DisplayName("referência de elemento: linhas apresentáveis sem chaves de correlação nem valores inseguros")
    void elementoSeguro() {
        String uuid = UUID.randomUUID().toString();
        ElementReference e = ElementReference.builder().accessibleName("Senha").semanticKind("PASSWORD_INPUT").tag("input")
                .htmlId("senha").sensitive(true).privacyPolicy(DisplaySafety.KNOWN_PRODUCER_POLICY)
                .correlation(CorrelationKeys.none().with(CorrelationKeys.Key.TARGET_ID, "tgt-" + uuid)).build();
        List<Row> rows = ElementPresentation.rows(e);
        String all = rows.toString();
        assertFalse(all.contains(uuid));
        assertFalse(all.contains("id HTML"), "elemento sensível não expõe atributos");
        assertTrue(all.contains("Campo de senha"));
        assertTrue(all.contains("nenhum valor digitado é guardado"));
        assertEquals(StudioText.NOT_AVAILABLE, StudioText.technical("Campo " + uuid));
        assertEquals("Painel da conta", StudioText.technical("Painel da conta"));
        assertEquals("Linha de tabela", ElementPresentation.kindLabel("TABLE_ROW"));
        assertEquals("Custom dropdown", ElementPresentation.kindLabel("CUSTOM_DROPDOWN"), "categoria nova continua legível");
    }

    @Test
    @DisplayName("textos de contagem e numeração")
    void textos() {
        assertEquals("01", StudioText.number(1));
        assertEquals("10", StudioText.number(10));
        assertEquals("Nenhuma etapa", StudioText.steps(0));
        assertEquals("1 etapa", StudioText.steps(1));
        assertEquals("3 sessões · 3 etapas", StudioText.summary(example()));
    }

    static Stream<NekkoraTheme> themes() {
        return NekkoraThemes.all().stream();
    }

    @ParameterizedTest
    @MethodSource("themes")
    @DisplayName("S001-016 componentes do Studio pintam em todos os temas")
    void pintamNosQuatroTemas(NekkoraTheme theme) {
        edt(() -> {
            ThemeManager.setTheme(theme);
            Automation a = example();
            AutomationOverview o = new AutomationOverview(a, NOOP);
            o.showSelection(a.sessions().get(0).id(), a.sessions().get(0).steps().get(0).id());
            paint(o, 900, 1200);
            AutomationNavigator n = new AutomationNavigator(() -> { });
            n.show(List.of(a), a.id());
            paint(n, 260, 600);
            paint(new AutomationNavigator(() -> { }), 260, 600);
            com.nekkora.ui.studio.NekkoraInspector inspector = new com.nekkora.ui.studio.NekkoraInspector("Propriedades");
            PropertiesPresenter p = new PropertiesPresenter(inspector, null);
            p.showStep(a, a.sessions().get(0).steps().get(0).id());
            paint(inspector, 320, 800);
            p.showSession(a, a.sessions().get(2).id());
            paint(inspector, 320, 800);
            p.showAutomation(a);
            paint(inspector, 320, 800);
            paint(com.nekkora.studio.ui.form.StudioForms.stepDetails(a, a.sessions().get(0).steps().get(0)).form(), 700, 900);
            ThemeManager.setTheme(NekkoraThemes.SAKURA_DARK);
            return null;
        });
    }

    @Test
    @DisplayName("S001-019 a UI do Studio não depende da aplicação")
    void semDependenciaDaAplicacao() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(f).contains("import com.nekkora.studio.app."), f.toString());
            }
        }
    }

    private static void paint(JComponent c, int w, int h) {
        c.setSize(w, h);
        layout(c);
        layout(c);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.printAll(g);
        g.dispose();
    }

    private static void layout(Component c) {
        if (c instanceof Container ct) {
            ct.doLayout();
            for (Component ch : ct.getComponents()) layout(ch);
        }
    }

    static <T> T edt(Callable<T> c) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    out.set(c.call());
                } catch (Throwable t) {
                    err.set(t);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (err.get() instanceof RuntimeException re) throw re;
        if (err.get() instanceof Error er) throw er;
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }
}
