package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.ui.StudioController;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S001-019 (camadas), S001-020 (sem funcionalidades fictícias) e S001-021 (encerramento). */
class StudioScopeTest {
    private static final Path ROOT = Path.of("..");
    private final StudioFixture fx = new StudioFixture();

    @AfterEach
    void tearDown() {
        fx.dispose();
    }

    @Test
    @DisplayName("S001-019 dependências descem: domínio ← UI do Studio ← aplicação; o design system não conhece automações")
    void camadas() throws IOException {
        List<String> v = new ArrayList<>();
        v.addAll(imports("nekkora-swing-ui", Pattern.compile("import\\s+com\\.nekkora\\.studio\\.")));
        v.addAll(imports("nekkora-design-tokens", Pattern.compile("import\\s+com\\.nekkora\\.(studio|ui)\\.")));
        v.addAll(imports("nekkora-studio-domain", Pattern.compile("import\\s+(javax\\.swing|java\\.awt|com\\.formdev|com\\.nekkora\\.ui|com\\.nekkora\\.tokens|com\\.nekkora\\.studio\\.(ui|app))\\b")));
        v.addAll(imports("nekkora-studio-ui", Pattern.compile("import\\s+com\\.nekkora\\.studio\\.app\\.")));
        v.addAll(imports("nekkora-swing-showcase", Pattern.compile("import\\s+com\\.nekkora\\.studio\\.")));
        assertTrue(v.isEmpty(), "Dependências entre camadas proibidas:\n" + String.join("\n", v));

        Pattern studioArtifact = Pattern.compile("<artifactId>nekkora-studio-(domain|ui|app)</artifactId>");
        for (String ds : List.of("nekkora-design-tokens", "nekkora-swing-ui", "nekkora-swing-showcase")) {
            assertFalse(studioArtifact.matcher(Files.readString(ROOT.resolve(ds + "/pom.xml"))).find(), ds + " não pode depender do Studio");
        }
        String studioUiPom = Files.readString(ROOT.resolve("nekkora-studio-ui/pom.xml"));
        assertFalse(studioUiPom.contains("<artifactId>nekkora-studio-app</artifactId>"));
        String domainPom = Files.readString(ROOT.resolve("nekkora-studio-domain/pom.xml"));
        assertFalse(domainPom.contains("flatlaf") || domainPom.contains("nekkora-swing-ui"));
    }

    private static List<String> imports(String module, Pattern forbidden) throws IOException {
        List<String> out = new ArrayList<>();
        Path src = ROOT.resolve(module).resolve("src/main/java");
        if (!Files.isDirectory(src)) throw new IllegalStateException("módulo não encontrado: " + src.toAbsolutePath());
        try (Stream<Path> files = Files.walk(src)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    if (forbidden.matcher(lines.get(i)).find()) out.add(module + "/" + src.relativize(f) + ":" + (i + 1) + "  " + lines.get(i).strip());
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("S001-020 gravar, executar e salvar existem apenas desabilitados e sem efeito")
    void futurasDesabilitadas() {
        StudioActions actions = fx.studio.view().actions();
        for (String id : StudioActions.FUTURE) assertFalse(Edt.call(() -> actions.get(id).isEnabled()), id);
        List<NekkoraToolbarButton> future = new ArrayList<>();
        Edt.run(() -> collect(fx.studio.view().component(), future));
        assertEquals(3, future.size(), "gravar, executar e salvar na toolbar");
        for (NekkoraToolbarButton b : future) {
            assertFalse(Edt.call(b::isEnabled));
            assertTrue(Edt.call(b::getToolTipText).contains("próxima versão") || Edt.call(b::getToolTipText).contains("não está disponível"));
            Edt.run(b::doClick); // clique em controle desabilitado: nada acontece
        }
        assertTrue(fx.studio.state().lastNotice().isEmpty(), "nenhuma notificação fictícia de sucesso");
        assertTrue(fx.studio.state().automations().isEmpty());
    }

    private static void collect(Component c, List<NekkoraToolbarButton> out) {
        if (c instanceof NekkoraToolbarButton b && b.getAction() != null
                && StudioActions.FUTURE.contains(String.valueOf(b.getAction().getValue(com.nekkora.ui.action.NekkoraAction.ID)))) {
            out.add(b);
        }
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collect(ch, out);
    }

    @Test
    @DisplayName("S001-020 código do Studio sem login, licença, IA, execução, exportação ou sucesso simulado")
    void semFuncionalidadesFicticias() throws IOException {
        Pattern fake = Pattern.compile("(?i)(execução concluída|alterações salvas|\"salvo\"|gravando|itens processados|login\\s*\\(|"
                + "licen[cç]a ativ|trial|ollama|openai|anthropic|playwright|selenium|pdf|ana souza)");
        List<String> v = new ArrayList<>();
        for (String module : List.of("nekkora-studio-domain", "nekkora-studio-ui", "nekkora-studio-app")) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(module).resolve("src/main/java"))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String name = f.getFileName().toString();
                    if (name.matches("(?i).*(login|auth|license|licen[cç]a|recorder|executor|runner|llm|export).*")) v.add("classe: " + f);
                    List<String> lines = Files.readAllLines(f);
                    for (int i = 0; i < lines.size(); i++) {
                        String l = lines.get(i).strip();
                        if (l.startsWith("*") || l.startsWith("//") || l.startsWith("/**")) continue; // documentação pode citar o escopo futuro
                        if (fake.matcher(l).find()) v.add(f.getFileName() + ":" + (i + 1) + "  " + l);
                    }
                }
            }
        }
        assertTrue(v.isEmpty(), "Funcionalidade fora de escopo ou simulada:\n" + String.join("\n", v));
    }

    @Test
    @DisplayName("S001-021 ao fechar com alterações em memória, avisa; cancelar mantém o Studio aberto")
    void encerramentoComAlteracoes() {
        fx.newAutomation("A", null);
        fx.prompts.thenExit(false);
        Edt.run(() -> fx.studio.controller().requestExit());
        assertEquals(List.of(StudioController.EXIT_MESSAGE), fx.prompts.exitMessages);
        assertTrue(StudioController.EXIT_MESSAGE.contains("memória") && StudioController.EXIT_MESSAGE.contains("perdidas"));
        assertEquals(0, fx.exits.get(), "cancelar não fecha");
        fx.prompts.thenExit(true);
        Edt.run(() -> fx.studio.controller().requestExit());
        assertEquals(1, fx.exits.get());
    }

    @Test
    @DisplayName("S001-021 sem alterações, fecha sem perguntar")
    void encerramentoSemAlteracoes() {
        Edt.run(() -> fx.studio.controller().requestExit());
        assertTrue(fx.prompts.exitMessages.isEmpty());
        assertEquals(1, fx.exits.get());
    }

    @Test
    @DisplayName("barra de status informa rascunho em memória e alterações não salvas")
    void barraDeStatus() {
        assertTrue(Edt.call(() -> fx.studio.view().statusItems().get(0).getText()).equals("Rascunho em memória"));
        assertFalse(Edt.call(() -> fx.studio.view().statusItems().get(1).isVisible()));
        fx.newAutomation("A", null);
        assertTrue(Edt.call(() -> fx.studio.view().statusItems().get(1).isVisible()));
        assertEquals("1 automação em memória", Edt.call(() -> fx.studio.view().statusItems().get(2).getText()));
    }
}
