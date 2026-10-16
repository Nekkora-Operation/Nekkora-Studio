package com.nekkora.studio.app.evidence;

import com.nekkora.studio.app.StudioApplication;
import com.nekkora.studio.app.ui.StudioActions;
import com.nekkora.studio.app.workspace.Selection;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.ui.recorder.FloatingRecorderPanel;
import com.nekkora.studio.ui.AutomationOverview;
import com.nekkora.studio.ui.LogicalSessionView;
import com.nekkora.studio.ui.form.MoveStepForm;
import com.nekkora.studio.ui.form.StepDetailsForm;
import com.nekkora.studio.domain.StepId;
import java.util.LinkedHashMap;
import com.nekkora.studio.ui.recorder.FloatingRecorderWindow;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
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
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Homologação REAL da CHROME-SECURITY-001 no Windows: Studio de verdade, Chrome for Testing com a extensão, Native Host
 * instalado pelo script oficial, páginas FICTÍCIAS do laboratório e mouse/teclado do sistema operacional (Robot).
 * Cenários 1 a 8 da missão: processo completo (Autenticação/Consulta), nome acessível sensível, URLs sensíveis, captura
 * técnica completa e incremental, exportação de PageMap, recuperação (service worker e Native Host), elementos difíceis e
 * compatibilidade de versões. Em cada cenário o domínio do Studio, a tela, o armazenamento da extensão e os logs/arquivos
 * do host são varridos pelos canários fictícios da matriz SEC001 (sem acento, minúsculas, separadores normalizados).
 *
 * <p>Uso: {@code java ... Sec001Homologation <saída> <repositório-da-extensão> [compat <repositório-atual-da-extensão>]} —
 * no modo {@code compat} o repositório indicado é o da LINHA DE BASE (extensão e host antigos) com o Studio novo.
 */
public final class Sec001Homologation {

    private static final int LAB_PORT = 18765;
    private static final String BASE = "http://127.0.0.1:" + LAB_PORT + "/lab/";
    private static final String S3 = BASE + "studio003/";
    private static final String SEC = BASE + "chrome-security-001/";
    /** Valores digitados na homologação (fictícios): NUNCA podem aparecer em etapas, logs, armazenamento ou tela. */
    private static final String USER = "anateodomiro";
    private static final String PASSWORD = "Senha2026Teste";
    private static final String SEARCH = "Teodomiro";
    private static final String PHONE = "988771234";
    private static final String RECOVERY = "Teodomiroxyz";
    private static final int CHROME_X = 880;

    private final File out;
    private final Path ext;
    private final Path mainExt;
    private final boolean compat;
    private final Robot robot;
    private final List<String> log = new ArrayList<>();
    private int failures;
    private int checks;
    private StudioApplication.Window window;
    private BridgeServer bridge;
    private Process chrome;
    private Process labServer;
    private Path profile;
    private Path testExtension;
    private Cdp cdp;
    private Cdp.Page harness;
    private Cdp.Page panel;
    private String extId;
    private Cdp.Target labTarget;
    private Cdp.Page page;
    private int labTab;

    private Sec001Homologation(File out, Path ext, Path mainExt, boolean compat) throws AWTException {
        this.out = out;
        this.ext = ext;
        this.mainExt = mainExt;
        this.compat = compat;
        this.robot = new Robot();
        robot.setAutoDelay(25);
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        boolean compat = args.length > 2 && "compat".equals(args[2]);
        Path ext = Path.of(args[1]).toAbsolutePath().normalize();
        Sec001Homologation h = new Sec001Homologation(out, ext, args.length > 3 ? Path.of(args[3]).toAbsolutePath().normalize() : ext, compat);
        int code = 1;
        try {
            h.run();
            code = h.failures == 0 ? 0 : 2;
        } catch (Throwable t) {
            h.log("ERRO " + t);
            for (StackTraceElement s : t.getStackTrace()) h.log("    at " + s);
            h.failures++;
        } finally {
            h.log(String.format("RESULTADO: %d verificações, %d falha(s)", h.checks, h.failures));
            h.writeLog();
            h.teardown();
        }
        System.exit(code);
    }

    // ============================================================================== roteiro

    private void run() throws Exception {
        log("Tela: " + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() + " · java " + System.getProperty("java.version"));
        log("Modo: " + (compat ? "COMPATIBILIDADE (extensão e host da linha de base, Studio novo)" : "completo (extensão, host e Studio da CHROME-SECURITY-001)"));
        loadCanaries();
        prepareEnvironment();
        bridge = BridgeServer.start(BridgeServer.Options.defaults(StudioApplication.VERSION));
        window = edt(() -> StudioApplication.launch(Optional.of(NekkoraThemes.SAKURA_DARK), bridge, bridge::close));
        edt(() -> {
            window.frame().setSize(870, 1000);
            window.frame().setLocation(0, 0);
            window.frame().toFront();
            return null;
        });
        waitFor("janela do Studio", 10, () -> edt(() -> window.frame().isShowing()));
        launchChrome(S3 + "login.html");
        connect();
        if (compat) {
            compatibility();
            return;
        }

        // ================================================================ Cenário 8a — política declarada pela extensão nova
        String status = statusText();
        String privacy = edt(() -> {
            var l = window.studio().recorder().snapshot().link();
            return l == null || l.extension() == null ? null : l.extension().privacy();
        });
        log("  política declarada no hello: " + privacy + " · status: " + status);
        check("Cenário 8a: extensão nova declara nekkora-privacy/2 e o Studio a aceita sem aviso", "nekkora-privacy/2".equals(privacy)
                && !status.contains("sem a política de privacidade atual"));

        focusStudio();
        createAutomation();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession auth = a.sessions().get(0);
        LogicalSession consulta = a.sessions().get(1);

        // ================================================================ Cenário 1 — Processo completo
        selectSession(a, auth);
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        moveFloatingAway();
        String tabTitle = edt(() -> window.studio().recorder().snapshot().tabTitle());
        log("  aba da gravação no Studio: título=" + tabTitle + " host=" + edt(() -> window.studio().recorder().snapshot().tabHost()));
        clickInPage("document.getElementById('usuario')");
        typeText(USER);
        clickInPage("document.getElementById('senha')");
        typeText(PASSWORD);
        clickInPage("document.getElementById('entrar')");
        waitFor("3 etapas na Autenticação", 30, () -> edt(auth::stepCount) == 3);
        waitPage("consulta.html");
        sleep(1500);
        edt(() -> {
            fp.sessionCombo().setSelectedItem(consulta);
            return null;
        });
        waitFor("sessão Consulta associada", 10, () -> edt(() -> consulta.id().equals(window.studio().recorder().snapshot().session())));
        clickInPage("document.getElementById('menu-clientes')");
        clickInPage("document.getElementById('pesquisa')");
        typeText(SEARCH);
        clickInPage("document.getElementById('estado')");
        press(KeyEvent.VK_DOWN);
        press(KeyEvent.VK_ENTER);
        sleep(600);
        clickInPage("document.getElementById('pesquisar')");
        waitFor("4 etapas na Consulta", 30, () -> edt(consulta::stepCount) >= 4);
        sleep(1500);
        List<String> kinds1 = edt(() -> {
            List<String> k = new ArrayList<>();
            for (LogicalSession s : List.of(auth, consulta)) for (AutomationStep st : s.steps()) k.add(st.actionType().name());
            return k;
        });
        log("  Autenticação: " + names(auth));
        log("  Consulta: " + names(consulta));
        log("  tipos: " + kinds1);
        check("Cenário 1: Autenticação = preencher, preencher, clicar", kinds1.subList(0, 3).equals(List.of("FILL", "FILL", "CLICK")));
        check("Cenário 1: Consulta = clicar (link), preencher (pesquisa), selecionar opção, clicar (Pesquisar)",
                kinds1.subList(3, kinds1.size()).equals(List.of("CLICK", "FILL", "SELECT_OPTION", "CLICK")));
        check("Cenário 1: senha marcada como sensível e sem valor", edt(() -> {
            AutomationStep senha = auth.steps().get(1);
            return senha.element().map(ElementReference::sensitive).orElse(false) && senha.parameters().value().isEmpty();
        }));
        check("Cenário 1: opção escolhida não veio da extensão (OPTION_WITHHELD); a escolha é configurada no Studio", edt(() -> {
            AutomationStep sel = consulta.steps().get(2);
            return sel.parameters().option().isEmpty() && sel.element().map(e -> e.stabilityNotes().toString().contains("Opção escolhida não registrada")).orElse(false);
        }));
        selectStep(a, consulta, edt(() -> consulta.steps().get(2)));
        captureStudio("01-processo-completo");
        privacyCheck("Cenário 1", a, List.of(USER, PASSWORD, SEARCH, "São Paulo"));

        // ================================================================ Cenário 2 — Nome acessível sensível
        LogicalSession nomes = newSession("Nomes");
        edt(() -> {
            fp.sessionCombo().setSelectedItem(nomes);
            return null;
        });
        waitFor("sessão Nomes associada", 10, () -> edt(() -> nomes.id().equals(window.studio().recorder().snapshot().session())));
        navigateByUser(SEC + "elementos.html");
        clickInPage("document.getElementById('consultar')");
        clickInPage("document.getElementById('fone')");
        typeText(PHONE);
        clickInPage("document.getElementById('apelido')");
        waitFor("etapas com nomes sensíveis", 30, () -> edt(nomes::stepCount) >= 2);
        sleep(1500);
        log("  Nomes: " + names(nomes));
        AutomationStep consultar = edt(() -> nomes.steps().get(0));
        check("Cenário 2: botão 'Consultar cliente Bartolomeu Quixadá' vira etapa com descrição segura (sem o nome do cliente)",
                edt(() -> consultar.actionType().name().equals("CLICK") && !norm(consultar.name()).contains("quixada")
                        && !norm(consultar.name()).contains("bartolomeu") && consultar.element().map(e -> e.accessibleName().isEmpty()).orElse(false)));
        check("Cenário 2: campo rotulado por 'telefone de …' (aria-labelledby) sem o rótulo privado", edt(() -> nomes.steps().stream()
                .noneMatch(st -> norm(st.name()).contains("ubirajara"))));
        selectStep(a, nomes, consultar);
        captureStudio("02-nome-sensivel");
        privacyCheck("Cenário 2", a, List.of(PHONE));

        // ================================================================ Cenário 3 — URLs sensíveis
        navigateByUser(SEC + "navegacao.html");
        int c3 = edt(nomes::stepCount);
        clickInPage("document.getElementById('rota-cliente')");
        sleep(QUIET_MS);
        clickInPage("document.getElementById('hash-cliente')");
        sleep(QUIET_MS);
        clickInPage("document.getElementById('doc-cliente')");
        waitPage("ataulfo-marcondes.html");
        waitFor("etapas de navegação", 30, () -> edt(nomes::stepCount) >= c3 + 3);
        sleep(QUIET_MS);
        List<String> urls = edt(() -> {
            List<String> u = new ArrayList<>();
            for (AutomationStep st : nomes.steps().subList(c3, nomes.stepCount())) {
                st.context().ifPresent(c -> u.add(c.location()));
                st.evidence().forEach(e -> u.add(String.valueOf(e.navigation())));
            }
            return u;
        });
        log("  endereços no Studio: " + urls);
        check("Cenário 3: rota (History API), fragmento e novo documento sem identificadores privados no Studio",
                leaks(String.join(" ", urls)).isEmpty() && edt(() -> nomes.stepCount()) >= c3 + 3);
        check("Cenário 3: título/host da aba no Studio sem dado do documento", leaks(edt(() -> window.studio().recorder().snapshot().tabTitle()
                + " " + window.studio().recorder().snapshot().tabHost())).isEmpty());
        captureStudio("03-urls");
        privacyCheck("Cenário 3", a, List.of());

        // ================================================================ Cenário 4 — Captura técnica (completa e incremental)
        navigateByUser(SEC + "dinamica.html");
        Object full = harness.eval("window.nekkora.call('page.capture', {tabId: " + labTab + "}).then(r => JSON.stringify(r))");
        clickInPage("document.getElementById('preencher')");
        clickInPage("document.getElementById('inserir')");
        sleep(2500);
        Object sync = harness.eval("window.nekkora.call('tracking.flush', {tabId: " + labTab + "}).then(r => JSON.stringify(r))");
        Object raw = harness.eval("window.nekkora.call('page.rawSnapshot', {tabId: " + labTab + "}).then(r => JSON.stringify(r))");
        Object events = harness.eval("window.nekkora.call('events.since', {since: 0}).then(r => JSON.stringify(r))");
        Object debug = harness.eval("window.nekkora.call('session.debug', {tabId: " + labTab + "}).then(r => JSON.stringify(r))");
        String pageText = String.valueOf(page.eval("document.body.innerText"));
        check("Cenário 4: a página real mostra os textos dinâmicos (o teste é significativo)", norm(pageText).contains("paranhos") && norm(pageText).contains("laranjeira"));
        log("  captura completa: " + String.valueOf(full).length() + " B; sincronização: " + String.valueOf(sync).replaceAll("\\s+", " ").substring(0, Math.min(200, String.valueOf(sync).length())));
        check("Cenário 4: snapshot (completo + deltas aplicados) sem os textos dinâmicos", leaks(String.valueOf(raw)).isEmpty());
        check("Cenário 4: resposta da captura sem conteúdo da página", leaks(String.valueOf(full)).isEmpty());
        check("Cenário 4: eventLog do service worker sem conteúdo da página", leaks(String.valueOf(events)).isEmpty());
        check("Cenário 4: estado de depuração da sessão de captura sem conteúdo da página", leaks(String.valueOf(debug)).isEmpty());
        check("Cenário 4: logs e arquivos do Native Host sem conteúdo da página", leaks(hostLogs() + hostData()).isEmpty());
        check("Cenário 4: snapshot declara a política nekkora-privacy/2", String.valueOf(raw).contains("nekkora-privacy/2"));

        // ================================================================ Cenário 5 — Exportação de PageMap
        navigateByUser(SEC + "exportacao.html");
        Object exp = harness.eval("window.nekkora.call('pagemap.export', {tabId: " + labTab + ", returnPageMap: false}).then(r => JSON.stringify(r))");
        String expPath = String.valueOf(Json.parseObject(String.valueOf(exp)).get("result") instanceof Map<?, ?> m ? m.get("path") : null);
        log("  PageMap exportado: " + expPath);
        String exported = Files.readString(Path.of(expPath), StandardCharsets.UTF_8);
        Map<String, Object> pm = Json.parseObject(exported);
        check("Cenário 5: arquivo exportado examinado inteiro: nenhum dado fictício da página", leaks(exported).isEmpty());
        check("Cenário 5: PageMap 0.3.0 com a política declarada", "0.3.0".equals(String.valueOf(pm.get("version"))) && exported.contains("nekkora-privacy/2"));
        log("  tamanho do PageMap exportado: " + exported.length() + " caracteres");

        // ================================================================ Cenário 6 — Recuperação (service worker e Native Host)
        LogicalSession rec = newSession("Recuperação");
        edt(() -> {
            fp.sessionCombo().setSelectedItem(rec);
            return null;
        });
        waitFor("sessão Recuperação associada", 10, () -> edt(() -> rec.id().equals(window.studio().recorder().snapshot().session())));
        navigateByUser(SEC + "recuperacao.html");
        clickInPage("document.getElementById('r-nome')");
        typeText(RECOVERY);
        clickInPage("document.getElementById('r-consultar')");
        waitFor("2 etapas antes do reinício", 30, () -> edt(rec::stepCount) == 2);
        String sw0 = serviceWorkerId();
        page.send("ServiceWorker.enable", Map.of());
        page.send("ServiceWorker.stopAllWorkers", Map.of());
        log("  service worker encerrado (" + sw0 + ")");
        sleep(2500);
        clickInPage("document.getElementById('r-consultar')");
        waitFor("etapa depois do reinício do service worker", 40, () -> edt(rec::stepCount) == 3);
        log("  service worker depois: " + serviceWorkerId());
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("Recorder degradado pela desconexão do Native Host", 30, () -> edt(() -> window.studio().recorder().snapshot().linkLost()));
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("reconexão", 60, () -> stateIs(RecorderState.RECORDING));
        sleep(3000);
        int afterReconnect = edt(rec::stepCount);
        clickInPage("document.getElementById('r-consultar')");
        waitFor("etapa depois da reconexão", 40, () -> edt(rec::stepCount) == afterReconnect + 1);
        sleep(QUIET_MS);
        log("  Recuperação: " + names(rec));
        check("Cenário 6: nenhuma etapa duplicada ou reenviada depois do reinício e da reconexão", edt(rec::stepCount) == 4);
        String storage = String.valueOf(harness.eval("chrome.storage.session.get(null).then(x => JSON.stringify(x))"))
                + harness.eval("chrome.storage.local.get(null).then(x => JSON.stringify(x))");
        check("Cenário 6: chrome.storage (session e local) sem dados da página nem valores digitados", leaks(storage).isEmpty() && !storage.contains(RECOVERY));
        Object events6 = harness.eval("window.nekkora.call('events.since', {since: 0}).then(r => JSON.stringify(r))");
        check("Cenário 6: eventLog depois da recuperação sem dados da página", leaks(String.valueOf(events6)).isEmpty());
        privacyCheck("Cenário 6", a, List.of(RECOVERY));
        captureStudio("06-recuperacao");

        // ================================================================ Cenário 7 — Elementos difíceis
        LogicalSession dif = newSession("Elementos difíceis");
        edt(() -> {
            fp.sessionCombo().setSelectedItem(dif);
            return null;
        });
        waitFor("sessão Elementos difíceis associada", 10, () -> edt(() -> dif.id().equals(window.studio().recorder().snapshot().session())));
        navigateByUser(SEC + "iframes.html");
        clickInPage("document.getElementById('autorizado').contentDocument.getElementById('no-quadro')", "document.getElementById('autorizado')");
        waitFor("etapa no iframe", 30, () -> edt(dif::stepCount) == 1);
        navigateByUser(SEC + "shadow.html");
        clickInPage("document.getElementById('ficha').shadowRoot.getElementById('s-ok')");
        waitFor("etapa no Shadow DOM", 30, () -> edt(dif::stepCount) == 2);
        navigateByUser(SEC + "tabela.html");
        clickInPage("document.querySelectorAll('button.ver')[1]");
        waitFor("etapa na tabela", 30, () -> edt(dif::stepCount) == 3);
        navigateByUser(SEC + "dinamica.html");
        clickInPage("document.getElementById('inserir')");
        waitFor("etapa no componente dinâmico", 30, () -> edt(dif::stepCount) == 4);
        sleep(QUIET_MS);
        List<String> labels = List.of("iframe autorizado", "Shadow DOM", "tabela (2ª linha, botão sem id)", "componente dinâmico");
        for (int i = 0; i < 4; i++) {
            int k = i;
            String d = edt(() -> describe(dif.steps().get(k)));
            log("  " + labels.get(k) + ": " + d);
        }
        check("Cenário 7: iframe → trajetória com IFRAME, nome privado ('Aprovar Genésio Furtado') retido", edt(() -> {
            AutomationStep st = dif.steps().get(0);
            return st.element().map(e -> e.accessPath().stream().anyMatch(s -> s.toString().contains("IFRAME"))).orElse(false)
                    && !norm(st.name()).contains("furtado");
        }));
        check("Cenário 7: Shadow DOM → trajetória com SHADOW_ROOT, nome privado retido", edt(() -> {
            AutomationStep st = dif.steps().get(1);
            return st.element().map(e -> e.accessPath().stream().anyMatch(s -> s.toString().contains("SHADOW"))).orElse(false)
                    && !norm(st.name()).contains("valdomiro");
        }));
        check("Cenário 7: tabela → linha não identificada por texto (nenhum localizador com o titular/pedido)", edt(() ->
                leaks(String.valueOf(dif.steps().get(2).element().map(ElementReference::locators).orElse(List.of()))).isEmpty()));
        check("Cenário 7: nenhum localizador preferido inventado — todo preferido é validado; sem localizador seguro, limitação declarada",
                edt(() -> dif.steps().stream().allMatch(st -> st.element().map(e -> e.preferredLocator().map(LocatorCandidate::validated).orElse(true)
                        && (e.preferredLocator().isPresent() || !e.stabilityNotes().isEmpty())).orElse(true))));
        selectStep(a, dif, edt(() -> dif.steps().get(2)));
        captureStudio("07-elementos-dificeis");
        privacyCheck("Cenário 7", a, List.of());

        // ================================================================ Cenário 9 — D7: continuidade técnica × identidade lógica
        navigateByUser(SEC + "continuidade.html");
        Object sel = harness.eval("window.nekkora.call('page.select', {tabId: " + labTab + ", css: '#virtual tr:first-child .ver'}).then(r => JSON.stringify(r))");
        Map<String, Object> selJson = Json.parseObject(String.valueOf(sel));
        String targetId = String.valueOf(Json.object(Json.object(selJson, "result"), "identity").get("targetId"));
        clickInPage("document.getElementById('rolar')"); // a lista virtual reaproveita a MESMA linha para outro registro
        sleep(1500);
        Map<String, Object> res = null;
        for (int i = 0; i < 10; i++) {
            Object r9 = harness.eval("window.nekkora.call('target.resolve', {tabId: " + labTab + ", targetId: '" + targetId + "'}).then(r => JSON.stringify(r))");
            res = Json.object(Json.object(Json.parseObject(String.valueOf(r9)), "result"), "result"); // { ok, result: { result } }
            if (String.valueOf(Json.object(res, "continuity")).contains("ROW_CONTENT_CHANGED")) {
                break;
            }
            sleep(700);
        }
        String resText = Json.write(res);
        log("  resolvedNodeId=" + Json.write(res.get("resolvedNodeId")) + " updatedLocators=" + Json.write(res.get("updatedLocators"))
                + " updatedIdentity=" + Json.write(res.get("updatedIdentity")));
        log("  resolução depois da virtualização: status=" + res.get("status") + " continuidade=" + Json.write(Json.object(res, "continuity")));
        check("Cenário 9 (D7): mesmo objeto DOM — LIVE_NODE_CONTINUITY com ROW_CONTENT_CHANGED (mudança observada por contadores, sem texto)",
                "LIVE_NODE_CONTINUITY".equals(String.valueOf(Json.object(res, "continuity").get("status"))) && resText.contains("ROW_CONTENT_CHANGED"));
        check("Cenário 9 (D7): nenhum texto ou identificador da linha no resultado", !resText.contains("Registro") && leaks(resText).isEmpty());
        check("Cenário 9 (D7): continuidade não autoriza uso — identidade lógica não confirmada, sem nó resolvido nem localizador novo",
                !"RESOLVED".equals(String.valueOf(res.get("status"))) && jsonNull(res.get("resolvedNodeId")) && jsonNull(res.get("updatedLocators"))
                        && jsonNull(res.get("updatedIdentity")));
        Object diag = harness.eval("window.nekkora.call('page.diagnostics', {tabId: " + labTab + "}).then(r => JSON.stringify(r))");
        Map<String, Object> sel9 = Json.object(Json.object(Json.object(Json.parseObject(String.valueOf(diag)), "result"), "highlight"), "boxes");
        check("Cenário 9 (D7): nenhum destaque de alvo confirmado", sel9 == null || Json.object(sel9, "selection") == null
                || !Boolean.TRUE.equals(Json.object(sel9, "selection").get("visible")));
        captureStudio("09-continuidade");

        // ================================================================ Cenário 10 — 2ª revisão dos PRs #7: nomes de componente
        LogicalSession comp = newSession("Componentes");
        edt(() -> {
            fp.sessionCombo().setSelectedItem(comp);
            return null;
        });
        waitFor("sessão Componentes associada", 10, () -> edt(() -> comp.id().equals(window.studio().recorder().snapshot().session())));
        navigateByUser(SEC + "componentes.html");
        clickInPage("document.getElementById('consultar-ficha')");
        waitFor("etapa no componente do DOM claro", 30, () -> edt(comp::stepCount) == 1);
        clickInPage("document.getElementById('acao')");
        waitFor("etapa no próprio componente", 30, () -> edt(comp::stepCount) == 2);
        clickInPage("document.getElementById('cartao').shadowRoot.getElementById('no-cartao')");
        waitFor("etapa no Shadow DOM do componente", 30, () -> edt(comp::stepCount) == 3);
        clickInPage("document.getElementById('no-painel')");
        waitFor("etapa no componente do vocabulário", 30, () -> edt(comp::stepCount) == 4);
        sleep(QUIET_MS);
        List<String> labels10 = List.of("botão em <ficha-cliente-teodolinda>", "<acao-teodolinda-ramos role=button>", "botão no Shadow DOM de <cartao-teodolinda-viana>",
                "botão em <painel-cliente>");
        for (int i = 0; i < 4; i++) {
            int k = i;
            log("  " + labels10.get(k) + ": " + edt(() -> describe(comp.steps().get(k))));
        }
        String compSteps = edt(() -> comp.steps().stream().map(st -> st.name() + " " + st.element().map(Object::toString).orElse("")).reduce("", String::concat));
        check("Cenário 10: nomes de componente fictícios ('teodolinda') não chegam ao Studio — nem na tag, nem em localizadores ou trajetórias",
                leaks(compSteps).isEmpty());
        check("Cenário 10: o componente clicado aparece com o marcador neutro (custom-element), sem o nome real",
                edt(() -> comp.steps().get(1).element().map(e -> "custom-element".equals(e.tag())).orElse(false)));
        check("Cenário 10: Shadow DOM de componente com nome retido → trajetória com SHADOW_ROOT preservada", edt(() ->
                comp.steps().get(2).element().map(e -> e.accessPath().stream().anyMatch(st -> st.toString().contains("SHADOW"))).orElse(false)));
        Object insp = harness.eval("Promise.all(['#ficha', '#acao', '#desconhecido', '#cartao', '#painel'].map(c => window.nekkora.call('page.select', {tabId: "
                + labTab + ", css: c}))).then(r => JSON.stringify(r))");
        String inspText = String.valueOf(insp);
        check("Cenário 10: motor (inspeção, identidade, localizadores) sem os nomes fictícios; componente do vocabulário ('painel-cliente') mantido",
                leaks(inspText).isEmpty() && inspText.contains("custom-element") && inspText.contains("painel-cliente"));
        check("Cenário 10: localizador do hospedeiro do Shadow DOM sem o nome, pela posição real entre os irmãos (:nth-child)",
                inspText.contains(":nth-child(") && !norm(inspText).contains("cartao teodolinda"));
        Object pm10 = harness.eval("window.nekkora.call('pagemap.export', {tabId: " + labTab + "}).then(r => JSON.stringify(r))");
        check("Cenário 10: PageMap exportado e arquivos do host sem os nomes fictícios", leaks(String.valueOf(pm10)).isEmpty() && leaks(hostLogs() + hostData()).isEmpty());
        Object ni = harness.eval("window.nekkora.call('page.idFor', {tabId: " + labTab + ", css: '#ficha'}).then(r => window.nekkora.call('page.nodeInfo', {tabId: "
                + labTab + ", nodeId: r.result.nodeIds[0]})).then(r => JSON.stringify(r))");
        check("Cenário 10: consulta de nó (page.nodeInfo) sem texto nem nome da página", leaks(String.valueOf(ni)).isEmpty() && !String.valueOf(ni).contains("\"text\""));
        privacyCheck("Cenário 10", a, List.of());
        captureStudio("10-componentes");

        // ---- encerra a gravação
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        sleep(800);
        captureWindow(floating(), "08-resumo");
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);

        // ---- verificação final (domínio, tela, armazenamento da extensão, logs e arquivos do host)
        privacyCheck("Final", a, List.of(USER, PASSWORD, SEARCH, PHONE, RECOVERY));
        captureStudio("11-automacao-final");
        log("Automação final:");
        for (LogicalSession s : edt(a::sessions)) {
            log("  Sessão " + s.name() + ": " + names(s));
        }
    }

    // ============================================================================== Cenário 8b — compatibilidade

    /**
     * Extensão e Native Host da LINHA DE BASE (política nekkora-privacy/1, sem {@code client.privacy}) com o Studio NOVO:
     * o Studio não aceita em silêncio — avisa e reaplica a política a nomes, localizadores e endereços (4ª barreira).
     */
    private void compatibility() throws Exception {
        String status = statusText();
        log("  status com a extensão antiga: " + status);
        check("Cenário 8b: extensão sem a política atual → aviso visível, nenhum downgrade silencioso", status.contains("sem a política de privacidade atual"));
        focusStudio();
        createAutomation();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSession s = a.sessions().get(0);
        selectSession(a, s);
        navigate(SEC + "elementos.html");
        startRecordingOn("127.0.0.1", RecordingMode.CAPTURE_AND_INTERACT);
        FloatingRecorderPanel fp = edt(() -> window.studio().recorderPresenter().panel());
        moveFloatingAway();
        clickInPage("document.getElementById('consultar')");
        clickInPage("document.getElementById('fone')");
        typeText(PHONE);
        clickInPage("document.getElementById('apelido')");
        navigateByUser(SEC + "navegacao.html");
        clickInPage("document.getElementById('rota-cliente')");
        sleep(QUIET_MS);
        clickInPage("document.getElementById('doc-cliente')");
        waitPage("ataulfo-marcondes.html");
        waitFor("etapas com a extensão antiga", 30, () -> edt(s::stepCount) >= 4);
        sleep(QUIET_MS);
        log("  etapas: " + names(s));
        for (int i = 0; i < edt(s::stepCount); i++) {
            int k = i;
            log("    " + edt(() -> describe(s.steps().get(k))));
        }
        check("Cenário 8b: o nome privado enviado pela extensão antiga é revalidado pelo Studio (sem 'Bartolomeu Quixadá')",
                edt(() -> s.steps().stream().noneMatch(st -> norm(st.name()).contains("quixada"))));
        check("Cenário 8b: referências marcadas com a política antiga não exibidas como seguras",
                edt(() -> s.steps().stream().allMatch(st -> st.element().map(e -> !"nekkora-privacy/1".equals(e.privacyPolicy().orElse(""))
                        || e.accessibleName().isEmpty()).orElse(true))));
        selectStep(a, s, edt(() -> s.steps().get(0)));
        captureStudio("08b-compatibilidade");
        click(edt(fp::finishButton));
        waitState(RecorderState.COMPLETED);
        clickButtonWithText(floating(), "Voltar ao Studio");
        waitState(RecorderState.IDLE);
        privacyCheckDomain("Cenário 8b", a, List.of(PHONE));
    }

    // ============================================================================== apoio CHROME-SECURITY-001

    private static final long QUIET_MS = 3300;
    private final List<String> canaries = new ArrayList<>();

    /** Canários fictícios da matriz SEC001 (lidos de PrivacyCanaries.java da extensão: a mesma lista dos testes). */
    private void loadCanaries() throws IOException {
        Path src = ext.resolve("integration-tests/src/test/java/com/nekkora/extension/it/support/PrivacyCanaries.java");
        if (!Files.exists(src)) {
            src = mainExt.resolve("integration-tests/src/test/java/com/nekkora/extension/it/support/PrivacyCanaries.java");
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bc\\(\"([^\"]+)\"").matcher(Files.readString(src, StandardCharsets.UTF_8));
        while (m.find()) {
            canaries.add(m.group(1));
        }
        for (String extra : List.of("paranhos", "tremembe", "laranjeira", "furtado", "genesio", "valdomiro", "pestana", "rufino", "cavalcanti",
                "wenceslau", "barbacena", "jandira", "pontual", "55821", "nicanor", "luciola", "assumpcao", "hipolito", "teodomiro")) {
            if (!canaries.contains(extra)) canaries.add(extra);
        }
        log("Canários verificados: " + canaries.size());
    }

    /** Normalização para a busca (sem acento, minúsculas, separadores e %20 viram espaço). */
    private static String norm(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s.replace("%20", " "), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\-_+]+", " ").replaceAll("\\s+", " ");
    }

    private List<String> leaks(String text) {
        String n = norm(text);
        List<String> out = new ArrayList<>();
        for (String c : canaries) {
            if (n.contains(norm(c))) out.add(c);
        }
        return out;
    }

    /** Domínio da automação, tela do Studio, armazenamento da extensão e logs/arquivos do host: nada privado. */
    private void privacyCheck(String label, Automation a, List<String> typed) throws Exception {
        privacyCheckDomain(label, a, typed);
        String storage = String.valueOf(harness.eval("chrome.storage.session.get(null).then(x => JSON.stringify(x))"))
                + harness.eval("chrome.storage.local.get(null).then(x => JSON.stringify(x))");
        String host = hostLogs() + hostData();
        List<String> found = new ArrayList<>(leaks(storage + host));
        for (String t : typed) if (storage.contains(t) || host.contains(t)) found.add("[digitado " + t.substring(0, 3) + "…]");
        check(label + ": armazenamento da extensão e logs/arquivos do host sem dados da página nem valores digitados" + (found.isEmpty() ? "" : " " + found), found.isEmpty());
    }

    private void privacyCheckDomain(String label, Automation a, List<String> typed) throws Exception {
        String screen = edt(() -> texts(window.frame().getContentPane()));
        String domain = domainText(a) + edt(() -> {
            StringBuilder b = new StringBuilder();
            for (LogicalSession s : a.sessions()) for (AutomationStep st : s.steps()) {
                b.append(st.element().map(Object::toString).orElse("")).append(st.capturedElement().map(Object::toString).orElse(""));
                st.context().ifPresent(c -> b.append(c));
                st.evidence().forEach(b::append);
            }
            return b.toString();
        });
        List<String> found = new ArrayList<>(leaks(domain + screen));
        for (String t : typed) if (domain.contains(t) || screen.contains(t)) found.add("[digitado " + t.substring(0, 3) + "…]");
        check(label + ": domínio da automação e tela do Studio sem dados da página nem valores digitados" + (found.isEmpty() ? "" : " " + found), found.isEmpty());
    }

    /** Resumo técnico de uma etapa (sem conteúdo da página: o que o domínio tem já passou pela política). */
    private static String describe(AutomationStep st) {
        StringBuilder b = new StringBuilder(st.actionType().name()).append(" «").append(st.name()).append("»");
        st.element().ifPresent(e -> {
            b.append(" tipo=").append(e.semanticKind()).append(" tag=").append(e.tag());
            b.append(" preferido=").append(e.preferredLocator().map(l -> l.toString()).orElse("nenhum"));
            b.append(" trajetória=").append(e.accessPath().size()).append(" passo(s)");
            b.append(" notas=").append(e.stabilityNotes());
            b.append(" política=").append(e.privacyPolicy().orElse("?"));
        });
        return b.toString();
    }

    /**
     * Diretório de dados do Native Host desta execução (NEKKORA_DATA_DIR): isola a homologação dos dados reais do usuário
     * e de arquivos de versões anteriores — a varredura de privacidade examina só o que ESTA execução produziu.
     */
    private Path hostDataDir() {
        return out.toPath().resolve("host-data").toAbsolutePath();
    }

    /** null do JSON chega como Json.NULL (ou ausente). */
    private static boolean jsonNull(Object o) {
        return o == null || o == Json.NULL;
    }

    private String serviceWorkerId() throws Exception {
        return cdp.targets().stream().filter(t -> "service_worker".equals(t.type()) && t.url().startsWith("chrome-extension://" + extId))
                .map(Cdp.Target::id).findFirst().orElse("(nenhum ativo)");
    }

    /** Arquivos do diretório de dados do host (exportações, registros de alvos, logs): conteúdo concatenado. */
    private String hostData() throws IOException {
        StringBuilder b = new StringBuilder();
        Path dir = hostDataDir(); // diretório de dados PRÓPRIO desta homologação (nunca o do usuário)
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path f : s.filter(Files::isRegularFile).toList()) {
                    try {
                        if (Files.size(f) < 50_000_000) b.append(Files.readString(f, StandardCharsets.ISO_8859_1));
                    } catch (IOException e) {
                        // em uso
                    }
                }
            }
        }
        return b.toString();
    }

    private boolean has(AutomationStep st, EvidenceType t) throws Exception {
        return edt(() -> st.evidence().stream().anyMatch(e -> e.type() == t));
    }

    // ============================================================================== apoio STUDIO-004

    private AutomationOverview overview(Automation a) {
        return window.studio().view().overview(a.id()).orElseThrow();
    }

    private List<String> lines(Automation a) throws Exception {
        return edt(() -> {
            List<String> out = new ArrayList<>();
            for (LogicalSessionView v : overview(a).sessionViews()) for (AutomationStep s : v.itemsInOrder()) out.add(v.labelOf(s));
            return out;
        });
    }

    private String domainText(Automation a) throws Exception {
        return edt(() -> {
            StringBuilder b = new StringBuilder(a.name()).append(a.description());
            for (LogicalSession s : a.sessions()) {
                b.append(s.name()).append(s.description()).append(s.notes());
                for (AutomationStep st : s.steps()) {
                    b.append(st.name()).append(st.description()).append(st.parameters().value().map(v -> v.display()).orElse(""))
                            .append(st.parameters().option().map(o -> o.label() + o.value()).orElse(""));
                    st.comments().forEach(c -> b.append(c.text()));
                    st.baseline().ifPresent(bl -> b.append(bl.name()).append(bl.description()));
                }
            }
            return b.toString();
        });
    }

    private void selectSession(Automation a, LogicalSession s) throws Exception {
        edt(() -> {
            window.studio().controller().selectSession(a.id(), s.id());
            return null;
        });
        sleep(500);
    }

    /** Botão do painel de propriedades pelo texto ou nome acessível (clicado com o mouse real). */
    private AbstractButton inspectorButton(String name) throws Exception {
        AbstractButton b = edt(() -> find(window.studio().view().inspector(), AbstractButton.class, x -> x.isShowing() && x.isEnabled()
                && (name.equals(x.getText()) || name.equals(x.getAccessibleContext().getAccessibleName()))));
        if (b == null) throw new IllegalStateException("botão do painel não encontrado: " + name);
        return b;
    }

    /** Clique REAL na linha da lista de etapas da sessão (dá o foco à lista, como o usuário). */
    private void clickRow(LogicalSession s, int index) throws Exception {
        focusStudio();
        Automation a = edt(() -> window.studio().state().activeAutomation().orElseThrow());
        LogicalSessionView v = edt(() -> overview(a).sessionView(s.id()).orElseThrow());
        Rectangle r = edt(() -> {
            v.stepList().ensureIndexIsVisible(index);
            Rectangle cell = v.stepList().getCellBounds(index, index);
            java.awt.Point p = v.stepList().getLocationOnScreen();
            return new Rectangle(p.x + cell.x, p.y + cell.y, cell.width, cell.height);
        });
        robot.mouseMove(r.x + Math.min(r.width / 2, 200), r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("linha selecionada", 10, () -> edt(() -> v.stepList().getSelectedIndex() == index && v.stepList().isFocusOwner()));
        sleep(300);
    }

    /** Clique REAL numa aba do editor de etapa. */
    private void clickTab(StepDetailsForm form, int index) throws Exception {
        Rectangle r = edt(() -> {
            Rectangle tab = form.tabs().getBoundsAt(index);
            java.awt.Point p = form.tabs().getLocationOnScreen();
            return new Rectangle(p.x + tab.x, p.y + tab.y, tab.width, tab.height);
        });
        robot.mouseMove(r.x + r.width / 2, r.y + r.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        waitFor("aba " + index, 5, () -> edt(() -> form.tabs().getSelectedIndex() == index));
        sleep(300);
    }

    /** Foco na área de trabalho do Studio (fora de campos de texto) para os atalhos da janela. */
    private void focusStudioWorkspace() throws Exception {
        edt(() -> {
            Automation a = window.studio().state().activeAutomation().orElseThrow();
            window.studio().controller().selectAutomation(a.id());
            return null;
        });
        focusStudio();
        sleep(300);
    }

    // ============================================================================== navegador

    private void connect() throws Exception {
        Cdp.Target harnessTarget = cdp.open("chrome-extension://" + extId + "/src/ui/harness.html");
        harness = cdp.page(harnessTarget);
        waitFor("harness da extensão", 20, () -> harness.eval("!!(window.nekkora && window.nekkora.call)") == Boolean.TRUE);
        labTab = ((Number) waitValue("aba do laboratório", 20,
                () -> harness.eval("chrome.tabs.query({}).then(t => (t.find(x => (x.url || '').startsWith('" + BASE + "')) || {}).id || null)"))).intValue();
        Cdp.Target panelTarget = cdp.open("chrome-extension://" + extId + "/src/ui/panel.html?tabId=" + labTab);
        panel = cdp.page(panelTarget);
        waitFor("painel", 20, () -> panel.eval("!!document.getElementById('studio-enabled')") == Boolean.TRUE);
        waitFor("painel inicializado", 20, () -> !"verificando…".equals(panel.eval("document.getElementById('host-status').textContent")));
        panel.eval("document.getElementById('studio-enabled').click()");
        waitFor("Studio conectado", 30, () -> statusText().startsWith("Navegador: conectado"));
        check("Conexão autenticada Studio ⇄ host ⇄ extensão", statusText().startsWith("Navegador: conectado (Nekkora Extension "));
        cdp.activate(labTarget);
        sleep(500);
    }

    /** Navegação de preparação do cenário (endereço digitado como o usuário faria). */
    private void navigate(String url) throws Exception {
        page.send("Page.navigate", Json.obj().put("url", url).build());
        waitFor("página " + url, 20, () -> "complete".equals(page.eval("document.readyState")) && String.valueOf(page.eval("location.href")).startsWith(url.split("\\?")[0]));
        sleep(1500);
    }

    /** Durante a gravação: o usuário digita o endereço na barra (Ctrl+L) e tecla Enter. */
    private void navigateByUser(String url) throws Exception {
        cdp.activate(labTarget);
        sleep(300);
        focusChrome(); // foco na janela do Chrome (barra de endereço)
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_L);
        typeAscii(url);
        press(KeyEvent.VK_ENTER);
        String path = url.split("\\?")[0];
        waitPage(path.substring(path.lastIndexOf('/') + 1)); // sem a consulta (?externo=…)
        sleep(2500);
    }

    private boolean waitPage(String suffix) throws Exception {
        try {
            waitFor("página " + suffix, 20, () -> String.valueOf(page.eval("location.pathname")).endsWith(suffix) && "complete".equals(page.eval("document.readyState")));
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Clique REAL do mouse no centro do elemento: rola o elemento para a vista, converte o retângulo da página em
     * coordenadas de tela (moldura do Chrome incluída) e clica com o Robot.
     */
    private void clickInPage(String elementExpr, String... frameExpr) throws Exception {
        cdp.activate(labTarget);
        focusChrome(); // a janela do Studio pode estar à frente cobrindo parte da página
        String frame = frameExpr.length == 0 ? null : frameExpr[0];
        page.eval("(" + elementExpr + ").scrollIntoView({block: 'center', inline: 'nearest'})");
        sleep(250);
        String js = "(() => { const e = " + elementExpr + "; const r = e.getBoundingClientRect();"
                + (frame == null ? " const fx = 0, fy = 0;" : " const f = (" + frame + ").getBoundingClientRect(); const fx = f.left + (" + frame + ").clientLeft, fy = f.top + (" + frame + ").clientTop;")
                + " const bx = (window.outerWidth - window.innerWidth) / 2; const by = window.outerHeight - window.innerHeight - bx;"
                + " return [window.screenX + bx + fx + r.left + r.width / 2, window.screenY + by + fy + r.top + r.height / 2]; })()";
        @SuppressWarnings("unchecked")
        List<Object> xy = (List<Object>) page.eval(js);
        int x = (int) Math.round(((Number) xy.get(0)).doubleValue());
        int y = (int) Math.round(((Number) xy.get(1)).doubleValue());
        robot.mouseMove(x, y);
        sleep(120);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(500);
    }

    /** Dá o foco à janela do Chrome com um clique na barra de endereço (fora da página; nenhuma aba é trocada). */
    private void focusChrome() {
        robot.mouseMove(CHROME_X + 400, 63);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        sleep(250);
    }

    /** Digitação REAL (teclas do sistema operacional), letras e dígitos. */
    private void typeText(String text) {
        for (char c : text.toCharArray()) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(c));
            if (Character.isUpperCase(c)) {
                robot.keyPress(KeyEvent.VK_SHIFT);
            }
            robot.keyPress(code);
            robot.keyRelease(code);
            if (Character.isUpperCase(c)) {
                robot.keyRelease(KeyEvent.VK_SHIFT);
            }
        }
        robot.waitForIdle();
        sleep(300);
    }

    /** Endereço na barra: colado da área de transferência (caracteres especiais da URL não dependem do layout do teclado). */
    private void typeAscii(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null));
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
    }

    private void prepareEnvironment() throws Exception {
        Path hostJar = ext.resolve("java-core/native-host/target/nekkora-native-host.jar");
        run(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ext.resolve("native-host/scripts/install-host.ps1").toString(), "-SkipBuild"));
        log("Native Host instalado pelo script oficial (install-host.ps1)");
        extId = run(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", hostJar.toString(),
                "com.nekkora.extension.host.tools.ExtensionId", ext.resolve("chrome-extension/manifest.json").toString())).trim();
        labServer = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar",
                ext.resolve("java-core/lab-server/target/nekkora-lab-server.jar").toString(), String.valueOf(LAB_PORT), ext.resolve("test-lab/pages").toString())
                .redirectErrorStream(true).redirectOutput(new File(out, "lab-server.log")).start();
        profile = Files.createTempDirectory("nekkora-sec001-chrome");
        // Perfil temporário sem o gerenciador de senhas do Chrome: o balão "Salvar senha?" não aparece nas evidências.
        Files.createDirectories(profile.resolve("Default"));
        Files.writeString(profile.resolve("Default").resolve("Preferences"),
                "{\"credentials_enable_service\": false, \"profile\": {\"password_manager_enabled\": false}}");
        testExtension = out.toPath().resolve("test-extension");
        copyExtension(ext.resolve("chrome-extension"), testExtension);
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        waitFor("laboratório respondendo", 20, () -> http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(S3 + "login.html")).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
    }

    /** Cópia de homologação: idêntica, exceto pelo site de teste declarado (concedido/revogado pelo controle do Chrome). */
    private static void copyExtension(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path rel = from.relativize(p);
                if (rel.startsWith("test") || rel.startsWith("node_modules")) continue;
                Path dst = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(dst);
                else Files.copy(p, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path manifest = to.resolve("manifest.json");
        Map<String, Object> m = Json.parseObject(Files.readString(manifest));
        List<Object> hosts = new ArrayList<>(Json.array(m, "host_permissions"));
        hosts.add("http://site-a.nekkora.test/*");
        m.put("host_permissions", hosts);
        Files.writeString(manifest, Json.write(m));
    }

    private void launchChrome(String url) throws Exception {
        Path chromeExe;
        try (Stream<Path> s = Files.walk(ext.resolve(".tools/chrome-for-testing"), 4)) {
            chromeExe = s.filter(p -> p.getFileName().toString().equals("chrome.exe")).findFirst().orElseThrow();
        }
        int cdpPort;
        try (java.net.ServerSocket free = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            cdpPort = free.getLocalPort();
        }
        Files.createDirectories(hostDataDir());
        ProcessBuilder chromeBuilder = new ProcessBuilder(chromeExe.toString(), "--remote-debugging-port=" + cdpPort, "--user-data-dir=" + profile,
                "--disable-features=NetworkServiceSandbox", "--load-extension=" + testExtension, "--no-first-run", "--no-default-browser-check",
                "--disable-search-engine-choice-screen", "--lang=pt-BR", "--window-position=" + CHROME_X + ",0", "--window-size=1040,1000",
                "--host-resolver-rules=MAP site-a.nekkora.test 127.0.0.1,MAP site-x.nekkora.test 127.0.0.1", "about:blank")
                .redirectErrorStream(true).redirectOutput(new File(out, "chrome.log"));
        chromeBuilder.environment().put("NEKKORA_DATA_DIR", hostDataDir().toString()); // herdado pelo Native Host
        chrome = chromeBuilder.start();
        cdp = new Cdp(cdpPort);
        waitFor("Chrome com DevTools", 30, () -> {
            try {
                return !cdp.targets().isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
        sleep(1500);
        labTarget = cdp.open(url);
        page = cdp.page(labTarget);
        waitFor("página inicial", 20, () -> "complete".equals(page.eval("document.readyState")));
        log("Chrome for Testing: " + chromeExe.getParent().getParent().getFileName());
        sleep(1500);
    }

    /** "Acesso ao site" do Chrome (chrome://extensions) — sem automatizar o diálogo de permissão. */
    private void siteAccess(String pattern, boolean granted) throws Exception {
        Cdp.Target t = cdp.open("chrome://extensions/?id=" + extId);
        try (Cdp.Page p = cdp.page(t)) {
            waitFor("chrome://extensions", 15, () -> p.eval("!!(window.chrome && chrome.developerPrivate)") == Boolean.TRUE);
            p.eval("new Promise((ok, ko) => chrome.developerPrivate." + (granted ? "addHostPermission" : "removeHostPermission") + "(" + Json.write(extId) + ", "
                    + Json.write(pattern) + ", () => chrome.runtime.lastError ? ko(chrome.runtime.lastError.message) : ok(true)))");
        }
        cdp.close(t);
        log("Acesso ao site " + pattern + " → " + (granted ? "concedido" : "revogado") + " pelo controle do Chrome");
        cdp.activate(labTarget);
    }

    private String hostLogs() throws IOException {
        StringBuilder b = new StringBuilder();
        Path dir = hostDataDir().resolve("logs");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                for (Path f : s.filter(p -> p.toString().endsWith(".log")).toList()) {
                    try {
                        b.append(Files.readString(f, StandardCharsets.ISO_8859_1));
                    } catch (IOException e) {
                        // em uso
                    }
                }
            }
        }
        return b.toString();
    }

    // ============================================================================== Studio

    private void createAutomation() throws Exception {
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova automação");
        typeIntoFocused(compat ? "Compatibilidade" : "Consulta de clientes");
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        for (String s : List.of("Autenticação", "Consulta")) {
            newSession(s);
        }
    }

    private LogicalSession newSession(String name) throws Exception {
        focusStudio();
        press(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N);
        NekkoraDialog d = waitDialog("Nova sessão");
        typeIntoFocused(name);
        press(KeyEvent.VK_ENTER);
        waitClosed(d);
        return edt(() -> {
            Automation a = window.studio().state().activeAutomation().orElseThrow();
            return a.sessions().get(a.sessions().size() - 1);
        });
    }

    private void startRecordingOn(String tabText, RecordingMode mode) throws Exception {
        focusStudio();
        click(recordButton());
        NekkoraDialog d = waitDialog("Iniciar gravação");
        waitFor("abas carregadas", 15, () -> edt(() -> radios(d).stream().anyMatch(b -> b.getText().contains(tabText) && b.isEnabled())));
        click(edt(() -> radios(d).stream().filter(b -> b.getText().contains(tabText) && b.isEnabled()).findFirst().orElseThrow()));
        NekkoraRadioButton m = edt(() -> find(d, NekkoraRadioButton.class, r -> mode.label().equals(r.getText())));
        if (m != null) {
            click(m);
        }
        clickButtonWithText(d, "Iniciar gravação");
        waitClosed(d);
        waitFor("gravando com etapas", 30, () -> stateIs(RecorderState.RECORDING) && edt(() -> window.studio().recorder().snapshot().steps().enabled()));
        sleep(800);
    }

    /** O Floating Recorder fica sobre o Studio (à esquerda), sem cobrir a página do Chrome onde o mouse vai clicar. */
    private void moveFloatingAway() throws Exception {
        edt(() -> {
            floating().setLocation(420, 470);
            return null;
        });
        sleep(400);
    }

    private void selectStep(Automation a, LogicalSession s, AutomationStep st) throws Exception {
        edt(() -> {
            window.studio().service().select(new Selection.OfStep(a.id(), s.id(), st.id()));
            return null;
        });
        sleep(600);
    }

    private List<String> names(LogicalSession s) throws Exception {
        return edt(() -> s.steps().stream().map(AutomationStep::name).toList());
    }

    private NekkoraToolbarButton recordButton() throws Exception {
        return edt(() -> find(window.frame().getContentPane(), NekkoraToolbarButton.class,
                b -> b.getAction() != null && StudioActions.RECORD.equals(b.getAction().getValue(com.nekkora.ui.action.NekkoraAction.ID))));
    }

    private String statusText() throws Exception {
        return edt(() -> window.studio().recorderPresenter().statusItem().getText());
    }

    private FloatingRecorderWindow floating() throws Exception {
        return edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof FloatingRecorderWindow f) return f;
            }
            return null;
        });
    }

    private boolean stateIs(RecorderState s) throws Exception {
        return edt(() -> window.studio().recorder().snapshot().state() == s);
    }

    private void waitState(RecorderState s) throws Exception {
        waitFor("estado " + s, 40, () -> stateIs(s));
    }

    private NekkoraDialog waitDialog(String title) throws Exception {
        long end = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < end) {
            NekkoraDialog d = edt(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof NekkoraDialog nd && nd.isShowing() && title.equals(nd.getTitle())) return nd;
                }
                return null;
            });
            if (d != null) {
                sleep(300);
                return d;
            }
            sleep(100);
        }
        throw new IllegalStateException("diálogo não apareceu: " + title);
    }

    private void waitClosed(Window d) throws Exception {
        waitFor("fechar diálogo", 15, () -> edt(() -> !d.isShowing()));
        sleep(250);
    }

    private static List<NekkoraRadioButton> radios(Container root) {
        List<NekkoraRadioButton> out = new ArrayList<>();
        collectRadios(root, out);
        return out;
    }

    private static void collectRadios(Component c, List<NekkoraRadioButton> out) {
        if (c instanceof NekkoraRadioButton r && r.getText() != null && r.getText().contains("—")) out.add(r);
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collectRadios(ch, out);
    }

    private void clickButtonWithText(Container root, String text) throws Exception {
        AbstractButton b = edt(() -> find(root, AbstractButton.class, x -> text.equals(x.getText()) && x.isShowing()));
        if (b == null) throw new IllegalStateException("botão não encontrado: " + text);
        click(b);
    }

    private void click(Component c) throws Exception {
        edt(() -> {
            if (c instanceof javax.swing.JComponent jc) jc.scrollRectToVisible(new Rectangle(0, 0, jc.getWidth(), jc.getHeight())); // painel rolável
            return null;
        });
        robot.waitForIdle();
        sleep(150);
        Rectangle r = edt(() -> new Rectangle(c.getLocationOnScreen(), c.getSize()));
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
        sleep(200);
    }

    /** Captura SÓ da janela do Studio (com o Floating Recorder por cima): nada da área de trabalho. */
    private void captureStudio(String name) throws Exception {
        robot.waitForIdle();
        sleep(400);
        Rectangle studio = edt(() -> window.frame().getBounds());
        Rectangle chromeArea = new Rectangle(CHROME_X, 0, 1040, 1000);
        Rectangle both = studio.union(chromeArea).intersection(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        save(robot.createScreenCapture(both), name);
    }

    private void captureWindow(Window w, String name) throws Exception {
        robot.waitForIdle();
        sleep(250);
        save(robot.createScreenCapture(edt(w::getBounds)), name);
    }

    private void save(BufferedImage img, String name) throws IOException {
        File f = new File(out, "SEC001-" + (compat ? "compat-" : "") + name + ".png");
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

    private static String texts(Container root) {
        StringBuilder b = new StringBuilder();
        collectTexts(root, b);
        return b.toString();
    }

    private static void collectTexts(Component c, StringBuilder b) {
        if (c instanceof javax.swing.JLabel l && l.getText() != null) b.append(l.getText()).append('\n');
        if (c instanceof AbstractButton ab && ab.getText() != null) b.append(ab.getText()).append('\n');
        if (c instanceof JTextComponent t && t.getText() != null) b.append(t.getText()).append('\n');
        if (c instanceof Container ct) for (Component ch : ct.getComponents()) collectTexts(ch, b);
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
        try (PrintWriter w = new PrintWriter(new File(out, "SEC001-homologacao" + (compat ? "-compat" : "") + "-log.txt"), StandardCharsets.UTF_8)) {
            log.forEach(w::println);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void teardown() {
        try {
            if (page != null) page.close();
            if (harness != null) harness.close();
            if (panel != null) panel.close();
            if (cdp != null) cdp.close();
        } catch (RuntimeException ignored) {
            // encerramento
        }
        if (chrome != null) chrome.destroy();
        if (labServer != null) labServer.destroy();
        if (bridge != null) bridge.close();
    }

    private static String run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IllegalStateException(String.join(" ", cmd) + " falhou:\n" + outText);
        return outText;
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
        diagnose(what);
        try {
            captureStudio("FALHA-" + what.replaceAll("[^A-Za-z0-9]+", "-"));
        } catch (Exception ignored) {
            // diagnóstico
        }
        throw new IllegalStateException("tempo esgotado aguardando: " + what + (last == null ? "" : " (" + last + ")"));
    }

    /** Estado de todas as peças quando uma espera falha (sem conteúdo digitado). */
    private void diagnose(String what) {
        try {
            log("DIAGNÓSTICO (" + what + "):");
            if (window != null) {
                log("  Recorder: " + edt(() -> {
                    var s = window.studio().recorder().snapshot();
                    return s.state() + " modo=" + s.mode() + " pendente=" + s.pendingMode() + " etapas=" + s.steps() + " motivo=" + s.reason();
                }));
                edt(() -> {
                    window.studio().state().activeAutomation().ifPresent(a -> a.sessions().forEach(x ->
                            log("  sessão " + x.name() + ": " + x.steps().stream().map(AutomationStep::name).toList())));
                    return null;
                });
            }
            if (harness != null) {
                log("  extensão: " + harness.eval("window.nekkora.call('studio.recordings', {}).then(r => JSON.stringify(r.result.map(x => [x.state, x.mode, x.reason, x.attached])))"));
            }
            if (page != null) {
                log("  página: " + page.eval("location.pathname"));
            }
            if (harness != null && labTab > 0) {
                log("  sensor: " + harness.eval("window.nekkora.call('page.diagnostics', {tabId: " + labTab + "}).then(r => JSON.stringify({ok: r.ok, rec: r.result && r.result.recorder, insp: r.result && r.result.inspectorMode, err: r.error}))"));
            }
        } catch (Exception e) {
            log("  (diagnóstico incompleto: " + e + ")");
        }
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
        if (SwingUtilities.isEventDispatchThread()) {
            return c.call();
        }
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
