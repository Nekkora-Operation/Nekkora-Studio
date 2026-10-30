package com.nekkora.studio.app.evidence;

import com.nekkora.studio.ai.quality.GeneratedCodeQuality;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Homologação REAL da NEKKORA-CODEGEN-QUALITY-001 rodada 4 (Q12 — modalidade tabular, RPA Challenge) no Windows: Studio de
 * verdade (dados isolados), mouse e teclado do sistema (Robot). Operador: Claude como AGENTE DE TESTE (TEST_AGENT).
 *
 * <p>O modelo é o {@code rpa-challenge.nekkora} gravado no site real pela homologação da E2E-001 (Recorder → PDD → debug
 * guiado, decisões CONFIRMED) com a configuração de geração declarada ao lado ({@code rpa-challenge.codegen.json}); nada do
 * modelo é editado aqui. O projeto é ABERTO pela interface (Ctrl+O) e o código é gerado pelo menu "Automação → Gerar
 * código Python…" do Studio (hoje em "Automação → Avançado → Gerador avançado (configuração manual)…") — o mesmo gerador da E2E-001. Cobre: geração (pacote automation/ em inglês, chaves tipadas),
 * determinismo, proteção de edição manual, regeneração sobre o projeto ANTERIOR (paginas.py/processo.py guardados em
 * previous-generation/), e as verificações V4/V5/VQ do próprio Studio ({@link PythonChecks}). A execução no site real e na
 * réplica é feita depois, pelas ferramentas de execução, sobre a pasta gerada aqui.
 *
 * <p>Uso: {@code CodegenQuality001Tabular <saída> <projeto.nekkora> <projeto.codegen.json> <pasta-gerada-anterior> <python>}
 */
public final class CodegenQuality001Tabular {

    private static final Pattern UUID_RE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final File out;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private int failures;
    private int checks;
    private StudioApplication.Window window;
    private BridgeServer bridge;
    private int generations;
    /** Amostra rpa-challenge da rodada 4 (opcional): regerar sobre ela comprova o vínculo da chave renomeada. */
    private Path round4;

    private CodegenQuality001Tabular(File out) throws AWTException {
        this.out = out;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        CodegenQuality001Tabular h = new CodegenQuality001Tabular(out);
        int code = 1;
        try {
            h.round4 = args.length > 5 ? Path.of(args[5]).toAbsolutePath() : null;
            h.run(Path.of(args[1]).toAbsolutePath(), Path.of(args[2]).toAbsolutePath(), Path.of(args[3]).toAbsolutePath(), Path.of(args[4]));
            code = h.failures == 0 ? 0 : 2;
        } catch (Throwable t) {
            for (Throwable x = t; x != null; x = x.getCause()) {
                h.log((x == t ? "ERRO " : "CAUSA ") + x);
                for (StackTraceElement s : x.getStackTrace()) h.log("    at " + s);
            }
            h.failures++;
        } finally {
            h.log(String.format("RESULTADO: %d verificações, %d falha(s)", h.checks, h.failures));
            h.writeLog();
            h.teardown();
        }
        System.exit(code);
    }

    private void run(Path sourceProject, Path sourceConfig, Path previousGeneration, Path python) throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version"));
        log("Operador: Claude como AGENTE DE TESTE (TEST_AGENT; nenhuma confirmação humana)");
        // cópias de trabalho: o projeto e a configuração da E2E-001 ficam intactos
        Path work = out.toPath().resolve("projeto");
        Files.createDirectories(work);
        Path project = work.resolve("rpa-challenge.nekkora");
        Path config = work.resolve("rpa-challenge.codegen.json");
        Files.copy(sourceProject, project);
        Files.copy(sourceConfig, config);
        log("modelo: " + sourceProject.getFileName() + " (gravado no site real pela E2E-001) · configuração declarada: " + sourceConfig.getFileName());

        Path studioData = out.toPath().resolve("studio-data");
        Files.createDirectories(studioData);
        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, bridge::close,
                com.nekkora.studio.app.StudioEnvironment.at(studioData), List.of()));
        edt(() -> {
            window.frame().setSize(1180, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));

        // ================================================================ G1 — abrir o projeto pela interface
        openByDialog(project);
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        List<String> kinds = edt(() -> a.sessions().stream().map(s -> s.name() + ":" + s.steps().stream().map(x -> x.actionType().name()).toList()).toList());
        log("  sessões: " + kinds);
        check("G1: projeto aberto pela interface com as 3 sessões e as 11 etapas gravadas na E2E-001", edt(() -> a.sessions().size() == 3 && a.stepCount() == 11));
        check("G1: etapas de página com decisão CONFIRMED (debug guiado da E2E-001)", edt(() -> Stream.concat(a.sessions().get(0).steps().stream(),
                a.sessions().get(1).steps().stream()).allMatch(s -> s.targetDecision().map(d -> d.confirmed()).orElse(false))));
        captureWindow(window.frame(), "G1-projeto-aberto");

        // ================================================================ G2 — gerar pelo menu (Q12)
        Path gen = out.toPath().resolve("codigo-gerado");
        String r1 = generateByMenu(config, gen);
        log("  resultado da geração:\n" + r1.indent(4));
        check("G2: projeto gerado pelo Studio (menu Automação → Avançado → Gerador avançado (configuração manual)…)", r1.contains("GERADO em"));
        List<String> expected = List.of("README.md", "automation/__init__.py", "automation/pages.py", "automation/process.py", "automation/selectors.py",
                "config/execution.toml", "main.py", "manifest.json", "requirements.txt", "selectors.json");
        check("G2: layout da rodada 3 também no tabular (pacote automation/, selectors.py + selectors.json, config/execution.toml)",
                expected.stream().allMatch(f -> Files.isRegularFile(gen.resolve(f))) && !Files.exists(gen.resolve("paginas.py")) && !Files.exists(gen.resolve("processo.py")));
        Map<String, Object> m = Json.parseObject(Files.readString(gen.resolve("manifest.json"), StandardCharsets.UTF_8));
        check("G2: manifesto 1.4.0, código em inglês, execução tabular, gerador " + m.get("generator"),
                "1.4.0".equals(m.get("version")) && "en".equals(m.get("language")) && "tabular".equals(m.get("execution")));
        String code = Files.readString(gen.resolve("automation/pages.py"), StandardCharsets.UTF_8) + Files.readString(gen.resolve("automation/process.py"),
                StandardCharsets.UTF_8) + Files.readString(gen.resolve("main.py"), StandardCharsets.UTF_8);
        check("G2: nenhum StepId (UUID) no código — step_id legível, o mapa fica no manifesto", !UUID_RE.matcher(code).find());
        check("G2: telas pelo nome da sessão em inglês (PreparationPage, RecordFillingPage, ConclusionPage)", code.contains("class PreparationPage(PageBase):")
                && code.contains("class RecordFillingPage(PageBase):") && code.contains("class ConclusionPage(PageBase):"));
        check("G2: alvos só como SelectorKey.<MEMBRO>.value (nenhuma chave como texto nas telas)",
                code.contains("SelectorKey.RECORD_FILLING_SUBMIT.value") && !Files.readString(gen.resolve("automation/pages.py")).contains("\"process."));
        check("G2: InputRow com os cabeçalhos da planilha como contrato (\"First Name\" … \"Phone Number\")",
                code.contains("first_name=_cell(item.data, \"First Name\")") && code.contains("phone_number=_cell(item.data, \"Phone Number\")"));
        check("G2: linha de comando em inglês com o nome anterior aceito (--output/--saida)", code.contains("\"--output\", \"--saida\""));
        for (String pt : List.of("Sessao", "executar", "segredos", "baixado", "marca =", "_valor(", "class Processo", "URL_INICIAL", "# etapa")) {
            if (code.contains(pt)) check("G2: identificador do gerador anterior ausente (" + pt + ")", false);
        }
        check("G2: nenhum identificador do gerador anterior (Sessao/executar/segredos/marca/# etapa…)", true);
        String selectorsPy = Files.readString(gen.resolve("automation/selectors.py"), StandardCharsets.UTF_8);
        check("G2 (CQ-R5-02): chave do download pelo efeito (PREPARATION_DOWNLOAD_SPREADSHEET), sem o token do ícone",
                selectorsPy.contains("PREPARATION_DOWNLOAD_SPREADSHEET = \"process.preparation.download_spreadsheet\"") && !selectorsPy.contains("CLOUD_DOWNLOAD ="));
        check("G2 (CQ-R5-02): o texto real da página continua no localizador (\"Download Excel cloud_download\")",
                selectorsPy.contains("\"name\": \"Download Excel cloud_download\"") && Files.readString(gen.resolve("selectors.json")).contains("Download Excel cloud_download"));
        check("G2 (CQ-R5-01): InputRow sem repr automático (dados pessoais não aparecem no repr)", code.contains("@dataclass(frozen=True, repr=False)\nclass InputRow:"));
        check("G2: código sem localizador posicional", !Files.readString(gen.resolve("selectors.json")).contains("nth-of-type")
                && !Files.readString(gen.resolve("selectors.json")).contains("nth-child"));

        // ================================================================ G3 — determinismo e proteção
        String r2 = generateByMenu(config, gen);
        check("G3: gerar de novo → nada muda (determinístico)", !r2.contains("WRITTEN") && r2.contains("UNCHANGED"));
        Path pages = gen.resolve("automation/pages.py");
        String original = Files.readString(pages, StandardCharsets.UTF_8);
        Files.writeString(pages, original.replace("    def submit_record(", "    # editado à mão\n    def submit_record("), StandardCharsets.UTF_8);
        String r3 = generateByMenu(config, gen);
        check("G3: arquivo gerado editado à mão é PROTEGIDO (.novo + .diff), não sobrescrito", r3.contains("PROTECTED  automation/pages.py")
                && Files.readString(pages).contains("editado à mão") && Files.exists(gen.resolve("automation/pages.py.diff")));
        Files.writeString(pages, original, StandardCharsets.UTF_8);
        Files.deleteIfExists(gen.resolve("automation/pages.py.novo"));
        Files.deleteIfExists(gen.resolve("automation/pages.py.diff"));
        Files.deleteIfExists(gen.resolve("manifest.json.novo"));
        String r4 = generateByMenu(config, gen);
        check("G3: depois de desfazer a edição, a geração volta a ficar limpa", !r4.contains("PROTECTED"));

        // ================================================================ G4 — regenerar sobre o projeto ANTERIOR (compatibilidade)
        Path upgrade = out.toPath().resolve("codigo-anterior-regerado");
        copyTree(previousGeneration, upgrade);
        List<String> before;
        try (Stream<Path> s = Files.walk(upgrade)) {
            before = s.filter(Files::isRegularFile).map(p -> upgrade.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
        log("  projeto anterior (gerador 1.0.0): " + before);
        String r5 = generateByMenu(config, upgrade);
        log("  regeneração sobre o anterior:\n" + r5.indent(4));
        Path prev = upgrade.resolve("previous-generation");
        List<String> kept;
        try (Stream<Path> s = Files.exists(prev) ? Files.walk(prev) : Stream.empty()) {
            kept = s.filter(Files::isRegularFile).map(p -> prev.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
        log("  previous-generation/: " + kept);
        check("G4: paginas.py/processo.py/config/execucao.toml do gerador anterior APOSENTADOS (RETIRED), nunca apagados",
                r5.contains("RETIRED  paginas.py") && r5.contains("RETIRED  processo.py") && r5.contains("RETIRED  config/execucao.toml"));
        check("G4: a geração anterior continua inteira em previous-generation/ (com o manifesto dela)",
                kept.stream().anyMatch(x -> x.endsWith("/paginas.py")) && kept.stream().anyMatch(x -> x.endsWith("/processo.py"))
                        && kept.stream().anyMatch(x -> x.endsWith("/manifest.json")));
        List<String> code5 = expected.stream().filter(f -> !f.equals("manifest.json")).toList();
        check("G4: o projeto regerado é igual ao gerado do zero (mesmos bytes nos arquivos gerados; o manifesto só ganha o vínculo das chaves)",
                sameFiles(gen, upgrade, code5) && withoutKeyLinks(upgrade).equals(withoutKeyLinks(gen)));
        Map<String, List<Object>> legacyLinks = keyLinks(upgrade);
        log("  chaves anteriores declaradas (gerador 1.0.0): " + legacyLinks.size() + " etapa(s) · ex.: " + legacyLinks.values().stream().findFirst().orElse(List.of()));
        check("G4 (CQ-R5-02): toda chave renomeada declara a chave do gerador anterior em previousTargetKeys (nada trocado em silêncio)",
                !legacyLinks.isEmpty() && legacyLinks.values().stream().allMatch(l -> l.size() == 1 && String.valueOf(l.get(0)).startsWith("processo.")));

        // ================================================================ G4b — regenerar sobre a amostra da RODADA 4 (chave renomeada)
        if (round4 != null) {
            Path regen4 = out.toPath().resolve("codigo-rodada-4-regerado");
            copyTree(round4, regen4);
            Files.deleteIfExists(regen4.resolve("SHA256SUMS.txt"));
            String r6 = generateByMenu(config, regen4);
            log("  regeneração sobre a rodada 4:\n" + r6.indent(4));
            check("G4b: regerar sobre a rodada 4 sem proteção (os arquivos eram os do gerador)", r6.contains("GERADO em") && !r6.contains("PROTECTED"));
            check("G4b: código regerado igual ao gerado do zero", sameFiles(gen, regen4, code5) && withoutKeyLinks(regen4).equals(withoutKeyLinks(gen)));
            Map<String, List<Object>> links = keyLinks(regen4);
            log("  vínculos: " + links);
            check("G4b: só a chave renomeada ganha o vínculo: download_spreadsheet ← download_excel_cloud_download",
                    links.size() == 1 && links.values().iterator().next().equals(List.of("process.preparation.download_excel_cloud_download")));
        }

        // ================================================================ G5 — verificações do Studio (V4/V5/VQ)
        PythonChecks.Environment env = PythonChecks.resolve(Optional.of(python), gen).orElseThrow(() -> new IllegalStateException("Python de verificação"));
        log("  ambiente de verificação: nekkora_rpa " + env.framework() + " · ruff " + env.ruff() + " · mypy " + env.mypy() + " · Python " + env.pythonVersion());
        List<PythonChecks.Check> res = PythonChecks.run(env, gen, () -> false);
        for (PythonChecks.Check c : res) log("  " + c.id() + " · " + c.layer() + " · " + c.status() + (c.problems() > 0 ? " · " + c.codes() : ""));
        check("G5: V4 (compilação, importação, ruff, formatação, mypy estrito), V5 (contrato) e VQ (AST) — todas PASS",
                !res.isEmpty() && res.stream().allMatch(c -> c.status() == PythonChecks.Status.PASS));
        var vq = GeneratedCodeQuality.assess(gen, res, true).toString();
        log("  VQ (manifesto + AST): " + vq);
        check("G5: VQ sem bloqueio nem palavra sem tradução", !vq.contains("BLOCKED") && !vq.contains("IDENTIFICADOR_SEM_TRADUCAO"));
        Files.writeString(out.toPath().resolve("G-resultados-da-geracao.txt"), String.join("\n---\n", List.of(r1, r2, r3, r4, r5)), StandardCharsets.UTF_8);
        copyForHumanReview(gen, "rpa-challenge");
    }

    /** Cópia ORIGINAL (bytes do Studio) da pasta gerada + SHA-256 de cada arquivo, para a revisão humana. */
    private void copyForHumanReview(Path gen, String name) throws Exception {
        String base = System.getProperty("nekkora.homolog.review");
        if (base == null || base.isBlank()) return;
        Path to = Path.of(base).resolve(name);
        if (Files.exists(to)) {
            try (Stream<Path> s = Files.walk(to)) {
                for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
        StringBuilder sums = new StringBuilder();
        try (Stream<Path> st = Files.walk(gen)) {
            for (Path p : st.sorted().toList()) {
                Path rel = gen.relativize(p);
                if (rel.toString().contains("__pycache__") || rel.toString().startsWith(".")) continue;
                Path dst = to.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(p, dst);
                    sums.append(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))))
                            .append("  ").append(rel.toString().replace('\\', '/')).append('\n');
                }
            }
        }
        Files.writeString(to.resolve("SHA256SUMS.txt"), sums.toString(), StandardCharsets.UTF_8);
        log("  revisão humana: cópia original em " + to + " (" + sums.toString().lines().count() + " arquivos com SHA-256)");
    }

    // ============================================================================== apoio

    private String generateByMenu(Path config, Path outDir) throws Exception {
        menuPath("Automação", "Avançado", "Gerador avançado (configuração manual)…"); // o mesmo gerador do menu da E2E-001
        NekkoraDialog d = waitDialog("Gerar código Python");
        JTextComponent cfg = edt(() -> find(d, JTextComponent.class, f -> "codegen.config".equals(f.getName())));
        JTextComponent dir = edt(() -> find(d, JTextComponent.class, f -> "codegen.out".equals(f.getName())));
        JTextComponent res = edt(() -> find(d, JTextComponent.class, f -> "codegen.result".equals(f.getName())));
        setText(cfg, config.toString());
        setText(dir, outDir.toString());
        AbstractButton gb = edt(() -> find(d, AbstractButton.class, x -> "codegen.generate".equals(x.getName())));
        captureWindow(d, "G-dialogo-" + (generations + 1));
        click(gb);
        String text = (String) waitValue("resultado da geração", 30, () -> edt(() -> res.getText().isBlank() ? null : res.getText()));
        captureWindow(d, "G-gerar-" + (++generations));
        clickButtonWithText(d, "Fechar");
        waitClosed(d);
        return text;
    }

    /** {@code previousTargetKeys} de cada etapa no manifesto da pasta (CQ-R5-02). */
    @SuppressWarnings("unchecked")
    private static Map<String, List<Object>> keyLinks(Path dir) throws IOException {
        Map<String, List<Object>> out = new java.util.LinkedHashMap<>();
        Map<String, Object> m = Json.parseObject(Files.readString(dir.resolve("manifest.json"), StandardCharsets.UTF_8));
        for (Object o : Json.array(Json.object(m, "trace"), "mappings")) {
            Map<String, Object> x = (Map<String, Object>) o;
            if (x.get("previousTargetKeys") instanceof List<?> l) out.put(String.valueOf(x.get("targetKey")), (List<Object>) l);
        }
        return out;
    }

    /** Manifesto sem as linhas {@code previousTargetKeys} (para comparar com o gerado do zero). */
    private static String withoutKeyLinks(Path dir) throws IOException {
        return Files.readString(dir.resolve("manifest.json"), StandardCharsets.UTF_8).replaceAll("(?m)^\\s*\"previousTargetKeys\": \\[[^\\]]*\\],\\n", "");
    }

    private static boolean sameFiles(Path a, Path b, List<String> files) throws IOException {
        for (String f : files) {
            if (!java.util.Arrays.equals(Files.readAllBytes(a.resolve(f)), Files.readAllBytes(b.resolve(f)))) return false;
        }
        return true;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                String rel = from.relativize(p).toString();
                if (rel.startsWith("venv") || rel.endsWith(".txt") && rel.contains("DIFF") || rel.endsWith("G5-validacao.json")) continue;
                Path t = to.resolve(rel);
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t);
            }
        }
    }

    private void openByDialog(Path file) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_O);
        waitFor("diálogo Abrir projeto", 15, () -> fileChooser() != null && "Abrir projeto".equals(((javax.swing.JDialog) fileChooser()).getTitle()));
        sleep(500);
        Window w = fileChooser();
        JTextComponent field = edt(() -> find(w, JTextComponent.class, x -> x.isShowing() && x.isEditable()));
        click(field);
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        SwingUtilities.invokeAndWait(() -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(file.toString()), null));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
        sleep(300);
        press(KeyEvent.VK_ENTER);
        waitFor("diálogo de arquivo fechado", 15, () -> fileChooser() == null);
        waitFor("projeto aberto " + file.getFileName(), 30, () -> edt(() -> window.studio().state().activeAutomation()
                .flatMap(x -> window.studio().state().project(x.id())).flatMap(com.nekkora.studio.app.workspace.ProjectState::path).map(file::equals)
                .orElse(false)));
        sleep(800);
    }

    private Window fileChooser() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof javax.swing.JDialog dl && dl.isShowing() && find(dl, javax.swing.JFileChooser.class, x -> true) != null) return w;
            }
            return null;
        });
    }

    /** Menu da barra com submenu: "Automação" → "Avançado" → "Gerador avançado (configuração manual)…". */
    private void menuPath(String top, String sub, String item) throws Exception {
        focusStudio();
        javax.swing.JMenu m = edt(() -> {
            var bar = window.frame().getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) if (top.equals(bar.getMenu(i).getText())) return bar.getMenu(i);
            return null;
        });
        click(m);
        waitFor("menu " + top, 5, () -> edt(m::isPopupMenuVisible));
        javax.swing.JMenu sm = (javax.swing.JMenu) waitValue("submenu " + sub, 5, () -> edt(() -> {
            for (Component c : m.getPopupMenu().getComponents()) if (c instanceof javax.swing.JMenu x && sub.equals(x.getText()) && x.isShowing()) return x;
            return null;
        }));
        click(sm);
        waitFor("submenu aberto", 5, () -> edt(sm::isPopupMenuVisible));
        javax.swing.JMenuItem it = (javax.swing.JMenuItem) waitValue("item " + item, 5, () -> edt(() -> {
            for (Component c : sm.getPopupMenu().getComponents()) if (c instanceof javax.swing.JMenuItem x && item.equals(x.getText()) && x.isShowing()) return x;
            return null;
        }));
        click(it);
        sleep(500);
    }

    private void menu(String top, String item) throws Exception {
        focusStudio();
        javax.swing.JMenu menu = edt(() -> {
            var bar = window.frame().getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) if (top.equals(bar.getMenu(i).getText())) return bar.getMenu(i);
            return null;
        });
        click(menu);
        waitFor("menu " + top, 5, () -> edt(menu::isPopupMenuVisible));
        javax.swing.JMenuItem it = (javax.swing.JMenuItem) waitValue("item " + item, 5, () -> edt(() -> {
            for (Component c : menu.getPopupMenu().getComponents()) {
                if (c instanceof javax.swing.JMenuItem x && item.equals(x.getText()) && x.isShowing()) return x;
            }
            return null;
        }));
        click(it);
        sleep(300);
    }

    private NekkoraDialog waitDialog(String title) throws Exception {
        return (NekkoraDialog) waitValue("diálogo " + title, 15, () -> edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return nd;
            }
            return null;
        }));
    }

    private void waitClosed(Window d) throws Exception {
        waitFor("fechar diálogo", 15, () -> edt(() -> !d.isShowing()));
        sleep(250);
    }

    private void clickButtonWithText(Container root, String text) throws Exception {
        AbstractButton b = edt(() -> find(root, AbstractButton.class, x -> text.equals(x.getText()) && x.isShowing()));
        if (b == null) throw new IllegalStateException("botão não encontrado: " + text);
        click(b);
    }

    private void setText(JTextComponent field, String text) throws Exception {
        click(field);
        edt(() -> {
            field.setText(text);
            return null;
        });
        sleep(150);
    }

    /**
     * Proteção da máquina: antes de cada clique ou tecla, a janela em primeiro plano tem de ser desta JVM (o Studio). Outro
     * programa na frente → NADA é clicado nem teclado e a homologação para. Só o nome do executável vai ao log.
     */
    private Process foreground;
    private java.io.BufferedWriter fgIn;
    private java.io.BufferedReader fgOut;

    private void guardForeground(String what) {
        try {
            long end = System.currentTimeMillis() + 3000;
            String seen = "";
            while (System.currentTimeMillis() < end) {
                String[] r = foregroundProcess();
                long pid = Long.parseLong(r[0]);
                if (pid == ProcessHandle.current().pid()) return;
                seen = r[1].isEmpty() ? "pid " + pid : Path.of(r[1]).getFileName().toString();
                sleep(200);
            }
            throw new IllegalStateException("outro programa em primeiro plano (" + seen + ") antes de: " + what
                    + " — homologação interrompida SEM clicar nem teclar");
        } catch (IOException e) {
            throw new IllegalStateException("verificação do primeiro plano indisponível: " + e.getMessage());
        }
    }

    private String[] foregroundProcess() throws IOException {
        if (foreground == null || !foreground.isAlive()) {
            Path script = Files.createTempFile("nk-cq4-fg", ".ps1");
            Files.writeString(script, String.join(System.lineSeparator(),
                    "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class NkFg {"
                            + " [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();"
                            + " [DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid); }'",
                    "while ($true) { $l = [Console]::In.ReadLine(); if ($l -eq $null) { break }",
                    "  $p = [uint32]0; [void][NkFg]::GetWindowThreadProcessId([NkFg]::GetForegroundWindow(), [ref]$p)",
                    "  $path = ''; try { $path = (Get-Process -Id $p -ErrorAction Stop).Path } catch { }",
                    "  [Console]::Out.WriteLine(\"$p|$path\"); [Console]::Out.Flush() }"), StandardCharsets.UTF_8);
            foreground = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
            fgIn = new java.io.BufferedWriter(new java.io.OutputStreamWriter(foreground.getOutputStream(), StandardCharsets.UTF_8));
            fgOut = new java.io.BufferedReader(new java.io.InputStreamReader(foreground.getInputStream(), StandardCharsets.UTF_8));
        }
        fgIn.write("?");
        fgIn.newLine();
        fgIn.flush();
        String line = fgOut.readLine();
        if (line == null) throw new IOException("sem resposta");
        int bar = line.indexOf('|');
        return new String[] {line.substring(0, bar).trim(), line.substring(bar + 1).trim()};
    }

    private void click(Component c) throws Exception {
        edt(() -> {
            if (c instanceof javax.swing.JComponent jc) jc.scrollRectToVisible(new Rectangle(0, 0, jc.getWidth(), jc.getHeight()));
            return null;
        });
        robot.waitForIdle();
        sleep(150);
        Rectangle r = edt(() -> new Rectangle(c.getLocationOnScreen(), c.getSize()));
        guardForeground("clique");
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(250);
    }

    private void focusStudio() throws Exception {
        edt(() -> {
            window.frame().toFront();
            return null;
        });
        Rectangle b = edt(() -> window.frame().getBounds());
        robot.mouseMove(b.x + b.width / 2, b.y + 12);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        waitFor("Studio com foco", 10, () -> edt(() -> window.frame().isFocused() || window.frame().isActive()));
        sleep(300);
    }

    private void press(int... keys) {
        guardForeground("tecla");
        for (int k : keys) robot.keyPress(k);
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keys[i]);
        robot.waitForIdle();
        sleep(200);
    }

    private void captureWindow(Window w, String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        var img = robot.createScreenCapture(edt(() -> {
            Rectangle b = w.getBounds();
            java.awt.Insets in = w.getInsets();
            int top = w instanceof java.awt.Frame f && f.isUndecorated() || w instanceof java.awt.Dialog d && d.isUndecorated() ? in.top : 1;
            return new Rectangle(b.x + in.left, b.y + top, b.width - in.left - in.right, b.height - top - in.bottom);
        }));
        File f = new File(out, "CQ4-" + name + ".png");
        ImageIO.write(img, "png", f);
        log("captura " + f.getName() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }

    private static <T extends Component> T find(Container root, Class<T> type, java.util.function.Predicate<T> p) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && p.test(type.cast(c))) return type.cast(c);
            if (c instanceof Container ct) {
                T t = find(ct, type, p);
                if (t != null) return t;
            }
        }
        return null;
    }

    private void check(String label, boolean ok) {
        checks++;
        if (!ok) failures++;
        log((ok ? "OK    " : "FALHA ") + label);
    }

    private void log(String line) {
        System.out.println(line);
        log.add(line);
    }

    private void writeLog() {
        try (PrintWriter w = new PrintWriter(new File(out, "CQ4-tabular-homologacao-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void teardown() {
        if (foreground != null) foreground.destroy();
        if (bridge != null) bridge.close();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitFor(String what, int seconds, Callable<Boolean> cond) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        Exception last = null;
        while (System.currentTimeMillis() < end) {
            try {
                if (Boolean.TRUE.equals(cond.call())) return;
            } catch (Exception e) {
                last = e;
            }
            sleep(150);
        }
        throw new IllegalStateException("tempo esgotado aguardando: " + what + (last == null ? "" : " (" + last + ")"));
    }

    private Object waitValue(String what, int seconds, Callable<Object> v) throws Exception {
        AtomicReference<Object> ref = new AtomicReference<>();
        waitFor(what, seconds, () -> {
            Object x = v.call();
            ref.set(x);
            return x != null;
        });
        return ref.get();
    }

    private static <T> T edt(Callable<T> c) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return c.call();
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

    @SuppressWarnings("unused")
    private static List<String> names(LogicalSession s) {
        return s.steps().stream().map(AutomationStep::name).toList();
    }
}
