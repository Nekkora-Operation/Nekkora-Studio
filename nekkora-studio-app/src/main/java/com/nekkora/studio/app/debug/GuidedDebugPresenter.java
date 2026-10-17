package com.nekkora.studio.app.debug;

import com.nekkora.studio.app.workspace.Notice;
import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.bridge.BridgeCommandException;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.ManualLocator;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.domain.target.TargetDecision;
import com.nekkora.studio.guided.GuidedPlan;
import com.nekkora.studio.guided.ReusePolicy;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.GuidedDebugClient;
import com.nekkora.studio.recorder.RecorderController;
import com.nekkora.studio.ui.ElementPresentation;
import com.nekkora.studio.ui.debug.GuidedDebugPanel;
import com.nekkora.studio.ui.debug.GuidedDebugPanel.Badge;
import com.nekkora.studio.ui.debug.GuidedDebugPanel.Card;
import com.nekkora.studio.ui.debug.GuidedDebugPanel.Mode;
import com.nekkora.studio.ui.debug.GuidedDebugText;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.awt.Rectangle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * STUDIO-009 — apresentador do debug guiado: liga a janela flutuante ({@link GuidedDebugPanel}) ao cliente da extensão
 * ({@link GuidedDebugClient}), ao plano ({@link GuidedPlan}), à regra de reuso ({@link ReusePolicy}) e às decisões no
 * domínio (pelo {@link WorkspaceService}: desfazíveis, marcam o projeto como alterado). Roda na EDT; respostas do Bridge
 * voltam para a EDT.
 *
 * <p>Continuar registra a decisão CONFIRMED do ator sobre o alvo — NUNCA executa a ação da etapa. Corrigir registra a
 * rejeição e abre as correções; uma referência escolhida no navegador só substitui o alvo depois da comparação e de
 * "Aplicar correção". Ambiguidade, alvo não comprovado e contexto indisponível nunca habilitam Continuar.
 */
public final class GuidedDebugPresenter implements GuidedDebugPanel.Actions, GuidedDebugClient.Listener {

    /** Onde a janela vive (real ou sem janela nos testes). */
    public interface Host {
        void show();

        void hide();

        boolean isShowing();

        /** O conteúdo mudou de tamanho. */
        void fit();

        /** Afasta-se do elemento destacado (coordenadas de tela). */
        void avoid(Rectangle target);

        void dispose();

        static Host headless() {
            return new Host() {
                private boolean showing;

                @Override
                public void show() {
                    showing = true;
                }

                @Override
                public void hide() {
                    showing = false;
                }

                @Override
                public boolean isShowing() {
                    return showing;
                }

                @Override
                public void fit() {
                }

                @Override
                public void avoid(Rectangle target) {
                }

                @Override
                public void dispose() {
                    showing = false;
                }
            };
        }
    }

    /** Registro de uma passagem (contexto read-only do G7: verificações e decisões, sem valores). */
    public record Entry(String stepId, Instant at, String kind, String status, String identitySource, List<String> reasons, String outcome,
                        List<String> outcomeReasons, String actor) {
        public Entry {
            reasons = List.copyOf(reasons);
            outcomeReasons = List.copyOf(outcomeReasons);
        }
    }

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,59}");
    /** Pausa antes de avançar sozinho numa etapa reconfirmada (o feedback compacto fica visível). */
    public static final int REUSE_ADVANCE_MS = 1200;

    private final WorkspaceService service;
    private final BrowserLink link;
    private final RecorderController recorder;
    private final DecisionActor actor;
    private final Clock clock;
    private final GuidedDebugPanel panel;
    private final Host host;
    private final List<Entry> journal = new CopyOnWriteArrayList<>();
    private GuidedDebugClient client;
    private AutomationId automationId;
    private List<StepId> steps = List.of();
    private int index;
    private GuidedDebugClient.Inspection last;
    private ReusePolicy.Outcome lastOutcome;
    private GuidedDebugClient.Picked picked;
    private GuidedDebugClient.Inspection beforeCorrection;
    private Timer advance;
    private int lastEpoch;
    /** A aba escolhida é de laboratório (origem LAB): só então a sonda existe. */
    private boolean labTab;
    private final Map<String, Boolean> labTabs = new LinkedHashMap<>();
    private long generation;

    public GuidedDebugPresenter(WorkspaceService service, BrowserLink link, RecorderController recorder, DecisionActor actor, Clock clock,
            Function<GuidedDebugPanel, Host> hostFactory) {
        this.service = Objects.requireNonNull(service);
        this.link = Objects.requireNonNull(link);
        this.recorder = Objects.requireNonNull(recorder);
        this.actor = Objects.requireNonNull(actor);
        this.clock = Objects.requireNonNull(clock);
        this.panel = new GuidedDebugPanel(this);
        this.host = hostFactory.apply(panel);
    }

    public GuidedDebugPanel panel() {
        return panel;
    }

    public DecisionActor actor() {
        return actor;
    }

    /** Passagens registradas (verificações, reuso e decisões) — sem valores nem texto da página. */
    public List<Entry> journal() {
        return List.copyOf(journal);
    }

    public boolean active() {
        return client != null;
    }

    // ------------------------------------------------------------------------------------------- abrir

    /** "Depurar no navegador…": escolhe a aba (só LAB/AUTHORIZED) e abre a janela. */
    public void open() {
        requireEdt();
        Automation a = service.state().activeAutomation().orElse(null);
        if (a == null || a.stepCount() == 0) {
            service.report(new Notice(Notice.Kind.WARNING, "Debug guiado indisponível", "Abra uma automação com etapas para depurar no navegador."));
            return;
        }
        automationId = a.id();
        show(Card.message(Mode.CHOOSE_TAB, Badge.IDLE, "Depurar \"" + a.name() + "\" no navegador", actor.label(), "Procurando abas do Chrome…"));
        if (link.status().state() != LinkStatus.State.CONNECTED) {
            show(Card.message(Mode.CHOOSE_TAB, Badge.NOT_VERIFIABLE, "Navegador não conectado", actor.label(),
                    "Ligue \"Conectar ao Nekkora Studio\" na Nekkora Extension do Chrome e tente de novo."));
            return;
        }
        long g = ++generation;
        recorder.listTabs().whenComplete((tabs, err) -> edt(() -> {
            if (g != generation) return;
            if (err != null) {
                show(Card.message(Mode.CHOOSE_TAB, Badge.NOT_VERIFIABLE, "Abas indisponíveis", actor.label(), failure(err)));
                return;
            }
            List<GuidedDebugPanel.Tab> options = new ArrayList<>();
            for (BrowserTab t : tabs) {
                if (t.access().allowed() && !t.recording()) {
                    options.add(new GuidedDebugPanel.Tab(t.tabRef(), t.label() + " · " + t.access().label()));
                    labTabs.put(t.tabRef(), t.access() == com.nekkora.studio.recorder.TabAccess.LAB);
                }
            }
            Card c = new Card(Mode.CHOOSE_TAB, Badge.IDLE, "Depurar \"" + a.name() + "\" no navegador", "", actor.label(), "", List.of(), List.of(), List.of(),
                    List.of(), null, options, options.isEmpty() ? "Nenhuma aba autorizada aberta (laboratório local ou site autorizado na extensão)."
                    : "Escolha a aba onde o processo está aberto. Nada será clicado, preenchido ou enviado.", false, false, false, false);
            show(c);
        }));
    }

    @Override
    public void start(String tabRef) {
        requireEdt();
        stopClient();
        labTab = labTabs.getOrDefault(tabRef, false);
        client = new GuidedDebugClient(link, Duration.ofSeconds(60), Duration.ofSeconds(20));
        client.addListener(this);
        GuidedDebugClient c = client;
        show(Card.message(Mode.STEP, Badge.CHECKING, "Abrindo a sessão de debug…", actor.label(), ""));
        c.start(tabRef).whenComplete((s, err) -> edt(() -> {
            if (c != client) return;
            if (err != null) {
                stopClient();
                show(Card.message(Mode.CHOOSE_TAB, Badge.NOT_VERIFIABLE, "Não foi possível iniciar", actor.label(), failure(err)));
                return;
            }
            lastEpoch = s.pageEpoch();
            buildPlan();
            index = 0;
            inspectCurrent();
        }));
    }

    private void buildPlan() {
        Automation a = automation();
        GuidedPlan plan = GuidedPlan.of(a, new AutomationValidator(RuleSet.V1_1).validate(a));
        List<StepId> ids = new ArrayList<>();
        for (GuidedPlan.Step s : plan.steps()) {
            if (s.applicability() != GuidedPlan.Applicability.NO_TARGET_REQUIRED) ids.add(s.id());
        }
        steps = List.copyOf(ids);
    }

    // ------------------------------------------------------------------------------------------- inspeção

    private void inspectCurrent() {
        cancelAdvance();
        picked = null;
        if (index >= steps.size()) {
            finished();
            return;
        }
        AutomationStep step = step();
        if (step == null) {
            next();
            return;
        }
        ElementReference target = step.element().orElse(null);
        if (target == null) {
            last = null;
            lastOutcome = null;
            show(stepCard(step, Badge.NOT_VERIFIABLE, "Esta etapa não tem elemento vinculado: só é possível escolher o alvo no navegador (Corrigir).",
                    List.of(), List.of(), false));
            return;
        }
        show(stepCard(step, Badge.CHECKING, "", List.of(), List.of(), false));
        GuidedDebugClient c = client;
        StepId id = step.id();
        c.inspect(id, step.actionType(), target, true).whenComplete((r, err) -> edt(() -> {
            if (c != client || index >= steps.size() || !steps.get(index).equals(id)) return; // resposta de outra etapa/sessão
            if (err != null) {
                last = null;
                show(stepCard(step(), Badge.NOT_VERIFIABLE, failure(err), List.of(), List.of(), false));
                return;
            }
            onInspection(step(), r);
        }));
    }

    private void onInspection(AutomationStep step, GuidedDebugClient.Inspection r) {
        last = r;
        lastEpoch = Math.max(lastEpoch, r.pageEpoch());
        TargetDecision decision = step.targetDecision().orElse(null);
        lastOutcome = ReusePolicy.evaluate(decision, Automation.expectation(step.actionType(), step.parameters(), step.element().orElseThrow()), verification(r),
                actor);
        journal.add(new Entry(step.id().value().toString(), r.observedAt() == null ? clock.instant() : r.observedAt(), "VERIFICATION", r.status().name(),
                r.identitySource().name(), r.reasons(), lastOutcome.verdict().name(), lastOutcome.reasons().stream().map(Enum::name).toList(), actor.name()));
        boolean resolved = r.status() == GuidedDebugClient.Status.RESOLVED;
        Badge badge;
        String message = "";
        List<String> reuseLines = new ArrayList<>();
        if (lastOutcome.reuse()) {
            badge = Badge.REUSED;
            message = "Mesma identidade comprovada agora e nada mudou desde a confirmação" + (decision == null ? "" : " (" + decision.actor().label() + ")")
                    + ": seguindo sem nova pergunta.";
        } else if (lastOutcome.verdict() == ReusePolicy.Verdict.REVIEW) {
            badge = resolved ? Badge.REVIEW : notVerifiable(r) ? Badge.NOT_VERIFIABLE : Badge.REVIEW;
            for (ReusePolicy.Reason x : lastOutcome.reasons()) reuseLines.add(GuidedDebugText.reuse(x.name()));
            message = "Já havia uma decisão para este alvo, mas ela precisa de revisão.";
        } else if (resolved) {
            badge = Badge.PROPOSED;
        } else {
            badge = notVerifiable(r) ? Badge.NOT_VERIFIABLE : Badge.REVIEW;
        }
        show(stepCard(step, badge, message, reuseLines, candidatesOf(r), resolved));
        if (r.box() != null) {
            GuidedDebugClient.Box b = r.box();
            host.avoid(new Rectangle(b.screenX(), b.screenY(), Math.max(1, b.width()), Math.max(1, b.height())));
        }
        if (lastOutcome.reuse()) scheduleAdvance();
    }

    private static boolean notVerifiable(GuidedDebugClient.Inspection r) {
        return switch (r.status()) {
            case NOT_FOUND, SCOPE_UNAVAILABLE, CONTEXT_UNAVAILABLE, STALE_TARGET -> true;
            default -> false;
        };
    }

    private static ReusePolicy.Verification verification(GuidedDebugClient.Inspection r) {
        Map<String, String> checks = new LinkedHashMap<>();
        r.checks().forEach((k, v) -> checks.put(k, v.name()));
        return new ReusePolicy.Verification(r.status().name(), r.identitySource().name(), r.reasons(), checks);
    }

    private static List<GuidedDebugPanel.Candidate> candidatesOf(GuidedDebugClient.Inspection r) {
        List<GuidedDebugPanel.Candidate> out = new ArrayList<>();
        if (r == null) return out;
        for (GuidedDebugClient.Candidate c : r.candidates()) {
            String name = c.name() == null || c.name().isBlank() ? "(sem nome do vocabulário)" : c.name();
            out.add(new GuidedDebugPanel.Candidate(c.candidateRef(), ElementPresentation.kindLabel(c.kind()) + " · " + name, "CONFIRMED".equals(c.verdict())));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------- decisões

    @Override
    public void continueStep() {
        requireEdt();
        AutomationStep step = step();
        if (step == null || last == null || last.status() != GuidedDebugClient.Status.RESOLVED) return; // só identidade comprovada
        try {
            service.decideTarget(automationId, step.id(), TargetDecision.Kind.CONFIRMED, actor, basis(step, last));
            journal.add(new Entry(step.id().value().toString(), clock.instant(), "DECISION", "CONFIRMED", last.identitySource().name(), List.of(), "", List.of(),
                    actor.name()));
        } catch (RuntimeException e) {
            show(stepCard(step, Badge.REVIEW, problem(e), List.of(), candidatesOf(last), true));
            return;
        }
        next();
    }

    @Override
    public void correct() {
        requireEdt();
        cancelAdvance();
        AutomationStep step = step();
        if (step == null) return;
        if (step.element().isPresent()) {
            try {
                service.decideTarget(automationId, step.id(), TargetDecision.Kind.REJECTED, actor, last == null ? TargetDecision.Basis.notInspected()
                        : basis(step, last));
                journal.add(new Entry(step.id().value().toString(), clock.instant(), "DECISION", "REJECTED", last == null ? "NONE"
                        : last.identitySource().name(), List.of(), "", List.of(), actor.name()));
            } catch (RuntimeException e) {
                show(stepCard(step, Badge.REVIEW, problem(e), List.of(), candidatesOf(last), false));
                return;
            }
        }
        beforeCorrection = last;
        correcting(step, "Escolha o elemento certo: selecione no navegador ou use um dos candidatos. A ação do elemento NÃO acontece.");
    }

    private void correcting(AutomationStep step, String message) {
        Card base = stepCard(step, Badge.REVIEW, message, List.of(), candidatesOf(beforeCorrection), false);
        show(new Card(Mode.CORRECTING, Badge.REVIEW, base.title(), base.progress(), base.actor(), base.recognized(), base.why(), base.limits(), base.details(),
                base.candidates(), null, List.of(), base.message(), false, false, index > 0, index < steps.size() - 1));
    }

    @Override
    public void pickInBrowser() {
        requireEdt();
        AutomationStep step = step();
        if (step == null || client == null) return;
        GuidedDebugClient c = client;
        c.pickStart(step.actionType()).whenComplete((rq, err) -> edt(() -> {
            if (c != client) return;
            if (err != null) {
                correcting(step(), failure(err));
                return;
            }
            show(Card.message(Mode.PICKING, Badge.PICKING, title(step()), actor.label(),
                    "Clique no Chrome sobre o elemento certo desta etapa. O clique é interceptado: nada é acionado na página."));
        }));
    }

    @Override
    public void cancelPick() {
        requireEdt();
        if (client != null) client.pickCancel();
        correcting(step(), "Seleção cancelada.");
    }

    @Override
    public void examine(String candidateRef) {
        requireEdt();
        if (client == null) return;
        client.highlight(candidateRef).whenComplete((ok, err) -> edt(() -> {
            if (err != null) correcting(step(), failure(err));
        }));
    }

    @Override
    public void adopt(String candidateRef) {
        requireEdt();
        AutomationStep step = step();
        if (step == null || client == null) return;
        client.adopt(candidateRef, step.actionType()).whenComplete((rq, err) -> edt(() -> {
            if (err != null) correcting(step(), failure(err));
            else show(Card.message(Mode.PICKING, Badge.PICKING, title(step()), actor.label(), "Descrevendo o candidato escolhido…"));
        }));
    }

    /** Elemento escolhido no navegador: comparação ANTES de qualquer alteração. */
    @Override
    public void picked(GuidedDebugClient.Picked p) {
        edt(() -> {
            AutomationStep step = step();
            if (step == null) return;
            picked = p;
            List<GuidedDebugPanel.Row> before = new ArrayList<>();
            step.element().ifPresentOrElse(e -> {
                before.add(new GuidedDebugPanel.Row("Elemento", ElementPresentation.title(e)));
                before.add(new GuidedDebugPanel.Row("Localizador", e.preferredLocator().map(l -> ElementPresentation.locatorLine(l)).orElse("Nenhum validado")));
            }, () -> before.add(new GuidedDebugPanel.Row("Elemento", "Nenhum")));
            if (beforeCorrection != null) before.add(new GuidedDebugPanel.Row("Verificação", GuidedDebugText.status(beforeCorrection.status().name(),
                    beforeCorrection.identitySource().name())));
            ElementReference e = p.element();
            List<GuidedDebugPanel.Row> after = new ArrayList<>();
            after.add(new GuidedDebugPanel.Row("Elemento", ElementPresentation.title(e)));
            after.add(new GuidedDebugPanel.Row("Localizador", e.preferredLocator().map(ElementPresentation::locatorLine).orElse("Nenhum único e seguro")));
            for (String k : List.of("visible", "enabled")) {
                after.add(new GuidedDebugPanel.Row(GuidedDebugText.checkLabel(k), GuidedDebugText.checkValue(p.checks().get(k).name())));
            }
            boolean unusual = GuidedPlan.unusual(step.actionType(), e.semanticKind());
            String verdict = unusual ? "Incompatível: " + ElementPresentation.kindLabel(e.semanticKind()) + " não serve para \"" + step.actionType().label()
                    + "\". Nada será alterado." : "Compatível com \"" + step.actionType().label() + "\". Aplicar troca só o alvo desta etapa; a ação e os "
                    + "parâmetros continuam como estão.";
            Card base = stepCard(step, Badge.COMPARING, "", List.of(), List.of(), false);
            show(new Card(Mode.COMPARING, Badge.COMPARING, base.title(), base.progress(), base.actor(), "", List.of(), List.of(), List.of(), List.of(),
                    new GuidedDebugPanel.Comparison(before, after, !unusual, verdict), List.of(), "", false, false, false, false));
        });
    }

    @Override
    public void applyCorrection() {
        requireEdt();
        AutomationStep step = step();
        GuidedDebugClient.Picked p = picked;
        if (step == null || p == null) return;
        TargetDecision.Basis basis = new TargetDecision.Basis("TARGET_ID", "SELECTED_IN_BROWSER", codes(p.notes()), p.observedAt(), location(step));
        try {
            service.replaceTarget(automationId, step.id(), p.element(), actor, basis);
            journal.add(new Entry(step.id().value().toString(), clock.instant(), "CORRECTION", "SELECTED_IN_BROWSER", "TARGET_ID", codes(p.notes()), "", List.of(),
                    actor.name()));
        } catch (RuntimeException e) {
            correcting(step, problem(e));
            return;
        }
        picked = null;
        beforeCorrection = null;
        inspectCurrent(); // verificação técnica do NOVO alvo, já como confirmado
    }

    @Override
    public void discardCorrection() {
        requireEdt();
        picked = null;
        correcting(step(), "Escolha descartada: nada foi alterado.");
    }

    @Override
    public void informLocator(String css) {
        requireEdt();
        AutomationStep step = step();
        if (step == null) return;
        try {
            service.addManualLocator(automationId, step.id(), new ManualLocator(ManualLocator.Syntax.CSS, css, "Informado no debug guiado", clock.instant()));
            correcting(step(), "Localizador registrado como NÃO verificado (proposta). Para trocar o alvo, selecione o elemento no navegador.");
        } catch (RuntimeException e) {
            correcting(step, problem(e));
        }
    }

    /**
     * G6: sonda de laboratório — UMA operação explícita no laboratório local (a extensão recusa fora de LAB), sobre o alvo
     * reencontrado. O resultado separa: alvo comprovado ≠ operação efetuada ≠ mudança observada; sucesso de negócio é do
     * oráculo do laboratório, nunca daqui.
     */
    @Override
    public void probe(String valueKey) {
        requireEdt();
        AutomationStep step = step();
        if (step == null || client == null || step.element().isEmpty()) return;
        GuidedDebugClient.ProbeOperation op = switch (step.actionType()) {
            case FILL -> GuidedDebugClient.ProbeOperation.FILL_FIXTURE;
            case PRESS_KEY -> GuidedDebugClient.ProbeOperation.PRESS_ENTER;
            default -> GuidedDebugClient.ProbeOperation.CLICK;
        };
        GuidedDebugClient c = client;
        StepId id = step.id();
        c.probe(id, op, step.element().get(), valueKey).whenComplete((r, err) -> edt(() -> {
            if (c != client) return;
            String text;
            if (err != null) {
                text = failure(err);
            } else {
                journal.add(new Entry(id.value().toString(), r.observedAt() == null ? clock.instant() : r.observedAt(), "PROBE", r.status().name(), "",
                        r.reasons(), r.performed() ? "PERFORMED" : "NOT_PERFORMED", List.of(), actor.name()));
                text = (r.performed() ? "Operação efetuada no laboratório" : "Operação NÃO efetuada")
                        + (r.reasons().isEmpty() ? "" : " (" + String.join(", ", r.reasons().stream().map(GuidedDebugPresenter::probeReason).toList()) + ")")
                        + ". Página mudou: " + (r.domChanged() || r.navigated() ? "sim" : "não")
                        + ". O resultado de negócio não é verificado aqui (o oráculo do laboratório confere).";
            }
            Card base = panel.card();
            show(new Card(base.mode(), base.badge(), base.title(), base.progress(), base.actor(), base.recognized(), base.why(), base.limits(), base.details(),
                    base.candidates(), base.comparison(), base.tabs(), text, base.canContinue(), base.canCorrect(), base.canPrevious(), base.canNext()));
        }));
    }

    private static String probeReason(String code) {
        return switch (code) {
            case "TARGET_NOT_RESOLVED" -> "alvo não comprovado";
            case "NOT_EDITABLE" -> "não aceita digitação";
            case "DISABLED" -> "desabilitado";
            case "HIDDEN" -> "oculto";
            case "UNKNOWN_FIXTURE" -> "dado fictício desconhecido";
            case "KIND_UNUSUAL_FOR_ACTION" -> "tipo não combina com a ação";
            default -> code;
        };
    }

    // ------------------------------------------------------------------------------------------- navegação

    @Override
    public void previous() {
        requireEdt();
        if (index > 0) {
            index--;
            inspectCurrent();
        }
    }

    @Override
    public void next() {
        requireEdt();
        cancelAdvance();
        index++;
        inspectCurrent();
    }

    private void finished() {
        int confirmed = 0;
        int rejected = 0;
        int reviewed = 0;
        Automation a = automation();
        for (StepId id : steps) {
            AutomationStep s = a.step(id).orElse(null);
            if (s == null) continue;
            TargetDecision d = s.targetDecision().orElse(null);
            if (d == null) reviewed++;
            else if (d.confirmed()) confirmed++;
            else rejected++;
        }
        show(Card.message(Mode.FINISHED, Badge.FINISHED, "Debug concluído", actor.label(), confirmed + " alvo(s) confirmado(s), " + rejected
                + " rejeitado(s) e " + reviewed + " sem decisão. Nenhuma etapa foi executada; salve o projeto para guardar as decisões."));
    }

    @Override
    public void finish() {
        requireEdt();
        cancelAdvance();
        stopClient();
        host.hide();
    }

    /** Fecha a janela pelo "x". */
    public Runnable closeRequest() {
        return this::finish;
    }

    @Override
    public void changed(GuidedDebugClient.Session s) {
        edt(() -> {
            if (client == null || !s.debugId().equals(client.debugId())) return;
            if (s.ended()) {
                cancelAdvance();
                GuidedDebugClient ended = client;
                client = null;
                ended.close();
                show(Card.message(Mode.FINISHED, Badge.NOT_VERIFIABLE, "Sessão de debug encerrada", actor.label(), endedReason(s.reason())));
                return;
            }
            if (s.pageEpoch() > lastEpoch) {
                lastEpoch = s.pageEpoch();
                Mode mode = panel.card().mode();
                if (mode == Mode.STEP) inspectCurrent(); // a página mudou: verifica de novo (nada vale da página anterior)
                else if (mode == Mode.PICKING || mode == Mode.COMPARING) correcting(step(), "A página mudou: escolha o elemento de novo.");
            }
        });
    }

    // ------------------------------------------------------------------------------------------- cartões

    private Card stepCard(AutomationStep step, Badge badge, String message, List<String> extraWhy, List<GuidedDebugPanel.Candidate> candidates,
            boolean canContinue) {
        GuidedDebugClient.Inspection r = badge == Badge.CHECKING ? null : last;
        String recognized = "";
        List<String> why = new ArrayList<>();
        List<String> limits = new ArrayList<>();
        List<GuidedDebugPanel.Row> details = new ArrayList<>();
        if (r != null) {
            recognized = r.element() == null ? "Nada reconhecido com segurança."
                    : "Reconhecido: " + ElementPresentation.kindLabel(r.element().kind()) + (r.element().name().isBlank() ? "" : " · " + r.element().name());
            why.add(GuidedDebugText.status(r.status().name(), r.identitySource().name()));
            for (String code : r.reasons()) {
                if (GuidedDebugText.limitation(code)) limits.add(GuidedDebugText.reason(code));
                else why.add(GuidedDebugText.reason(code));
            }
            for (String k : GuidedDebugText.CHECK_ORDER) {
                details.add(new GuidedDebugPanel.Row(GuidedDebugText.checkLabel(k), GuidedDebugText.checkValue(r.check(k).name())));
            }
            details.add(new GuidedDebugPanel.Row("Fonte da identidade", "TARGET_ID".equals(r.identitySource().name()) ? "Identidade registrada" : "Descritor salvo"));
            details.add(new GuidedDebugPanel.Row("Pontuação do motor", String.format(java.util.Locale.ROOT, "%.2f", r.score())));
            details.add(new GuidedDebugPanel.Row("Época da página", String.valueOf(r.pageEpoch())));
        }
        why.addAll(extraWhy);
        limits.addAll(GuidedDebugText.ALWAYS_LIMITS);
        step.targetDecision().ifPresent(d -> details.add(new GuidedDebugPanel.Row("Decisão registrada", d.kind().label() + " · " + d.actor().label())));
        return new Card(Mode.STEP, badge, title(step), progressText(), actor.label(), recognized, why, limits, details, candidates, null, List.of(), message,
                canContinue, badge != Badge.CHECKING, index > 0, index < steps.size() - 1);
    }

    private String title(AutomationStep step) {
        if (step == null) return "";
        Automation a = automation();
        String pos = a.positionOf(step.id()).map(p -> p.sessionNumber() + "." + p.numberInSession()).orElse("?");
        return "Etapa " + pos + " · " + step.actionType().label() + " · " + step.name();
    }

    private String progressText() {
        return steps.isEmpty() ? "" : Math.min(index + 1, steps.size()) + " de " + steps.size();
    }

    private TargetDecision.Basis basis(AutomationStep step, GuidedDebugClient.Inspection r) {
        return new TargetDecision.Basis(r.identitySource().name(), r.status().name(), codes(r.reasons()), r.observedAt(), location(step));
    }

    private static List<String> codes(List<String> raw) {
        return raw.stream().filter(c -> c != null && CODE.matcher(c).matches()).distinct().limit(16).toList();
    }

    /** Modelo de URL da etapa (já sanitizado) — só se for ponto fixo da política; senão vazio. */
    private static String location(AutomationStep step) {
        String l = step.context().map(c -> c.location()).orElse("");
        return !l.isEmpty() && l.equals(CapturePrivacy.urlTemplate(l)) ? l : "";
    }

    // ------------------------------------------------------------------------------------------- apoio

    private void show(Card c) {
        panel.show(c);
        AutomationStep st = c.mode() == Mode.STEP ? step() : null;
        if (st == null || !labTab || st.element().isEmpty() || last == null || last.status() != GuidedDebugClient.Status.RESOLVED) {
            panel.setProbe("", List.of());
        } else {
            switch (st.actionType()) {
                case FILL -> panel.setProbe("Preencher (laboratório)", GuidedDebugClient.LAB_FIXTURE_KEYS);
                case CLICK -> panel.setProbe("Clicar (laboratório)", List.of());
                case PRESS_KEY -> panel.setProbe("Enter (laboratório)", List.of());
                default -> panel.setProbe("", List.of());
            }
        }
        if (!host.isShowing()) host.show();
        host.fit();
    }

    private Automation automation() {
        return service.state().catalog().require(automationId);
    }

    private AutomationStep step() {
        if (automationId == null || index < 0 || index >= steps.size()) return null;
        return automation().step(steps.get(index)).orElse(null);
    }

    private void scheduleAdvance() {
        cancelAdvance();
        advance = new Timer(REUSE_ADVANCE_MS, e -> {
            if (index < steps.size() - 1) next();
            else finished();
        });
        advance.setRepeats(false);
        advance.start();
    }

    private void cancelAdvance() {
        if (advance != null) {
            advance.stop();
            advance = null;
        }
    }

    private void stopClient() {
        GuidedDebugClient c = client;
        client = null;
        if (c != null) {
            c.finish();
            c.close();
        }
    }

    private static String failure(Throwable t) {
        Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
        if (c instanceof BridgeCommandException b) {
            return switch (b.code()) {
                case UNKNOWN_COMMAND -> "A extensão instalada não tem o debug guiado (guided-debug/1). Atualize a Nekkora Extension.";
                case ORIGIN_NOT_ALLOWED -> "A extensão não tem acesso a esta página. Autorize o site na Nekkora Extension.";
                case TAB_NOT_FOUND -> "A aba não existe mais.";
                case INVALID_STATE -> "A aba está ocupada (gravação em andamento ou outro debug).";
                case CANDIDATE_EXPIRED -> "O candidato pertence a uma página anterior: inspecione de novo.";
                case PROBE_NOT_ALLOWED -> "A sonda só existe no laboratório local.";
                case DEBUG_ENDED, DEBUG_NOT_FOUND -> "A sessão de debug foi encerrada.";
                case TIMEOUT -> "O navegador não respondeu a tempo.";
                default -> "O navegador recusou o pedido (" + b.code() + ").";
            };
        }
        return "Falha de comunicação com o navegador.";
    }

    private static String problem(RuntimeException e) {
        return e instanceof com.nekkora.studio.domain.DomainValidationException d ? d.problem().message() : "Não foi possível registrar a decisão.";
    }

    private static String endedReason(String reason) {
        return switch (reason == null ? "" : reason) {
            case "TAB_CLOSED" -> "A aba foi fechada.";
            case "PERMISSION_REVOKED" -> "A autorização do site foi revogada.";
            case "LEASE_EXPIRED" -> "O Studio parou de renovar a sessão.";
            case "RECORDING_STARTED" -> "Uma gravação começou nesta aba.";
            case "FINISHED" -> "Debug encerrado.";
            default -> "A sessão terminou (" + reason + ").";
        };
    }

    private static void edt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("fora da EDT");
    }

    public void dispose() {
        cancelAdvance();
        stopClient();
        host.dispose();
    }
}
