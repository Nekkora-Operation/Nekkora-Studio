package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepPosition;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.StepForm;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.accessibility.AccessibleContext;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Roteiro de evidências da STUDIO-001 com a <b>janela real</b> (não headless): teclado real via {@link Robot}
 * (Ctrl+N, Tab, Enter, Esc, setas), diálogos reais do design system e capturas de tela da aplicação.
 * Textos com acentos são inseridos no campo focado pela EDT (o Robot não digita acentos de forma portátil);
 * confirmar, navegar e cancelar acontecem sempre pelo teclado.
 *
 * <p>Uso: {@code java -cp ... StudioEvidence <pastaSaida> <largura> <altura> <prefixo> [--full]}.
 * Não é um teste automático (exige área de trabalho interativa); o resultado fica em {@code <pastaSaida>}.
 */
public final class StudioEvidence {
    private final File out;
    private final String prefix;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private int failures;
    private StudioApplication.Window window;

    private StudioEvidence(File out, String prefix) throws AWTException {
        this.out = out;
        this.prefix = prefix;
        this.robot = new Robot();
        robot.setAutoDelay(40);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int w = Integer.parseInt(args[1]), h = Integer.parseInt(args[2]);
        boolean full = args.length > 4 && args[4].equals("--full");
        StudioEvidence e = new StudioEvidence(out, args[3]);
        int code = 1;
        try {
            e.run(w, h, full);
            code = e.failures == 0 ? 0 : 2;
        } catch (Throwable t) {
            e.log("ERRO " + t);
            for (StackTraceElement s : t.getStackTrace()) e.log("    at " + s);
        } finally {
            e.writeLog();
        }
        System.exit(code);
    }

    // ------------------------------------------------------------------ roteiro

    private void run(int w, int h, boolean full) throws Exception {
        log("Tela: " + java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()
                + " · flatlaf.uiScale=" + System.getProperty("flatlaf.uiScale", "(padrão)"));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), () -> { }));
        edt(() -> {
            window.frame().setSize(w, h);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela visível", () -> edt(() -> window.frame().isShowing()));
        focusWindow();
        log("Janela " + w + "x" + h + ", tema " + edt(() -> ThemeManager.current().displayName()));

        check("S001-001 abre sem login/licença, direto no Studio", edt(() -> window.frame().getTitle().equals("Nekkora Studio")));
        check("S001-002 estado vazio, sem automações fictícias", edt(() -> window.studio().state().automations().isEmpty()
                && window.studio().view().navigator().items().isEmpty()));
        capture("01-inicial-vazio");

        // ---- criar automação (Ctrl+N real, Enter real)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused("Consulta de clientes");
        press(KeyEvent.VK_TAB);
        typeIntoFocused("Consulta o cadastro de clientes e abre os detalhes do primeiro resultado.");
        capture("02-dialogo-nova-automacao");
        press(KeyEvent.VK_SHIFT, KeyEvent.VK_TAB); // volta ao nome: Enter confirma pelo botão padrão
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        check("S001-003 automação criada no domínio", edt(() -> window.studio().state().automations().size() == 1
                && window.studio().state().automations().get(0).name().equals("Consulta de clientes")));
        sleep(700);
        capture("03-automacao-criada");

        // ---- validação: nome vazio
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        d = waitDialog("Nova automação");
        press(KeyEvent.VK_ENTER);
        sleep(300);
        check("S001-004 nome vazio mantém o diálogo aberto com mensagem", edt(() -> d0().isShowing()
                && texts(d0()).contains("Informe um nome.")));
        capture("04-validacao-nome-obrigatorio");
        press(KeyEvent.VK_ESCAPE);
        waitClosed(d);
        check("S001-004 nada foi criado", edt(() -> window.studio().state().automations().size() == 1));

        // ---- sessões
        for (String s : List.of("Autenticação", "Consulta", "Resultado")) {
            press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N);
            d = waitDialog("Nova sessão");
            typeIntoFocused(s);
            press(KeyEvent.VK_ENTER);
            waitClosed(d);
        }
        check("S001-007 sessões na ordem", edt(() -> active().sessions().stream().map(LogicalSession::name).toList()
                .equals(List.of("Autenticação", "Consulta", "Resultado"))));
        sleep(500);
        capture("05-multiplas-sessoes");

        // ---- etapas (Ctrl+E; sessão escolhida no combo; tipo no combo)
        String[][] steps = {
                {"Autenticação", "Preencher usuário", "FILL"}, {"Autenticação", "Preencher senha", "FILL"},
                {"Autenticação", "Clicar em Entrar", "CLICK"}, {"Consulta", "Abrir menu Clientes", "CLICK"},
                {"Consulta", "Preencher campo de pesquisa", "FILL"}, {"Consulta", "Clicar em Pesquisar", "CLICK"},
                {"Resultado", "Consultar detalhes", "OBSERVE"}};
        for (String[] st : steps) {
            press(KeyEvent.VK_CONTROL, KeyEvent.VK_E);
            d = waitDialog("Nova etapa");
            NekkoraDialog dd = d;
            edt(() -> {
                StepForm f = find(dd, StepForm.class);
                JComboBox<StepForm.SessionChoice> sc = f.sessionInput();
                for (int i = 0; i < sc.getItemCount(); i++) if (sc.getItemAt(i).label().endsWith(st[0])) sc.setSelectedIndex(i);
                f.typeInput().setSelectedItem(ActionType.valueOf(st[2]));
                return null;
            });
            typeIntoFocused(st[1]);
            if (st[1].equals("Clicar em Pesquisar")) capture("06-dialogo-nova-etapa");
            press(KeyEvent.VK_ENTER);
            waitClosed(d);
        }
        check("S001-009 sequência 01..07 na ordem do domínio", edt(() -> {
            Automation a = active();
            List<StepPosition> seq = a.sequence();
            List<String> names = seq.stream().map(p -> a.step(p.stepId()).orElseThrow().name()).toList();
            return names.equals(List.of("Preencher usuário", "Preencher senha", "Clicar em Entrar", "Abrir menu Clientes",
                    "Preencher campo de pesquisa", "Clicar em Pesquisar", "Consultar detalhes"));
        }));
        check("S001-009 numeração visual exibida 01..07", edt(() -> {
            List<String> labels = new ArrayList<>();
            for (LogicalSessionView v : window.studio().view().overview(active().id()).orElseThrow().sessionViews()) {
                for (AutomationStep s : v.itemsInOrder()) labels.add(v.labelOf(s));
            }
            return labels.get(0).startsWith("01") && labels.get(6).startsWith("07") && labels.get(3).equals("04   Abrir menu Clientes");
        }));
        sleep(500);
        capture("07-sessoes-com-etapas");

        // ---- seleção: clique real do mouse na etapa 04 e seta para baixo (teclado) até a 05
        edt(() -> {
            LogicalSessionView v = window.studio().view().overview(active().id()).orElseThrow().sessionViews().get(1);
            v.stepList().scrollRectToVisible(v.stepList().getCellBounds(0, 0));
            return null;
        });
        sleep(300);
        java.awt.Point click = edt(() -> {
            LogicalSessionView v = window.studio().view().overview(active().id()).orElseThrow().sessionViews().get(1);
            Rectangle cell = v.stepList().getCellBounds(0, 0);
            java.awt.Point origin = v.stepList().getLocationOnScreen();
            return new java.awt.Point(origin.x + cell.x + cell.width / 3, origin.y + cell.y + cell.height / 2);
        });
        robot.mouseMove(click.x, click.y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(300);
        log("foco após o clique: " + edt(() -> String.valueOf(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner())
                .replaceAll("\\[.*", "")));
        check("S001-011 clique seleciona a etapa 04", edt(() -> active().step(selectedStep()).map(AutomationStep::name)
                .orElse("").equals("Abrir menu Clientes")));
        press(KeyEvent.VK_DOWN);
        sleep(400);
        check("S001-011 seleção por teclado atualiza propriedades", edt(() -> {
            AutomationStep s = active().step(selectedStep()).orElseThrow();
            return s.name().equals("Preencher campo de pesquisa") && texts(window.studio().view().inspector()).contains("Preencher campo de pesquisa");
        }));
        capture("08-propriedades-da-etapa");

        // ---- detalhes: Enter na lista
        press(KeyEvent.VK_ENTER);
        d = waitDialog("Preencher campo de pesquisa");
        sleep(400);
        check("S001-012 diálogo de detalhes corresponde à etapa", edt(() -> texts(d0()).contains("Etapa 05 de 07")
                && texts(d0()).contains("Consulta")));
        check("S001-013 diálogo sem identificadores internos", edt(() -> !DisplaySafety.UUID.matcher(texts(d0())).find()));
        capture("09-dialogo-detalhes-da-etapa");
        press(KeyEvent.VK_ESCAPE);
        waitClosed(d);
        check("S001-018 Esc fecha e devolve o foco", edt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != null));

        check("S001-013 janela principal sem UUIDs", edt(() -> !DisplaySafety.UUID.matcher(texts(window.frame())).find()));

        if (full) {
            // ---- temas, sem perder estado
            for (NekkoraTheme t : NekkoraThemes.all()) {
                edt(() -> {
                    ThemeManager.setTheme(t);
                    return null;
                });
                sleep(600);
                check("S001-016 " + t.displayName() + " mantém 7 etapas e a seleção", edt(() -> active().stepCount() == 7
                        && selectedStep() != null));
                capture("10-tema-" + t.id());
            }
            edt(() -> {
                ThemeManager.setTheme(NekkoraThemes.SAKURA_DARK);
                return null;
            });
            sleep(400);
        }

        // ---- encerramento: aviso de perda
        // invokeLater: o aviso é modal e prende a EDT até ser respondido
        SwingUtilities.invokeLater(() -> window.frame().dispatchEvent(
                new java.awt.event.WindowEvent(window.frame(), java.awt.event.WindowEvent.WINDOW_CLOSING)));
        d = waitDialog("Fechar sem salvar?");
        sleep(300);
        check("S001-021 aviso de perda ao fechar", edt(() -> texts(d0()).contains("memória")));
        capture("11-aviso-ao-fechar");
        press(KeyEvent.VK_ESCAPE);
        waitClosed(d);
        check("S001-021 cancelar mantém o Studio aberto", edt(() -> window.frame().isShowing()));
        edt(() -> {
            window.studio().view().dispose();
            window.frame().dispose();
            return null;
        });
        log("Fim: " + failures + " falha(s).");
    }

    // ------------------------------------------------------------------ apoio

    private Automation active() {
        return window.studio().state().activeAutomation().orElseThrow();
    }

    private com.nekkora.studio.domain.StepId selectedStep() {
        return window.studio().state().selection() instanceof com.nekkora.studio.app.workspace.Selection.OfStep s ? s.step() : null;
    }

    private NekkoraDialog current;

    private NekkoraDialog d0() {
        return current;
    }

    private NekkoraDialog waitDialog(String title) throws Exception {
        AtomicReference<NekkoraDialog> found = new AtomicReference<>();
        waitFor("diálogo " + title, () -> edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) {
                    found.set(nd);
                    return true;
                }
            }
            return false;
        }));
        current = found.get();
        sleep(350); // animação/foco inicial
        return current;
    }

    private void waitClosed(NekkoraDialog d) throws Exception {
        waitFor("fechar " + d.getTitle(), () -> edt(() -> !d.isShowing()));
        sleep(250);
    }

    private void typeIntoFocused(String text) throws Exception {
        edt(() -> {
            Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (!(c instanceof JTextComponent t)) throw new IllegalStateException("foco não está em um campo de texto: " + c);
            t.setText(text);
            return null;
        });
        sleep(120);
    }

    private void press(int... keys) {
        for (int k : keys) robot.keyPress(k);
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keys[i]);
        robot.waitForIdle();
        sleep(120);
    }

    private void focusWindow() throws Exception {
        Rectangle b = edt(() -> window.frame().getBounds());
        robot.mouseMove(b.x + b.width / 2, b.y + 12);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(b.x + b.width - 5, b.y + b.height - 5);
        waitFor("janela com foco", () -> edt(() -> window.frame().isFocused()));
        edt(() -> window.studio().view().navigator().createButton().requestFocusInWindow());
        sleep(300);
    }

    private void capture(String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        Rectangle r = edt(() -> {
            Window w = current != null && current.isShowing() ? current.getOwner() : window.frame();
            return w.getBounds();
        });
        BufferedImage img = robot.createScreenCapture(r);
        File f = new File(out, prefix + "-" + name + ".png");
        ImageIO.write(img, "png", f);
        log("captura " + f.getName() + " (" + r.width + "x" + r.height + ")");
    }

    private void check(String label, boolean ok) {
        if (!ok) failures++;
        log((ok ? "OK    " : "FALHA ") + label);
    }

    private void log(String line) {
        System.out.println(line);
        log.add(line);
    }

    private void writeLog() {
        try (PrintWriter w = new PrintWriter(new File(out, prefix + "-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitFor(String what, Callable<Boolean> cond) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(cond.call())) return;
            sleep(100);
        }
        throw new IllegalStateException("tempo esgotado aguardando: " + what);
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

    @SuppressWarnings("unused")
    private static boolean all(BooleanSupplier... s) {
        for (BooleanSupplier b : s) if (!b.getAsBoolean()) return false;
        return true;
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container ct) {
                T t = find(ct, type);
                if (t != null) return t;
            }
        }
        return null;
    }

    /** Todo texto visível ao usuário (rótulos, botões, campos, tooltips e nomes acessíveis). */
    static String texts(Container root) {
        StringBuilder b = new StringBuilder();
        collect(root, b);
        return b.toString();
    }

    private static void collect(Component c, StringBuilder b) {
        if (c instanceof JLabel l && l.getText() != null) b.append(l.getText()).append('\n');
        if (c instanceof AbstractButton ab && ab.getText() != null) b.append(ab.getText()).append('\n');
        if (c instanceof JTextComponent t) b.append(t.getText()).append('\n');
        if (c instanceof javax.swing.JComponent jc && jc.getToolTipText() != null) b.append(jc.getToolTipText()).append('\n');
        AccessibleContext ac = c.getAccessibleContext();
        if (ac != null && ac.getAccessibleName() != null) b.append(ac.getAccessibleName()).append('\n');
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collect(ch, b);
    }
}
