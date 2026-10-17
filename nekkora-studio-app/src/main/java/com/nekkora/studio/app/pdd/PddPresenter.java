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
import com.nekkora.studio.pdd.VisualVerifier;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.PddCaptureClient;
import com.nekkora.studio.recorder.RecorderController;
import com.nekkora.studio.recorder.TabAccess;
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
    private PddCaptureClient capturing;
    private String bannerTitle = "";
    private String banner = "";
    private Severity bannerSeverity = Severity.INFO;
    private Path lastExport;
    private List<BufferedImage> preview = List.of();

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
            automationId = a.id();
            undo.clear();
            redo.clear();
            lastExport = null;
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
        }
        listTabs();
        render();
        host.show();
    }

    private void listTabs() {
        if (link.status().state() != LinkStatus.State.CONNECTED) {
            tabs = List.of();
            return;
        }
        recorder.listTabs().whenComplete((list, err) -> edt(() -> {
            List<PddPanel.Tab> out = new ArrayList<>();
            if (list != null) {
                for (BrowserTab t : list) if (t.access() == TabAccess.LAB && !t.recording()) out.add(new PddPanel.Tab(t.tabRef(), t.label() + " · laboratório"));
            }
            tabs = out;
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
        retain();
    }

    @Override
    public void removeCapture(String blockId) {
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
        Automation a = automation();
        List<PddCaptureClient.StepTarget> targets = new ArrayList<>();
        for (StepId id : b.stepIds()) {
            AutomationStep st = a.step(id).orElse(null);
            if (st != null) targets.add(new PddCaptureClient.StepTarget(id, st.actionType(), st.element().orElse(null)));
        }
        PddCaptureClient c = new PddCaptureClient(link, Duration.ofSeconds(20));
        capturing = c;
        say(Severity.INFO, "Preparando a captura…", "");
        render();
        c.arm(tabRef, targets, Duration.ofSeconds(60)).whenComplete((armed, err) -> edt(() -> {
            if (capturing != c) return;
            if (err != null) {
                finishCapture(c);
                say(Severity.WARNING, "Captura não armada", failure(err));
                render();
                return;
            }
            String sc = armed.shortcut();
            say(Severity.INFO, "Captura armada", sc.isEmpty()
                    ? "Defina um atalho para \"Capturar a tela para o PDD\" em chrome://extensions/shortcuts e pressione-o no Chrome, na aba do laboratório."
                    : "No Chrome, deixe a aba do laboratório no estado desta etapa e pressione " + sc + ". Nada é clicado ou enviado; só rótulos de interface "
                            + "aparecem na imagem.");
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
                accept(blockId, r);
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

    private void accept(String blockId, PddCaptureClient.Result r) {
        Block b = draft.block(blockId).orElse(null);
        if (b == null) {
            say(Severity.WARNING, "Imagem descartada", "O bloco mudou durante a captura.");
            return;
        }
        List<Region> regions = new ArrayList<>();
        for (PddCaptureClient.Region x : r.regions()) regions.add(new Region(x.kind(), x.x(), x.y(), x.width(), x.height()));
        try {
            VisualVerifier.verify(r.png(), r.sha256(), r.width(), r.height(), r.devicePixelRatio(), regions); // 4ª barreira
        } catch (VisualVerifier.VisualPolicyException e) {
            say(Severity.WARNING, "Imagem recusada pelo Studio", "A imagem não passou na verificação da política visual (" + e.code() + "). Nada foi guardado.");
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
        SafeCapture c = new SafeCapture(sha, r.width(), r.height(), r.devicePixelRatio(), r.observedAt(), 0, VisualVerifier.POLICY, b.screenKey(),
                b.stepIds(), anns, limits, regions);
        int finalPlaced = placed;
        apply(d -> DraftEdits.dropUnreferencedCaptures(DraftEdits.attachCapture(d, blockId, c)));
        retain();
        long labels = regions.stream().filter(x -> "CARRIER".equals(x.kind())).count();
        say(Severity.SUCCESS, "Imagem sanitizada associada", labels + " rótulo(s) de interface visíveis; " + finalPlaced + " de " + b.stepIds().size()
                + " marcador(es) posicionados. Campos e o restante da tela ficam ocultos.");
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
        boolean transientMessage = bText.isEmpty() && bTitle.isEmpty() || bSev == Severity.INFO || bSev == Severity.SUCCESS;
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
        long nBlocking = pending.stream().filter(Pending::blocking).count();
        panel.show(new PddPanel.Model(items, selected, title, titleState, attention, block, rows, itemPending, hasImage, canJoin, tabs, capturing != null,
                bTitle, bText, bSev, blocking ? nBlocking + " pendência(s) para revisar" : "Pronto para exportar", blocking ? Status.WARNING : Status.SUCCESS,
                preview, !undo.isEmpty(), !redo.isEmpty(), !blocking && capturing == null));
    }

    /** Prévia: o próprio PDF (modo prévia) rasterizado. */
    private List<BufferedImage> renderPreview() {
        try {
            Map<String, BufferedImage> images = new LinkedHashMap<>();
            for (Block b : draft.blocks()) {
                if (b.capture().isEmpty()) continue;
                SafeCapture c = draft.captures().get(b.captureSha());
                byte[] png = store.capture(draft.automationId(), c.sha256()).orElse(null);
                if (png == null) continue;
                try {
                    images.put(c.sha256(), VisualVerifier.verify(png, c.sha256(), c.width(), c.height(), c.devicePixelRatio(), c.regions()));
                } catch (VisualVerifier.VisualPolicyException e) {
                    // imagem recusada: a prévia mostra "Imagem indisponível"
                }
            }
            byte[] pdf = PdfRenderer.render(draft, images, fonts, new PdfRenderer.Options("prévia", LocalDate.now(clock.withZone(ZoneOffset.UTC)), true));
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

    private void retain() {
        try {
            store.retain(draft);
        } catch (IOException e) {
            // retenção é melhor esforço
        }
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
            case "MARKER_UNAVAILABLE" -> "Instrução " + p.instruction() + " sem marcador na imagem (alvo não comprovado).";
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
            case "DRAFT_HAS_BLOCKING_PENDING" -> "Há pendências que impedem a exportação.";
            case "CAPTURE_FILE_MISSING" -> "Uma imagem do rascunho não está mais no diretório de dados: capture de novo.";
            case "PDF_TEXT_POLICY", "PDF_METADATA_POLICY" -> "A verificação final do PDF falhou: nada foi salvo.";
            case "CANCELLED" -> "Exportação cancelada: nada foi salvo.";
            default -> code.startsWith("CAPTURE_REJECTED") ? "Uma imagem não passou na verificação da política visual: capture de novo." : "Operação recusada (" + code + ").";
        };
    }

    static String captureFailure(String code) {
        return switch (code == null ? "" : code) {
            case "VISUAL_CAPTURE_NOT_ALLOWED" -> "A captura de tela só existe no laboratório local.";
            case "PAGE_CHANGED_DURING_CAPTURE" -> "A página mudou durante a captura: deixe-a parada e tente de novo.";
            case "PAGE_CHANGED", "TAB_CLOSED" -> "A aba navegou ou foi fechada antes do atalho.";
            case "ARM_EXPIRED" -> "O atalho não foi pressionado a tempo.";
            case "TAB_MISMATCH" -> "O atalho foi pressionado em outra aba.";
            case "CANCELLED" -> "Captura cancelada.";
            case "LINK_LOST" -> "A conexão com o navegador caiu.";
            case "SANITIZATION_CHECK_FAILED" -> "A imagem não passou na verificação da política visual: nada foi guardado.";
            case "CAPTURE_DENIED" -> "O Chrome não autorizou a captura (o atalho precisa ser pressionado na aba).";
            default -> "A captura não pôde ser feita com segurança (" + code + "). Nenhuma imagem foi usada.";
        };
    }

    private static String failure(Throwable err) {
        Throwable t = err instanceof java.util.concurrent.CompletionException && err.getCause() != null ? err.getCause() : err;
        if (t instanceof com.nekkora.studio.bridge.BridgeCommandException b) return captureFailure(b.code().name());
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
        if (host != null) host.hide();
    }

    /** Usado pelo roteiro: força a releitura das abas. */
    public void reloadTabs() {
        listTabs();
    }
}
