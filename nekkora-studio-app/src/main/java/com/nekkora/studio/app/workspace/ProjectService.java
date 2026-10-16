package com.nekkora.studio.app.workspace;

import com.nekkora.studio.app.project.RecentProjects;
import com.nekkora.studio.app.project.StudioSettings;
import com.nekkora.studio.app.ui.StudioPrompts;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.persistence.ProjectException;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectFormat;
import com.nekkora.studio.persistence.ProjectPackage;
import com.nekkora.studio.persistence.ProjectRecovery;
import com.nekkora.studio.persistence.ProjectRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * Casos de uso de PROJETO (STUDIO-006): salvar, salvar como, abrir, recarregar, fechar, encerrar com "Salvar todos" e
 * recuperação após falhas.
 *
 * <p>Threads (D-S006-10, §21): cada operação começa na EDT — que tira a fotografia consistente do domínio
 * ({@link ProjectFiles.Snapshot}) e marca o estado —, o trabalho de disco (compactar, gravar, reler, validar,
 * reconstruir) roda no {@code worker}, e o resultado volta à EDT, onde o workspace é alterado. Um projeto aberto só
 * entra no catálogo depois de TODAS as verificações (D-S006-07); nada parcial.
 *
 * <p>Sem execução: abrir um projeto não executa ação, seletor nem navegador (D-S006-15). Sem rede (D-S006-17). Logs só
 * técnicos — operação, resultado, duração, tamanho, código — nunca conteúdo nem caminho (§32).
 */
public final class ProjectService {

    /** Resultado de uma operação (para a interface e os testes). */
    public enum Outcome { SAVED, OPENED, ACTIVATED, CANCELLED, BUSY, RECORDING, FAILED, CONFLICT, CLOSED, RELOADED }

    /** Motivo mostrado quando a automação não está quiescente (§22). */
    public static final String RECORDING_MESSAGE = "Esta automação possui uma gravação em andamento. Finalize a gravação e aguarde a confirmação "
            + "das etapas antes de salvar o projeto.";

    private static final Logger LOG = Logger.getLogger(ProjectService.class.getName());

    private final WorkspaceService workspace;
    private final StudioState state;
    private final StudioPrompts prompts;
    private final ProjectFiles files;
    private final Executor worker;
    private final Clock clock;
    private final RecentProjects recents;
    private final StudioSettings settings;
    private final ProjectRecovery recovery;
    private Predicate<AutomationId> busy = id -> false;
    private javax.swing.Timer recoveryTimer;
    private volatile long lastSnapshotNanos;

    public ProjectService(WorkspaceService workspace, StudioPrompts prompts, ProjectFiles files, Executor worker, Clock clock, RecentProjects recents,
            StudioSettings settings, ProjectRecovery recovery) {
        this.workspace = Objects.requireNonNull(workspace);
        this.state = workspace.state();
        this.prompts = Objects.requireNonNull(prompts);
        this.files = Objects.requireNonNull(files);
        this.worker = Objects.requireNonNull(worker);
        this.clock = Objects.requireNonNull(clock);
        this.recents = Objects.requireNonNull(recents);
        this.settings = Objects.requireNonNull(settings);
        this.recovery = recovery;
    }

    /** A automação NÃO está quiescente para salvar (gravação ativa, etapas ou evidências pendentes ou aguardando). */
    public void setBusyGuard(Predicate<AutomationId> busy) {
        this.busy = Objects.requireNonNull(busy);
    }

    public RecentProjects recents() {
        return recents;
    }

    public StudioSettings settings() {
        return settings;
    }

    public Optional<ProjectRecovery> recovery() {
        return Optional.ofNullable(recovery);
    }

    /** Tempo da última fotografia na EDT (a única parte do salvamento que roda na thread de interface). */
    public long lastSnapshotNanos() {
        return lastSnapshotNanos;
    }

    // ================================================================================================== salvar

    /** Salvar: no arquivo associado; sem arquivo, "Salvar como". */
    public CompletableFuture<Outcome> save(AutomationId id) {
        requireEdt();
        Automation a = state.catalog().require(id);
        ProjectState p = project(id);
        Outcome blocked = blocked(id, p);
        if (blocked != null) return done(blocked);
        if (p.path().isEmpty()) return saveAs(id);
        return write(a, p, p.path().get(), p.fingerprint().orElse(null), false);
    }

    /** Salvar como: novo destino, MESMA identidade interna (a automação não é duplicada no catálogo). */
    public CompletableFuture<Outcome> saveAs(AutomationId id) {
        requireEdt();
        Automation a = state.catalog().require(id);
        ProjectState p = project(id);
        Outcome blocked = blocked(id, p);
        if (blocked != null) return done(blocked);
        Optional<Path> chosen = prompts.chooseSaveLocation(suggestedName(a.name()), p.path().map(Path::getParent).orElse(lastDirectory()));
        if (chosen.isEmpty()) return done(Outcome.CANCELLED);
        Path target = withExtension(chosen.get()).toAbsolutePath().normalize();
        if (p.path().map(target::equals).orElse(false)) return write(a, p, target, p.fingerprint().orElse(null), false);
        Optional<Automation> other = openWithPath(target).filter(x -> !x.equals(id)).flatMap(x -> state.catalog().find(x));
        if (other.isPresent()) {
            workspace.report(new Notice(Notice.Kind.ERROR, "Arquivo em uso por outro projeto",
                    "O arquivo escolhido já é o projeto aberto \"" + other.get().name() + "\". Escolha outro nome."));
            return done(Outcome.FAILED);
        }
        boolean overwrite = false;
        if (Files.exists(target)) {
            if (!confirm(new StudioPrompts.Confirmation("Substituir arquivo?", "Já existe \"" + target.getFileName() + "\" nesta pasta. Substituí-lo pelo projeto \""
                    + a.name() + "\"? O conteúdo atual desse arquivo será perdido.", "Substituir", true))) {
                return done(Outcome.CANCELLED);
            }
            overwrite = true;
        }
        return write(a, p, target, null, overwrite);
    }

    /** Salva, em sequência, as automações informadas; {@code true} só se TODAS ficaram salvas. */
    public CompletableFuture<Boolean> saveAll(List<AutomationId> ids) {
        requireEdt();
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (AutomationId id : ids) {
            chain = chain.thenCompose(ok -> {
                if (!ok) return CompletableFuture.completedFuture(false);
                if (state.catalog().find(id).isEmpty()) return CompletableFuture.completedFuture(true);
                return save(id).thenApply(o -> o == Outcome.SAVED && !project(id).dirty());
            });
        }
        return chain;
    }

    private Outcome blocked(AutomationId id, ProjectState p) {
        if (p.saving()) {
            workspace.report(new Notice(Notice.Kind.INFO, "Salvamento em andamento", "Aguarde o salvamento anterior deste projeto terminar."));
            return Outcome.BUSY;
        }
        if (busy.test(id)) {
            workspace.report(new Notice(Notice.Kind.WARNING, "Salvamento indisponível durante a gravação", RECORDING_MESSAGE));
            return Outcome.RECORDING;
        }
        return null;
    }

    private CompletableFuture<Outcome> write(Automation a, ProjectState p, Path target, ProjectRepository.Fingerprint expected, boolean overwrite) {
        AutomationId id = a.id();
        String projectId = p.projectId().orElseGet(() -> UUID.randomUUID().toString());
        long generation = p.generation();
        long t0 = System.nanoTime();
        ProjectFiles.Snapshot snapshot = ProjectFiles.Snapshot.of(a, projectId); // EDT: único ponto consistente
        lastSnapshotNanos = System.nanoTime() - t0;
        p.savingStarted();
        p.clearError();
        state.fire(id, StudioChange.PROJECT);
        Instant at = clock.instant();
        Clock domainClock = state.catalog().clock();
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        worker.execute(() -> {
            long w0 = System.nanoTime();
            try {
                ProjectRepository.Saved saved = files.save(target, snapshot, expected, overwrite, at, domainClock);
                log("salvar", "ok", w0, saved.fingerprint().size(), null);
                SwingUtilities.invokeLater(() -> result.complete(onSaved(id, p, saved, projectId, generation, at)));
            } catch (ProjectException e) {
                log("salvar", "erro", w0, -1, e.error());
                SwingUtilities.invokeLater(() -> result.complete(onSaveFailed(id, p, e, target)));
            } catch (RuntimeException e) {
                log("salvar", "erro", w0, -1, ProjectException.Error.WRITE_FAILED);
                SwingUtilities.invokeLater(() -> result.complete(onSaveFailed(id, p, new ProjectException(ProjectException.Error.WRITE_FAILED, "", e), target)));
            }
        });
        return result;
    }

    private Outcome onSaved(AutomationId id, ProjectState p, ProjectRepository.Saved saved, String projectId, long generation, Instant at) {
        p.saved(saved.path(), projectId, saved.fingerprint(), generation, at);
        recents.touch(saved.path(), at, ProjectFormat.FORMAT_VERSION);
        if (recovery != null) worker.execute(() -> recovery.discard(id, generation)); // superadas pelo salvamento confirmado
        String name = state.catalog().find(id).map(Automation::name).orElse("Projeto");
        String pending = p.dirty() ? " Há alterações feitas durante o salvamento que ainda não foram salvas." : "";
        workspace.report(new Notice(Notice.Kind.SUCCESS, "Projeto salvo", name + " — " + saved.path().getFileName() + "." + pending
                + (saved.atomic() ? "" : " (substituição com cópia de segurança: o sistema de arquivos não oferece troca atômica)")));
        state.fire(id, StudioChange.PROJECT, StudioChange.UNSAVED_CHANGES);
        return Outcome.SAVED;
    }

    private Outcome onSaveFailed(AutomationId id, ProjectState p, ProjectException e, Path target) {
        p.failed(e.error());
        state.fire(id, StudioChange.PROJECT, StudioChange.UNSAVED_CHANGES);
        String name = state.catalog().find(id).map(Automation::name).orElse("Projeto");
        if (e.error() == ProjectException.Error.CONFLICT && p.path().map(target::equals).orElse(false)) {
            switch (prompts.resolveConflict(name)) {
                case SAVE_AS -> saveAs(id);
                case RELOAD -> reload(id);
                case CANCEL -> { }
            }
            return Outcome.CONFLICT;
        }
        boolean existed = Files.exists(target);
        workspace.report(new Notice(Notice.Kind.ERROR, "Erro ao salvar", e.error().message() + (existed ? " O arquivo anterior foi preservado." : "")
                + " As alterações continuam abertas no Studio."));
        return e.error() == ProjectException.Error.CONFLICT ? Outcome.CONFLICT : Outcome.FAILED;
    }

    // ================================================================================================== abrir

    /** Abrir projeto: escolhe o arquivo e abre. */
    public CompletableFuture<Outcome> open() {
        requireEdt();
        Optional<Path> chosen = prompts.chooseProjectToOpen(lastDirectory());
        return chosen.isEmpty() ? done(Outcome.CANCELLED) : open(chosen.get());
    }

    /** Abre o arquivo; se já está aberto, apenas ativa a automação (nunca duas instâncias com as mesmas identidades). */
    public CompletableFuture<Outcome> open(Path path) {
        requireEdt();
        Path file = path.toAbsolutePath().normalize();
        Optional<AutomationId> already = openWithPath(file);
        if (already.isPresent()) {
            workspace.openAutomation(already.get());
            return done(Outcome.ACTIVATED);
        }
        Clock domainClock = state.catalog().clock();
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        worker.execute(() -> {
            long w0 = System.nanoTime();
            try {
                ProjectFiles.Loaded l = files.load(file, domainClock);
                if (l.manifest().kind() != ProjectFormat.Kind.PROJECT) throw new ProjectException(ProjectException.Error.NOT_A_PROJECT, "recuperação");
                log("abrir", "ok", w0, l.fingerprint().size(), null);
                SwingUtilities.invokeLater(() -> result.complete(adoptOpened(file, l)));
            } catch (ProjectException e) {
                log("abrir", "erro", w0, -1, e.error());
                SwingUtilities.invokeLater(() -> {
                    workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível abrir o projeto", e.error().message() + " Nada foi carregado."));
                    result.complete(Outcome.FAILED);
                });
            } catch (RuntimeException e) {
                log("abrir", "erro", w0, -1, ProjectException.Error.CORRUPTED);
                SwingUtilities.invokeLater(() -> {
                    workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível abrir o projeto", ProjectException.Error.CORRUPTED.message()));
                    result.complete(Outcome.FAILED);
                });
            }
        });
        return result;
    }

    private Outcome adoptOpened(Path file, ProjectFiles.Loaded l) {
        Automation a = l.automation();
        Optional<Automation> same = state.catalog().find(a.id());
        if (same.isPresent()) {
            if (project(a.id()).path().map(file::equals).orElse(false)) {
                workspace.openAutomation(a.id());
                return Outcome.ACTIVATED;
            }
            workspace.report(new Notice(Notice.Kind.ERROR, "Projeto com a mesma identidade já aberto", "A automação \"" + same.get().name()
                    + "\" (de outro arquivo) tem a mesma identidade deste projeto. Feche-a antes de abrir este arquivo; nada foi substituído."));
            return Outcome.CONFLICT;
        }
        try {
            state.catalog().adopt(a);
        } catch (DomainValidationException e) {
            workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível abrir o projeto", e.problem().message() + " Renomeie ou feche a outra automação."));
            return Outcome.FAILED;
        }
        workspace.history().clear(a.id()); // D-S006-09: o histórico começa vazio
        state.putProject(a.id(), ProjectState.opened(file, l.manifest().projectId(), l.fingerprint(), l.manifest().savedAt()));
        recents.touch(file, clock.instant(), l.manifest().formatVersion());
        show(a.id());
        workspace.report(new Notice(Notice.Kind.SUCCESS, "Projeto aberto", a.name() + " — " + file.getFileName() + "."));
        return Outcome.OPENED;
    }

    /** Recarrega do arquivo, DESCARTANDO as alterações locais — só com confirmação (conflito). */
    public CompletableFuture<Outcome> reload(AutomationId id) {
        requireEdt();
        ProjectState p = project(id);
        Path file = p.path().orElse(null);
        if (file == null || busy.test(id)) return done(Outcome.FAILED);
        if (!confirm(new StudioPrompts.Confirmation("Recarregar do arquivo?", "As alterações deste projeto que existem só no Studio serão "
                + "descartadas e o conteúdo atual do arquivo será aberto.", "Descartar e recarregar", true))) {
            return done(Outcome.CANCELLED);
        }
        Clock domainClock = state.catalog().clock();
        CompletableFuture<Outcome> result = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                ProjectFiles.Loaded l = files.load(file, domainClock);
                if (!l.automation().id().equals(id) || l.manifest().kind() != ProjectFormat.Kind.PROJECT) {
                    throw new ProjectException(ProjectException.Error.INTEGRITY, "outra automação");
                }
                SwingUtilities.invokeLater(() -> {
                    remove(id, false);
                    result.complete(adoptOpened(file, l) == Outcome.OPENED ? Outcome.RELOADED : Outcome.FAILED);
                });
            } catch (ProjectException e) {
                SwingUtilities.invokeLater(() -> {
                    workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível recarregar", e.error().message() + " O projeto aberto não mudou."));
                    result.complete(Outcome.FAILED);
                });
            }
        });
        return result;
    }

    // ================================================================================================== fechar e sair

    /** Fecha o projeto (retira do catálogo), perguntando antes se há alterações não salvas. */
    public CompletableFuture<Outcome> closeProject(AutomationId id) {
        requireEdt();
        Automation a = state.catalog().find(id).orElse(null);
        if (a == null) return done(Outcome.CLOSED);
        if (busy.test(id)) {
            workspace.report(new Notice(Notice.Kind.WARNING, "Projeto em gravação", "Finalize ou cancele a gravação antes de fechar o projeto."));
            return done(Outcome.RECORDING);
        }
        ProjectState p = project(id);
        if (p.saving()) return done(Outcome.BUSY);
        if (!p.dirty()) return done(remove(id, false));
        return switch (ask(List.of(a.name()), false)) {
            case CANCEL -> done(Outcome.CANCELLED);
            case DISCARD -> done(remove(id, true));
            case SAVE -> save(id).thenApply(o -> o == Outcome.SAVED && !project(id).dirty() ? remove(id, false) : o);
        };
    }

    /**
     * Encerramento: {@code true} se o Studio pode fechar. Com alterações não salvas: Salvar todos (só fecha se TODOS
     * ficarem salvos), Descartar (descarte autorizado, inclusive das fotografias de recuperação) ou Cancelar.
     */
    public CompletableFuture<Boolean> confirmExit() {
        requireEdt();
        List<Automation> dirty = state.unsavedAutomations();
        if (dirty.isEmpty()) return CompletableFuture.completedFuture(true);
        return switch (ask(dirty.stream().map(Automation::name).toList(), true)) {
            case CANCEL -> CompletableFuture.completedFuture(false);
            case DISCARD -> {
                if (recovery != null) for (Automation a : dirty) worker.execute(() -> recovery.discardAll(a.id()));
                yield CompletableFuture.completedFuture(true);
            }
            case SAVE -> saveAll(dirty.stream().map(Automation::id).toList()).thenApply(ok -> {
                if (!ok) workspace.report(new Notice(Notice.Kind.WARNING, "O Studio continua aberto",
                        "Nem todos os projetos foram salvos. Revise os avisos e tente novamente."));
                return ok;
            });
        };
    }

    private StudioPrompts.UnsavedChoice ask(List<String> names, boolean exit) {
        if (!workspace.beginOperation(exit ? StudioOperation.EXIT : StudioOperation.CLOSE_PROJECT)) return StudioPrompts.UnsavedChoice.CANCEL;
        try {
            return prompts.confirmUnsaved(names, exit);
        } finally {
            workspace.endOperation();
        }
    }

    private Outcome remove(AutomationId id, boolean discardRecovery) {
        workspace.closeAutomation(id);
        state.catalog().remove(id);
        state.forgetProject(id);
        workspace.history().clear(id);
        if (discardRecovery && recovery != null) worker.execute(() -> recovery.discardAll(id));
        state.fire(id, StudioChange.CATALOG, StudioChange.OPEN_AUTOMATIONS, StudioChange.ACTIVE_AUTOMATION, StudioChange.SELECTION,
                StudioChange.UNSAVED_CHANGES, StudioChange.PROJECT, StudioChange.HISTORY);
        return Outcome.CLOSED;
    }

    // ================================================================================================== recuperação

    /** Diálogo da recuperação automática (consentimento informado, intervalo, limpeza). */
    public void configureRecovery() {
        requireEdt();
        if (recovery == null) {
            workspace.report(new Notice(Notice.Kind.INFO, "Recuperação indisponível", "Esta sessão do Studio não tem um diretório de dados local."));
            return;
        }
        if (!workspace.beginOperation(StudioOperation.RECOVERY)) return;
        Optional<StudioPrompts.RecoveryPreferences> r;
        try {
            r = prompts.editRecovery(new StudioPrompts.RecoveryPreferences(settings.recoveryEnabled(), settings.recoveryIntervalSeconds(), false),
                    StudioSettings.INTERVALS, recoveryExplanation());
        } finally {
            workspace.endOperation();
        }
        if (r.isEmpty()) return;
        applyRecovery(r.get().enabled(), r.get().intervalSeconds(), r.get().clear());
    }

    /** Aplica a decisão sobre a recuperação automática. */
    public void applyRecovery(boolean enabled, int intervalSeconds, boolean clear) {
        requireEdt();
        settings.setRecovery(enabled, intervalSeconds);
        if (clear && recovery != null) worker.execute(recovery::clear);
        restartRecoveryTimer();
        workspace.report(new Notice(Notice.Kind.SUCCESS, "Recuperação automática " + (enabled ? "ativada" : "desativada"),
                enabled ? "Fotografias das alterações não salvas a cada " + (settings.recoveryIntervalSeconds() / 60) + " min." : "Nenhuma fotografia nova será criada."
                        + (clear ? " Os arquivos de recuperação guardados serão apagados." : "")));
        state.fire(null, StudioChange.PROJECT);
    }

    public String recoveryExplanation() {
        return "Quando ativada, o Studio guarda periodicamente uma cópia das alterações AINDA NÃO SALVAS de cada projeto, para oferecê-la se o "
                + "Studio for encerrado inesperadamente. A cópia não substitui o projeto e não é um salvamento.\n\nLocal: "
                + (recovery == null ? "—" : recovery.directory()) + "\nRetenção: até " + ProjectRecovery.MAX_VERSIONS + " cópias por projeto, apagadas "
                + "depois de " + ProjectRecovery.RETENTION.toDays() + " dias, depois de um salvamento confirmado ou quando você pedir.\n\n"
                + "Os arquivos são locais e NÃO são criptografados: têm o mesmo conteúdo do projeto. O Studio não guarda valores digitados nem "
                + "segredos de credenciais.";
    }

    /** (Re)inicia a fotografia periódica conforme a configuração. */
    public void restartRecoveryTimer() {
        requireEdt();
        if (recoveryTimer != null) recoveryTimer.stop();
        recoveryTimer = null;
        if (recovery == null || !settings.recoveryEnabled()) return;
        recoveryTimer = new javax.swing.Timer(settings.recoveryIntervalSeconds() * 1000, e -> checkpoint());
        recoveryTimer.setRepeats(true);
        recoveryTimer.start();
    }

    /**
     * Fotografa as automações com alterações ainda não fotografadas (ponto do ciclo de vida ou intervalo). Não fotografa
     * durante gravação nem durante salvamento. Só com consentimento.
     *
     * @return quantas fotografias foram gravadas
     */
    public CompletableFuture<Integer> checkpoint() {
        requireEdt();
        if (recovery == null || !settings.recoveryEnabled()) return CompletableFuture.completedFuture(0);
        List<CompletableFuture<Boolean>> all = new ArrayList<>();
        Clock domainClock = state.catalog().clock();
        for (Automation a : state.catalog().all()) {
            ProjectState p = project(a.id());
            if (!p.dirty() || p.generation() == p.recoveryGeneration() || p.saving() || busy.test(a.id())) continue;
            String projectId = p.projectId().orElseGet(() -> UUID.randomUUID().toString());
            long generation = p.generation();
            ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, projectId);
            ProjectPackage.RecoveryOrigin origin = new ProjectPackage.RecoveryOrigin(p.path().map(Path::toString).orElse(null),
                    p.path().isPresent() ? p.fingerprint().orElse(null) : null);
            CompletableFuture<Boolean> f = new CompletableFuture<>();
            all.add(f);
            Instant at = clock.instant();
            worker.execute(() -> {
                long w0 = System.nanoTime();
                try {
                    recovery.write(s, generation, origin, at, domainClock);
                    log("recuperacao", "ok", w0, -1, null);
                    SwingUtilities.invokeLater(() -> {
                        p.recoveryWritten(generation);
                        f.complete(true);
                    });
                } catch (ProjectException | RuntimeException e) {
                    log("recuperacao", "erro", w0, -1, e instanceof ProjectException pe ? pe.error() : ProjectException.Error.WRITE_FAILED);
                    SwingUtilities.invokeLater(() -> f.complete(false));
                }
            });
        }
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new))
                .thenApply(v -> (int) all.stream().filter(CompletableFuture::join).count());
    }

    /**
     * Ao abrir o Studio: oferece, uma a uma, as fotografias de recuperação VÁLIDAS (já verificadas). Nunca restaura em
     * silêncio, nunca substitui o arquivo do projeto e o projeto recuperado continua NÃO salvo.
     *
     * @return quantas foram recuperadas
     */
    public CompletableFuture<Integer> offerRecoveries() {
        requireEdt();
        if (recovery == null) return CompletableFuture.completedFuture(0);
        Clock domainClock = state.catalog().clock();
        CompletableFuture<Integer> result = new CompletableFuture<>();
        worker.execute(() -> {
            List<ProjectRecovery.Candidate> found = recovery.scan(domainClock);
            SwingUtilities.invokeLater(() -> {
                int recovered = 0;
                for (ProjectRecovery.Candidate c : found) {
                    Automation a = c.loaded().automation();
                    if (state.catalog().find(a.id()).isPresent()) continue;
                    ProjectPackage.RecoveryOrigin o = c.loaded().manifest().recoveryOf();
                    String origin = o == null || o.projectPath() == null ? null : Path.of(o.projectPath()).getFileName().toString();
                    StudioPrompts.RecoveryChoice choice = prompts.offerRecovery(new StudioPrompts.RecoveryOffer(a.name(), c.savedAt(), origin));
                    if (choice == StudioPrompts.RecoveryChoice.CANCEL) break;
                    if (choice == StudioPrompts.RecoveryChoice.IGNORE) continue;
                    if (adoptRecovered(c)) recovered++;
                }
                result.complete(recovered);
            });
        });
        return result;
    }

    private boolean adoptRecovered(ProjectRecovery.Candidate c) {
        Automation a = c.loaded().automation();
        try {
            state.catalog().adopt(a);
        } catch (DomainValidationException e) {
            workspace.report(new Notice(Notice.Kind.ERROR, "Recuperação não aberta", e.problem().message()));
            return false;
        }
        workspace.history().clear(a.id());
        ProjectPackage.RecoveryOrigin o = c.loaded().manifest().recoveryOf();
        Path path = o == null || o.projectPath() == null ? null : Path.of(o.projectPath());
        ProjectState p = ProjectState.recovered(path, c.loaded().manifest().projectId(), o == null ? null : o.base(), c.generation() + 1);
        p.recoveryWritten(c.generation());
        state.putProject(a.id(), p);
        show(a.id());
        workspace.report(new Notice(Notice.Kind.INFO, "Alterações recuperadas", a.name() + " continua NÃO salvo: salve para confirmar. "
                + (path == null ? "" : "O arquivo " + path.getFileName() + " não foi alterado.")));
        return true;
    }

    public void dispose() {
        if (recoveryTimer != null) recoveryTimer.stop();
    }

    // ================================================================================================== apoio

    private void show(AutomationId id) {
        state.open(id);
        state.activate(id);
        state.select(id, new Selection.OfAutomation(id));
        state.fire(id, StudioChange.CATALOG, StudioChange.OPEN_AUTOMATIONS, StudioChange.ACTIVE_AUTOMATION, StudioChange.SELECTION, StudioChange.PROJECT,
                StudioChange.UNSAVED_CHANGES, StudioChange.HISTORY);
    }

    private ProjectState project(AutomationId id) {
        return state.project(id).orElseGet(() -> {
            ProjectState p = new ProjectState(0);
            state.putProject(id, p);
            return p;
        });
    }

    private Optional<AutomationId> openWithPath(Path file) {
        for (Automation a : state.catalog().all()) {
            if (state.project(a.id()).flatMap(ProjectState::path).map(file::equals).orElse(false)) return Optional.of(a.id());
        }
        return Optional.empty();
    }

    private Path lastDirectory() {
        return recents.entries().stream().map(e -> e.path().getParent()).filter(d -> d != null && Files.isDirectory(d)).findFirst().orElse(null);
    }

    private boolean confirm(StudioPrompts.Confirmation c) {
        if (!workspace.beginOperation(StudioOperation.CONFIRM)) return false;
        try {
            return prompts.confirm(c);
        } finally {
            workspace.endOperation();
        }
    }

    /** Nome de arquivo sugerido a partir do nome da automação (só caracteres seguros para nomes de arquivo). */
    static String suggestedName(String automation) {
        String n = automation.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
        if (n.isEmpty()) n = "Projeto";
        if (n.length() > 80) n = n.substring(0, 80).strip();
        return n + ProjectFormat.SUFFIX;
    }

    static Path withExtension(Path p) {
        String name = p.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(ProjectFormat.SUFFIX) ? p : p.resolveSibling(name + ProjectFormat.SUFFIX);
    }

    private static CompletableFuture<Outcome> done(Outcome o) {
        return CompletableFuture.completedFuture(o);
    }

    private static void log(String operation, String result, long startNanos, long bytes, ProjectException.Error error) {
        LOG.info(String.format(Locale.ROOT, "projeto operacao=%s resultado=%s duracaoMs=%d bytes=%d codigo=%s", operation, result,
                (System.nanoTime() - startNanos) / 1_000_000, bytes, error == null ? "-" : error.name()));
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Operações de projeto começam na thread de interface (EDT).");
        }
    }
}
