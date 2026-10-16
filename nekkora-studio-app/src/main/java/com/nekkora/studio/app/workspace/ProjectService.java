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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
 * <p>Integridade (revisão 1 do PR #8): nenhuma operação informa sucesso nem destrói o estado anterior antes de concluir
 * as suas garantias. Recarregar só retira a automação atual depois que a substituta foi admitida pelo catálogo; salvar
 * só é informado depois de apagar as fotografias de recuperação que ele supera; descartar e sair só encerra depois de
 * apagá-las (ou com a decisão informada do usuário, se a exclusão falhar); Salvar como confere, antes da troca, o conteúdo
 * do destino que o usuário autorizou substituir. Todo trabalho de disco fica registrado até terminar, e o encerramento o
 * espera sem bloquear a EDT.
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
    /** Trabalho de disco em andamento no worker (o encerramento espera por ele). Confinado à EDT. */
    private final List<CompletableFuture<?>> pending = new ArrayList<>();
    /** Fotografias que deveriam ter sido apagadas e não foram: refeitas ao sair. Confinado à EDT. */
    private final Map<AutomationId, Debt> cleanupDebt = new LinkedHashMap<>();
    /** Encerramento aguardando gravações ou limpezas. */
    private boolean exiting;

    /** Limpeza de recuperação pendente: nome (para o aviso) e até qual geração apagar. */
    private record Debt(String name, long upTo) {}

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
        if (!Files.exists(target)) return write(a, p, target, null, false);
        // a confirmação vale para o conteúdo que existe AGORA no destino: a impressão digital dele é lida fora da EDT antes
        // da pergunta e conferida de novo imediatamente antes da troca — o que mudar depois da confirmação é conflito
        return background(() -> current(target)).thenCompose(c -> overwrite(id, target, c));
    }

    /** Conteúdo atual de um destino (ou o erro ao lê-lo). */
    private record Current(ProjectRepository.Fingerprint fingerprint, ProjectException error) {}

    private Current current(Path target) {
        try {
            return new Current(files.repository().fingerprintOf(target), null);
        } catch (ProjectException e) {
            return new Current(null, e);
        } catch (RuntimeException e) {
            return new Current(null, new ProjectException(ProjectException.Error.CORRUPTED, "", e));
        }
    }

    private CompletableFuture<Outcome> overwrite(AutomationId id, Path target, Current current) {
        Automation a = state.catalog().find(id).orElse(null);
        if (a == null) return done(Outcome.FAILED);
        ProjectState p = project(id);
        Outcome blocked = blocked(id, p);
        if (blocked != null) return done(blocked);
        if (current.error() != null) {
            workspace.report(new Notice(Notice.Kind.ERROR, "Erro ao salvar", current.error().error().message()
                    + " Nada foi sobrescrito. As alterações continuam abertas no Studio."));
            return done(Outcome.FAILED);
        }
        if (current.fingerprint() == null) return write(a, p, target, null, false); // removido nesse meio-tempo: arquivo novo
        if (!confirm(new StudioPrompts.Confirmation("Substituir arquivo?", "Já existe \"" + target.getFileName() + "\" nesta pasta. Substituí-lo pelo projeto \""
                + a.name() + "\"? O conteúdo atual desse arquivo será perdido.", "Substituir", true))) {
            return done(Outcome.CANCELLED);
        }
        blocked = blocked(id, p);
        if (blocked != null) return done(blocked);
        return write(a, p, target, current.fingerprint(), false);
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
        if (p.reloading()) {
            workspace.report(new Notice(Notice.Kind.INFO, "Recarregamento em andamento", "Aguarde o recarregamento deste projeto terminar."));
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
        boolean confirmedOverwrite = expected != null && !p.path().map(target::equals).orElse(false);
        return background(() -> {
            long w0 = System.nanoTime();
            try {
                ProjectRepository.Saved saved = files.save(target, snapshot, expected, overwrite, at, domainClock);
                log("salvar", "ok", w0, saved.fingerprint().size(), null);
                // as fotografias superadas por este salvamento são apagadas ANTES de o sucesso ser informado
                return new Written(saved, discardOnWorker(id, generation), null);
            } catch (ProjectException e) {
                log("salvar", "erro", w0, -1, e.error());
                return new Written(null, false, e);
            } catch (RuntimeException e) {
                log("salvar", "erro", w0, -1, ProjectException.Error.WRITE_FAILED);
                return new Written(null, false, new ProjectException(ProjectException.Error.WRITE_FAILED, "", e));
            }
        }).thenApply(w -> w.error() == null ? onSaved(id, p, w.saved(), projectId, generation, at, w.cleaned())
                : onSaveFailed(id, p, w.error(), target, confirmedOverwrite));
    }

    /** Resultado do trabalho de disco de um salvamento. */
    private record Written(ProjectRepository.Saved saved, boolean cleaned, ProjectException error) {}

    private Outcome onSaved(AutomationId id, ProjectState p, ProjectRepository.Saved saved, String projectId, long generation, Instant at, boolean cleaned) {
        p.saved(saved.path(), projectId, saved.fingerprint(), generation, at);
        recents.touch(saved.path(), at, ProjectFormat.FORMAT_VERSION);
        String name = state.catalog().find(id).map(Automation::name).orElse("Projeto");
        if (cleaned) settleDebt(id, generation);
        else owe(id, name, generation);
        String pending = p.dirty() ? " Há alterações feitas durante o salvamento que ainda não foram salvas." : "";
        String cleanup = cleaned ? "" : " Uma fotografia de recuperação antiga deste projeto não pôde ser apagada; o Studio tentará de novo ao sair.";
        workspace.report(new Notice(cleaned ? Notice.Kind.SUCCESS : Notice.Kind.WARNING, "Projeto salvo", name + " — " + saved.path().getFileName() + "."
                + pending + (saved.atomic() ? "" : " (substituição com cópia de segurança: o sistema de arquivos não oferece troca atômica)") + cleanup));
        state.fire(id, StudioChange.PROJECT, StudioChange.UNSAVED_CHANGES);
        return Outcome.SAVED;
    }

    private Outcome onSaveFailed(AutomationId id, ProjectState p, ProjectException e, Path target, boolean confirmedOverwrite) {
        boolean ownFile = p.path().map(target::equals).orElse(false);
        boolean conflict = e.error() == ProjectException.Error.CONFLICT;
        // conflito no destino ESCOLHIDO (não no arquivo do projeto): o projeto não está em conflito, o salvamento falhou
        p.failed(conflict && !ownFile ? ProjectException.Error.WRITE_FAILED : e.error());
        state.fire(id, StudioChange.PROJECT, StudioChange.UNSAVED_CHANGES);
        String name = state.catalog().find(id).map(Automation::name).orElse("Projeto");
        if (conflict && ownFile) {
            switch (prompts.resolveConflict(name)) {
                case SAVE_AS -> saveAs(id);
                case RELOAD -> reload(id);
                case CANCEL -> { }
            }
            return Outcome.CONFLICT;
        }
        if (conflict) {
            workspace.report(new Notice(Notice.Kind.ERROR, "Erro ao salvar", (confirmedOverwrite
                    ? "O arquivo \"" + target.getFileName() + "\" foi alterado por outro programa depois da sua confirmação de substituição."
                    : "O arquivo \"" + target.getFileName() + "\" passou a existir enquanto o projeto era gravado.")
                    + " Nada foi sobrescrito: use Salvar como de novo para decidir sobre o conteúdo atual. As alterações continuam abertas no Studio."));
            return Outcome.CONFLICT;
        }
        boolean existed = Files.exists(target);
        workspace.report(new Notice(Notice.Kind.ERROR, "Erro ao salvar", e.error().message() + (existed ? " O arquivo anterior foi preservado." : "")
                + " As alterações continuam abertas no Studio."));
        return Outcome.FAILED;
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
        return background(() -> read("abrir", file, domainClock, null)).thenApply(r -> {
            if (r.error() == null) return adoptOpened(file, r.loaded());
            workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível abrir o projeto", r.error().error().message() + " Nada foi carregado."));
            return Outcome.FAILED;
        });
    }

    /** Leitura completa de um projeto no worker (ou o erro); {@code expected}, se informado, é a identidade exigida. */
    private record Read(ProjectFiles.Loaded loaded, ProjectException error) {}

    private Read read(String operation, Path file, Clock domainClock, AutomationId expected) {
        long w0 = System.nanoTime();
        try {
            ProjectFiles.Loaded l = files.load(file, domainClock);
            if (l.manifest().kind() != ProjectFormat.Kind.PROJECT) throw new ProjectException(ProjectException.Error.NOT_A_PROJECT, "recuperação");
            if (expected != null && !l.automation().id().equals(expected)) throw new ProjectException(ProjectException.Error.INTEGRITY, "outra automação");
            log(operation, "ok", w0, l.fingerprint().size(), null);
            return new Read(l, null);
        } catch (ProjectException e) {
            log(operation, "erro", w0, -1, e.error());
            return new Read(null, e);
        } catch (RuntimeException e) {
            log(operation, "erro", w0, -1, ProjectException.Error.CORRUPTED);
            return new Read(null, new ProjectException(ProjectException.Error.CORRUPTED, "", e));
        }
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

    /**
     * Recarrega do arquivo, DESCARTANDO as alterações locais — só com confirmação (conflito). Transacional do ponto de
     * vista do workspace: a automação atual só é trocada depois que a substituta foi lida, validada e ADMITIDA pelo
     * catálogo (identidade e nome únicos); qualquer recusa deixa automação, alterações não salvas, seleção, histórico,
     * arquivo e recuperação exatamente como estavam. Não começa durante um salvamento ou outro recarregamento do mesmo
     * projeto, e salvar ou fechar esperam o recarregamento terminar.
     */
    public CompletableFuture<Outcome> reload(AutomationId id) {
        requireEdt();
        ProjectState p = project(id);
        Path file = p.path().orElse(null);
        if (file == null) return done(Outcome.FAILED);
        Outcome blocked = reloadBlocked(id, p);
        if (blocked != null) return done(blocked);
        if (!confirm(new StudioPrompts.Confirmation("Recarregar do arquivo?", "As alterações deste projeto que existem só no Studio serão "
                + "descartadas e o conteúdo atual do arquivo será aberto.", "Descartar e recarregar", true))) {
            return done(Outcome.CANCELLED);
        }
        blocked = reloadBlocked(id, p);
        if (blocked != null) return done(blocked);
        p.reloadingStarted();
        state.fire(id, StudioChange.PROJECT);
        Clock domainClock = state.catalog().clock();
        return background(() -> read("recarregar", file, domainClock, id)).thenCompose(r -> {
            p.reloadingEnded();
            state.fire(id, StudioChange.PROJECT);
            if (r.error() != null) {
                workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível recarregar", r.error().error().message() + " O projeto aberto não mudou."));
                return done(Outcome.FAILED);
            }
            return adoptReloaded(id, p, file, r.loaded());
        });
    }

    private Outcome reloadBlocked(AutomationId id, ProjectState p) {
        if (p.saving() || p.reloading()) {
            workspace.report(new Notice(Notice.Kind.INFO, "Operação em andamento", "Aguarde o salvamento ou o recarregamento deste projeto terminar."));
            return Outcome.BUSY;
        }
        if (busy.test(id)) {
            workspace.report(new Notice(Notice.Kind.WARNING, "Recarregar indisponível durante a gravação",
                    "Finalize a gravação e aguarde a confirmação das etapas antes de recarregar o projeto."));
            return Outcome.RECORDING;
        }
        return null;
    }

    private CompletableFuture<Outcome> adoptReloaded(AutomationId id, ProjectState p, Path file, ProjectFiles.Loaded l) {
        if (state.catalog().find(id).isEmpty() || state.project(id).orElse(null) != p) {
            workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível recarregar", "O projeto foi fechado durante a leitura; nada foi aberto."));
            return done(Outcome.FAILED);
        }
        if (busy.test(id)) {
            workspace.report(new Notice(Notice.Kind.WARNING, "Recarregar indisponível durante a gravação",
                    "Uma gravação começou durante a leitura. O projeto aberto não mudou."));
            return done(Outcome.RECORDING);
        }
        try {
            state.catalog().replace(l.automation()); // única etapa que pode recusar: valida tudo antes de trocar
        } catch (DomainValidationException e) {
            workspace.report(new Notice(Notice.Kind.ERROR, "Não foi possível recarregar", e.problem().message()
                    + " O projeto aberto não mudou; feche ou renomeie a outra automação para recarregar este arquivo."));
            return done(Outcome.FAILED);
        }
        // a substituta foi admitida: daqui em diante nada falha
        Automation a = l.automation();
        workspace.closeAutomation(id);
        workspace.history().clear(id); // D-S006-09: o histórico começa vazio
        state.putProject(id, ProjectState.opened(file, l.manifest().projectId(), l.fingerprint(), l.manifest().savedAt()));
        recents.touch(file, clock.instant(), l.manifest().formatVersion());
        show(id);
        workspace.report(new Notice(Notice.Kind.SUCCESS, "Projeto recarregado", a.name() + " — " + file.getFileName()
                + ". As alterações que existiam só no Studio foram descartadas."));
        // as fotografias de recuperação guardavam justamente as alterações descartadas
        return cleanRecovery(id, a.name(), Long.MAX_VALUE).thenApply(ok -> Outcome.RELOADED);
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
        if (p.saving() || p.reloading()) return done(Outcome.BUSY);
        if (!p.dirty()) return done(remove(id));
        return switch (ask(List.of(a.name()), false)) {
            case CANCEL -> done(Outcome.CANCELLED);
            case DISCARD -> {
                remove(id);
                yield cleanRecovery(id, a.name(), Long.MAX_VALUE).thenApply(ok -> Outcome.CLOSED);
            }
            case SAVE -> save(id).thenApply(o -> o == Outcome.SAVED && !project(id).dirty() ? remove(id) : o);
        };
    }

    /**
     * Encerramento: {@code true} se o Studio pode fechar. Com alterações não salvas: Salvar todos (só fecha se TODOS
     * ficarem salvos), Descartar (descarte autorizado, inclusive das fotografias de recuperação) ou Cancelar.
     *
     * <p>Em qualquer caminho, o Studio só fecha depois que o trabalho de disco em andamento terminou e que as fotografias
     * a apagar (descartadas ou superadas por salvamentos) deixaram de existir. A espera não bloqueia a EDT. Se alguma
     * exclusão falhar, o usuário é informado e decide se sai mesmo assim — o Studio nunca afirma uma limpeza que não
     * aconteceu.
     */
    public CompletableFuture<Boolean> confirmExit() {
        requireEdt();
        if (exiting) {
            workspace.report(new Notice(Notice.Kind.INFO, "Encerrando o Studio", "Aguarde: o Studio está concluindo as gravações e a limpeza da recuperação."));
            return CompletableFuture.completedFuture(false);
        }
        List<Automation> dirty = state.unsavedAutomations();
        CompletableFuture<Boolean> decided;
        if (dirty.isEmpty()) {
            decided = finishExit();
        } else {
            decided = switch (ask(dirty.stream().map(Automation::name).toList(), true)) {
                case CANCEL -> CompletableFuture.completedFuture(false);
                case DISCARD -> {
                    for (Automation a : dirty) {
                        project(a.id()).recoveryDiscarded(); // nenhuma fotografia nova das alterações descartadas
                        owe(a.id(), a.name(), Long.MAX_VALUE);
                    }
                    yield finishExit();
                }
                case SAVE -> saveAll(dirty.stream().map(Automation::id).toList()).thenCompose(ok -> {
                    if (ok) return finishExit();
                    workspace.report(new Notice(Notice.Kind.WARNING, "O Studio continua aberto",
                            "Nem todos os projetos foram salvos. Revise os avisos e tente novamente."));
                    return CompletableFuture.completedFuture(false);
                });
            };
        }
        if (decided.isDone()) return decided;
        exiting = true; // estado temporário de encerramento: um novo pedido de Sair não abre outra pergunta
        return decided.whenComplete((ok, e) -> exiting = false);
    }

    /** Espera o trabalho de disco em andamento e refaz as limpezas devidas; se alguma falhar, o usuário decide. */
    private CompletableFuture<Boolean> finishExit() {
        CompletableFuture<List<String>> cleanup = settle().thenCompose(v -> retryCleanup());
        if (!cleanup.isDone()) {
            // só aparece quando o encerramento realmente espera (nunca durante um Salvar todos que ainda pode falhar)
            workspace.report(new Notice(Notice.Kind.INFO, "Encerrando o Studio", "Concluindo as gravações e a limpeza da recuperação…"));
        }
        return cleanup.thenApply(failed -> {
            if (failed.isEmpty()) return true;
            boolean leave = confirm(new StudioPrompts.Confirmation("Fotografias de recuperação não apagadas", "As fotografias de recuperação de "
                    + String.join(", ", failed.stream().map(n -> "\"" + n + "\"").toList()) + " não puderam ser apagadas (o arquivo pode estar em uso "
                    + "por outro programa). Se você sair agora, elas continuarão no disco e poderão ser oferecidas na próxima abertura do Studio; "
                    + "nesse caso, escolha Ignorar. Sair mesmo assim?", "Sair mesmo assim", true));
            if (!leave) workspace.report(new Notice(Notice.Kind.WARNING, "O Studio continua aberto",
                    "As fotografias de recuperação não foram apagadas. Tente sair de novo ou apague-as em Recuperação automática."));
            return leave;
        });
    }

    private StudioPrompts.UnsavedChoice ask(List<String> names, boolean exit) {
        if (!workspace.beginOperation(exit ? StudioOperation.EXIT : StudioOperation.CLOSE_PROJECT)) return StudioPrompts.UnsavedChoice.CANCEL;
        try {
            return prompts.confirmUnsaved(names, exit);
        } finally {
            workspace.endOperation();
        }
    }

    private Outcome remove(AutomationId id) {
        workspace.closeAutomation(id);
        state.catalog().remove(id);
        state.forgetProject(id);
        workspace.history().clear(id);
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
        if (clear && recovery != null) {
            background(recovery::clear).thenAccept(ok -> {
                if (ok) {
                    cleanupDebt.clear();
                } else {
                    workspace.report(new Notice(Notice.Kind.WARNING, "Fotografias de recuperação não apagadas",
                            "Algumas fotografias não puderam ser apagadas (o arquivo pode estar em uso por outro programa). Tente de novo mais tarde."));
                }
            });
        }
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
            if (!p.dirty() || p.generation() == p.recoveryGeneration() || p.saving() || p.reloading() || busy.test(a.id())) continue;
            String projectId = p.projectId().orElseGet(() -> UUID.randomUUID().toString());
            long generation = p.generation();
            ProjectFiles.Snapshot s = ProjectFiles.Snapshot.of(a, projectId);
            ProjectPackage.RecoveryOrigin origin = new ProjectPackage.RecoveryOrigin(p.path().map(Path::toString).orElse(null),
                    p.path().isPresent() ? p.fingerprint().orElse(null) : null);
            Instant at = clock.instant();
            all.add(background(() -> {
                long w0 = System.nanoTime();
                try {
                    recovery.write(s, generation, origin, at, domainClock);
                    log("recuperacao", "ok", w0, -1, null);
                    return true;
                } catch (ProjectException | RuntimeException e) {
                    log("recuperacao", "erro", w0, -1, e instanceof ProjectException pe ? pe.error() : ProjectException.Error.WRITE_FAILED);
                    return false;
                }
            }).thenApply(ok -> {
                if (ok) p.recoveryWritten(generation);
                return ok;
            }));
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
        return background(() -> recovery.scan(domainClock)).thenApply(found -> {
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
            return recovered;
        });
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

    // ================================================================================================== trabalho de disco

    /**
     * Executa {@code task} no worker. O futuro devolvido completa NA EDT; até o trabalho terminar ele fica registrado em
     * {@link #pending}, e o encerramento espera por ele.
     */
    private <T> CompletableFuture<T> background(Supplier<T> task) {
        CompletableFuture<T> onWorker = new CompletableFuture<>();
        pending.removeIf(CompletableFuture::isDone);
        pending.add(onWorker);
        worker.execute(() -> {
            try {
                onWorker.complete(task.get());
            } catch (RuntimeException | Error e) {
                onWorker.completeExceptionally(e);
            }
        });
        CompletableFuture<T> onEdt = new CompletableFuture<>();
        onWorker.whenComplete((v, e) -> SwingUtilities.invokeLater(() -> {
            if (e != null) onEdt.completeExceptionally(e);
            else onEdt.complete(v);
        }));
        return onEdt;
    }

    /** Completa (na EDT) quando todo o trabalho de disco registrado até agora terminou; já completo se não há nenhum. */
    private CompletableFuture<Void> settle() {
        pending.removeIf(CompletableFuture::isDone);
        if (pending.isEmpty()) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> all = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        CompletableFuture<Void> onEdt = new CompletableFuture<>();
        all.whenComplete((v, e) -> SwingUtilities.invokeLater(() -> onEdt.complete(null)));
        return onEdt;
    }

    /** No worker: apaga as fotografias da automação até a geração; {@code true} se nenhuma delas restou. */
    private boolean discardOnWorker(AutomationId id, long upTo) {
        if (recovery == null) return true;
        long w0 = System.nanoTime();
        boolean ok;
        try {
            ok = recovery.discard(id, upTo);
        } catch (RuntimeException e) {
            ok = false;
        }
        log("descartar-recuperacao", ok ? "ok" : "erro", w0, -1, null);
        return ok;
    }

    /**
     * Apaga as fotografias (descarte decidido pelo usuário) depois do trabalho de disco em andamento — uma fotografia que
     * estava sendo gravada também é apagada. O resultado volta na EDT; uma falha é avisada e refeita ao sair.
     */
    private CompletableFuture<Boolean> cleanRecovery(AutomationId id, String name, long upTo) {
        if (recovery == null) return CompletableFuture.completedFuture(true);
        owe(id, name, upTo);
        return settle().thenCompose(v -> background(() -> discardOnWorker(id, upTo))).thenApply(ok -> {
            if (ok) {
                settleDebt(id, upTo);
            } else {
                workspace.report(new Notice(Notice.Kind.WARNING, "Fotografias de recuperação não apagadas", "As fotografias de recuperação de \"" + name
                        + "\" não puderam ser apagadas (o arquivo pode estar em uso por outro programa). O Studio tentará de novo ao sair."));
            }
            return ok;
        });
    }

    /** Refaz (no worker) as limpezas devidas; devolve, na EDT, os nomes dos projetos cujas fotografias ainda restam. */
    private CompletableFuture<List<String>> retryCleanup() {
        if (recovery == null || cleanupDebt.isEmpty()) return CompletableFuture.completedFuture(List.of());
        Map<AutomationId, Debt> owed = new LinkedHashMap<>(cleanupDebt);
        return background(() -> {
            List<AutomationId> cleaned = new ArrayList<>();
            owed.forEach((id, d) -> {
                if (discardOnWorker(id, d.upTo())) cleaned.add(id);
            });
            return cleaned;
        }).thenApply(cleaned -> {
            List<String> failed = new ArrayList<>();
            owed.forEach((id, d) -> {
                if (cleaned.contains(id)) settleDebt(id, d.upTo());
                else failed.add(d.name());
            });
            return failed;
        });
    }

    private void owe(AutomationId id, String name, long upTo) {
        if (recovery == null) return;
        cleanupDebt.merge(id, new Debt(name, upTo), (old, now) -> new Debt(now.name(), Math.max(old.upTo(), now.upTo())));
    }

    private void settleDebt(AutomationId id, long upTo) {
        Debt d = cleanupDebt.get(id);
        if (d != null && d.upTo() <= upTo) cleanupDebt.remove(id);
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
