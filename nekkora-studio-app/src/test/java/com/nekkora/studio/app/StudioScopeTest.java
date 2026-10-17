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

/**
 * S001-019 (camadas), S001-020 / S002-030 (sem funcionalidades fictícias) e S001-021 (encerramento).
 *
 * <p>STUDIO-002: "Gravar" deixou de ser uma ação futura (a gravação técnica é real e só muda de estado com a confirmação
 * da extensão — coberta por {@code RecorderStudioTest}); executar e salvar continuam apenas desabilitados. As regras
 * de camadas passam a valer também para os módulos novos (bridge e recorder).
 */
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
        // STUDIO-002: o Bridge é transporte puro (sem domínio, sem Swing); o Recorder é Java puro (sem Swing).
        v.addAll(imports("nekkora-studio-bridge", Pattern.compile("import\\s+(javax\\.swing|java\\.awt|com\\.formdev|com\\.nekkora\\.ui|com\\.nekkora\\.tokens|com\\.nekkora\\.studio\\.(domain|ui|app|recorder))\\b")));
        v.addAll(imports("nekkora-studio-recorder", Pattern.compile("import\\s+(javax\\.swing|java\\.awt|com\\.formdev|com\\.nekkora\\.ui|com\\.nekkora\\.tokens|com\\.nekkora\\.studio\\.(ui|app))\\b")));
        v.addAll(imports("nekkora-swing-showcase", Pattern.compile("import\\s+com\\.nekkora\\.studio\\.")));
        assertTrue(v.isEmpty(), "Dependências entre camadas proibidas:\n" + String.join("\n", v));

        Pattern studioArtifact = Pattern.compile("<artifactId>nekkora-studio-(domain|ui|app|bridge|recorder)</artifactId>");
        for (String ds : List.of("nekkora-design-tokens", "nekkora-swing-ui", "nekkora-swing-showcase")) {
            assertFalse(studioArtifact.matcher(Files.readString(ROOT.resolve(ds + "/pom.xml"))).find(), ds + " não pode depender do Studio");
        }
        String studioUiPom = Files.readString(ROOT.resolve("nekkora-studio-ui/pom.xml"));
        assertFalse(studioUiPom.contains("<artifactId>nekkora-studio-app</artifactId>"));
        String domainPom = Files.readString(ROOT.resolve("nekkora-studio-domain/pom.xml"));
        assertFalse(domainPom.contains("flatlaf") || domainPom.contains("nekkora-swing-ui"));
        for (String pure : List.of("nekkora-studio-bridge", "nekkora-studio-recorder")) {
            String pom = Files.readString(ROOT.resolve(pure + "/pom.xml"));
            assertFalse(pom.contains("flatlaf") || pom.contains("nekkora-swing-ui") || pom.contains("nekkora-studio-ui"), pure + " sem Swing");
        }
        assertFalse(Files.readString(ROOT.resolve("nekkora-studio-bridge/pom.xml")).contains("<artifactId>nekkora-studio-domain</artifactId>"),
                "o Bridge não conhece o domínio das automações");
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
    @DisplayName("S001-020 executar existe apenas desabilitado e sem efeito (gravar é real desde a STUDIO-002; salvar, desde a STUDIO-006)")
    void futurasDesabilitadas() {
        StudioActions actions = fx.studio.view().actions();
        // STUDIO-006: "Salvar" deixou de ser futura (§28); sem automação ativa, fica desabilitada — sem efeito, sem aviso fictício
        assertEquals(List.of(StudioActions.RUN), StudioActions.FUTURE);
        assertFalse(Edt.call(() -> actions.get(StudioActions.SAVE).isEnabled()), "sem automação ativa não há o que salvar");
        for (String id : StudioActions.FUTURE) assertFalse(Edt.call(() -> actions.get(id).isEnabled()), id);
        assertFalse(Edt.call(() -> actions.get(StudioActions.RECORD).isEnabled()), "sem automação/sessão de destino não há o que gravar");
        assertEquals("Gravar", Edt.call(() -> actions.get(StudioActions.RECORD).getValue(javax.swing.Action.NAME)));
        List<NekkoraToolbarButton> future = new ArrayList<>();
        Edt.run(() -> collect(fx.studio.view().component(), future));
        assertEquals(1, future.size(), "executar na toolbar");
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
    @DisplayName("S001-020/S002-030 código do Studio sem login, licença, IA, execução, sensores de interação, exportação ou sucesso simulado")
    void semFuncionalidadesFicticias() throws IOException {
        Pattern fake = Pattern.compile("(?i)(execução concluída|alterações salvas|\"salvo\"|gravando|itens processados|login\\s*\\(|"
                + "licen[cç]a ativ|trial|ollama|openai|anthropic|playwright|selenium|pdf|ana souza|etapas? gravadas?|cliques? capturados?)");
        // Usos legítimos de "Gravando": o rótulo do botão, devolvido SOMENTE para o estado RECORDING confirmado pela
        // extensão (verificado em RecorderStudioTest), e — STUDIO-003 — o rótulo do Recorder quando, além disso, a captura
        // de ações está ativa (verificado em RecorderStepsStudioTest).
        String confirmedLabel = "case RECORDING -> \"Gravando\";";
        String confirmedStepsLabel = "private static final String CONFIRMED_RECORDING_LABEL = \"Gravando\";";
        // STUDIO-006: "Salvo" é real — ProjectState só devolve SAVED quando a geração em memória é a gravada por um salvamento
        // CONFIRMADO (verificado em ProjectStudioTest); antes da STUDIO-006, salvar não existia e o texto só poderia ser fictício
        String confirmedSavedLabel = "SAVED(\"Salvo\"),";
        // STUDIO-010 (D-S010-13): o PDF do PDD visual é REAL — gerado e verificado pelo módulo nekkora-studio-pdd (PddPdfTest,
        // PddUiTest e a homologação). Só os pacotes do PDD (app/pdd, ui/pdd) e a descrição da ação podem falar em PDF — e, nesses
        // pacotes, a exceção retira SÓ a palavra "PDF": o resto da linha continua sujeito a todos os outros termos proibidos.
        String pddActionDescription = ".description(\"Documento PDF do processo mapeado: sessões, instruções e capturas sanitizadas do laboratório. Não altera a automação.\"));";
        List<String> v = new ArrayList<>();
        for (String module : List.of("nekkora-studio-domain", "nekkora-studio-ui", "nekkora-studio-app", "nekkora-studio-bridge", "nekkora-studio-recorder")) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(module).resolve("src/main/java"))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String name = f.getFileName().toString();
                    // "recorder" saiu da lista (STUDIO-002); BridgeAuth é a autenticação LOCAL do canal, não login de usuário.
                    if (name.matches("(?i).*(login|license|licen[cç]a|executor|runner|llm|export|sensor|clickcapture).*") || name.matches("(Ai|AI)[A-Z].*")
                            || (name.matches("(?i).*auth.*") && !name.equals("BridgeAuth.java"))) v.add("classe: " + f);
                    List<String> lines = Files.readAllLines(f);
                    String unix = f.toString().replace(java.io.File.separatorChar, '/');
                    boolean pddPackage = unix.contains("/com/nekkora/studio/app/pdd/") || unix.contains("/com/nekkora/studio/ui/pdd/");
                    for (int i = 0; i < lines.size(); i++) {
                        String l = lines.get(i).strip();
                        if (name.equals("StudioActions.java") && l.equals(pddActionDescription)) continue;
                        if (l.startsWith("*") || l.startsWith("//") || l.startsWith("/**")) continue; // documentação pode citar o escopo futuro
                        String checked = pddPackage ? l.replaceAll("(?i)pdf", "") : l;
                        if (fake.matcher(checked).find() && !(f.getFileName().toString().equals("RecorderPresenter.java") && l.equals(confirmedLabel))
                                && !(f.getFileName().toString().equals("RecorderText.java") && l.equals(confirmedStepsLabel))
                                && !(f.getFileName().toString().equals("ProjectState.java") && l.equals(confirmedSavedLabel))) {
                            v.add(f.getFileName() + ":" + (i + 1) + "  " + l);
                        }
                    }
                }
            }
        }
        assertTrue(v.isEmpty(), "Funcionalidade fora de escopo ou simulada:\n" + String.join("\n", v));
    }

    @Test
    @DisplayName("S001-021/S006-068/070 ao fechar com alterações não salvas, avisa quais projetos; cancelar mantém o Studio aberto")
    void encerramentoComAlteracoes() {
        fx.newAutomation("A", null);
        fx.prompts.thenExit(false);
        Edt.run(() -> fx.studio.controller().requestExit());
        // STUDIO-006 (§29): o aviso lista os projetos com alterações e oferece Salvar todos / Descartar / Cancelar
        assertEquals(List.of(List.of("A")), fx.prompts.unsavedAsked);
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
    @DisplayName("barra de status informa a situação do projeto ativo e alterações não salvas (STUDIO-006)")
    void barraDeStatus() {
        assertEquals("Nenhum projeto aberto", Edt.call(() -> fx.studio.view().statusItems().get(0).getText()));
        assertFalse(Edt.call(() -> fx.studio.view().statusItems().get(1).isVisible()));
        fx.newAutomation("A", null);
        assertEquals("Alterações não salvas — sem arquivo", Edt.call(() -> fx.studio.view().statusItems().get(0).getText()));
        assertTrue(Edt.call(() -> fx.studio.view().statusItems().get(1).isVisible()));
        assertEquals("1 automação no Studio", Edt.call(() -> fx.studio.view().statusItems().get(2).getText()));
    }
}
