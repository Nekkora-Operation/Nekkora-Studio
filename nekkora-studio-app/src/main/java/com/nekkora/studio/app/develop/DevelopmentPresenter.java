package com.nekkora.studio.app.develop;

import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.generation.GenerationOrchestrator;
import com.nekkora.studio.ai.plan.DevelopmentPlan;
import com.nekkora.studio.ai.plan.VariableDeriver;
import com.nekkora.studio.ai.prompt.PromptRepository;
import com.nekkora.studio.ai.provider.AIProvider;
import com.nekkora.studio.ai.provider.AiAssist;
import com.nekkora.studio.ai.provider.PlanVerifier;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.quality.AutomationQualityGate;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.ai.quality.QualityReport;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.ai.store.DevelopmentStore;
import com.nekkora.studio.app.workspace.Notice;
import com.nekkora.studio.app.workspace.ProjectState;
import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.codegen.CodegenConfig;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.target.DecisionActor;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.PddStore;
import com.nekkora.studio.ui.develop.DevelopmentPanel;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * STUDIO-014 — "Automação → Desenvolver automação": um clique, nenhum formulário. Fases visíveis (Analisando projeto →
 * Verificando PDD → Organizando arquitetura e variáveis → Validando modelo → Gerando Python → Validando saída), canceláveis,
 * sem congelar a interface (disco e ferramentas Python fora da EDT; o modelo, confinado à EDT, só é lido nela).
 *
 * <ul>
 *   <li>PDD não pronto → nenhum arquivo; a pendência aponta o bloco/etapa com "Abrir PDD"/"Ir para etapa";</li>
 *   <li>variáveis derivadas → registradas no modelo numa operação desfazível (Ctrl+Z), anunciada no resultado;</li>
 *   <li>geração pelo gerador oficial, gravação protegida (edição manual → {@code .novo}/{@code .diff});</li>
 *   <li>verificação real (compilação, importação, ruff, mypy, contrato) com o Python configurado; sem ele, "não
 *       verificado";</li>
 *   <li>uma resposta a um pedido de ajuda vai para a especificação funcional e a análise recomeça.</li>
 * </ul>
 * Nunca executa o processo gerado nem age no sistema-alvo.
 */
public final class DevelopmentPresenter implements DevelopmentPanel.Actions {

    /** Janela e diálogos (a aplicação dá os reais; os testes, um sem tela). */
    public interface Host {
        void show();

        void hide();

        Optional<Path> chooseFolder(String title);

        Optional<Path> choosePython();

        /** NEKKORA-E2E-002: lista de entrada do teste autorizado (sem janela: nenhuma, e o teste não começa). */
        default Optional<Path> chooseRunInput(String title) {
            return Optional.empty();
        }

        /** NEKKORA-E2E-002: planilha de saída do teste autorizado. */
        default Optional<Path> chooseRunOutput(String title, String suggestedName) {
            return Optional.empty();
        }

        void showText(String title, String text);

        /** Prévia do envio à IA: true = a pessoa autorizou ESTE envio. */
        boolean consent(String title, List<String> sent, List<String> notSent, String payload);

        void openFolder(Path p);

        /** Confirmação explícita (autorizar site público de treinamento). Sem janela que pergunte, nada é confirmado. */
        default boolean confirm(String title, String message, String action) {
            return false;
        }

        static Host headless(Path folder, boolean consents) {
            return new Host() {
                public void show() {}

                public void hide() {}

                public Optional<Path> chooseFolder(String title) {
                    return Optional.ofNullable(folder);
                }

                public Optional<Path> choosePython() {
                    return Optional.empty();
                }

                public void showText(String title, String text) {}

                public boolean consent(String title, List<String> sent, List<String> notSent, String payload) {
                    return consents;
                }

                public void openFolder(Path p) {}

                public boolean confirm(String title, String message, String action) {
                    return consents;
                }
            };
        }
    }

    /** Outras partes do Studio que a janela aciona. */
    public interface Studio {
        void saveActive();

        void goToStep(StepId step);

        void openPdd(String blockId);

        void openDebug();

        void showValidation();

        void legacyGenerator();
    }

    private final WorkspaceService service;
    private final Studio studio;
    private final Clock clock;
    private final DecisionActor actor;
    private final DevelopmentStore store;
    private final PddStore pdds;
    private final FrameworkCapabilityCatalog catalog = FrameworkCapabilityCatalog.bundled();
    /** PACKAGING-001: ambiente Python por automação (nulo sem diretório de dados — testes em memória: comportamento anterior). */
    private final com.nekkora.studio.ai.deps.DependencyManager deps;
    /** Última avaliação do ambiente (escrita no worker, lida na EDT). */
    private volatile com.nekkora.studio.ai.deps.DependencyManager.Assessment lastEnvironment;
    /** Python BASE indicado pela pessoa quando nenhum 3.12+ foi encontrado (só nesta sessão; não muda o Python dela). */
    private Optional<Path> chosenBase = Optional.empty();
    private final PromptRepository.Prompt prompt;
    private final AIProvider provider;
    private final DevelopmentPanel panel = new DevelopmentPanel(this);
    private final Function<DevelopmentPanel, Host> hosts;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nekkora-develop");
        t.setDaemon(true);
        return t;
    });
    private Host host;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private boolean running;
    private final Map<GenerationOrchestrator.Phase, DevelopmentPanel.PhaseState> phases = new EnumMap<>(GenerationOrchestrator.Phase.class);
    private DevelopmentPanel.Model model = DevelopmentPanel.Model.empty("", false);
    private GenerationOrchestrator.Run last;
    private final List<QualityIssue> aiProposals = new ArrayList<>();
    /** Automação a que as propostas da IA pertencem (outra automação ativa = propostas descartadas). */
    private com.nekkora.studio.domain.AutomationId proposalsFor;
    private final Map<String, Integer> fixAttempts = new LinkedHashMap<>();
    private Consumer<QualityReport> reportListener = r -> { };

    public DevelopmentPresenter(WorkspaceService service, Studio studio, Clock clock, DecisionActor actor, Path dataDir, AIProvider provider,
                                Function<DevelopmentPanel, Host> hosts) {
        this.service = service;
        this.studio = studio;
        this.clock = clock;
        this.actor = actor;
        // sem diretório de dados (testes, uso em memória): nada é gravado fora dos arquivos do projeto e da pasta de saída
        this.store = dataDir == null ? DevelopmentStore.inMemory() : new DevelopmentStore(dataDir);
        this.pdds = dataDir == null ? null : new PddStore(dataDir);
        this.deps = dataDir == null ? null
                : new com.nekkora.studio.ai.deps.DependencyManager(dataDir, com.nekkora.studio.ai.deps.FrameworkArtifacts.bundled(), clock);
        this.provider = provider;
        this.hosts = hosts;
        PromptRepository.Prompt p;
        try {
            p = PromptRepository.bundled().current();
        } catch (PromptRepository.PromptUnavailable e) {
            p = null; // sem o Prompt-Mestre conferido, a IA fica indisponível (o fluxo determinístico continua)
        }
        this.prompt = p;
    }

    /** Recebe cada relatório de qualidade produzido aqui (a aba Validação o mostra). */
    public void onReport(Consumer<QualityReport> listener) {
        this.reportListener = listener == null ? r -> { } : listener;
    }

    /** Camadas para a aba Validação: nota de origem, estado de cada camada e os apontamentos VIVOS (com pergunta e correção). */
    public record Layers(String note, List<QualityReport.LayerResult> layers, List<QualityIssue> issues) {}

    private Consumer<Layers> layersListener = l -> { };
    private long validationRound;

    public void onLayers(Consumer<Layers> listener) {
        this.layersListener = listener == null ? l -> { } : listener;
    }

    /**
     * "Validar automação" (Ctrl+Shift+V), depois da validação estrutural: V0–V3 na hora (offline, sem IA); V4/V5 em
     * segundo plano quando há código da revisão atual e um Python de verificação — a aba atualiza ao terminar. V6 nunca é
     * presumida. Nada é executado no sistema-alvo nem enviado para fora.
     */
    public void validateLayers() {
        Automation a = active().orElse(null);
        if (a == null) return;
        long round = ++validationRound;
        Optional<Path> project = projectFile(a);
        Path out = project.map(p -> outputDir(a, p)).orElse(null);
        Optional<DocumentDraft> draft = loadDraft(a);
        FunctionalSpec spec = loadSpec(a);
        Optional<CodegenConfig> tabular = project.flatMap(p -> {
            try {
                return declaredTabular(p);
            } catch (CodegenConfig.Invalid e) {
                return Optional.empty();
            }
        });
        var v0 = new AutomationValidator(RuleSet.LATEST).validate(a);
        String promptLabel = prompt == null ? "" : prompt.label();
        AutomationQualityGate.Assessment first = AutomationQualityGate.assess(new AutomationQualityGate.Inputs(a, v0, draft, spec, catalog, out, tabular,
                Optional.empty(), true, List.of(), promptLabel, clock.instant(), store.options(a.id()).origins(), runRecord(a)));
        boolean code = first.code().exists() && first.code().current();
        layersListener.accept(new Layers("revisão atual", List.copyOf(first.report().layers().values()), live(first)));
        if (!code) {
            AutomationQualityGate.Assessment noCode = AutomationQualityGate.assess(new AutomationQualityGate.Inputs(a, v0, draft, spec, catalog, out, tabular,
                    Optional.empty(), false, List.of(), promptLabel, clock.instant(), store.options(a.id()).origins(), runRecord(a)));
            publish(a, noCode, round);
            return;
        }
        Optional<Path> pythonSetting = store.options(a.id()).python();
        worker.submit(() -> {
            Optional<PythonChecks.Environment> env = environmentFor(a, out);
            List<PythonChecks.Check> checks = List.of();
            if (env.isPresent()) {
                try {
                    checks = PythonChecks.run(env.get(), out, () -> round != validationRound);
                } catch (IOException e) {
                    checks = List.of();
                }
            }
            List<PythonChecks.Check> done = checks;
            SwingUtilities.invokeLater(() -> {
                if (round != validationRound || active().map(x -> x != a).orElse(true)) return; // outra validação ou outra automação: descartado
                AutomationQualityGate.Assessment full = AutomationQualityGate.assess(new AutomationQualityGate.Inputs(a, new AutomationValidator(RuleSet.LATEST)
                        .validate(a), loadDraft(a), loadSpec(a), catalog, out, tabular, env, false, done, promptLabel, clock.instant(),
                        store.options(a.id()).origins(), runRecord(a), environmentProblem()));
                publish(a, full, round);
            });
        });
    }

    private void publish(Automation a, AutomationQualityGate.Assessment as, long round) {
        if (round != validationRound) return;
        layersListener.accept(new Layers("revisão atual", List.copyOf(as.report().layers().values()), live(as)));
        QualityReport r = as.report();
        worker.submit(() -> {
            try {
                store.saveQuality(a.id(), r.toJson());
            } catch (IOException ignored) {
                // auxiliar
            }
        });
        reportListener.accept(r);
    }

    private List<QualityIssue> live(AutomationQualityGate.Assessment as) {
        List<QualityIssue> out = new ArrayList<>();
        out.addAll(as.report().issues()); // inclusive as limitações visuais (INFO): o documento as declara
        return out;
    }

    /** Relatório persistido (ao reabrir): mostrado com a nota de atualidade; nunca como resultado atual de outra revisão. */
    public Optional<Layers> persistedLayers() {
        Automation a = active().orElse(null);
        if (a == null) return Optional.empty();
        return store.quality(a.id()).flatMap(t -> {
            try {
                QualityReport r = QualityReport.parse(t);
                if (!r.automationId().equals(a.id().value().toString())) return Optional.empty();
                boolean current = r.automationRevision() == a.revision();
                return Optional.of(new Layers(current ? "último relatório, " + r.at().toString().substring(0, 16).replace('T', ' ') + " UTC"
                        : "relatório de outra revisão — valide de novo", List.copyOf(r.layers().values()), current ? r.issues() : List.of()));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    /** "Resolver…" na aba Validação: abre esta janela na pendência (sem iniciar outra análise). */
    public void resolveFromValidation(QualityIssue issue) {
        open(false);
        List<QualityIssue> pend = new ArrayList<>(model.pending());
        if (pend.stream().noneMatch(x -> x.id().equals(issue.id()))) pend.add(0, issue);
        model = new DevelopmentPanel.Model(model.subject(), model.projectSaved(), running, "Pendência da validação", issue.title(), com.nekkora.ui.core.Severity.INFO,
                model.phases(), model.uses(), model.answers(), model.variables(), pend, model.results(), model.outputDir(), model.python(), model.canOpenCode(),
                model.aiAvailable());
        panel.show(model);
        panel.focusPending();
        for (int i = 0; i < panel.pendingList().getModel().getSize(); i++) {
            if (panel.pendingList().getModel().getElementAt(i).id().equals(issue.id())) panel.pendingList().setSelectedIndex(i);
        }
    }

    public DevelopmentPanel panel() {
        return panel;
    }

    public DevelopmentStore store() {
        return store;
    }

    // ------------------------------------------------------------------------------------------- abrir / desenvolver

    /** Menu "Desenvolver automação": mostra a janela e já inicia a análise (um clique). */
    public void open() {
        open(true);
    }

    /** Mostra a janela; {@code start} = iniciar a análise agora. */
    public void open(boolean start) {
        Automation a = active().orElse(null);
        if (a == null) {
            service.report(new Notice(Notice.Kind.WARNING, "Desenvolvimento indisponível", "Abra uma automação para desenvolver o código."));
            return;
        }
        if (host == null) host = hosts.apply(panel);
        if (!running) {
            model = DevelopmentPanel.Model.empty(a.name(), projectFile(a).isPresent());
            model = withPaths(model, a);
            panel.show(model);
        }
        host.show();
        refreshRun(a);
        if (start && !running && projectFile(a).isPresent()) develop();
    }

    @Override
    public void develop() {
        if (running) return;
        Automation a = active().orElse(null);
        if (a == null) return;
        Optional<Path> project = projectFile(a);
        if (project.isEmpty()) {
            render(a, "Salve o projeto antes", "O código vai para uma pasta ao lado do arquivo do projeto: use Salvar projeto (Ctrl+S).",
                    com.nekkora.ui.core.Severity.WARNING, List.of(), List.of());
            return;
        }
        running = true;
        cancelled.set(false);
        phases.clear();
        for (GenerationOrchestrator.Phase p : GenerationOrchestrator.Phase.values()) phases.put(p, DevelopmentPanel.PhaseState.PENDING);
        Path out = outputDir(a, project.get());
        Optional<DocumentDraft> draft = loadDraft(a);
        FunctionalSpec spec = loadSpec(a);
        pruneProposals(a, spec);
        Optional<CodegenConfig> tabular;
        try {
            tabular = declaredTabular(project.get());
        } catch (CodegenConfig.Invalid e) {
            running = false;
            render(a, "Configuração tabular do projeto recusada", "O arquivo " + project.get().getFileName().toString().replaceFirst("\\.nekkora$", "")
                    + ".codegen.json existe mas não segue o contrato (" + e.code() + "). Corrija-o ou remova-o para a execução única.",
                    com.nekkora.ui.core.Severity.ERROR, List.of(), List.of());
            return;
        }
        // variáveis derivadas: registradas antes do plano (operação desfazível); o gate é avaliado de novo na nova revisão
        List<String> notes = new ArrayList<>();
        ProjectContext ctx = ProjectContext.of(a, draft);
        List<VariableDeriver.Planned> derived = VariableDeriver.derive(a, ctx);
        if (derived.stream().anyMatch(VariableDeriver.Planned::changesModel) && pddReady(a, draft, spec)) {
            int n = service.applyDerivedVariables(a.id(), derived);
            if (n > 0) notes.add("✓ " + n + " variável(is)/ligação(ões) derivada(s) registrada(s) no modelo — Ctrl+Z desfaz (Registrar variáveis derivadas)");
        }
        GenerationOrchestrator.Run run = GenerationOrchestrator.prepare(a, draft, spec, catalog, tabular, store.options(a.id()).origins(), out, clock.instant(),
                this::phase, cancelled::get);
        last = run;
        if (run.stoppedAt() != null) {
            phases.put(run.stoppedAt(), DevelopmentPanel.PhaseState.BLOCKED);
            for (GenerationOrchestrator.Phase p : GenerationOrchestrator.Phase.values()) {
                if (p.ordinal() > run.stoppedAt().ordinal()) phases.put(p, DevelopmentPanel.PhaseState.SKIPPED);
            }
            running = false;
            worker.submit(() -> saveGate(run));
            boolean pdd = run.stoppedAt() == GenerationOrchestrator.Phase.PDD;
            List<QualityIssue> shown = new ArrayList<>(run.issues());
            if (pdd) { // o código espera o PDD, mas o diagnóstico continua: capacidades (V2) já aparecem
                shown.addAll(com.nekkora.studio.ai.capability.CapabilityCheck.check(a, ProjectContext.of(a, draft)
                        .withAuthorizedPublicOrigins(store.options(a.id()).origins()), catalog, tabular).issues());
            }
            render(a, pdd ? "PDD não está pronto: nenhum código foi gerado" : "Nenhum código foi gerado",
                    pdd ? "Resolva as pendências do PDD (aba Pendências). As análises que não dependem dele continuam disponíveis em Validar automação."
                            : "O plano ou o pré-voo do gerador encontrou bloqueios. Veja a aba Pendências.", com.nekkora.ui.core.Severity.WARNING, notes, shown);
            panel.focusPending();
            return;
        }
        phases.put(GenerationOrchestrator.Phase.GENERATING, DevelopmentPanel.PhaseState.RUNNING);
        render(a, "Escrevendo os arquivos do projeto…", "Gerador oficial: " + run.files().size() + " arquivo(s).", com.nekkora.ui.core.Severity.INFO, notes, run.issues());
        Optional<Path> pythonSetting = store.options(a.id()).python();
        worker.submit(() -> {
            GenerationOrchestrator.Run written;
            try {
                written = GenerationOrchestrator.write(run, store);
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> fail(a, "Falha ao gravar o projeto", "A pasta de saída não pôde ser gravada (" + e.getClass().getSimpleName() + ")."));
                return;
            }
            SwingUtilities.invokeLater(() -> {
                last = written;
                phases.put(GenerationOrchestrator.Phase.GENERATING, DevelopmentPanel.PhaseState.DONE);
                phases.put(GenerationOrchestrator.Phase.CHECKING, DevelopmentPanel.PhaseState.RUNNING);
                render(a, "Validando a saída…", "Compilação, importação, ruff, mypy e testes de contrato (sem rede, sem executar o processo).",
                        com.nekkora.ui.core.Severity.INFO, notes, written.issues());
            });
            Optional<PythonChecks.Environment> env = environmentFor(a, written.outputDir());
            List<PythonChecks.Check> checks = List.of();
            if (env.isPresent() && !cancelled.get()) {
                try {
                    checks = PythonChecks.run(env.get(), written.outputDir(), cancelled::get);
                } catch (IOException e) {
                    checks = List.of();
                }
            }
            List<PythonChecks.Check> done = checks;
            SwingUtilities.invokeLater(() -> finish(a, written, env, done, notes));
        });
    }

    private void finish(Automation a, GenerationOrchestrator.Run run, Optional<PythonChecks.Environment> env, List<PythonChecks.Check> checks, List<String> notes) {
        running = false;
        boolean allPass = !checks.isEmpty() && checks.stream().allMatch(c -> c.status() == PythonChecks.Status.PASS);
        phases.put(GenerationOrchestrator.Phase.CHECKING, env.isEmpty() || checks.isEmpty() ? DevelopmentPanel.PhaseState.SKIPPED
                : allPass ? DevelopmentPanel.PhaseState.DONE : DevelopmentPanel.PhaseState.BLOCKED);
        List<String> results = new ArrayList<>(notes);
        Map<GenerationOrchestrator.Stage, Boolean> stages = GenerationOrchestrator.stages(run, allPass);
        stages.forEach((s, ok) -> results.add((ok ? "✓ " : "— ") + s.name()));
        results.add("Pasta: " + run.outputDir());
        run.written().files().forEach((f, st) -> results.add("  " + statusLabel(st) + "  " + f));
        if (run.written().protectedAny()) results.add("! Arquivo editado à mão preservado: veja os .novo e .diff ao lado dele.");
        if (env.isEmpty()) {
            var le = lastEnvironment;
            results.add(le == null ? "— Código não verificado: configure o Python para verificação (Opções avançadas). Nada foi dado por aprovado."
                    : "— Código não verificado: ambiente Python da automação — " + le.diagnosis().title() + " (" + le.detail() + "). Nada foi dado por aprovado.");
        } else {
            for (PythonChecks.Check c : checks) {
                results.add((c.status() == PythonChecks.Status.PASS ? "✓ " : c.status() == PythonChecks.Status.FAIL ? "× " : "— ") + label(c) + ": "
                        + (c.status() == PythonChecks.Status.PASS ? "sem problema" : c.problems() + " problema(s) " + c.codes() + c.reason()) + " (" + c.millis() + " ms)");
            }
            results.add("Ambiente: Python " + env.get().pythonVersion() + ", nekkora_rpa " + env.get().framework() + ", ruff " + env.get().ruff() + ", mypy "
                    + env.get().mypy());
        }
        results.add("— Execução real: não executada (o Studio não age no sistema-alvo; rode só em ambiente autorizado).");
        QualityReport report = assess(a, env, checks);
        String title = allPass ? "Código gerado e verificado" : env.isEmpty() ? "Código gerado (não verificado)" : "Código gerado com problemas na verificação";
        String text = allPass ? "Gerado pelo gerador oficial e conferido por compilação, importação, ruff, mypy e testes de contrato. Execução real: não verificada."
                : env.isEmpty() ? "O projeto foi gravado. Sem o Python de verificação, as camadas V4/V5 ficam \"não verificado\"."
                : "Veja o resultado: a correção vai para o gerador ou para o modelo, não para o arquivo.";
        model = withPaths(new DevelopmentPanel.Model(a.name(), true, false, title, text, allPass ? com.nekkora.ui.core.Severity.SUCCESS
                : com.nekkora.ui.core.Severity.WARNING, phaseRows(), uses(run.plan()), answers(a, run), variables(run.plan()), pending(withEnvironment(run.issues())), results,
                "", "", true, provider != null && provider.available()), a);
        panel.show(model);
        panel.focusResults();
        if (report != null) reportListener.accept(report);
        // o relatório é gravado pela fila de trabalho: o estado do teste autorizado é recalculado depois dele
        worker.submit(() -> SwingUtilities.invokeLater(() -> refreshRun(a)));
    }

    private void fail(Automation a, String title, String text) {
        running = false;
        phases.put(GenerationOrchestrator.Phase.GENERATING, DevelopmentPanel.PhaseState.BLOCKED);
        render(a, title, text, com.nekkora.ui.core.Severity.ERROR, List.of(), List.of());
    }

    /** Relatório de qualidade da revisão atual (persistido) — o mesmo que "Validar automação" produz. */
    private QualityReport assess(Automation a, Optional<PythonChecks.Environment> env, List<PythonChecks.Check> checks) {
        Optional<Path> project = projectFile(a);
        AutomationQualityGate.Assessment as = AutomationQualityGate.assess(new AutomationQualityGate.Inputs(a, new AutomationValidator(RuleSet.LATEST).validate(a),
                loadDraft(a), loadSpec(a), catalog, project.map(p -> outputDir(a, p)).orElse(null), project.flatMap(p -> {
                    try {
                        return declaredTabular(p);
                    } catch (CodegenConfig.Invalid e) {
                        return Optional.empty();
                    }
                }), env, false, checks, prompt == null ? "" : prompt.label(), clock.instant(), store.options(a.id()).origins(), runRecord(a), environmentProblem()));
        QualityReport r = as.report();
        worker.submit(() -> {
            try {
                store.saveQuality(a.id(), r.toJson());
            } catch (IOException ignored) {
                // relatório é auxiliar: sem ele, a próxima validação recalcula
            }
        });
        return r;
    }

    private void phase(GenerationOrchestrator.Phase p) {
        for (GenerationOrchestrator.Phase x : GenerationOrchestrator.Phase.values()) {
            if (x.ordinal() < p.ordinal() && phases.get(x) != DevelopmentPanel.PhaseState.BLOCKED) phases.put(x, DevelopmentPanel.PhaseState.DONE);
        }
        phases.put(p, DevelopmentPanel.PhaseState.RUNNING);
    }

    private List<DevelopmentPanel.Phase> phaseRows() {
        List<DevelopmentPanel.Phase> out = new ArrayList<>();
        for (GenerationOrchestrator.Phase p : GenerationOrchestrator.Phase.values()) {
            out.add(new DevelopmentPanel.Phase(p.label(), phases.getOrDefault(p, DevelopmentPanel.PhaseState.PENDING), ""));
        }
        return out;
    }

    private void render(Automation a, String title, String text, com.nekkora.ui.core.Severity sev, List<String> results, List<QualityIssue> issues) {
        DevelopmentPlan plan = last == null ? null : last.plan();
        model = withPaths(new DevelopmentPanel.Model(a.name(), projectFile(a).isPresent(), running, title, text, sev, phaseRows(), uses(plan),
                answers(a, last), variables(plan), pending(issues), results, "", "", outputExists(a), provider != null && provider.available()), a);
        panel.show(model);
    }

    private List<QualityIssue> pending(List<QualityIssue> issues) {
        List<QualityIssue> out = new ArrayList<>();
        for (QualityIssue i : issues) if (i.kind() != QualityIssue.Kind.INFO) out.add(i);
        out.addAll(aiProposals);
        return out;
    }

    // ------------------------------------------------------------------------------------------- textos do plano

    private List<DevelopmentPanel.Use> uses(DevelopmentPlan plan) {
        List<DevelopmentPanel.Use> out = new ArrayList<>();
        List<String> ids = plan == null || plan.capabilities().isEmpty() ? List.of() : plan.capabilities();
        for (String id : ids) {
            catalog.find(id).ifPresent(c -> out.add(new DevelopmentPanel.Use(c.id(), c.name(), c.group(), c.purpose(), c.whereToRead(catalog.version()))));
        }
        return out;
    }

    private List<DevelopmentPanel.Answer> answers(Automation a, GenerationOrchestrator.Run run) {
        List<DevelopmentPanel.Answer> out = new ArrayList<>();
        DevelopmentPlan plan = run == null ? null : run.plan();
        if (plan == null) {
            out.add(new DevelopmentPanel.Answer("O que será criado?", "Depende do PDD pronto: ainda não há plano."));
        } else if (plan.model() == DevelopmentPlan.ExecutionModel.TABULAR) {
            out.add(new DevelopmentPanel.Answer("O que será criado?", "Processo em lote (uma transação por linha da planilha) e uma Page por sessão — gerador "
                    + "tabular da E2E-001, com a configuração declarada no projeto."));
        } else {
            out.add(new DevelopmentPanel.Answer("O que será criado?", "processo.py (RpaProcess) e " + plan.pages().size() + " Page(s): "
                    + String.join(", ", plan.pages().stream().map(DevelopmentPlan.PlannedPage::className).toList())
                    + " — uma por tela observada. Nenhum Module: nada se repete o bastante para justificar um (o LoginModule usaria StepIds próprios)."));
        }
        if (run != null && run.gate() != null) {
            var r = run.gate().record();
            out.add(new DevelopmentPanel.Answer("De onde vieram as etapas?", "Do PDD (" + r.state() + ", documento " + shortRev(r.documentRevision()) + ") e de "
                    + r.totalSteps() + " etapa(s) do modelo, cada uma pelo seu StepId; " + r.coveredSteps() + " de " + r.totalSteps() + " descritas no PDD."));
        }
        out.add(new DevelopmentPanel.Answer("Quais funções serão utilizadas?", plan == null ? "As do catálogo do nekkora_rpa " + catalog.version()
                + " que o plano escolher." : String.join(", ", uses(plan).stream().filter(u -> u.group().startsWith("Libs")).map(DevelopmentPanel.Use::name).toList())));
        out.add(new DevelopmentPanel.Answer("Onde consulto cada função?", "Em \"O que vou utilizar\": selecione e use Ver documentação — assinatura, arquivo e linha "
                + "no pacote instalado e a seção da documentação do nekkora_rpa " + catalog.version() + "."));
        if (plan != null && plan.model() == DevelopmentPlan.ExecutionModel.LIST && plan.singleConfig().isPresent()) {
            var cfg = plan.singleConfig().get();
            out.add(new DevelopmentPanel.Answer("Como meus dados serão recebidos?", "Lista de entrada CSV ou XLSX com as colunas "
                    + String.join(", ", cfg.inputs()) + ", uma transação por linha; saída numa planilha Excel com as colunas "
                    + String.join(", ", cfg.output().columns().stream().map(com.nekkora.studio.codegen.SingleConfig.Column::header).toList())
                    + ", gravada só se todos os itens derem certo."));
        } else if (plan != null && plan.model() == DevelopmentPlan.ExecutionModel.SINGLE && plan.singleConfig().isPresent()) {
            var cfg = plan.singleConfig().get();
            long creds = plan.variables().stream().filter(v -> "CREDENTIAL".equals(v.origin())).count();
            out.add(new DevelopmentPanel.Answer("Como meus dados serão recebidos?", "Entrada única: um arquivo JSON com "
                    + (cfg.inputs().isEmpty() ? "nenhuma entrada" : String.join(", ", cfg.inputs())) + (creds == 0 ? "" : "; credenciais pelo provedor de segredos ("
                    + creds + " apelido(s), nunca no projeto)") + ". Nenhuma planilha."));
        } else if (plan != null && plan.tabularConfig().isPresent()) {
            out.add(new DevelopmentPanel.Answer("Como meus dados serão recebidos?", "Planilha " + plan.tabularConfig().get().input().format() + " com "
                    + plan.tabularConfig().get().input().columns().size() + " coluna(s), como declarado no projeto."));
        }
        projectFile(a).ifPresent(p -> out.add(new DevelopmentPanel.Answer("Para onde vai o Python?", outputDir(a, p) + " (Opções avançadas para mudar).")));
        var le = lastEnvironment;
        if (le != null) {
            out.add(new DevelopmentPanel.Answer("Em que ambiente o código é verificado?", le.requirement().pip() + " — "
                    + (le.ready() ? le.detail() + "; " + le.environment().map(e -> e.python().toString()).orElse("")
                    : le.diagnosis().title() + " (" + le.detail() + "). " + le.diagnosis().recovery())));
        }
        long open = run == null ? 0 : run.issues().stream().filter(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED).count();
        out.add(new DevelopmentPanel.Answer("O que a IA não sabe?", open == 0 ? "Nenhuma pergunta aberta. O plano é determinístico; propostas da IA só "
                + "entram depois de \"Revisar e aplicar\"."
                : open + " pergunta(s) por etapa na aba Pendências — o Studio não decide sem evidência."));
        return out;
    }

    private static List<DevelopmentPanel.Variable> variables(DevelopmentPlan plan) {
        if (plan == null) return List.of();
        List<DevelopmentPanel.Variable> out = new ArrayList<>();
        for (VariableDeriver.Planned p : plan.variables()) {
            String origin = switch (p.origin()) {
                case "INPUT" -> "entrada da execução";
                case "STEP_OUTPUT" -> "saída de etapa";
                case "CREDENTIAL" -> "credencial (provedor de segredos)";
                default -> p.origin().toLowerCase(java.util.Locale.ROOT);
            };
            out.add(new DevelopmentPanel.Variable(p.name(), p.type(), origin, provenance(p) + " — " + p.basis()));
        }
        return out;
    }

    private static String provenance(VariableDeriver.Planned p) {
        return switch (p.provenance()) {
            case EXISTING -> "já declarada";
            case DERIVED_LABEL -> "derivada do rótulo";
            case DERIVED_SENSITIVE -> "derivada: campo sensível";
            case DERIVED_LOGIN_PAIR -> "derivada: usuário de acesso";
            case DERIVED_GENERIC -> "derivada: nome provisório";
            case DERIVED_OBSERVATION -> "derivada da etapa Observar";
        };
    }

    static String shortRev(String rev) {
        return rev == null || rev.length() < 19 ? String.valueOf(rev) : rev.substring(0, 19) + "…";
    }

    private static String statusLabel(OutputWriter.Status s) {
        return switch (s) {
            case WRITTEN -> "gravado";
            case UNCHANGED -> "igual";
            case PROTECTED -> "PROTEGIDO (.novo/.diff)";
        };
    }

    private static String label(PythonChecks.Check c) {
        return switch (c.id()) {
            case "PY_COMPILE" -> "Compilação";
            case "PY_IMPORT" -> "Importação";
            case "RUFF_CHECK" -> "ruff check";
            case "RUFF_FORMAT" -> "ruff format";
            case "MYPY_STRICT" -> "mypy estrito";
            case "CONTRACT_TESTS" -> "Testes de contrato";
            default -> c.id();
        };
    }

    // ------------------------------------------------------------------------------------------- pendências

    @Override
    public void resolve(QualityIssue issue, AssistanceRequest.Option option) {
        if (running) return;
        Automation a = active().orElse(null);
        if (a == null) return;
        if (option.studioAction() != null) {
            switch (option.studioAction()) {
                case "GO_TO_STEP" -> goTo(issue);
                case "OPEN_PDD" -> openPdd(issue);
                case "OPEN_DEBUG" -> studio.openDebug();
                case "SEE_EXTENDING" -> host.showText("Como estender o framework", catalog.extending().file() + " — " + catalog.extending().section().replaceFirst("^#+\\s*", "")
                        + "\n\nUma Lib nova entra no nekkora_rpa com contrato público, efeito classificado, prazo, identidade do alvo, eventos sem valores e testes "
                        + "positivos, negativos e de regressão — nunca uma Lib de um site específico. Enquanto ela não existir, o Studio não gera aproximações.");
                case "GO_TO_LAST_STEP" -> lastStep(a).ifPresent(studio::goToStep);
                case "AUTHORIZE_PUBLIC_SITE" -> authorizePublicSite(a);
                case "PREPARE_ENVIRONMENT" -> prepareEnvironment(a);
                case "CHOOSE_BASE_PYTHON" -> host.choosePython().ifPresent(p -> {
                    chosenBase = Optional.of(p);
                    prepareEnvironment(a);
                });
                default -> {
                }
            }
            return;
        }
        FunctionalSpec.Field field = issue.assistance().map(AssistanceRequest::destination).orElse(null);
        if (field == null) return;
        ProjectContext ctx = ProjectContext.of(a, loadDraft(a));
        String fp = issue.step() == null ? null : ctx.step(issue.step()).map(ProjectContext.Step::fingerprint).orElse(null);
        if (issue.step() != null && fp == null) {
            service.report(new Notice(Notice.Kind.INFO, "Etapa não encontrada", "A etapa desta pergunta não existe mais. Desenvolva de novo."));
            return;
        }
        FunctionalSpec spec = loadSpec(a).with(new FunctionalSpec.Decision(field, field == FunctionalSpec.Field.CONCLUSION ? null : issue.step(), option.value(),
                actorOf(), FunctionalSpec.Basis.ANSWER, field == FunctionalSpec.Field.CONCLUSION ? null : fp, clock.instant()));
        try {
            store.saveSpec(spec);
        } catch (IOException e) {
            service.report(new Notice(Notice.Kind.WARNING, "Resposta não guardada", "A especificação funcional não pôde ser gravada."));
            return;
        }
        develop(); // a resposta muda a especificação: o gate anterior não vale; a análise recomeça
    }

    /**
     * Autoriza a execução nos sites públicos de TREINAMENTO desta automação (ambiente benchmark do framework): origens https
     * exatas observadas na gravação, com confirmação explícita; registrado com o ator e a data nas opções do projeto. Nunca
     * para sistemas de clientes; nunca misturado com laboratório.
     */
    private void authorizePublicSite(Automation a) {
        ProjectContext ctx = ProjectContext.of(a, loadDraft(a));
        List<String> origins = ctx.origins().stream().filter(o -> o.startsWith("https://")).toList();
        if (origins.isEmpty() || origins.size() != ctx.origins().size()) return;
        if (!host.confirm("Autorizar site público de treinamento?", "Origens: " + String.join(", ", origins) + ".\n\nUse só para sites públicos feitos para "
                + "praticar automação. O código gerado vai executar ações nesses sites quando você rodá-lo; nada é executado agora. Sistemas de clientes "
                + "continuam bloqueados (não há ambiente de produção no nekkora_rpa 0.2.0).", "Autorizar")) {
            return;
        }
        DevelopmentStore.Options o = store.options(a.id());
        List<DevelopmentStore.PublicOrigin> pub = new ArrayList<>(o.publicOrigins());
        for (String origin : origins) {
            if (pub.stream().noneMatch(x -> x.origin().equals(origin))) pub.add(new DevelopmentStore.PublicOrigin(origin, actorOf().name(), clock.instant().toString()));
        }
        try {
            store.saveOptions(a.id(), new DevelopmentStore.Options(o.outputDir(), o.python(), pub));
        } catch (IOException e) {
            service.report(new Notice(Notice.Kind.WARNING, "Autorização não guardada", "As opções do projeto não puderam ser gravadas."));
            return;
        }
        develop();
    }

    @Override
    public void answerText(QualityIssue issue, String text) {
        if (running) return;
        Automation a = active().orElse(null);
        if (a != null && issue.step() != null && issue.assistance().map(AssistanceRequest::destination).orElse(null) == FunctionalSpec.Field.OUTPUT_COLUMN) {
            answerHeader(a, issue, text);
            return;
        }
        if (a == null || issue.assistance().map(AssistanceRequest::destination).orElse(null) != FunctionalSpec.Field.START_URL) return;
        String url = text == null ? "" : text.strip();
        ProjectContext ctx = ProjectContext.of(a, loadDraft(a));
        if (!ProjectContext.matchesTemplate(url, ctx.startTemplate())) {
            host.showText("Endereço recusado", "O endereço precisa ter a mesma origem e os mesmos trechos conhecidos do modelo gravado (" + ctx.startTemplate()
                    + "), sem consulta (?) nem fragmento (#). Nada foi guardado.");
            return;
        }
        FunctionalSpec spec;
        try {
            spec = loadSpec(a).with(new FunctionalSpec.Decision(FunctionalSpec.Field.START_URL, null, url, actorOf(), FunctionalSpec.Basis.ANSWER, null,
                    clock.instant()));
            store.saveSpec(spec);
        } catch (IOException | IllegalArgumentException e) {
            host.showText("Endereço recusado", "O endereço não segue o formato esperado (http ou https, sem consulta). Nada foi guardado.");
            return;
        }
        develop(); // a resposta muda a especificação: a análise recomeça
    }

    /** NEKKORA-E2E-002: cabeçalho de coluna digitado — rótulo de interface do vocabulário controlado, nunca dado. */
    private void answerHeader(Automation a, QualityIssue issue, String text) {
        String header = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (!FunctionalSpec.headerOk(header)) {
            host.showText("Cabeçalho recusado", "O cabeçalho precisa ser um rótulo de interface curto (até 60 caracteres), formado só por termos do vocabulário "
                    + "controlado e sem começar por =, +, - ou @. Ele nomeia a coluna; não é o dado. Nada foi guardado.");
            return;
        }
        ProjectContext ctx = ProjectContext.of(a, loadDraft(a));
        String fp = ctx.step(issue.step()).map(ProjectContext.Step::fingerprint).orElse(null);
        if (fp == null) return;
        try {
            store.saveSpec(loadSpec(a).with(new FunctionalSpec.Decision(FunctionalSpec.Field.OUTPUT_COLUMN, issue.step(), FunctionalSpec.COLUMN + header,
                    actorOf(), FunctionalSpec.Basis.ANSWER, fp, clock.instant())));
        } catch (IOException | IllegalArgumentException e) {
            host.showText("Cabeçalho recusado", "A resposta não pôde ser guardada na especificação funcional. Nada foi alterado.");
            return;
        }
        develop();
    }

    @Override
    public void apply(QualityIssue issue) {
        if (running) return;
        Automation a = active().orElse(null);
        if (a == null || issue.fix() == null) return;
        String id = issue.fix().id();
        int attempts = fixAttempts.merge(issue.id(), 1, Integer::sum);
        if (attempts > 2) { // falhas repetidas não viram laço: pedem revisão humana
            service.report(new Notice(Notice.Kind.WARNING, "Correção automática não resolveu", "Esta correção já foi aplicada duas vezes e o problema voltou. "
                    + "Revise a etapa (Ir para etapa) — o Studio não vai tentar de novo sozinho."));
            return;
        }
        if (id.equals("APPLY_DERIVED_VARIABLES")) {
            int n = service.applyDerivedVariables(a.id(), VariableDeriver.derive(a, ProjectContext.of(a, loadDraft(a))));
            service.report(new Notice(Notice.Kind.SUCCESS, "Variáveis registradas", n + " mudança(s) no modelo, numa operação desfazível (Ctrl+Z)."));
            develop();
        } else if (id.equals("REGENERATE")) {
            develop();
        } else if (id.startsWith("PREFER_LOCATOR:") && issue.step() != null) {
            service.choosePreferredLocator(a.id(), issue.step(), Integer.parseInt(id.substring("PREFER_LOCATOR:".length())));
            service.report(new Notice(Notice.Kind.INFO, "Localizador trocado", "Confirme o alvo de novo em Depurar no navegador antes de gerar."));
        } else if (id.startsWith("AI:")) {
            acceptProposal(a, issue);
        }
    }

    @Override
    public void goTo(QualityIssue issue) {
        if (issue.step() != null) studio.goToStep(issue.step());
    }

    @Override
    public void openPdd(QualityIssue issue) {
        studio.openPdd(issue.blockId());
    }

    @Override
    public void showDocs(String capabilityId) {
        catalog.find(capabilityId).ifPresentOrElse(c -> host.showText(c.name(), c.name() + "  (" + c.group() + ")\n\n" + c.importLine() + "\n" + c.name()
                + c.signature() + "\n\n" + c.summary() + "\n\nFinalidade: " + c.purpose() + "\nEfeito: " + c.effect() + (c.limitations().isEmpty() ? ""
                : "\nLimitações:\n- " + String.join("\n- ", c.limitations())) + "\n\nOnde consultar:\n- código no pacote instalado: " + c.source().file() + ", linha "
                + c.source().line() + "\n- documentação: Nekkora-RPA-Framework/" + c.docs().file() + " — " + c.docs().section().replaceFirst("^#+\\s*", "")
                + "\n\nCatálogo gerado do nekkora_rpa " + catalog.version() + " (" + catalog.fingerprint() + ")."),
                () -> host.showText("Capacidade inexistente", "UNSUPPORTED_CAPABILITY: \"" + capabilityId + "\" não existe no nekkora_rpa " + catalog.version()
                        + ". Veja " + catalog.extending().file() + "."));
    }

    @Override
    public void openCode() {
        active().flatMap(a -> projectFile(a).map(p -> outputDir(a, p))).filter(Files::isDirectory).ifPresent(p -> host.openFolder(p));
    }

    @Override
    public void showReport() {
        studio.showValidation();
    }

    @Override
    public void validate() {
        studio.showValidation();
    }

    @Override
    public void saveProject() {
        studio.saveActive();
        active().ifPresent(a -> {
            model = withPaths(DevelopmentPanel.Model.empty(a.name(), projectFile(a).isPresent()), a);
            panel.show(model);
        });
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public void chooseOutput() {
        Automation a = active().orElse(null);
        if (a == null) return;
        host.chooseFolder("Pasta do projeto Python (arquivos editados à mão nunca são sobrescritos)").ifPresent(p -> {
            DevelopmentStore.Options o = store.options(a.id());
            try {
                store.saveOptions(a.id(), new DevelopmentStore.Options(Optional.of(p), o.python(), o.publicOrigins()));
            } catch (IOException e) {
                service.report(new Notice(Notice.Kind.WARNING, "Opção não guardada", "A pasta escolhida não pôde ser registrada."));
            }
            panel.show(model = withPaths(model, a));
        });
    }

    // ------------------------------------------------------------------------------------------- ambiente Python (PACKAGING-001)

    /**
     * O ambiente para o código de {@code out}, SEM instalar e sem rede: (1) o Python indicado em Opções avançadas, sempre
     * conferido contra o requisito do projeto (divergência = diagnóstico, nunca troca silenciosa); (2) a venv da automação
     * preparada pelo Dependency Manager; (3) {@code NEKKORA_PYTHON} ou a {@code .venv} da pasta gerada, só se passarem na
     * mesma verificação de versão; senão o diagnóstico e o plano de "Preparar ambiente". Sem código gerado (ou sem pasta
     * de dados), o comportamento anterior.
     */
    Optional<PythonChecks.Environment> environmentFor(Automation a, Path out) {
        Optional<Path> configured = store.options(a.id()).python();
        Optional<com.nekkora.studio.ai.deps.FrameworkRequirement> req = com.nekkora.studio.ai.deps.FrameworkRequirement.ofGeneratedProject(out);
        if (deps == null || req.isEmpty()) {
            lastEnvironment = null;
            return PythonChecks.resolve(configured, out);
        }
        String id = a.id().value().toString();
        if (configured.isPresent()) {
            var as = deps.assess(id, req.get(), configured, Optional.empty());
            lastEnvironment = as;
            return as.environment();
        }
        var as = deps.assess(id, req.get(), Optional.empty(), chosenBase);
        if (!as.ready()) {
            for (Path p : developerPythons(out)) {
                var v = com.nekkora.studio.ai.deps.IntegrityVerifier.verify(p, req.get(), deps.artifacts(), false);
                if (v.ok()) {
                    lastEnvironment = new com.nekkora.studio.ai.deps.DependencyManager.Assessment(req.get(), v.diagnosis(),
                            "Python de desenvolvimento (" + p.getFileName() + "): " + v.detail(), v.environment(), Optional.empty(), as.environmentDir(), true);
                    return v.environment();
                }
            }
        }
        lastEnvironment = as;
        return as.environment();
    }

    private static List<Path> developerPythons(Path out) {
        List<Path> out2 = new ArrayList<>();
        String env = System.getenv("NEKKORA_PYTHON");
        if (env != null && !env.isBlank()) out2.add(Path.of(env));
        if (out != null) {
            out2.add(out.resolve(".venv/Scripts/python.exe"));
            out2.add(out.resolve(".venv/bin/python"));
        }
        return out2.stream().filter(Files::isRegularFile).toList();
    }

    /** A pendência do ambiente (quando ele não está pronto), com as ações de preparar e de indicar o Python. */
    /** O diagnóstico do ambiente em uma linha (vazio = pronto ou não avaliado) para o gate de qualidade. */
    private String environmentProblem() {
        var le = lastEnvironment;
        return le == null || le.ready() ? "" : le.diagnosis().title() + " — " + le.detail();
    }

    private List<QualityIssue> withEnvironment(List<QualityIssue> issues) {
        var le = lastEnvironment;
        if (le == null || le.ready()) return issues;
        List<AssistanceRequest.Option> opts = new ArrayList<>();
        if (le.configured()) {
            opts.add(AssistanceRequest.Option.action("PREPARE_ENVIRONMENT", "Preparar ambiente da automação…",
                    "deixa o Python de Opções avançadas de lado só se você limpar a opção; prepara a venv própria da automação"));
        } else if (le.diagnosis() == com.nekkora.studio.ai.deps.Diagnosis.PYTHON_MISSING) {
            opts.add(AssistanceRequest.Option.action("CHOOSE_BASE_PYTHON", "Indicar o Python 3.12…", "o interpretador base da venv (não é alterado)"));
        } else if (le.plan().isPresent()) {
            opts.add(AssistanceRequest.Option.action("PREPARE_ENVIRONMENT", "Preparar ambiente…", "mostra o que será instalado e pede sua confirmação"));
        }
        List<QualityIssue> out = new ArrayList<>(issues);
        // aviso acionável (não é pergunta da especificação): não impede gerar; impede só a verificação V4/V5 e o teste autorizado
        out.add(new QualityIssue("ENVIRONMENT_NOT_READY", Layer.V4_CODE, QualityIssue.Kind.WARNING, null, null, "Ambiente Python da automação",
                le.diagnosis().title() + ": " + le.detail() + ".", le.diagnosis().recovery(),
                new AssistanceRequest("Preparar o ambiente Python desta automação (" + le.requirement().pip() + ")?",
                        "O código gerado exige exatamente esta versão. A venv é exclusiva da automação; nada é instalado sem sua confirmação.", opts, null,
                        "Ambiente Python da automação"), null, null));
        return out;
    }

    /**
     * "Preparar ambiente": plano → CONFIRMAÇÃO da pessoa com pacote, versão, extras, origem, destino e efeitos → instalação
     * no worker (progresso, Cancelar) → verificação completa → Desenvolver de novo (V4/V5 com o ambiente novo).
     */
    void prepareEnvironment(Automation a) {
        if (running || deps == null) return;
        Path out = projectFile(a).map(p -> outputDir(a, p)).orElse(null);
        Optional<com.nekkora.studio.ai.deps.FrameworkRequirement> req = out == null ? Optional.empty()
                : com.nekkora.studio.ai.deps.FrameworkRequirement.ofGeneratedProject(out);
        if (req.isEmpty()) {
            host.showText("Preparar ambiente", "Use Desenvolver antes: o ambiente segue a versão exigida pelo código gerado.");
            return;
        }
        var as = deps.assess(a.id().value().toString(), req.get(), Optional.empty(), chosenBase);
        lastEnvironment = as;
        if (as.ready()) {
            develop();
            return;
        }
        if (as.plan().isEmpty()) {
            host.showText(as.diagnosis().title(), as.detail() + ".\n\n" + as.diagnosis().recovery());
            return;
        }
        var plan = as.plan().get();
        if (!host.confirm("Preparar o ambiente Python da automação?", String.join("\n\n", plan.summary()), "Preparar ambiente")) return;
        running = true;
        cancelled.set(false);
        render(a, "Preparando o ambiente Python da automação…", "Etapa 1 de 6. Use Cancelar para interromper.", com.nekkora.ui.core.Severity.INFO,
                List.of(), List.of());
        worker.submit(() -> {
            com.nekkora.studio.ai.deps.IntegrityVerifier.Result r = null;
            com.nekkora.studio.ai.deps.DependencyException err = null;
            try {
                r = deps.install(plan, p -> SwingUtilities.invokeLater(() -> environmentProgress(a, p)), cancelled::get);
            } catch (com.nekkora.studio.ai.deps.DependencyException e) {
                err = e;
            }
            var done = r;
            var failed = err;
            SwingUtilities.invokeLater(() -> {
                running = false;
                if (done != null && done.ok()) {
                    service.report(new Notice(Notice.Kind.SUCCESS, "Ambiente pronto", plan.requirement().pip() + " — " + done.detail() + "."));
                    develop();
                    return;
                }
                var d = failed != null ? failed.diagnosis() : done.diagnosis();
                String detail = failed != null ? failed.getMessage() : done.detail();
                lastEnvironment = deps.assess(a.id().value().toString(), plan.requirement(), Optional.empty(), chosenBase);
                render(a, d.title(), detail + ". " + d.recovery() + " O projeto, o PDD e o código gerado continuam como estavam.",
                        d == com.nekkora.studio.ai.deps.Diagnosis.CANCELLED ? com.nekkora.ui.core.Severity.WARNING : com.nekkora.ui.core.Severity.ERROR,
                        List.of("× Ambiente Python da automação: " + d.name() + " — " + detail), withEnvironment(List.of()));
                panel.focusPending();
            });
        });
    }

    private void environmentProgress(Automation a, com.nekkora.studio.ai.deps.DependencyManager.Progress p) {
        if (!running) return;
        render(a, "Preparando o ambiente Python da automação…", "Etapa " + p.step() + " de " + p.steps() + ": " + p.label()
                + (p.line().isBlank() ? "" : " · " + (p.line().length() > 140 ? p.line().substring(0, 140) + "…" : p.line())) + ". Use Cancelar para interromper.",
                com.nekkora.ui.core.Severity.INFO, List.of(), List.of());
    }

    @Override
    public void choosePython() {
        Automation a = active().orElse(null);
        if (a == null) return;
        host.choosePython().ifPresent(p -> {
            Optional<PythonChecks.Environment> env = PythonChecks.resolve(Optional.of(p), null);
            if (env.isEmpty()) {
                host.showText("Python recusado", "Este interpretador não tem nekkora_rpa, ruff e mypy instalados. Nada foi alterado.");
                return;
            }
            DevelopmentStore.Options o = store.options(a.id());
            try {
                store.saveOptions(a.id(), new DevelopmentStore.Options(o.outputDir(), Optional.of(p), o.publicOrigins()));
            } catch (IOException e) {
                service.report(new Notice(Notice.Kind.WARNING, "Opção não guardada", "O interpretador escolhido não pôde ser registrado."));
            }
            panel.show(model = withPaths(model, a));
        });
    }

    @Override
    public void showConfig() {
        Automation a = active().orElse(null);
        if (a == null) return;
        String text = store.config(a.id()).orElse(last != null && last.configJson() != null ? last.configJson() : "Ainda não há configuração derivada: use Desenvolver.");
        host.showText("Configuração derivada (codegen-config 1.1.0)", "Gerada pelo Studio a partir do modelo, do PDD e da especificação funcional. Sem valores, "
                + "credenciais nem seletores. Não precisa ser editada.\n\n" + text);
    }

    @Override
    public void legacyGenerator() {
        studio.legacyGenerator();
    }

    @Override
    public void close() {
        if (host != null) host.hide();
    }

    // ------------------------------------------------------------------------------------------- IA (opcional)

    @Override
    public void analyzeWithAi() {
        Automation a = active().orElse(null);
        if (a == null || running) return;
        panel.focusPending();
        if (provider == null || !provider.available() || prompt == null) {
            host.showText("Solicitar ajuda", "As perguntas pendentes estão na aba Pendências: cada uma diz o que falta, por quê e para onde vai a resposta.\n\n"
                    + com.nekkora.studio.ai.provider.AIProviders.unavailableExplanation());
            return;
        }
        List<QualityIssue> open = last == null ? List.of() : last.issues();
        ProjectContext ctx = ProjectContext.of(a, loadDraft(a));
        AiAssist.Payload p = AiAssist.payload(ctx, open, catalog, prompt);
        if (p.questions() == 0) {
            host.showText("Solicitar ajuda", "Não há pergunta aberta para analisar.");
            return;
        }
        if (!host.consent("Enviar para análise por IA?", p.sent(), p.notSent(), p.json())) return; // sem autorização: nada sai
        running = true;
        render(a, "Analisando pendências com IA…", "Provedor " + provider.id() + ". A resposta é só proposta: nada é aplicado sem você.",
                com.nekkora.ui.core.Severity.INFO, List.of(), open);
        worker.submit(() -> {
            PlanVerifier.Result r;
            String error = null;
            int status = 0;
            long t0 = System.nanoTime();
            try {
                r = AiAssist.run(provider, true, prompt, p, ctx, cancelled::get);
            } catch (AIProvider.AIException e) {
                r = null;
                error = e.code().name();
                status = e.status();
            }
            recordAiCall(a, p, r, error, status, (System.nanoTime() - t0) / 1_000_000);
            PlanVerifier.Result res = r;
            String err = error;
            SwingUtilities.invokeLater(() -> {
                running = false;
                aiProposals.clear();
                proposalsFor = a.id();
                if (res != null) {
                    for (PlanVerifier.Proposal x : res.accepted()) aiProposals.add(proposalIssue(x));
                }
                render(a, err != null ? "Análise por IA não concluída (" + err + ")" : "Propostas da IA: " + aiProposals.size(),
                        err != null ? "Nada foi aplicado. As perguntas continuam abertas." : "Cada proposta precisa de \"Revisar e aplicar\". Recusadas pelo verificador: "
                                + res.rejected().size() + ". Consumo: " + res.inputTokens() + " + " + res.outputTokens() + " tokens.",
                        err != null ? com.nekkora.ui.core.Severity.WARNING : com.nekkora.ui.core.Severity.INFO, List.of(), open);
            });
        });
    }

    /**
     * NEKKORA-E2E-002: metadados VERIFICÁVEIS de uma chamada real feita pela interface (consentida): provedor e modelo
     * informados pela resposta, contrato, Prompt-Mestre, tamanho da carga, perguntas, resultado (RESPONSE_VERIFIED ou o
     * código do erro), aceitas/recusadas pelo verificador, tokens e duração. Nunca a carga, a resposta ou a chave.
     */
    private void recordAiCall(Automation a, AiAssist.Payload p, PlanVerifier.Result r, String error, int status, long millis) {
        java.util.Map<String, Object> ev = new java.util.LinkedHashMap<>();
        ev.put("schema", "nekkora-studio/ai-call-evidence");
        ev.put("version", "1.0.0");
        ev.put("at", clock.instant().toString());
        ev.put("origin", "UI:Solicitar ajuda");
        ev.put("actor", actorOf().name());
        ev.put("provider", provider.id());
        ev.put("model", r == null ? "" : r.model());
        ev.put("contract", AiAssist.CONTRACT);
        ev.put("prompt", prompt.label());
        ev.put("payloadChars", p.json().length());
        ev.put("payloadSha256", com.nekkora.studio.ai.Hashes.sha256(p.json()));
        ev.put("steps", p.steps());
        ev.put("openQuestions", p.questions());
        ev.put("consent", "PREVIEW_SHOWN_AND_ACCEPTED");
        ev.put("outcome", r != null ? "RESPONSE_VERIFIED" : "PROVIDER_ERROR");
        if (r != null) {
            ev.put("accepted", r.accepted().stream().map(x -> (Object) (x.kind().name() + (x.step() == null ? "" : "@etapa"))).toList());
            ev.put("rejected", r.rejected().stream().map(x -> (Object) x.code()).toList());
            ev.put("inputTokens", r.inputTokens());
            ev.put("outputTokens", r.outputTokens());
        } else {
            ev.put("code", error);
            ev.put("httpStatus", status);
        }
        ev.put("millis", millis);
        try {
            store.appendAiCall(a.id(), com.nekkora.studio.bridge.json.Json.write(ev));
        } catch (IOException ignored) {
            // a janela mostra o resultado mesmo sem o registro
        }
    }

    public static QualityIssue proposalIssue(PlanVerifier.Proposal p) {
        String what = switch (p.kind()) {
            case EFFECT -> "efeito " + ("EXTERNAL".equals(p.value()) ? "externo (grava/envia)" : "nenhum (consulta/navega)");
            case POSTCONDITION -> "confirmação " + p.value();
            case VARIABLE_NAME -> "nome de variável " + p.value();
            case DATA_FLOW -> "LIST_XLSX".equals(p.value()) ? "uma transação por linha de uma lista e planilha de saída" : "uma execução (JSON)";
            case OUTPUT_COLUMN -> p.value().startsWith(FunctionalSpec.COLUMN) ? "coluna “" + p.value().substring(FunctionalSpec.COLUMN.length()) + "”"
                    : p.value().startsWith(FunctionalSpec.FALLBACK) ? "fonte alternativa de outra coluna" : "fora da planilha";
            case VARIABLE_TYPE -> "DATE".equals(p.value()) ? "ler como data (por extenso → AAAA-MM-DD)" : "ler como texto";
        };
        return new QualityIssue("AI_PROPOSAL", Layer.V3_PLAN, QualityIssue.Kind.PROPOSE_REVIEW, p.step(), null, "IA propõe: " + what,
                p.rationale().isEmpty() ? "Proposta da IA (sem justificativa aproveitável)." : p.rationale(), "Revise antes de aplicar: a decisão fica registrada com o seu ator.",
                null, new QualityIssue.Fix("AI:" + p.kind() + ":" + p.value(), what, "", p.value()), null);
    }

    /**
     * E2E-002: as propostas da IA ainda não revisadas continuam entre execuções do Desenvolver (inclusive as internas,
     * como registrar variáveis derivadas) — a pessoa não precisa pedir de novo. Saem quando a automação muda, quando a
     * etapa deixa de existir ou quando a decisão daquele campo já foi registrada (por proposta aplicada ou resposta).
     */
    private void pruneProposals(Automation a, FunctionalSpec spec) {
        if (!a.id().equals(proposalsFor)) {
            aiProposals.clear();
            return;
        }
        aiProposals.removeIf(p -> obsoleteProposal(p, a, spec));
    }

    /** A proposta da IA já não tem o que propor: etapa removida, ou decisão daquele campo já registrada. */
    public static boolean obsoleteProposal(QualityIssue p, Automation a, FunctionalSpec spec) {
        String[] parts = p.fix().id().split(":", 3);
        if (p.step() != null && a.step(p.step()).isEmpty()) return true;
        return switch (PlanVerifier.Kind.valueOf(parts[1])) {
            case DATA_FLOW -> spec.decision(FunctionalSpec.Field.DATA_FLOW, null).isPresent();
            case EFFECT -> spec.decision(FunctionalSpec.Field.EFFECT, p.step()).isPresent();
            case POSTCONDITION -> spec.decision(FunctionalSpec.Field.POSTCONDITION, p.step()).isPresent();
            case OUTPUT_COLUMN -> spec.decision(FunctionalSpec.Field.OUTPUT_COLUMN, p.step()).isPresent();
            case VARIABLE_TYPE -> a.variables().stream().anyMatch(v -> p.step().equals(v.origin().producer())
                    && v.type() == ("DATE".equals(parts[2]) ? com.nekkora.studio.domain.variable.VariableType.DATE
                    : com.nekkora.studio.domain.variable.VariableType.STRING));
            case VARIABLE_NAME -> false;
        };
    }

    private void acceptProposal(Automation a, QualityIssue issue) {
        String[] parts = issue.fix().id().split(":", 3);
        PlanVerifier.Kind kind = PlanVerifier.Kind.valueOf(parts[1]);
        String value = parts[2];
        aiProposals.removeIf(x -> x.id().equals(issue.id()) && x.fix().id().equals(issue.fix().id()));
        // a mesma decisão (tipo + etapa) não fica oferecida duas vezes; as demais propostas sobrevivem à reanálise
        aiProposals.removeIf(x -> x.fix() != null && x.fix().id().startsWith("AI:" + kind.name() + ":") && java.util.Objects.equals(x.step(), issue.step()));
        if (kind == PlanVerifier.Kind.DATA_FLOW) {
            try {
                store.saveSpec(loadSpec(a).with(new FunctionalSpec.Decision(FunctionalSpec.Field.DATA_FLOW, null, value, actorOf(),
                        FunctionalSpec.Basis.PROPOSAL_ACCEPTED, null, clock.instant())));
            } catch (IOException e) {
                return;
            }
            develop();
            return;
        }
        if (kind == PlanVerifier.Kind.VARIABLE_TYPE) {
            a.variables().stream().filter(v -> v.origin().producer() != null && v.origin().producer().equals(issue.step())).findFirst().ifPresent(v ->
                    service.updateVariable(a.id(), v.id(), com.nekkora.studio.domain.variable.VariableDraft.of(v.name(), "DATE".equals(value)
                            ? com.nekkora.studio.domain.variable.VariableType.DATE : com.nekkora.studio.domain.variable.VariableType.STRING)
                            .withOrigin(v.origin()).withSensitivity(v.sensitivity()).withPurpose(v.purpose())));
            develop();
            return;
        }
        if (kind == PlanVerifier.Kind.VARIABLE_NAME) {
            AutomationStep st = a.step(issue.step()).orElse(null);
            String current = st == null ? null : st.parameters().value().map(v -> v instanceof com.nekkora.studio.domain.action.ValueSource.VariableReference r ? r.name()
                    : null).orElse(null);
            a.variables().stream().filter(v -> v.name().equals(current) || (v.origin().producer() != null && v.origin().producer().equals(issue.step()))).findFirst()
                    .ifPresent(v -> service.renameVariable(a.id(), v.id(), value));
            develop();
            return;
        }
        ProjectContext ctx = ProjectContext.of(a, loadDraft(a));
        String fp = ctx.step(issue.step()).map(ProjectContext.Step::fingerprint).orElse(null);
        if (fp == null) return;
        FunctionalSpec.Field field = kind == PlanVerifier.Kind.EFFECT ? FunctionalSpec.Field.EFFECT
                : kind == PlanVerifier.Kind.OUTPUT_COLUMN ? FunctionalSpec.Field.OUTPUT_COLUMN : FunctionalSpec.Field.POSTCONDITION;
        try {
            store.saveSpec(loadSpec(a).with(new FunctionalSpec.Decision(field, issue.step(), value, actorOf(), FunctionalSpec.Basis.PROPOSAL_ACCEPTED, fp,
                    clock.instant())));
        } catch (IOException e) {
            return;
        }
        develop();
    }

    // ------------------------------------------------------------------------------------------- teste autorizado (E2E-002)

    /**
     * Por que o TESTE AUTORIZADO não está disponível agora (vazio = disponível). Só lê estado: PDD_READY da revisão atual,
     * código gerado da revisão atual e verificado (CODE_VALIDATED), processo em lista com planilha e origem autorizada.
     */
    Optional<String> runBlocker(Automation a) {
        Optional<Path> project = projectFile(a);
        if (project.isEmpty()) return Optional.of("Salve o projeto e desenvolva o código primeiro.");
        Path out = outputDir(a, project.get());
        Optional<DocumentDraft> draft = loadDraft(a);
        com.nekkora.studio.ai.gate.PddGate.Result gate = com.nekkora.studio.ai.gate.PddGate.evaluate(ProjectContext.of(a, draft), draft, loadSpec(a),
                com.nekkora.studio.pdd.ProcessProjection.of(a), clock.instant());
        if (!gate.ready()) return Optional.of("O PDD não está pronto (PDD_READY) na revisão atual.");
        AutomationQualityGate.CodeState code = AutomationQualityGate.codeState(out, a, gate);
        if (!code.current()) return Optional.of("Não há código gerado da revisão atual (" + code.reason() + "): use Desenvolver.");
        if (!validated(a, code.manifestHash())) return Optional.of("O código desta revisão ainda não passou na verificação (CODE_VALIDATED).");
        Optional<com.nekkora.studio.codegen.SingleConfig> cfg = derivedConfig(a);
        if (cfg.isEmpty() || !cfg.get().list()) return Optional.of("Nesta versão o teste autorizado roda processos em lista com planilha de saída.");
        boolean lab = cfg.get().allowedOrigins().stream().allMatch(o -> o.startsWith("http://127.0.0.1") || o.startsWith("http://localhost"));
        if (!lab && !store.options(a.id()).origins().containsAll(cfg.get().allowedOrigins())) {
            return Optional.of("A origem do processo não está autorizada como site público de treinamento.");
        }
        return Optional.empty();
    }

    /** A verificação real (V4/V5) passou para ESTE manifesto? (relatório de qualidade persistido da revisão.) */
    private boolean validated(Automation a, String manifest) {
        return store.quality(a.id()).map(t -> {
            try {
                QualityReport r = QualityReport.parse(t);
                return manifest.equals(r.codeManifest()) && r.layers().get(Layer.V4_CODE).state() == QualityReport.State.PASS
                        && r.layers().get(Layer.V5_TESTS).state() == QualityReport.State.PASS;
            } catch (RuntimeException e) {
                return false;
            }
        }).orElse(false);
    }

    private Optional<com.nekkora.studio.codegen.SingleConfig> derivedConfig(Automation a) {
        return store.config(a.id()).flatMap(t -> {
            try {
                return Optional.of(com.nekkora.studio.codegen.SingleConfig.parse(t));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    Optional<com.nekkora.studio.ai.quality.SupervisedRunRecord> runRecord(Automation a) {
        return store.supervisedRun(a.id()).flatMap(t -> {
            try {
                com.nekkora.studio.ai.quality.SupervisedRunRecord r = com.nekkora.studio.ai.quality.SupervisedRunRecord.parse(t);
                return r.automationId().equals(a.id().value().toString()) ? Optional.of(r) : Optional.empty();
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    private void refreshRun(Automation a) {
        Optional<String> why = running ? Optional.of("Aguarde o fim da operação em andamento.") : runBlocker(a);
        panel.runState(why.isEmpty(), why.orElse("Roda o código gerado da revisão atual, com confirmação do site e do destino."));
    }

    @Override
    public void supervisedRun() {
        if (running) return;
        Automation a = active().orElse(null);
        if (a == null) return;
        Optional<String> blocker = runBlocker(a);
        if (blocker.isPresent()) {
            host.showText("Teste autorizado indisponível", blocker.get() + "\n\nNada foi executado.");
            return;
        }
        Path out = outputDir(a, projectFile(a).orElseThrow());
        com.nekkora.studio.codegen.SingleConfig cfg = derivedConfig(a).orElseThrow();
        Optional<PythonChecks.Environment> env = environmentFor(a, out);
        Optional<com.nekkora.studio.ai.deps.FrameworkRequirement> required = com.nekkora.studio.ai.deps.FrameworkRequirement.ofGeneratedProject(out);
        if (env.isEmpty() || required.isEmpty() || !required.get().version().equals(env.get().framework())) {
            var le = lastEnvironment;
            host.showText("Teste autorizado indisponível", "O ambiente Python da automação precisa ter exatamente "
                    + required.map(com.nekkora.studio.ai.deps.FrameworkRequirement::pip).orElse("a versão do código gerado") + ", ruff e mypy"
                    + (le == null || le.ready() ? "." : ": " + le.diagnosis().title() + " — " + le.detail() + ". " + le.diagnosis().recovery())
                    + "\n\nUse \"Preparar ambiente\" em Pendências.\n\nNada foi executado.");
            return;
        }
        Optional<Path> input = host.chooseRunInput("Lista de entrada do teste autorizado (colunas: " + String.join(", ", cfg.inputs()) + ")");
        if (input.isEmpty()) return;
        Optional<Path> chosen = host.chooseRunOutput("Planilha de saída do teste autorizado", cfg.processName() + ".xlsx");
        if (chosen.isEmpty()) return;
        Path sheet = chosen.get().getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx") ? chosen.get()
                : chosen.get().resolveSibling(chosen.get().getFileName() + ".xlsx");
        String manifest;
        try {
            manifest = com.nekkora.studio.ai.pycheck.SupervisedRun.verifyGenerated(out);
        } catch (com.nekkora.studio.ai.pycheck.SupervisedRun.Refused r) {
            host.showText("Teste autorizado recusado", "O código da pasta de saída não corresponde ao que o Studio gerou (" + r.code() + "). Desenvolva de novo; "
                    + "código editado à mão não é executado.\n\nNada foi executado.");
            return;
        }
        long rows = csvRows(input.get());
        String message = "Vai acessar: " + String.join(", ", cfg.allowedOrigins()) + " — só estas origens (o framework bloqueia as demais e redirecionamentos "
                + "para fora).\nVai gravar: " + sheet + (Files.exists(sheet) ? " — o arquivo existente será SUBSTITUÍDO (troca atômica)." : "")
                + "\nLista: " + input.get() + (rows >= 0 ? " — " + rows + " linha(s)" : "") + "\nNavegador: Chrome visível. Código: revisão " + shortRev(manifest)
                + " (gerado e verificado), nekkora_rpa " + env.get().framework() + ".\n\nSomente as ações gravadas e documentadas no PDD. Não é execução de "
                + "produção. Cancelar fecha o navegador e a planilha não é gravada.";
        if (!host.confirm("Executar teste autorizado?", message, "Executar")) return;
        try {
            store.saveRunPaths(a.id(), input.get(), sheet);
        } catch (IOException ignored) {
            // conveniência: a próxima escolha começa vazia
        }
        Path runDir;
        try {
            Path base = store.folder(a.id()) == null ? Files.createTempDirectory("nekkora-teste-") : store.folder(a.id()).resolve("testes");
            runDir = base.resolve(clock.instant().toString().replace(':', '-').replace('.', '-'));
            Files.createDirectories(runDir);
        } catch (IOException e) {
            host.showText("Teste autorizado não iniciado", "A pasta da execução não pôde ser criada. Nada foi executado.");
            return;
        }
        running = true;
        cancelled.set(false);
        Path project = out;
        Path chosenInput = input.get();
        render(a, "Teste autorizado em andamento…", "Abrindo o navegador. Itens: 0. Use Cancelar para interromper.", com.nekkora.ui.core.Severity.INFO, List.of(),
                List.of());
        panel.runState(false, "Teste autorizado em andamento.");
        worker.submit(() -> {
            com.nekkora.studio.ai.pycheck.SupervisedRun.Outcome o = null;
            String error = null;
            try {
                o = com.nekkora.studio.ai.pycheck.SupervisedRun.run(env.get(), new com.nekkora.studio.ai.pycheck.SupervisedRun.Request(project, chosenInput,
                        sheet, runDir, true, java.time.Duration.ofMinutes(20)), p -> SwingUtilities.invokeLater(() -> runProgress(a, p, rows)), cancelled::get);
            } catch (com.nekkora.studio.ai.pycheck.SupervisedRun.Refused r) {
                error = r.code();
            } catch (IOException e) {
                error = "IO_ERROR";
            }
            Path provenance = null;
            if (o != null && cfg.spreadsheet()) {
                try {
                    String prov = com.nekkora.studio.ai.pycheck.RunProvenance.build(project, runDir, sheet, cfg);
                    provenance = runDir.resolve("proveniencia.json");
                    Files.writeString(provenance, prov, java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException | RuntimeException e) {
                    provenance = null;
                }
            }
            if (o != null) {
                var v = o.verification();
                com.nekkora.studio.ai.quality.SupervisedRunRecord rec = new com.nekkora.studio.ai.quality.SupervisedRunRecord(a.id().value().toString(), a.revision(),
                        o.codeRevision(), clock.instant().toString(), actorOf().name(), o.status().name(), o.exitCode(), o.counts(), o.outputStatus(), o.outputRows(),
                        o.outputDetail(), v != null && v.ok(), v == null ? "" : v.code(), v == null ? 0 : v.rows(), v == null ? List.of() : v.headers(),
                        v == null ? "" : v.sha256(), o.millis(), "chrome visível");
                try {
                    store.saveSupervisedRun(a.id(), rec.toJson());
                    Files.writeString(runDir.resolve("teste-autorizado.json"), rec.toJson(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    // o resultado ainda aparece na janela
                }
            }
            com.nekkora.studio.ai.pycheck.SupervisedRun.Outcome done = o;
            String err = error;
            Path prov = provenance;
            SwingUtilities.invokeLater(() -> runFinished(a, done, err, runDir, sheet, prov));
        });
    }

    private void runProgress(Automation a, com.nekkora.studio.ai.pycheck.SupervisedRun.Progress p, long total) {
        if (!running) return;
        String of = total > 0 ? " de " + total : "";
        render(a, "Teste autorizado em andamento…", "Itens iniciados: " + p.started() + of + " · concluídos: " + p.succeeded() + " · recusados: " + p.rejected()
                + " · falhas: " + p.failed() + " · fase " + p.phase() + " · " + (p.elapsedMs() / 1000) + " s. Use Cancelar para interromper.",
                com.nekkora.ui.core.Severity.INFO, List.of(), List.of());
    }

    private void runFinished(Automation a, com.nekkora.studio.ai.pycheck.SupervisedRun.Outcome o, String error, Path runDir, Path sheet, Path provenance) {
        running = false;
        List<String> results = new ArrayList<>();
        String title;
        com.nekkora.ui.core.Severity sev;
        if (o == null) {
            title = "Teste autorizado não executado (" + error + ")";
            sev = com.nekkora.ui.core.Severity.ERROR;
            results.add("× Teste autorizado recusado antes de iniciar: " + error + ". Nada foi executado.");
        } else {
            boolean proven = o.proven();
            title = proven ? "Teste autorizado terminou: planilha gravada e conferida" : "Teste autorizado terminou sem planilha conferida (" + o.status() + ")";
            sev = proven ? com.nekkora.ui.core.Severity.SUCCESS : com.nekkora.ui.core.Severity.WARNING;
            results.add((proven ? "✓ " : "× ") + "Teste autorizado: " + o.status() + " · saída " + (o.outputStatus().isEmpty() ? "—" : o.outputStatus())
                    + (o.outputDetail().isEmpty() ? "" : " (" + o.outputDetail() + ")") + " · " + (o.millis() / 1000) + " s");
            results.add("  Itens por estado: " + (o.counts().isEmpty() ? "nenhum" : o.counts().toString()));
            var v = o.verification();
            results.add(v == null ? "— Planilha não conferida (não foi gravada)." : (v.ok() ? "✓ " : "× ") + "Planilha reaberta: " + v.rows() + " linha(s), colunas "
                    + v.headers() + (v.code().isEmpty() ? "" : " — " + v.code()) + " · " + shortRev(v.sha256()));
            results.add("  Planilha: " + sheet);
            results.add("  Revisão do código executado: " + shortRev(o.codeRevision()));
        }
        results.add("  Execução (eventos sem valores, checkpoint, resumo): " + runDir);
        if (provenance != null) results.add("  Proveniência (coluna → etapa → alvo, separada da planilha): " + provenance);
        model = withPaths(new DevelopmentPanel.Model(a.name(), true, false, title, o != null && o.proven()
                ? "Execução supervisionada pela interface: o Studio lançou o código gerado, acompanhou e conferiu a planilha reabrindo o arquivo. V6 registrado."
                : "Nada foi dado por executado: veja o resultado e a pasta da execução.", sev, model.phases(), model.uses(), model.answers(), model.variables(),
                model.pending(), results, "", "", true, provider != null && provider.available()), a);
        panel.show(model);
        panel.focusResults();
        refreshRun(a);
        validateLayers(); // V6 da revisão atual (a aba Validação recebe o relatório novo)
    }

    /** Linhas de dados de uma lista CSV (sem o cabeçalho); -1 se não der para contar sem abrir o XLSX. */
    private static long csvRows(Path p) {
        if (!p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) return -1;
        try (var lines = Files.lines(p, java.nio.charset.StandardCharsets.UTF_8)) {
            return Math.max(0, lines.filter(l -> !l.isBlank()).count() - 1);
        } catch (IOException | java.io.UncheckedIOException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------------------------------- apoio

    private FunctionalSpec.Actor actorOf() {
        return actor == DecisionActor.TEST_AGENT ? FunctionalSpec.Actor.TEST_AGENT : FunctionalSpec.Actor.DEVELOPER;
    }

    private boolean pddReady(Automation a, Optional<DocumentDraft> draft, FunctionalSpec spec) {
        return com.nekkora.studio.ai.gate.PddGate.evaluate(ProjectContext.of(a, draft), draft, spec, com.nekkora.studio.pdd.ProcessProjection.of(a),
                clock.instant()).ready();
    }

    private Optional<StepId> lastStep(Automation a) {
        List<LogicalSession> s = a.sessions();
        for (int i = s.size() - 1; i >= 0; i--) if (!s.get(i).steps().isEmpty()) return Optional.of(s.get(i).steps().get(s.get(i).steps().size() - 1).id());
        return Optional.empty();
    }

    private Optional<Automation> active() {
        return service.state().activeAutomation();
    }

    private Optional<Path> projectFile(Automation a) {
        return service.state().project(a.id()).flatMap(ProjectState::path);
    }

    private Path outputDir(Automation a, Path project) {
        return store.options(a.id()).outputDir().orElse(GenerationOrchestrator.defaultOutput(project));
    }

    private boolean outputExists(Automation a) {
        return projectFile(a).map(p -> Files.isRegularFile(outputDir(a, p).resolve("manifest.json"))).orElse(false);
    }

    private DevelopmentPanel.Model withPaths(DevelopmentPanel.Model m, Automation a) {
        String out = projectFile(a).map(p -> outputDir(a, p).toString()).orElse("");
        var le = lastEnvironment;
        String py = le != null && le.environment().isPresent()
                ? le.environment().get().python() + " · nekkora-rpa " + le.environment().get().framework() + (le.configured() ? " (indicado)" : " (venv da automação)")
                : le != null ? "ambiente da automação: " + le.diagnosis().title()
                : store.options(a.id()).python().map(Path::toString).orElse(System.getenv("NEKKORA_PYTHON") == null ? "" : "NEKKORA_PYTHON");
        return new DevelopmentPanel.Model(m.subject(), m.projectSaved(), m.running(), m.statusTitle(), m.statusText(), m.severity(), m.phases(), m.uses(),
                m.answers(), m.variables(), m.pending(), m.results(), out, py, m.canOpenCode() || outputExists(a), provider != null && provider.available());
    }

    Optional<DocumentDraft> loadDraft(Automation a) {
        if (pdds == null) return draftSource.apply(a);
        try {
            return pdds.loadDraft(a.id());
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    FunctionalSpec loadSpec(Automation a) {
        try {
            return store.spec(a.id());
        } catch (IOException | RuntimeException e) {
            return FunctionalSpec.empty(a.id());
        }
    }

    /** Configuração tabular DECLARADA ao lado do projeto ({@code <projeto>.codegen.json}, contrato 1.0.0) — o caminho da E2E-001. */
    static Optional<CodegenConfig> declaredTabular(Path project) {
        Path f = project.resolveSibling(project.getFileName().toString().replaceFirst("\\.nekkora$", "") + ".codegen.json");
        if (!Files.isRegularFile(f)) return Optional.empty();
        try {
            return Optional.of(CodegenConfig.parse(Files.readString(f, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new CodegenConfig.Invalid("CONFIG_UNREADABLE");
        }
    }

    private void saveGate(GenerationOrchestrator.Run run) {
        try {
            store.saveGate(run.gate().record());
        } catch (IOException ignored) {
            // auxiliar
        }
    }

    public boolean running() {
        return running;
    }

    private Function<Automation, Optional<DocumentDraft>> draftSource = a -> Optional.empty();

    /** Sem diretório de dados (testes): de onde vem o rascunho do PDD (o PDD visual em memória). */
    public void useDraftSource(Function<Automation, Optional<DocumentDraft>> source) {
        this.draftSource = source == null ? a -> Optional.empty() : source;
    }

    public Optional<GenerationOrchestrator.Run> lastRun() {
        return Optional.ofNullable(last);
    }

    /** Espera o trabalho em segundo plano (testes e homologação). */
    public void awaitIdle() throws InterruptedException {
        java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
        worker.submit(l::countDown);
        l.await();
    }

    public void dispose() {
        cancelled.set(true);
        worker.shutdownNow();
        if (host != null) host.hide();
    }

    static Supplier<String> none() {
        return () -> "";
    }
}
