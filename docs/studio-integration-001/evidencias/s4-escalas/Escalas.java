import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.StudioEnvironment;
import com.nekkora.studio.app.develop.SwingDevelopmentHost;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.ui.develop.DevelopmentPanel;
import com.nekkora.tokens.NekkoraThemes;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

/**
 * INTEGRATION-CONSOLIDATION-001 (S4): Studio REAL na escala da JVM (-Dsun.java2d.uiScale) — janela principal com um projeto
 * aberto, cada menu, e a janela "Desenvolver automação" (SwingDevelopmentHost real) nos estados gerado, em execução e com
 * pergunta. Confere que nenhum botão sai da janela e que o Tab percorre o rodapé até Fechar. Capturas em <saida>.
 * Uso: java -Dsun.java2d.uiScale=<s> -cp <cp> Escalas <saida> <projeto.nekkora>
 */
public class Escalas {
    static final List<String> log = new ArrayList<>();
    static int fails;
    static Robot robot;
    static File out;

    public static void main(String[] a) throws Exception {
        out = new File(a[0]);
        out.mkdirs();
        robot = new Robot();
        String sc = System.getProperty("sun.java2d.uiScale", "1");
        Path tmp = Files.createTempDirectory("escalas-");
        Path proj = tmp.resolve("biografias.nekkora");
        Files.copy(Path.of(a[1]), proj);
        StudioApplication.Window w = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), BrowserLink.offline("DISABLED"), () -> {},
                StudioEnvironment.inMemory(), List.of(proj)));
        Thread.sleep(2500);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        check("escala " + sc + ": área útil lógica " + usable.width + "x" + usable.height, true);
        Rectangle fb = edt(() -> w.frame().getBounds());
        check("janela principal dentro da área útil " + fb, usable.contains(fb) || fb.width <= usable.width + 16 && fb.height <= usable.height + 16);
        shot("principal", fb);
        JMenuBar bar = edt(() -> w.frame().getJMenuBar());
        int n = edt(bar::getMenuCount);
        for (int i = 0; i < n; i++) {
            int k = i;
            String name = edt(() -> bar.getMenu(k).getText());
            int mn = edt(() -> bar.getMenu(k).getMnemonic());
            check("menu \"" + name + "\" com mnemônico (Alt+" + (char) mn + ")", mn != 0);
            edt(() -> { bar.getMenu(k).doClick(); return null; });
            Thread.sleep(500);
            Rectangle pop = edt(() -> {
                JMenu m = bar.getMenu(k);
                Rectangle r = new Rectangle(m.getPopupMenu().getLocationOnScreen(), m.getPopupMenu().getSize());
                return r.union(new Rectangle(m.getLocationOnScreen(), m.getSize()));
            });
            check("menu \"" + name + "\" aberto inteiro na tela " + pop, usable.contains(pop));
            shot("menu-" + (i + 1), pop.union(new Rectangle(fb.x, fb.y, Math.min(fb.width, 900), 60)));
            edt(() -> { MenuSelectionManager.defaultManager().clearSelectedPath(); return null; });
            Thread.sleep(200);
        }
        // janela Desenvolver real (SwingDevelopmentHost) com o painel real
        DevelopmentPanel.Actions actions = (DevelopmentPanel.Actions) java.lang.reflect.Proxy.newProxyInstance(Escalas.class.getClassLoader(),
                new Class<?>[] {DevelopmentPanel.Actions.class}, (p, method, args) -> null);
        DevelopmentPanel panel = edt(() -> new DevelopmentPanel(actions));
        SwingDevelopmentHost host = new SwingDevelopmentHost(w::frame, panel);
        edt(() -> { host.show(); return null; });
        Thread.sleep(800);
        JDialog dlg = edt(() -> (JDialog) SwingUtilities.getWindowAncestor(panel));
        List<DevelopmentPanel.Phase> phases = List.of(new DevelopmentPanel.Phase("Analisando projeto", DevelopmentPanel.PhaseState.DONE, ""),
                new DevelopmentPanel.Phase("Gerando Python", DevelopmentPanel.PhaseState.DONE, ""), new DevelopmentPanel.Phase("Validando saída",
                        DevelopmentPanel.PhaseState.DONE, ""));
        List<String> results = List.of("✓ PROJECT_ANALYZED", "✓ CODE_GENERATED", "✓ CODE_VALIDATED", "Pasta: <pasta de saída>", "  igual  main.py",
                "✓ Compilação: sem problema (83 ms)", "! Qualidade da geração: 1 aviso(s) de legibilidade — veja Pendências");
        DevelopmentPanel.Model gen = new DevelopmentPanel.Model("Biografias da Wikipédia", true, false, "Código gerado e verificado",
                "Gerado pelo gerador oficial e conferido por compilação, importação, ruff, mypy e testes de contrato.", com.nekkora.ui.core.Severity.SUCCESS,
                phases, List.of(), List.of(), List.of(), List.of(), results, "", "", true, true);
        DevelopmentPanel.Model run = new DevelopmentPanel.Model(gen.subject(), false, true, "Executando teste autorizado", "Acompanhe o progresso.",
                com.nekkora.ui.core.Severity.INFO, phases, List.of(), List.of(), List.of(), List.of(), results, "", "", true, true);
        List<AssistanceRequest.Option> opts = new ArrayList<>();
        opts.add(AssistanceRequest.Option.answer("COLUMN:Ocupação", "Manter a coluna “Ocupação”", "o rótulo da linha nomeia a coluna"));
        for (String h : List.of("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade")) {
            opts.add(AssistanceRequest.Option.answer(FunctionalSpec.FALLBACK + java.util.UUID.randomUUID(), "Usar quando faltar “" + h + "”", "fonte alternativa"));
        }
        opts.add(AssistanceRequest.Option.answer(FunctionalSpec.NOT_IN_OUTPUT, "Não vai para a planilha", "fora das colunas"));
        QualityIssue q = new QualityIssue("SPEC_OUTPUT_COLUMN_REVIEW", Layer.V1_PDD, QualityIssue.Kind.WARNING, null, null, "Sessão 3 · Etapa 8",
                "Coluna derivada do rótulo da linha.", "Confirme a coluna.", new AssistanceRequest("Para qual coluna da planilha vai esta leitura?", "por quê",
                opts, FunctionalSpec.Field.OUTPUT_COLUMN, "Especificação funcional · colunas da planilha"), null, null);
        DevelopmentPanel.Model ask = new DevelopmentPanel.Model(gen.subject(), true, false, "Falta responder 1 pergunta", "", com.nekkora.ui.core.Severity.WARNING,
                phases, List.of(), List.of(), List.of(), List.of(q), List.of(), "", "", false, true);
        String[] names = {"gerado", "em-execucao", "pergunta"};
        DevelopmentPanel.Model[] models = {gen, run, ask};
        for (int i = 0; i < models.length; i++) {
            DevelopmentPanel.Model m = models[i];
            int k = i;
            edt(() -> { panel.show(m); if (k == 2) { panel.tabs().setSelectedIndex(1); panel.pendingList().setSelectedIndex(0); } dlg.validate(); return null; });
            Thread.sleep(700);
            Rectangle db = edt(dlg::getBounds);
            check("Desenvolver (" + names[i] + ") dentro da área útil " + db, usable.contains(db));
            List<String> bad = edt(() -> {
                List<String> b = new ArrayList<>();
                List<AbstractButton> bs = new ArrayList<>();
                collect(dlg.getContentPane(), bs);
                for (AbstractButton x : bs) {
                    if (!x.isShowing()) continue;
                    Rectangle r = SwingUtilities.convertRectangle(x.getParent(), x.getBounds(), dlg.getContentPane());
                    Rectangle vis = x.getVisibleRect();
                    if (r.x < 0 || r.x + r.width > dlg.getContentPane().getWidth() || vis.width < x.getWidth()) b.add(x.getText() + " " + r);
                }
                return b;
            });
            check("Desenvolver (" + names[i] + "): nenhum botão cortado ou fora da janela " + bad, bad.isEmpty());
            if (k != 2) {
                List<String> seq = edt(() -> new ArrayList<>());
                AbstractButton start = edt(() -> (AbstractButton) find(dlg.getContentPane(), m.running() ? "Cancelar" : "Desenvolver de novo", "Desenvolver"));
                edt(() -> { start.requestFocusInWindow(); return null; });
                Thread.sleep(300);
                for (int t = 0; t < 40; t++) {
                    Component f = edt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                    if (f instanceof AbstractButton b) seq.add(b.getText());
                    if (f instanceof AbstractButton b && "Fechar".equals(b.getText())) break;
                    edt(() -> { KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(); return null; });
                    Thread.sleep(80);
                }
                check("Desenvolver (" + names[i] + "): Tab percorre o rodapé até Fechar " + seq, seq.contains("Fechar"));
            }
            shot("desenvolver-" + names[i], edt(dlg::getBounds));
        }
        Files.write(out.toPath().resolve("resultado.txt"), log);
        log.forEach(System.out::println);
        System.out.println("RESULTADO escala " + sc + ": " + log.size() + " verificações, " + fails + " falha(s)");
        System.exit(fails == 0 ? 0 : 1);
    }

    static Component find(Container c, String... texts) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton b && b.isShowing()) for (String t : texts) if (t.equals(b.getText())) return b;
            if (x instanceof Container k) { Component r = find(k, texts); if (r != null) return r; }
        }
        return null;
    }

    static void collect(Container c, List<AbstractButton> outList) {
        for (Component x : c.getComponents()) {
            if (x instanceof AbstractButton b) outList.add(b);
            if (x instanceof Container k) collect(k, outList);
        }
    }

    static void check(String what, boolean ok) {
        if (!ok) fails++;
        log.add((ok ? "OK    " : "FALHA ") + what);
    }

    static void shot(String name, Rectangle r) throws Exception {
        MultiResolutionImage mi = robot.createMultiResolutionScreenCapture(r);
        List<Image> vs = mi.getResolutionVariants();
        Image img = vs.get(vs.size() - 1);
        BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
        bi.getGraphics().drawImage(img, 0, 0, null);
        ImageIO.write(bi, "png", new File(out, name + ".png"));
    }

    static <T> T edt(Callable<T> c) throws Exception {
        AtomicReference<T> v = new AtomicReference<>();
        AtomicReference<Exception> e = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                v.set(c.call());
            } catch (Exception x) {
                e.set(x);
            }
        });
        if (e.get() != null) throw e.get();
        return v.get();
    }
}
