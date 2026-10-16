package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.StudioEnvironment;
import com.nekkora.studio.app.project.StudioSettings;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraComboBox;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Processo do Studio REAL para o cenário 5 da homologação STUDIO-006 (recuperação). Diretório de dados vindo de
 * {@code NEKKORA_STUDIO_DATA_DIR}; mouse e teclado do sistema (Robot) dentro do próprio processo.
 * <ul>
 *   <li>{@code crash}: abre o projeto, dá o consentimento pelo diálogo "Recuperação automática" (intervalo de 1 min),
 *   altera a automação SEM salvar, espera a fotografia gravada pelo temporizador e imprime {@code READY} — o roteiro
 *   principal mata este processo sem aviso;</li>
 *   <li>{@code restart}: abre o Studio no mesmo diretório de dados, confere a oferta de recuperação, recupera, confere que
 *   continua NÃO salvo e que o arquivo não mudou, salva (Ctrl+S) no arquivo original e imprime {@code DONE}.</li>
 * </ul>
 * Uso: {@code Studio006RecoveryProbe crash|restart <projeto.nekkora> <pasta-de-saída>}
 */
public final class Studio006RecoveryProbe {
    static final String RECOVERED_NAME = "Entrar (alteração antes da queda)";

    private final Robot robot;
    private final File out;
    private StudioApplication.Window window;
    private int failures;

    private Studio006RecoveryProbe(File out) throws Exception {
        this.robot = new Robot();
        robot.setAutoDelay(25);
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        Studio006RecoveryProbe p = new Studio006RecoveryProbe(new File(args[2]));
        Path project = Path.of(args[1]).toAbsolutePath().normalize();
        int code = 1;
        try {
            switch (args[0]) {
                case "crash" -> p.crash(project);
                case "exit-discard" -> p.exitDiscard(project);
                case "exit-save" -> p.exitSave(project);
                case "none" -> p.none();
                default -> p.restart(project);
            }
            code = p.failures == 0 ? 0 : 2;
        } catch (Throwable t) {
            System.out.println("ERRO " + t);
            for (StackTraceElement s : t.getStackTrace()) System.out.println("    at " + s);
        }
        System.out.flush();
        System.exit(code);
    }

    private Path dataDir() {
        return Path.of(System.getenv("NEKKORA_STUDIO_DATA_DIR")).toAbsolutePath().normalize();
    }

    private void launch(List<Path> open) throws Exception {
        launch(open, () -> { });
    }

    /** {@code afterExit} roda quando o Studio encerraria a JVM (na aplicação real: fecha o Bridge e chama {@code System.exit}). */
    private void launch(List<Path> open, Runnable afterExit) throws Exception {
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), BrowserLink.offline("DISABLED"), afterExit,
                StudioEnvironment.installation(), open));
        edt(() -> {
            window.frame().setSize(870, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela", 15, () -> edt(() -> window.frame().isShowing()));
    }

    // ============================================================================== fase 1: até a queda

    private void crash(Path project) throws Exception {
        launch(List.of(project));
        waitFor("projeto aberto", 30, () -> edt(() -> window.studio().state().activeAutomation().isPresent()));
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        var file = new com.nekkora.studio.persistence.ProjectFiles(new com.nekkora.studio.persistence.ProjectRepository(), "sonda")
                .load(project, java.time.Clock.systemUTC()).automation();
        java.util.function.Function<Automation, java.util.List<java.util.List<Object>>> ids = x -> x.sessions().get(0).steps().stream()
                .map(st -> java.util.List.<Object>of(st.id(), st.provenance())).toList();
        check("Cenário 1: nova instância do Studio (outro processo) abre o projeto salvo — 3 etapas GRAVADAS, mesmas identidades e procedência do arquivo",
                edt(() -> a.id().equals(file.id()) && a.sessions().get(0).steps().size() == 3 && ids.apply(a).equals(ids.apply(file))
                        && a.sessions().get(0).steps().stream().allMatch(st -> st.provenance().origin() == com.nekkora.studio.domain.origin.StepOrigin.RECORDED)));
        check("Recuperação desativada num diretório de dados novo (consentimento ainda não pedido)",
                edt(() -> window.studio().projects().settings().recoveryConsent()) == StudioSettings.Consent.NOT_ASKED);
        menu("Arquivo", "Recuperação automática…");
        NekkoraDialog d = waitDialog("Recuperação automática");
        String explanation = edt(() -> texts(d));
        check("S006-061 consentimento informado: local, retenção (3 cópias, 7 dias) e aviso de não criptografado no diálogo",
                explanation.contains(dataDir().resolve("recovery").toString()) && explanation.contains("3 cópias") && explanation.contains("7 dias")
                && explanation.contains("NÃO são criptografados"));
        NekkoraCheckBox on = edt(() -> find(d, NekkoraCheckBox.class, c -> c.getText().startsWith("Ativar a recuperação")));
        click(on);
        edt(() -> {
            NekkoraComboBox<?> every = find(d, NekkoraComboBox.class, c -> true);
            every.setSelectedItem(60);
            return null;
        });
        capture(d, "12-consentimento-recuperacao");
        click(edt(() -> find(d, AbstractButton.class, b -> "Aplicar".equals(b.getText()) && b.isShowing())));
        waitFor("diálogo fechado", 10, () -> edt(() -> !d.isShowing()));
        check("S006-061 consentimento registrado e recuperação ativa (1 min)", edt(() -> window.studio().projects().settings().recoveryConsent()) == StudioSettings.Consent.GRANTED
                && edt(() -> window.studio().projects().settings().recoveryIntervalSeconds()) == 60
                && edt(() -> texts(window.frame().getContentPane())).contains("Recuperação ativa"));
        AutomationStep entrar = edt(() -> a.sessions().get(0).steps().get(2));
        edt(() -> {
            window.studio().service().updateStepBasics(a.id(), entrar.id(), RECOVERED_NAME, "Alterado e não salvo", ActionType.CLICK);
            window.studio().service().addComment(a.id(), entrar.id(), "Comentário antes da queda");
            return null;
        });
        long t0 = System.currentTimeMillis();
        waitFor("fotografia de recuperação pelo temporizador", 100, () -> snapshots().size() == 1);
        System.out.println("  fotografia gravada pelo temporizador " + (System.currentTimeMillis() - t0) / 1000 + " s depois da alteração");
        check("S006-061 fotografia de recuperação gravada sem salvar o projeto (continua 'Alterações não salvas')",
                edt(() -> window.studio().state().project(a.id()).orElseThrow().dirty()));
        capture(window.frame(), "13-antes-da-queda");
        System.out.println("READY");
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE); // morto sem aviso pelo roteiro principal
    }

    private List<Path> snapshots() throws Exception {
        Path dir = dataDir().resolve("recovery");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".nekkora")).toList();
        }
    }

    // ============================================================================== fase 2: depois da queda

    private void restart(Path project) throws Exception {
        String before = Studio006Homologation.sha(project);
        launch(List.of());
        NekkoraDialog offer = waitDialog("Recuperação disponível");
        String text = edt(() -> texts(offer));
        check("S006-062 oferta explícita ao abrir: projeto, horário e arquivo de origem; nada restaurado antes da escolha",
                text.contains("Encontramos alterações não salvas") && text.contains("Login homologacao 006") && text.contains("recuperacao.nekkora")
                && edt(() -> window.studio().state().catalog().all().isEmpty()));
        capture(offer, "14-oferta-de-recuperacao");
        click(edt(() -> find(offer, AbstractButton.class, b -> "Recuperar alterações".equals(b.getText()) && b.isShowing())));
        waitFor("recuperado", 20, () -> edt(() -> window.studio().state().activeAutomation().isPresent()));
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        AutomationStep entrar = edt(() -> a.sessions().get(0).steps().get(2));
        String status = edt(() -> window.studio().view().statusItems().get(0).getText());
        System.out.println("  status: " + status);
        check("S006-062 recuperado continua NÃO salvo ('Recuperado — alterações não salvas', aba com *)", status.startsWith("Recuperado — alterações não salvas")
                && edt(() -> window.studio().state().project(a.id()).orElseThrow().dirty()));
        check("S006-062 alterações feitas antes da queda presentes (nome e comentário)", edt(() -> RECOVERED_NAME.equals(entrar.name())
                && entrar.comments().size() == 3));
        check("S006-063 recuperar não alterou o arquivo do projeto", Studio006Homologation.sha(project).equals(before));
        capture(window.frame(), "15-recuperado-nao-salvo");
        focusWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitFor("salvo no arquivo original", 30, () -> edt(() -> !window.studio().state().project(a.id()).orElseThrow().dirty()));
        String saved = edt(() -> window.studio().view().statusItems().get(0).getText());
        check("S006-062 Ctrl+S grava no arquivo de origem (conteúdo base conferido): 'Salvo — recuperacao.nekkora'", saved.equals("Salvo — recuperacao.nekkora")
                && !Studio006Homologation.sha(project).equals(before));
        waitFor("fotografias apagadas", 20, () -> snapshots().isEmpty());
        capture(window.frame(), "16-recuperado-salvo");
        System.out.println("DONE");
    }

    // ============================================================================== revisão 1 (P1-B): encerramento real

    /** Encerramento real: confere o disco no instante em que a JVM terminaria e então chama {@code System.exit}. */
    private Runnable exitChecking(String label) {
        return () -> {
            try {
                List<Path> left = snapshots();
                check(label + " (" + left.size() + " no disco no instante do encerramento)", left.isEmpty());
            } catch (Exception e) {
                check(label + " (" + e + ")", false);
            }
            System.out.println("EXIT");
            System.out.flush();
            System.exit(failures == 0 ? 0 : 2);
        };
    }

    /** Abre o projeto, altera e grava uma fotografia (recuperação já consentida neste diretório de dados). */
    private Automation editWithSnapshot(Path project, String newName, Runnable afterExit) throws Exception {
        launch(List.of(project), afterExit);
        waitFor("projeto aberto", 30, () -> edt(() -> window.studio().state().activeAutomation().isPresent()));
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        AutomationStep entrar = edt(() -> a.sessions().get(0).steps().get(2));
        edt(() -> {
            window.studio().service().updateStepBasics(a.id(), entrar.id(), newName, "", ActionType.CLICK);
            return null;
        });
        focusWorkspace(); // a janela ganha o foco: o ciclo de vida não dispara outra fotografia fora de hora
        int written = edt(() -> window.studio().projects().checkpoint()).get(30, java.util.concurrent.TimeUnit.SECONDS);
        waitFor("fotografia de recuperação", 30, () -> snapshots().size() == 1);
        check("Revisão 1: alteração não salva com fotografia de recuperação gravada (" + written + ")", snapshots().size() == 1
                && edt(() -> window.studio().state().project(a.id()).orElseThrow().dirty()));
        return a;
    }

    /** Sair → Descartar alterações pelo diálogo real; o processo encerra de verdade ({@code System.exit}). */
    private void exitDiscard(Path project) throws Exception {
        editWithSnapshot(project, "Alteração descartada ao sair", exitChecking("P1-B cenário A: Sair → Descartar alterações só encerra depois de apagar as fotografias"));
        menu("Arquivo", "Sair");
        NekkoraDialog d = waitDialog("Sair do Nekkora Studio?");
        capture(d, "17-sair-descartar");
        click(edt(() -> find(d, AbstractButton.class, b -> "Descartar alterações".equals(b.getText()) && b.isShowing())));
        Thread.sleep(30_000); // o encerramento chama System.exit; se não chamar, o roteiro principal acusa
        check("P1-B cenário A: o Studio encerrou", false);
    }

    /** Ctrl+S e, assim que o salvamento é informado, Sair (sem pergunta: nada pendente). */
    private void exitSave(Path project) throws Exception {
        Automation a = editWithSnapshot(project, "Alteração salva antes de sair",
                exitChecking("P1-B cenário B: salvar e sair logo em seguida não deixa fotografia superada"));
        focusWorkspace();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_S);
        waitFor("salvamento informado", 30, () -> edt(() -> !window.studio().state().project(a.id()).orElseThrow().dirty()));
        check("P1-B cenário B: no instante em que o salvamento é informado, a fotografia superada já não existe", snapshots().isEmpty());
        menu("Arquivo", "Sair");
        Thread.sleep(30_000);
        check("P1-B cenário B: o Studio encerrou", false);
    }

    /** Novo processo no mesmo diretório de dados: nenhuma recuperação é oferecida. */
    private void none() throws Exception {
        launch(List.of());
        long end = System.currentTimeMillis() + 10_000;
        boolean offered = false;
        while (System.currentTimeMillis() < end && !offered) {
            offered = edt(() -> {
                for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog nd && nd.isShowing() && "Recuperação disponível".equals(nd.getTitle())) return true;
                return false;
            });
            Thread.sleep(200);
        }
        check("P1-B: novo processo do Studio não oferece alterações descartadas nem fotografias superadas", !offered
                && edt(() -> window.studio().state().catalog().all().isEmpty()));
        capture(window.frame(), "18-reinicio-sem-oferta");
        System.out.println("DONE");
    }

    // ============================================================================== apoio

    private void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.println((ok ? "OK    " : "FALHA ") + label);
        System.out.flush();
    }

    private void focusWorkspace() throws Exception {
        edt(() -> {
            window.frame().toFront();
            window.studio().controller().selectAutomation(window.studio().state().activeAutomation().orElseThrow().id());
            return null;
        });
        Rectangle b = edt(() -> window.frame().getBounds());
        robot.mouseMove(b.x + b.width / 2, b.y + 12);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        waitFor("Studio com foco", 10, () -> edt(() -> window.frame().isFocused() || window.frame().isActive()));
        Thread.sleep(300);
    }

    private void menu(String... path) throws Exception {
        focusWorkspace();
        JMenu top = edt(() -> {
            var bar = window.frame().getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) if (path[0].equals(bar.getMenu(i).getText())) return bar.getMenu(i);
            return null;
        });
        click(top);
        waitFor("menu", 5, () -> edt(top::isPopupMenuVisible));
        for (int i = 1; i < path.length; i++) {
            String name = path[i];
            waitFor("item " + name, 5, () -> edt(() -> item(top, name)) != null);
            click(edt(() -> item(top, name)));
        }
    }

    private static JMenuItem item(JMenu menu, String text) {
        for (Component c : menu.getPopupMenu().getComponents()) if (c instanceof JMenuItem it && text.equals(it.getText()) && it.isShowing()) return it;
        return null;
    }

    private NekkoraDialog waitDialog(String title) throws Exception {
        AtomicReference<NekkoraDialog> found = new AtomicReference<>();
        waitFor("diálogo " + title, 30, () -> {
            found.set(edt(() -> {
                for (Window w : Window.getWindows()) if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return nd;
                return null;
            }));
            return found.get() != null;
        });
        Thread.sleep(400);
        return found.get();
    }

    private void click(Component c) throws Exception {
        robot.waitForIdle();
        Thread.sleep(150);
        Rectangle r = edt(() -> new Rectangle(c.getLocationOnScreen(), c.getSize()));
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        Thread.sleep(250);
    }

    private void press(int... keys) throws Exception {
        for (int k : keys) robot.keyPress(k);
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keys[i]);
        robot.waitForIdle();
        Thread.sleep(200);
    }

    private void capture(Window w, String name) throws Exception {
        robot.waitForIdle();
        Thread.sleep(300);
        File f = new File(out, "S006-" + name + ".png");
        ImageIO.write(robot.createScreenCapture(edt(w::getBounds)), "png", f);
        System.out.println("captura " + f.getName());
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

    private static String texts(Container root) {
        StringBuilder b = new StringBuilder();
        collect(root, b);
        return b.toString();
    }

    private static void collect(Component c, StringBuilder b) {
        if (c instanceof javax.swing.JLabel l && l.getText() != null) b.append(l.getText()).append('\n');
        if (c instanceof AbstractButton ab && ab.getText() != null) b.append(ab.getText()).append('\n');
        if (c instanceof JTextComponent t && t.getText() != null) b.append(t.getText()).append('\n');
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collect(ch, b);
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
            Thread.sleep(150);
        }
        throw new IllegalStateException("tempo esgotado aguardando: " + what + (last == null ? "" : " (" + last + ")"));
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
}
