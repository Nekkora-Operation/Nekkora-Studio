package com.nekkora.studio.app.workspace;

import com.nekkora.studio.persistence.ProjectException;
import com.nekkora.studio.persistence.ProjectRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Estado de persistência de UMA automação (STUDIO-006, §20, D-S006-13). Confinado à EDT; alterado só por
 * {@link WorkspaceService} e {@link ProjectService}.
 *
 * <p>A {@link #generation() geração} conta toda alteração que precisa ser salva — edições, etapas e evidências do
 * Recorder, desfazer/refazer — e é independente de {@code Automation.revision()} (que não muda com evidências, de
 * propósito, para o desfazer/refazer). Só a geração EFETIVAMENTE gravada é considerada salva: uma alteração que chega
 * durante a gravação deixa o projeto com alterações pendentes.
 */
public final class ProjectState {

    /** Situação apresentada ao usuário. */
    public enum Status {
        SAVED("Salvo"),
        UNSAVED("Alterações não salvas"),
        SAVING("Salvando…"),
        ERROR("Erro ao salvar"),
        CONFLICT("Arquivo alterado externamente"),
        RECOVERED("Recuperado — alterações não salvas");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private Path path;
    private String projectId;
    private ProjectRepository.Fingerprint fingerprint;
    private long generation;
    private long savedGeneration;
    private long recoveryGeneration;
    private Instant lastSavedAt;
    private boolean saving;
    private boolean reloading;
    private ProjectException.Error lastError;
    private boolean recovered;

    ProjectState(long generation) {
        this.generation = generation;
    }

    /** Projeto aberto de um arquivo (nada pendente). */
    static ProjectState opened(Path path, String projectId, ProjectRepository.Fingerprint fingerprint, Instant savedAt) {
        ProjectState p = new ProjectState(0);
        p.path = path;
        p.projectId = projectId;
        p.fingerprint = fingerprint;
        p.lastSavedAt = savedAt;
        return p;
    }

    public Optional<Path> path() {
        return Optional.ofNullable(path);
    }

    public Optional<String> projectId() {
        return Optional.ofNullable(projectId);
    }

    public Optional<ProjectRepository.Fingerprint> fingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    public long generation() {
        return generation;
    }

    public long savedGeneration() {
        return savedGeneration;
    }

    public Optional<Instant> lastSavedAt() {
        return Optional.ofNullable(lastSavedAt);
    }

    public boolean saving() {
        return saving;
    }

    /** Um recarregamento do arquivo está em andamento (salvar, fechar e recarregar de novo esperam). */
    public boolean reloading() {
        return reloading;
    }

    public Optional<ProjectException.Error> lastError() {
        return Optional.ofNullable(lastError);
    }

    /** Há alterações que ainda não estão num arquivo confirmado. */
    public boolean dirty() {
        return generation != savedGeneration;
    }

    /** Veio de uma fotografia de recuperação e ainda não foi salvo. */
    public boolean recovered() {
        return recovered;
    }

    /** Última geração gravada numa fotografia de recuperação. */
    public long recoveryGeneration() {
        return recoveryGeneration;
    }

    public Status status() {
        if (saving) return Status.SAVING;
        if (lastError == ProjectException.Error.CONFLICT) return Status.CONFLICT;
        if (lastError != null) return Status.ERROR;
        if (dirty()) return recovered ? Status.RECOVERED : Status.UNSAVED;
        return Status.SAVED;
    }

    // ------------------------------------------------------------------ mutadores (pacote)

    void changed() {
        generation++;
    }

    void savingStarted() {
        saving = true;
    }

    void saved(Path path, String projectId, ProjectRepository.Fingerprint fingerprint, long generationWritten, Instant at) {
        saving = false;
        lastError = null;
        recovered = false;
        this.path = path;
        this.projectId = projectId;
        this.fingerprint = fingerprint;
        this.savedGeneration = Math.max(savedGeneration, generationWritten);
        this.lastSavedAt = at;
    }

    void failed(ProjectException.Error error) {
        saving = false;
        lastError = error;
    }

    void reloadingStarted() {
        reloading = true;
    }

    void reloadingEnded() {
        reloading = false;
    }

    /** O usuário descartou as alterações atuais: nenhuma fotografia nova delas (só de alterações posteriores). */
    void recoveryDiscarded() {
        recoveryGeneration = Math.max(recoveryGeneration, generation);
    }

    void clearError() {
        lastError = null;
    }

    void recoveryWritten(long generationWritten) {
        recoveryGeneration = Math.max(recoveryGeneration, generationWritten);
    }

    /** Projeto restaurado de uma fotografia de recuperação: continua NÃO salvo até um salvamento confirmado. */
    static ProjectState recovered(Path path, String projectId, ProjectRepository.Fingerprint base, long generation) {
        // a numeração continua acima das fotografias antigas: um salvamento confirmado as supera e as descarta
        ProjectState p = new ProjectState(Math.max(1, generation));
        p.path = path;
        p.projectId = projectId;
        p.fingerprint = base;
        p.recovered = true;
        return p;
    }
}
