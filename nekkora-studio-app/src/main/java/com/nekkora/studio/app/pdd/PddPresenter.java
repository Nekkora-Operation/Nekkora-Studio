package com.nekkora.studio.app.pdd;

import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.pdd.DocText;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.DocumentDraft.Annotation;
import com.nekkora.studio.pdd.DocumentDraft.Block;
import com.nekkora.studio.pdd.DocumentDraft.Instruction;
import com.nekkora.studio.pdd.DocumentDraft.Pending;
import com.nekkora.studio.pdd.DocumentDraft.Region;
import com.nekkora.studio.pdd.DocumentDraft.SafeCapture;
import com.nekkora.studio.pdd.DocumentDraft.Section;
import com.nekkora.studio.pdd.DraftBuilder;
import com.nekkora.studio.pdd.DraftEdits;
import com.nekkora.studio.pdd.DraftEdits.DraftException;
import com.nekkora.studio.pdd.PddExporter;
import com.nekkora.studio.pdd.PddStore;
import com.nekkora.studio.pdd.PddText;
import com.nekkora.studio.pdd.PdfRenderer;
import com.nekkora.studio.pdd.ProcessProjection;
import com.nekkora.studio.pdd.SyntheticRenderer;
import com.nekkora.studio.pdd.VisualVerifier;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.PddCaptureClient;
import com.nekkora.studio.recorder.PddConsentClient;
import com.nekkora.studio.recorder.TabAccess;
import com.nekkora.studio.recorder.RecorderController;
import com.nekkora.studio.ui.pdd.PddImageEditor;
import com.nekkora.studio.ui.pdd.PddPanel;
import com.nekkora.studio.ui.pdd.PddPanel.InstructionRow;
import com.nekkora.studio.ui.pdd.PddPanel.Item;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * STUDIO-010 — "Automação → Gerar documentação → PDD visual": rascunho do documento (nunca a automação), com
 * desfazer/refazer próprio, captura sanitizada do laboratório (gesto no Chrome + 4ª barreira), prévia que É o PDF e
 * exportação local versionada. Todo método público é chamado na EDT.
 *
 * <p>STUDIO-012 PDD-01: pede {@code nekkora-visual/2} (representação documental) a uma extensão que a declare;
 * "Capturar novamente" num bloco COM imagem traz uma CANDIDATA (só em memória, já verificada) que só substitui a anterior
 * em "Aplicar" — cancelar, falhar, trocar de aba ou mudar o processo nunca troca a imagem do bloco; recorte e marcadores
 * manuais são edições do rascunho (com desfazer/refazer); a retenção de imagens respeita o histórico da sessão.
 */
public final class PddPresenter implements PddPanel.Actions {

    /** Janela do PDD (a aplicação dá a real; os testes, uma sem tela). */
    public interface Host {
        void show();

        void hide();

        /** Pasta escolhida para exportar (vazia = cancelado). */
        Optional<Path> chooseFolder();

        static Host headless(Path folder) {
            return new Host() {
                public void show() {}

                public void hide() {}

                public Optional<Path> chooseFolder() {
                    return Optional.ofNullable(folder);
                }
            };
        }
    }

    private final WorkspaceService service;
    private final BrowserLink link;
    private final RecorderController recorder;
    private final DecisionActor actor;
    private final Clock clock;
    private final PddStore store;
    private final PdfRenderer.Fonts fonts;
    private final PddPanel panel = new PddPanel(this);
    private final java.util.function.Function<PddPanel, Host> hosts;
    private Host host;
    private AutomationId automationId;
    private DocumentDraft draft;
    private ProcessProjection projection; // projeção da qual o rascunho atual foi derivado (quando conhecida)
    private final Deque<DocumentDraft> undo = new ArrayDeque<>();
    private final Deque<DocumentDraft> redo = new ArrayDeque<>();
    private String selected = "";
    private List<PddPanel.Tab> tabs = List.of();
    /** STUDIO-013: por que nenhuma aba captura (só contagens, nunca endereço ou título) e os sites autorizados para o PDD. */
    private String tabsHint = "";
    private List<PddPanel.Site> sites = List.of();
    private PddCaptureClient capturing;
    private String bannerTitle = "";
    private String banner = "";
    private Severity bannerSeverity = Severity.INFO;
    private Path lastExport;
    private List<BufferedImage> preview = List.of();
    private Candidate candidate; // nova captura do bloco aguardando Aplicar/Cancelar (pixels só em memória)
    private final Map<String, BufferedImage> verified = new LinkedHashMap<>(); // sha-256 → imagem já verificada (prévia e editor)

    /**
     * Captura candidata: já passou pela 4ª barreira, NÃO está no armazenamento nem no rascunho. {@code processRevision}: a
     * revisão do processo quando a captura foi armada — se mudar, a candidata não pode ser aplicada.
     */
    private static final class Candidate {
        final String blockId;
        final SafeCapture capture;
        final byte[] png;
        final BufferedImage image;
        final String processRevision;
        DocumentDraft.Crop crop;

        Candidate(String blockId, SafeCapture capture, byte[] png, BufferedImage image, String processRevision) {
            this.blockId = blockId;
            this.capture = capture;
            this.png = png;
            this.image = image;
            this.processRevision = processRevision;
        }

        void wipe() {
            java.util.Arrays.fill(png, (byte) 0);
        }
    }

    public PddPresenter(WorkspaceService service, BrowserLink link, RecorderController recorder, DecisionActor actor, Clock clock, Path dataDir,
            java.util.function.Function<PddPanel, Host> hosts) {
        this.service = service;
        this.link = link;
        this.recorder = recorder;
        this.actor = actor;
        this.clock = clock;
        this.hosts = hosts;
        Path dir = dataDir;
        if (dir == null) {
            try {
                dir = Files.createTempDirectory("nekkora-pdd"); // sem diretório de dados: o rascunho dura só esta sessão
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        this.store = new PddStore(dir);
        try {
            this.fonts = PdfRenderer.Fonts.fromClasspath(PddPresenter.class.getClassLoader());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public PddPanel panel() {
        return panel;
    }

    public Optional<DocumentDraft> draft() {
        return Optional.ofNullable(draft);
    }

    public PddStore store() {
        return store;
    }

    // ------------------------------------------------------------------------------------------- abrir

    /** Abre o PDD da automação ativa: rascunho salvo (se houver) ou um novo derivado da automação. */
    public void open() {
        Automation a = service.state().activeAutomation().orElse(null);
        if (a == null || a.stepCount() == 0) {
            service.report(new com.nekkora.studio.app.workspace.Notice(com.nekkora.studio.app.workspace.Notice.Kind.WARNING, "PDD visual indisponível",
                    "Abra uma automação com etapas para gerar a documentação."));
            return;
        }
        if (host == null) host = hosts.apply(panel);
        if (!a.id().equals(automationId)) {
            endHistory(); // o desfazer da automação anterior acaba aqui: as imagens que só ele alcançava saem do disco
            automationId = a.id();
            undo.clear();
            redo.clear();
            lastExport = null;
            discardCandidate();
            verified.clear();
            ProcessProjection now = ProcessProjection.of(a);
            DocumentDraft saved = null;
            try {
                saved = store.loadDraft(a.id()).orElse(null);
            } catch (IOException | RuntimeException e) {
                say(Severity.WARNING, "Rascunho anterior não pôde ser lido", "Um rascunho novo foi derivado da automação. O arquivo anterior não foi usado.");
            }
            draft = saved != null ? saved : DraftBuilder.build(now);
            projection = saved != null && saved.processRevision().equals(now.revision()) ? now : saved == null ? now : null;
            selected = draft.sections().isEmpty() ? "" : "secao-1";
            if (saved != null) retain(); // histórico novo: sobras de uma sessão anterior saem; rascunho ilegível = nada é apagado
        }
        listTabs();
        render();
        host.show();
    }

    private void listTabs() {
        if (link.status().state() != LinkStatus.State.CONNECTED) {
            tabs = List.of();
            sites = List.of();
            tabsHint = "O Studio não está conectado ao Chrome: ligue \"Conectar ao Nekkora Studio\" no painel da Nekkora Extension.";
            return;
        }
        boolean consentAware = consentAware();
        recorder.listTabs().whenComplete((list, err) -> edt(() -> {
            List<PddPanel.Tab> out = new ArrayList<>();
            int recording = 0;
            int withoutAccess = 0;
            int blocked = 0;
            int withoutConsent = 0;
            if (list != null) {
                for (BrowserTab t : list) {
                    PddPanel.Tab tab = tab(t, consentAware);
                    if (tab == null) {
                        if (t.access() == TabAccess.BROWSER_RESTRICTED || t.access() == TabAccess.BROWSER_INTERNAL) blocked++;
                        continue;
                    }
                    switch (tab.state()) {
                        case "RECORDING" -> recording++;
                        case "CHROME_ACCESS_REQUIRED" -> withoutAccess++;
                        case "CONSENT_REQUIRED", "UPDATE_EXTENSION" -> withoutConsent++;
                        default -> { }
                    }
                    out.add(tab);
                }
            }
            // as que podem capturar primeiro; depois as que só precisam de autorização
            out.sort(java.util.Comparator.comparingInt(x -> List.of("READY", "CONSENT_REQUIRED", "UPDATE_EXTENSION", "RECORDING", "CHROME_ACCESS_REQUIRED")
                    .indexOf(x.state())));
            tabs = out;
            tabsHint = out.stream().anyMatch(PddPanel.Tab::ready) ? "" : tabsHint(list == null, recording, withoutAccess, withoutConsent, blocked);
            render();
        }));
        if (consentAware) {
            new PddConsentClient(link, Duration.ofSeconds(10)).list().whenComplete((list, err) -> edt(() -> {
                List<PddPanel.Site> out = new ArrayList<>();
                if (list != null) {
                    for (PddConsentClient.Consent c : list) {
                        out.add(new PddPanel.Site(c.origin(), c.origin() + " · " + ("REMEMBERED".equals(c.scope()) ? "lembrado" : "até fechar o navegador")
                                + " · " + ("TEST_AGENT".equals(c.actor()) ? "agente de teste" : "desenvolvedor") + (c.chromeAccess() ? "" : " · sem acesso do Chrome")));
                    }
                }
                sites = out;
                render();
            }));
        } else {
            sites = List.of();
        }
    }

    /** A extensão conectada entende o consentimento PDD por origem e a nekkora-visual/3 (0.6.0+)? */
    private boolean consentAware() {
        LinkStatus.ExtensionInfo ext = link.status().extension();
        return ext != null && PddCaptureClient.supportsV3(ext.version());
    }

    /**
     * Aba para a escolha da captura, com o estado: pode capturar, falta autorizar o PDD no site, falta acesso do Chrome,
     * está gravando, ou a extensão é anterior à 0.6.0. Páginas do navegador e endereços sem suporte não são oferecidos.
     */
    public static PddPanel.Tab tab(BrowserTab t, boolean consentAware) {
        String origin = t.origin();
        String profile = t.visualCapture();
        String label = t.label();
        if (t.recording() && (profile != null || t.access().allowed())) return new PddPanel.Tab(t.tabRef(), label + " · em gravação", origin, "RECORDING");
        if ("LAB".equals(profile)) return new PddPanel.Tab(t.tabRef(), label + " · laboratório", origin, "READY");
        if ("AUTHORIZED_SITE".equals(profile)) return new PddPanel.Tab(t.tabRef(), label + " · site autorizado para o PDD", origin, "READY");
        if ("BENCHMARK".equals(profile)) {
            // extensão anterior à 0.6.0 (lista compilada): este Studio pede a v3 e só arma com extensão nova
            return new PddPanel.Tab(t.tabRef(), label + " · benchmark (extensão antiga)", origin, "UPDATE_EXTENSION");
        }
        if (t.access() == TabAccess.AUTHORIZED) {
            if (!consentAware) return new PddPanel.Tab(t.tabRef(), label + " · captura indisponível", origin, "UPDATE_EXTENSION");
            if (t.pddConsentMissing()) return new PddPanel.Tab(t.tabRef(), label + " · captura PDD não autorizada", origin, "CONSENT_REQUIRED");
            return null;
        }
        if (t.access() == TabAccess.PERMISSION_REQUIRED) return new PddPanel.Tab(t.tabRef(), label + " · sem acesso do Chrome", null, "CHROME_ACCESS_REQUIRED");
        return null;
    }

    /** Motivo, em linguagem de uso, de nenhuma aba poder ser capturada — só contagens, nunca endereço ou título. */
    public static String tabsHint(boolean failed, int recording, int withoutAccess, int withoutConsent, int blocked) {
        if (failed) return "A extensão não informou as abas abertas. Abra a escolha de aba de novo para tentar outra vez.";
        StringBuilder b = new StringBuilder("Nenhuma aba pode ser capturada agora.");
        if (withoutConsent > 0) b.append(' ').append(withoutConsent).append(" aba(s) com acesso do Chrome, mas sem autorização de captura do PDD: escolha a aba e")
                .append(" use \"Autorizar captura PDD neste site\".");
        if (withoutAccess > 0) b.append(' ').append(withoutAccess).append(" aba(s) sem acesso do Chrome (ícone da extensão → Acesso ao site → Neste site).");
        if (recording > 0) b.append(' ').append(recording).append(" aba(s) sendo gravada(s): finalize a gravação para capturar.");
        if (blocked > 0) b.append(' ').append(blocked).append(" aba(s) bloqueada(s) pelo navegador (páginas internas ou da loja do Chrome).");
        if (withoutConsent + withoutAccess + recording + blocked == 0) b.append(" Abra o laboratório local ou um site http/https no Chrome.");
        return b.toString();
    }

    @Override
    public void refreshTabs() {
        listTabs();
        render();
    }

    /** STUDIO-013: autoriza a captura do PDD na origem mostrada (registro com quem decidiu: desenvolvedor ou agente de teste). */
    @Override
    public void authorizeSite(String tabRef, String origin, boolean remember) {
        if (!consentAware() || !PddConsentClient.validOrigin(origin)) return;
        String scope = remember ? "REMEMBERED" : "SESSION";
        new PddConsentClient(link, Duration.ofSeconds(10)).grant(tabRef, origin, scope, actor.name()).whenComplete((c, err) -> edt(() -> {
            if (err != null) {
                say(Severity.WARNING, "Autorização não concedida", failure(err));
            } else {
                say(Severity.SUCCESS, "Captura PDD autorizada", c.origin() + (remember ? " — site lembrado." : " — até fechar o navegador.")
                        + " A imagem do PDD é sintética: nenhum pixel da página é copiado. Revogue quando quiser em \"Sites autorizados para o PDD\".");
            }
            listTabs();
            render();
        }));
    }

    @Override
    public void revokeSite(String origin) {
        if (!consentAware() || !PddConsentClient.validOrigin(origin)) return;
        new PddConsentClient(link, Duration.ofSeconds(10)).revoke(origin).whenComplete((r, err) -> edt(() -> {
            if (err != null) say(Severity.WARNING, "Revogação não confirmada", failure(err));
            else say(Severity.INFO, "Captura PDD revogada", origin + ": capturas armadas ou em curso nesse site foram canceladas.");
            listTabs();
            render();
        }));
    }

    // ------------------------------------------------------------------------------------------- edições do rascunho

    private void apply(java.util.function.UnaryOperator<DocumentDraft> op) {
        try {
            DocumentDraft next = op.apply(draft);
            undo.push(draft);
            redo.clear();
            draft = next;
            save();
            say(Severity.INFO, "", "");
        } catch (DraftException e) {
            say(Severity.WARNING, "Alteração recusada", message(e.code()));
        }
        render();
    }

    @Override
    public void select(String key) {
        selected = key;
        render();
    }

    @Override
    public void undo() {
        if (undo.isEmpty()) return;
        redo.push(draft);
        draft = undo.pop();
        save();
        render();
    }

    @Override
    public void redo() {
        if (redo.isEmpty()) return;
        undo.push(draft);
        draft = redo.pop();
        save();
        render();
    }

    @Override
    public void refresh() {
        Automation a = automation();
        ProcessProjection now = ProcessProjection.of(a);
        apply(d -> DraftEdits.refresh(d, projection, now));
        projection = now;
        retain();
        if (draft.block(selected).isEmpty() && !selected.startsWith("secao-")) selected = "secao-1";
        if (candidate != null && draft.block(candidate.blockId).map(b -> !b.stepIds().equals(candidate.capture.forSteps())).orElse(true)) {
            discardCandidate();
            say(Severity.WARNING, "Nova captura descartada", "O bloco mudou com a automação; a imagem anterior (se havia) continua.");
        }
        render();
    }

    @Override
    public void saveTitle(String key, String text) {
        if (key.startsWith("secao-")) apply(d -> DraftEdits.reviseSectionTitle(d, Integer.parseInt(key.substring(6)), text, actor.name()));
        else apply(d -> DraftEdits.reviseBlockTitle(d, key, text, actor.name()));
    }

    @Override
    public void acceptTitle(String key) {
        if (key.startsWith("secao-")) {
            Section s = section(key);
            saveTitle(key, s.title().value());
        } else {
            draft.block(key).ifPresent(b -> saveTitle(key, b.title().value()));
        }
    }

    @Override
    public void saveInstruction(String blockId, int ordinal, String text) {
        apply(d -> DraftEdits.reviseInstruction(d, blockId, ordinal, text, actor.name()));
    }

    @Override
    public void acceptInstruction(String blockId, int ordinal) {
        draft.block(blockId).flatMap(b -> b.instructions().stream().filter(i -> i.ordinal() == ordinal).findFirst())
                .ifPresent(i -> saveInstruction(blockId, ordinal, i.text().value()));
    }

    @Override
    public void split(String blockId, int at) {
        ProcessProjection p = currentProjection();
        apply(d -> DraftEdits.split(d, p, blockId, at - 1));
        if (candidate != null && candidate.blockId.equals(blockId) && draft.block(blockId).isEmpty()) discardCandidate();
        retain();
    }

    @Override
    public void joinNext(String blockId) {
        ProcessProjection p = currentProjection();
        Section s = draft.sections().stream().filter(x -> x.blocks().stream().anyMatch(b -> b.blockId().equals(blockId))).findFirst().orElse(null);
        if (s == null) return;
        int i = 0;
        while (i < s.blocks().size() && !s.blocks().get(i).blockId().equals(blockId)) i++;
        if (i + 1 >= s.blocks().size()) return;
        String next = s.blocks().get(i + 1).blockId();
        apply(d -> DraftEdits.join(d, p, blockId, next));
        if (candidate != null && draft.block(candidate.blockId).isEmpty()) discardCandidate();
        retain();
    }

    @Override
    public void removeCapture(String blockId) {
        if (candidate != null && candidate.blockId.equals(blockId)) discardCandidate();
        apply(d -> DraftEdits.dropUnreferencedCaptures(DraftEdits.detachCapture(d, blockId)));
        retain();
    }

    // ------------------------------------------------------------------------------------------- captura

    /**
     * Arma a captura sanitizada para as etapas do bloco: a extensão espera o atalho no Chrome. A imagem que volta passa pela
     * 4ª barreira ({@link VisualVerifier}) antes de ir para o armazenamento e para o rascunho.
     */
    @Override
    public void capture(String blockId, String tabRef) {
        Block b = draft.block(blockId).orElse(null);
        if (b == null || capturing != null) return;
        if (!draft.processRevision().equals(currentProjection().revision())) {
            say(Severity.WARNING, "Atualize o rascunho antes", "A automação mudou desde o rascunho: use \"Atualizar com a automação\" e capture de novo.");
            render();
            return;
        }
        if (candidate != null) {
            say(Severity.WARNING, "Revise a nova captura antes", "Aplique ou cancele a nova captura pendente antes de capturar de novo.");
            render();
            return;
        }
        Automation a = automation();
        List<PddCaptureClient.StepTarget> targets = new ArrayList<>();
        for (StepId id : b.stepIds()) {
            AutomationStep st = a.step(id).orElse(null);
            if (st != null) targets.add(new PddCaptureClient.StepTarget(id, st.actionType(), st.element().orElse(null)));
        }
        boolean replace = b.capture().isPresent(); // bloco COM imagem: a nova vira candidata (revisar → Aplicar/Cancelar)
        String armedRevision = draft.processRevision();
        LinkStatus.ExtensionInfo ext = link.status().extension();
        // STUDIO-013: extensão 0.6.0+ → sempre a v3 (sintética) e a origem mostrada; 0.5.x → v2 (só laboratório); antes → v1
        boolean v3 = ext != null && PddCaptureClient.supportsV3(ext.version());
        // revisão da usuária: o Studio pede o print real com o texto de dados mascarado (v4); a v3 sintética segue no contrato
        String policy = v3 ? PddCaptureClient.POLICY_V4 : ext != null && PddCaptureClient.supportsV2(ext.version()) ? PddCaptureClient.POLICY_V2 : null;
        String origin = v3 ? tabs.stream().filter(t -> t.tabRef().equals(tabRef)).map(PddPanel.Tab::origin).findFirst().orElse(null) : null;
        PddCaptureClient c = new PddCaptureClient(link, Duration.ofSeconds(20));
        capturing = c;
        say(Severity.INFO, "Preparando a captura…", "");
        render();
        c.arm(tabRef, targets, Duration.ofSeconds(60), policy, origin).whenComplete((armed, err) -> edt(() -> {
            if (capturing != c) return;
            if (err != null) {
                finishCapture(c);
                say(Severity.WARNING, "Captura não armada", failure(err));
                render();
                return;
            }
            String sc = armed.shortcut();
            say(Severity.INFO, "Captura armada", sc.isEmpty()
                    ? "Defina um atalho para \"Capturar a tela para o PDD\" em chrome://extensions/shortcuts e pressione-o no Chrome, na aba escolhida."
                    : "No Chrome, deixe a aba escolhida no estado desta etapa e pressione " + sc + ". Nada é clicado ou enviado; "
                            + (PddCaptureClient.POLICY_V4.equals(armed.policy()) ? "a imagem é a tela real com os dados cobertos por faixas."
                                    : PddCaptureClient.POLICY_V3.equals(armed.policy()) ? "a imagem é desenhada pelo Studio, sem nenhum pixel da página."
                                    : "só rótulos de interface aparecem na imagem."));
            render();
        }));
        c.result().whenComplete((r, err) -> edt(() -> {
            if (capturing != c) return;
            finishCapture(c);
            if (err != null || r == null) {
                say(Severity.WARNING, "Imagem indisponível", "A captura não foi concluída.");
            } else if (!r.captured()) {
                say(Severity.WARNING, "Imagem indisponível", captureFailure(r.failureCode()));
            } else {
                accept(blockId, r, replace, armedRevision);
            }
            render();
        }));
    }

    @Override
    public void cancelCapture() {
        PddCaptureClient c = capturing;
        if (c == null) return;
        c.cancel();
        finishCapture(c);
        say(Severity.INFO, "Captura cancelada", "Nada foi capturado.");
        render();
    }

    private void finishCapture(PddCaptureClient c) {
        if (capturing == c) capturing = null;
        c.close();
    }

    private void accept(String blockId, PddCaptureClient.Result r, boolean replace, String armedRevision) {
        Block b = draft.block(blockId).orElse(null);
        if (b == null) {
            say(Severity.WARNING, "Imagem descartada", "O bloco mudou durante a captura.");
            return;
        }
        String policy = r.policy() == null ? VisualVerifier.POLICY : r.policy();
        List<Region> regions = new ArrayList<>();
        BufferedImage image;
        try {
            if (VisualVerifier.POLICY_V3.equals(policy)) {
                // STUDIO-013: a extensão mandou só a descrição; o Studio desenha (nenhum pixel da página) e verifica o próprio desenho
                List<Region> css = new ArrayList<>();
                for (PddCaptureClient.Region x : r.regions()) {
                    css.add(new Region(x.kind(), x.x(), x.y(), x.width(), x.height(), x.tone(), x.text(), x.size(), x.weight(), x.ink(), x.align()));
                }
                SyntheticRenderer.Rendered drawn = SyntheticRenderer.render(new SyntheticRenderer.Description(r.width(), r.height(), r.devicePixelRatio(), css));
                r = scaledResult(r, drawn);
                regions.addAll(drawn.regions());
            } else if (VisualVerifier.POLICY_V4.equals(policy)) {
                for (PddCaptureClient.Region x : r.regions()) regions.add(new Region(x.kind(), x.x(), x.y(), x.width(), x.height(), null, null, 0, null, x.ink(), null));
            } else {
                for (PddCaptureClient.Region x : r.regions()) regions.add(new Region(x.kind(), x.x(), x.y(), x.width(), x.height(), x.tone()));
            }
            image = VisualVerifier.verify(r.png(), r.sha256(), r.width(), r.height(), r.devicePixelRatio(), regions, policy); // 4ª barreira
        } catch (VisualVerifier.VisualPolicyException e) {
            say(Severity.WARNING, "Imagem recusada pelo Studio", "A imagem não passou na verificação da política visual (" + e.code() + "). Nada foi guardado."
                    + (replace ? " A imagem anterior continua." : ""));
            return;
        } catch (IllegalArgumentException e) {
            say(Severity.WARNING, "Imagem recusada pelo Studio", "As regiões da imagem não seguem a política visual. Nada foi guardado.");
            return;
        }
        if (replace) {
            if (!armedRevision.equals(draft.processRevision()) || !b.stepIds().equals(draft.block(blockId).orElseThrow().stepIds())) {
                say(Severity.WARNING, "Nova captura descartada", "O processo mudou durante a captura; a imagem anterior continua.");
                return;
            }
            SafeCapture c = safeCapture(b, r, policy, regions, r.sha256());
            discardCandidate();
            candidate = new Candidate(blockId, c, r.png(), image, armedRevision);
            selected = blockId;
            long labels = regions.stream().filter(x -> "CARRIER".equals(x.kind()) || "LABEL".equals(x.kind())).count();
            say(Severity.INFO, "Nova captura pronta para revisão", labels + " rótulo(s) de interface visíveis. A prévia já mostra a nova imagem; use \"Selecionar área\" "
                    + "se quiser recortar e então \"Aplicar\". \"Cancelar\" mantém a imagem anterior.");
            return;
        }
        String sha;
        try {
            sha = store.putCapture(draft.automationId(), r.png());
        } catch (IOException e) {
            say(Severity.WARNING, "Imagem não guardada", "Não foi possível gravar a imagem sanitizada no diretório de dados.");
            return;
        }
        List<Annotation> anns = new ArrayList<>();
        int placed = 0;
        for (PddCaptureClient.Marker m : r.markers()) {
            if (!b.stepIds().contains(m.step())) continue;
            boolean ok = "PLACED".equals(m.status()) && m.width() > 0 && m.height() > 0;
            anns.add(ok ? new Annotation(m.step(), Annotation.Status.PLACED, m.x(), m.y(), m.width(), m.height(), "")
                    : new Annotation(m.step(), Annotation.Status.OMITTED, 0, 0, 0, 0, code(m.reason())));
            if (ok) placed++;
        }
        List<String> limits = r.limitations().stream().filter(l -> l.matches("[A-Z][A-Z0-9_]{0,59}")).toList();
        SafeCapture c = new SafeCapture(sha, r.width(), r.height(), r.devicePixelRatio(), r.observedAt(), 0, policy, b.screenKey(), b.stepIds(), anns,
                limits, VisualVerifier.POLICY.equals(policy) ? regions : List.of());
        if (!VisualVerifier.POLICY.equals(policy)) c = safeCapture(b, r, policy, regions, sha); // v2: referência própria (rascunho 1.1.0)
        SafeCapture attached = c;
        int finalPlaced = placed;
        boolean upgrade = !draft.needsV11();
        boolean upgrade12 = !draft.needsV12();
        apply(d -> DraftEdits.dropUnreferencedCaptures(DraftEdits.attachCapture(d, blockId, attached)));
        retain();
        if (upgrade12 && draft.needsV12()) {
            say(Severity.SUCCESS, "Imagem associada · rascunho 1.2.0", "O rascunho do documento passou a usar o formato 1.2.0 (representação sintética). "
                    + "A automação não muda.");
            return;
        }
        if (upgrade && draft.needsV11()) {
            say(Severity.SUCCESS, "Imagem associada · rascunho 1.1.0", "O rascunho do documento passou a usar o formato 1.1.0 (representação documental). "
                    + "A automação não muda.");
            return;
        }
        long labels = regions.stream().filter(x -> "CARRIER".equals(x.kind()) || "LABEL".equals(x.kind())).count();
        say(Severity.SUCCESS, "Imagem sanitizada associada", labels + " rótulo(s) de interface visíveis; " + finalPlaced + " de " + b.stepIds().size()
                + " marcador(es) posicionados. " + (VisualVerifier.POLICY_V4.equals(policy)
                        ? "Print real da tela: campos, textos de dados, tabelas e frames cobertos por faixas; revise antes de compartilhar."
                        : VisualVerifier.POLICY_V3.equals(policy)
                        ? "Representação sintética: textos que não são rótulos de interface viraram barras e os campos ficam vazios."
                        : "Campos e o restante da tela ficam ocultos."));
    }

    /** v3: o resultado passa a apontar para o desenho do Studio (PNG, sha, dimensões e escala); marcadores normalizados não mudam. */
    private static PddCaptureClient.Result scaledResult(PddCaptureClient.Result r, SyntheticRenderer.Rendered d) {
        return new PddCaptureClient.Result(r.captureId(), true, null, d.png(), d.sha256(), d.width(), d.height(), d.scale(), r.observedAt(), r.regions(),
                r.markers(), r.limitations(), r.policy());
    }

    /** Metadados da captura: marcadores só das etapas do bloco; v2 com referência própria. */
    private SafeCapture safeCapture(Block b, PddCaptureClient.Result r, String policy, List<Region> regions, String sha) {
        List<Annotation> anns = new ArrayList<>();
        for (PddCaptureClient.Marker m : r.markers()) {
            if (!b.stepIds().contains(m.step())) continue;
            boolean ok = "PLACED".equals(m.status()) && m.width() > 0 && m.height() > 0;
            anns.add(ok ? new Annotation(m.step(), Annotation.Status.PLACED, m.x(), m.y(), m.width(), m.height(), "")
                    : new Annotation(m.step(), Annotation.Status.OMITTED, 0, 0, 0, 0, code(m.reason())));
        }
        List<String> limits = r.limitations().stream().filter(l -> l.matches("[A-Z][A-Z0-9_]{0,59}")).toList();
        SafeCapture c = new SafeCapture(null, sha, r.width(), r.height(), r.devicePixelRatio(), r.observedAt(), 0, policy, b.screenKey(), b.stepIds(), anns,
                limits, regions, null);
        return VisualVerifier.POLICY.equals(policy) ? c : withRef(c, DraftEdits.freshRef(c, b.blockId()));
    }

    private static SafeCapture withRef(SafeCapture c, String ref) {
        return new SafeCapture(ref, c.sha256(), c.width(), c.height(), c.devicePixelRatio(), c.observedAt(), c.pageEpoch(), c.policy(), c.screenKey(),
                c.forSteps(), c.annotations(), c.limitations(), c.regions(), c.crop());
    }

    // ------------------------------------------------------------------------------------------- candidata, recorte, marcadores

    @Override
    public void applyCandidate() {
        Candidate k = candidate;
        if (k == null) return;
        Block b = draft.block(k.blockId).orElse(null);
        if (b == null || !b.stepIds().equals(k.capture.forSteps()) || !k.processRevision.equals(currentProjection().revision())
                || !k.processRevision.equals(draft.processRevision())) {
            discardCandidate();
            say(Severity.WARNING, "Nova captura descartada", "O processo ou o bloco mudou desde a captura; a imagem anterior continua.");
            render();
            return;
        }
        try {
            String sha = store.putCapture(draft.automationId(), k.png);
            if (!sha.equals(k.capture.sha256())) throw new IOException("conteúdo");
        } catch (IOException e) {
            say(Severity.WARNING, "Imagem não guardada", "Não foi possível gravar a imagem sanitizada; a imagem anterior continua.");
            render();
            return;
        }
        SafeCapture c = k.capture.withCrop(k.crop);
        boolean upgrade = !draft.needsV11();
        apply(d -> DraftEdits.dropUnreferencedCaptures(DraftEdits.attachCapture(d, k.blockId, c)));
        verified.put(c.sha256(), k.image);
        discardCandidate();
        retain();
        say(Severity.SUCCESS, "Imagem substituída", "A nova captura passou a ilustrar este bloco. Desfazer volta à anterior."
                + (upgrade && draft.needsV11() ? " O rascunho do documento passou ao formato 1.1.0; a automação não muda." : ""));
        render();
    }

    @Override
    public void cancelCandidate() {
        if (candidate == null) return;
        discardCandidate();
        say(Severity.INFO, "Nova captura descartada", "Nada mudou: a imagem anterior continua no bloco.");
        render();
    }

    private void discardCandidate() {
        if (candidate != null) candidate.wipe();
        candidate = null;
    }

    @Override
    public void setCrop(String blockId, int x, int y, int width, int height) {
        DocumentDraft.Crop crop;
        try {
            crop = new DocumentDraft.Crop(x, y, width, height, actor.name());
        } catch (IllegalArgumentException e) {
            say(Severity.WARNING, "Recorte recusado", "A área precisa ter pelo menos " + DocumentDraft.Crop.MIN_WIDTH + "×" + DocumentDraft.Crop.MIN_HEIGHT
                    + " px da imagem.");
            render();
            return;
        }
        if (candidate != null && candidate.blockId.equals(blockId)) {
            SafeCapture c = candidate.capture;
            if (x + width > c.width() || y + height > c.height()) {
                say(Severity.WARNING, "Recorte recusado", message("CROP_OUT_OF_IMAGE"));
            } else {
                candidate.crop = crop;
                say(Severity.INFO, "Recorte da nova captura ajustado", "Revise a prévia e use \"Aplicar\".");
            }
            render();
            return;
        }
        apply(d -> DraftEdits.setCrop(d, blockId, crop));
    }

    @Override
    public void autoCrop(String blockId) {
        if (candidate != null && candidate.blockId.equals(blockId)) {
            candidate.crop = null;
            render();
            return;
        }
        apply(d -> DraftEdits.setCrop(d, blockId, null));
    }

    @Override
    public void placeMarker(String blockId, int ordinal, double x, double y, double width, double height) {
        StepId step = stepOf(blockId, ordinal);
        if (step == null) return;
        apply(d -> DraftEdits.placeManualMarker(d, blockId, step, x, y, width, height, actor.name()));
    }

    @Override
    public void removeMarker(String blockId, int ordinal) {
        StepId step = stepOf(blockId, ordinal);
        if (step == null) return;
        apply(d -> DraftEdits.removeManualMarker(d, blockId, step));
    }

    private StepId stepOf(String blockId, int ordinal) {
        return draft.block(blockId).flatMap(b -> b.instructions().stream().filter(i -> i.ordinal() == ordinal).findFirst()).map(Instruction::stepId)
                .orElse(null);
    }

    // ------------------------------------------------------------------------------------------- exportar

    @Override
    public void export() {
        ProcessProjection now = currentProjection();
        if (!draft.pending(now).stream().noneMatch(Pending::blocking)) {
            say(Severity.WARNING, "Revise antes de exportar", "Há pendências que impedem a exportação (veja a lista em cada item).");
            render();
            return;
        }
        Path dir = host.chooseFolder().orElse(null);
        if (dir == null) return;
        try {
            PddExporter.Exported e = PddExporter.export(draft, now, store, dir, fonts, clock, null);
            lastExport = dir;
            say(Severity.SUCCESS, "PDF exportado", e.pdf().getFileName() + " · " + e.pages() + " página(s). Versões anteriores ficam como estão.");
        } catch (PddExporter.ExportException e) {
            say(Severity.WARNING, "Exportação recusada", message(e.code()));
        } catch (IOException | RuntimeException e) {
            say(Severity.WARNING, "Exportação não concluída", "Nenhum documento parcial foi deixado com nome de versão.");
        }
        render();
    }

    // ------------------------------------------------------------------------------------------- apresentação

    private void render() {
        if (draft == null) return;
        ProcessProjection now = currentProjection();
        List<Pending> pending = draft.pending(now);
        List<Item> items = new ArrayList<>();
        for (Section s : draft.sections()) {
            items.add(new Item("secao-" + s.number(), s.number() + ". " + label(s.title()), s.blocks().size() + " bloco(s)", true));
            for (Block b : s.blocks()) {
                long p = pending.stream().filter(x -> x.place().equals(b.blockId())).count();
                items.add(new Item(b.blockId(), "    " + label(b.title()), b.stepIds().size() + " etapa(s) · " + (b.capture().isPresent() ? "com imagem" : "sem imagem")
                        + (p > 0 ? " · " + p + " pendência(s)" : ""), false));
            }
        }
        boolean blocking = pending.stream().anyMatch(Pending::blocking);
        String title = "";
        String titleState = "";
        boolean attention = false;
        boolean block = false;
        boolean hasImage = false;
        boolean canJoin = false;
        List<InstructionRow> rows = new ArrayList<>();
        List<String> itemPending = new ArrayList<>();
        if (selected.startsWith("secao-")) {
            Section s = section(selected);
            if (s != null) {
                title = s.title().value();
                titleState = state(s.title());
                attention = !s.title().exportable();
            }
        } else {
            Block b = draft.block(selected).orElse(null);
            if (b != null) {
                block = true;
                title = b.title().value();
                titleState = state(b.title());
                attention = !b.title().exportable();
                hasImage = b.capture().isPresent();
                for (Instruction i : b.instructions()) rows.add(new InstructionRow(i.ordinal(), i.text().value(), state(i.text()), !i.text().exportable()));
                Section s = draft.sections().stream().filter(x -> x.blocks().contains(b)).findFirst().orElse(null);
                canJoin = s != null && s.blocks().indexOf(b) < s.blocks().size() - 1;
            }
        }
        for (Pending p : pending) {
            if (p.place().equals(selected) || p.place().isEmpty()) itemPending.add(pendingText(p));
        }
        String bTitle = bannerTitle;
        String bText = banner;
        Severity bSev = bannerSeverity;
        // com uma captura armada, a instrução do gesto (Alt+Shift+K) não pode ser coberta por um aviso de estado
        boolean transientMessage = capturing == null && (bText.isEmpty() && bTitle.isEmpty() || bSev == Severity.INFO || bSev == Severity.SUCCESS);
        if (transientMessage) { // avisos de ESTADO (automação mudou, PDF desatualizado) têm prioridade sobre mensagens passageiras
            if (!draft.processRevision().equals(now.revision())) {
                bTitle = "A automação mudou";
                bText = "O rascunho foi derivado de outra versão do processo. Use \"Atualizar com a automação\" antes de exportar.";
                bSev = Severity.WARNING;
            } else if (lastExport != null) {
                try {
                    Optional<PddExporter.Latest> l = PddExporter.latest(lastExport, now);
                    if (l.isPresent() && l.get().outdated()) {
                        bTitle = "PDF desatualizado";
                        bText = l.get().pdf().getFileName() + " foi exportado antes da última alteração da automação.";
                        bSev = Severity.WARNING;
                    }
                } catch (IOException e) {
                    // pasta indisponível: sem aviso
                }
            }
        }
        preview = renderPreview();
        PddPanel.ImageView image = null;
        List<PddPanel.MarkerSlot> slots = new ArrayList<>();
        Block sel = draft.block(selected).orElse(null);
        if (sel != null) {
            boolean cand = candidate != null && candidate.blockId.equals(sel.blockId());
            SafeCapture c = cand ? candidate.capture.withCrop(candidate.crop) : sel.capture().map(draft.captures()::get).orElse(null);
            BufferedImage img = cand ? candidate.image : c == null ? null : verified.get(c.sha256());
            if (c != null && img != null) {
                List<PddImageEditor.Mark> marks = new ArrayList<>();
                for (Instruction i : sel.instructions()) {
                    Annotation a = c.annotations().stream().filter(z -> z.stepId().equals(i.stepId()) && z.status() == Annotation.Status.PLACED && !z.manual())
                            .findFirst().orElse(c.annotations().stream().filter(z -> z.stepId().equals(i.stepId()) && z.manual()).findFirst().orElse(null));
                    if (a != null) {
                        marks.add(new PddImageEditor.Mark(i.ordinal(), new java.awt.Rectangle((int) Math.round(a.x() * c.width()), (int) Math.round(a.y() * c.height()),
                                (int) Math.round(a.width() * c.width()), (int) Math.round(a.height() * c.height())), a.manual()));
                    }
                    boolean auto = a != null && !a.manual();
                    slots.add(new PddPanel.MarkerSlot(i.ordinal(), auto ? "automático" : a != null ? "manual" : "sem marcador", !auto, a != null && a.manual()));
                }
                java.awt.Rectangle crop = c.crop() == null ? null : new java.awt.Rectangle(c.crop().x(), c.crop().y(), c.crop().width(), c.crop().height());
                image = new PddPanel.ImageView(img, crop, marks, cand, c.policy());
            }
        }
        long nBlocking = pending.stream().filter(Pending::blocking).count();
        // com uma nova captura em revisão a exportação fica bloqueada: o selo diz isso (antes dizia "Pronto para exportar")
        String status = blocking ? nBlocking + " pendência(s) para revisar" : candidate != null ? "Nova captura em revisão" : "Pronto para exportar";
        panel.show(new PddPanel.Model(items, selected, title, titleState, attention, block, rows, itemPending, hasImage, canJoin, tabs, capturing != null,
                bTitle, bText, bSev, status, blocking || candidate != null ? Status.WARNING : Status.SUCCESS,
                preview, !undo.isEmpty(), !redo.isEmpty(), !blocking && capturing == null && candidate == null, image, slots, tabsHint, sites));
    }

    /** Prévia: o próprio PDF (modo prévia) rasterizado — com a candidata no lugar da imagem do bloco, se houver. */
    private List<BufferedImage> renderPreview() {
        try {
            Map<String, BufferedImage> images = new LinkedHashMap<>();
            for (Block b : draft.blocks()) {
                if (b.capture().isEmpty()) continue;
                SafeCapture c = draft.captures().get(b.captureSha());
                if (c == null) continue;
                byte[] png = store.capture(draft.automationId(), c.sha256()).orElse(null);
                if (png == null) {
                    verified.remove(c.sha256());
                    continue;
                }
                try {
                    // sempre REVERIFICADA a partir do disco (o arquivo pode ter sido trocado); o editor usa a mesma imagem
                    BufferedImage img = VisualVerifier.verify(png, c.sha256(), c.width(), c.height(), c.devicePixelRatio(), c.regions(), c.policy());
                    images.put(c.sha256(), img);
                    verified.put(c.sha256(), img);
                } catch (VisualVerifier.VisualPolicyException e) {
                    verified.remove(c.sha256()); // imagem recusada: a prévia mostra "Imagem indisponível"
                }
            }
            DocumentDraft shown = draft;
            if (candidate != null && draft.block(candidate.blockId).isPresent()) {
                try {
                    shown = DraftEdits.attachCapture(draft, candidate.blockId, candidate.capture.withCrop(candidate.crop));
                    images.put(candidate.capture.sha256(), candidate.image);
                } catch (DraftException e) {
                    shown = draft;
                }
            }
            byte[] pdf = PdfRenderer.render(shown, images, fonts, new PdfRenderer.Options("prévia", LocalDate.now(clock.withZone(ZoneOffset.UTC)), true));
            List<BufferedImage> pages = new ArrayList<>();
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                PDFRenderer r = new PDFRenderer(doc);
                for (int i = 0; i < doc.getNumberOfPages(); i++) pages.add(r.renderImageWithDPI(i, 72));
            }
            return pages;
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private void save() {
        try {
            store.saveDraft(draft);
        } catch (IOException e) {
            say(Severity.WARNING, "Rascunho não salvo", "Não foi possível gravar o rascunho no diretório de dados.");
        }
    }

    /** Retenção: só sai do disco a imagem que nem o rascunho nem o desfazer/refazer desta sessão alcançam (achado A1). */
    private void retain() {
        try {
            java.util.Set<String> history = new java.util.HashSet<>();
            undo.forEach(d -> history.addAll(d.referencedImages()));
            redo.forEach(d -> history.addAll(d.referencedImages()));
            store.retain(draft, history);
        } catch (IOException e) {
            // retenção é melhor esforço
        }
    }

    /** Fim do desfazer/refazer desta sessão do PDD: só ficam no disco as imagens que o rascunho referencia. */
    private void endHistory() {
        undo.clear();
        redo.clear();
        if (draft != null) retain();
    }

    private void say(Severity s, String title, String text) {
        bannerSeverity = s;
        bannerTitle = title;
        banner = text;
    }

    private Automation automation() {
        return service.state().catalog().require(automationId);
    }

    private ProcessProjection currentProjection() {
        return ProcessProjection.of(automation());
    }

    private Section section(String key) {
        int n = Integer.parseInt(key.substring(6));
        return draft.sections().stream().filter(s -> s.number() == n).findFirst().orElse(null);
    }

    private static String label(DocText t) {
        return t.exportable() ? t.value() : PddText.problems(t.value()).isEmpty() ? t.value() + " (revisar)" : "Texto recusado pela política";
    }

    private static String state(DocText t) {
        if (!PddText.problems(t.value()).isEmpty()) return "Fora da política: corrija";
        return switch (t.state()) {
            case DERIVED -> "Derivado do processo";
            case NEEDS_REVIEW -> "Revisar";
            case REVIEWED -> "Revisado (" + ("TEST_AGENT".equals(t.reviewer()) ? "agente de teste" : "desenvolvedor") + ")";
        };
    }

    private static String pendingText(Pending p) {
        return switch (p.code()) {
            case "PROCESS_CHANGED" -> "A automação mudou: atualize o rascunho.";
            case "SECTION_TITLE_REVIEW" -> "Título da seção para revisar.";
            case "TITLE_REVIEW" -> "Título do bloco para revisar.";
            case "INSTRUCTION_REVIEW" -> "Instrução " + p.instruction() + " para revisar.";
            case "TEXT_POLICY" -> "Texto fora da política (valores, dados pessoais, seletores, variáveis, endereços)" + (p.instruction() > 0 ? ": instrução "
                    + p.instruction() : "") + ".";
            case "CAPTURE_MISMATCH" -> "A imagem não corresponde a este bloco: capture de novo.";
            case "CAPTURE_MISSING" -> "Sem imagem: o PDF dirá \"Imagem indisponível\".";
            case "MARKER_UNAVAILABLE" -> "Instrução " + p.instruction() + " sem marcador na imagem (alvo não comprovado). Você pode posicionar um marcador manual.";
            case "MARKER_OUTSIDE_CROP" -> "Instrução " + p.instruction() + ": o marcador fica fora do recorte escolhido.";
            default -> p.code();
        };
    }

    static String message(String code) {
        if (code.startsWith("TEXT_POLICY")) return "O texto contém algo que não pode ir ao documento (números, dados pessoais, endereços, seletores ou variáveis).";
        return switch (code) {
            case "TEXT_EMPTY" -> "O texto não pode ficar vazio.";
            case "JOIN_CROSSES_SCREEN" -> "Estes blocos são de telas diferentes: não podem ser juntados.";
            case "BLOCKS_NOT_ADJACENT" -> "Só blocos vizinhos da mesma seção podem ser juntados.";
            case "SPLIT_POSITION_INVALID" -> "Escolha uma instrução depois da primeira.";
            case "CAPTURE_DOES_NOT_MATCH_BLOCK" -> "A imagem não corresponde a este bloco.";
            case "CROP_OUT_OF_IMAGE" -> "O recorte precisa ficar dentro da imagem.";
            case "MARKER_ALREADY_PLACED" -> "Esta instrução já tem marcador automático (comprovado na captura); ele não é substituído.";
            case "MARKER_OUTSIDE_CROP" -> "O marcador precisa ficar dentro do recorte escolhido.";
            case "MARKER_INVALID" -> "O marcador precisa ficar dentro da imagem.";
            case "MARKER_NOT_FOUND" -> "Esta instrução não tem marcador manual.";
            case "CAPTURE_MISSING" -> "Este bloco ainda não tem imagem.";
            case "DRAFT_HAS_BLOCKING_PENDING" -> "Há pendências que impedem a exportação.";
            case "CAPTURE_FILE_MISSING" -> "Uma imagem do rascunho não está mais no diretório de dados: capture de novo.";
            case "PDF_TEXT_POLICY", "PDF_METADATA_POLICY" -> "A verificação final do PDF falhou: nada foi salvo.";
            case "CANCELLED" -> "Exportação cancelada: nada foi salvo.";
            default -> code.startsWith("CAPTURE_REJECTED") ? "Uma imagem não passou na verificação da política visual: capture de novo." : "Operação recusada (" + code + ").";
        };
    }

    static String captureFailure(String code) {
        return switch (code == null ? "" : code) {
            case "VISUAL_CAPTURE_NOT_ALLOWED" -> "Esta origem não está autorizada para a captura do PDD (laboratório local, ou site com acesso do Chrome e autorização de"
                    + " captura PDD).";
            case "PDD_CONSENT_REVOKED" -> "A autorização de captura do PDD neste site foi revogada: nada foi capturado.";
            case "PERMISSION_REVOKED" -> "O acesso do Chrome a este site foi revogado: nada foi capturado.";
            case "ORIGIN_CHANGED" -> "A aba mudou de site (login, redirecionamento ou navegação) durante a captura: nada foi capturado.";
            case "LABEL_NOT_INTERFACE_TEXT", "REGION_FIELD_UNKNOWN", "TEXT_NOT_QUANTIZED" -> "A descrição da tela não passou na política visual: nada foi guardado.";
            case "CARRIER_OVERLAPS_CONTENT" -> "Um rótulo encosta em imagem ou quadro incorporado: a captura foi recusada por segurança.";
            case "PAGE_CHANGED_DURING_CAPTURE" -> "A página mudou durante a captura: deixe-a parada e tente de novo.";
            case "PAGE_CHANGED", "TAB_CLOSED" -> "A aba navegou ou foi fechada antes do atalho.";
            case "ARM_EXPIRED" -> "O atalho não foi pressionado a tempo.";
            case "TAB_MISMATCH" -> "O atalho foi pressionado em outra aba.";
            case "ACTIVE_TAB_CHANGED" -> "A aba ativa mudou durante a captura: a imagem foi descartada. Fique na aba do laboratório e tente de novo.";
            case "CANCELLED" -> "Captura cancelada.";
            case "LINK_LOST" -> "A conexão com o navegador caiu.";
            case "SANITIZATION_CHECK_FAILED" -> "A imagem não passou na verificação da política visual: nada foi guardado.";
            case "CAPTURE_DENIED" -> "O Chrome não autorizou a captura (o atalho precisa ser pressionado na aba).";
            default -> "A captura não pôde ser feita com segurança (" + code + "). Nenhuma imagem foi usada.";
        };
    }

    private static String failure(Throwable err) {
        Throwable t = err instanceof java.util.concurrent.CompletionException && err.getCause() != null ? err.getCause() : err;
        if (t instanceof com.nekkora.studio.bridge.BridgeCommandException b) {
            String why = b.reason() == null ? "" : switch (b.reason()) {
                // STUDIO-013: motivos distintos para "não pode capturar aqui"
                case "PDD_CONSENT_REQUIRED" -> "Esta página permite gravação, mas a captura de PDD ainda não está autorizada: use \"Autorizar captura PDD neste site\".";
                case "PERMISSION_REQUIRED" -> "Sem acesso do Chrome a este site: conceda pelo ícone da Nekkora Extension (Acesso ao site → Neste site).";
                case "BROWSER_INTERNAL", "BROWSER_RESTRICTED" -> "Página bloqueada pelo navegador: o Chrome não permite extensões nela.";
                case "UNSUPPORTED_SCHEME", "INVALID_URL", "URL_UNAVAILABLE" -> "Só páginas http:// e https:// podem ser capturadas.";
                case "ORIGIN_CHANGED" -> "A aba mudou de site desde a escolha: confira a aba e tente de novo.";
                case "POLICY_REQUIRES_V3" -> "Neste site a captura exige a política sintética (nekkora-visual/3): atualize a Nekkora Extension e o Studio.";
                case "RECORDING_ACTIVE" -> "Há uma gravação nesta aba: finalize a gravação para capturar.";
                case "CAPTURE_BUSY" -> "Já há uma captura armada ou em andamento.";
                case "NOT_REQUIRED" -> "O laboratório local não precisa de autorização.";
                default -> "";
            };
            return why.isEmpty() ? captureFailure(b.code().name()) : why;
        }
        return "O navegador não respondeu.";
    }

    private static String code(String c) {
        return c != null && c.matches("[A-Z][A-Z0-9_]{0,59}") ? c : "";
    }

    private static void edt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    public void dispose() {
        if (capturing != null) capturing.close();
        endHistory();
        if (host != null) host.hide();
    }

    /** Usado pelo roteiro: força a releitura das abas. */
    public void reloadTabs() {
        listTabs();
    }
}
