package com.nekkora.studio.persistence;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.AutomationRestoration;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Projeto {@code .nekkora} de ponta a ponta (STUDIO-006): fotografia → serialização → gravação atômica verificada; e
 * leitura → validação → reconstrução, integral ou recusada.
 *
 * <p>Threads: {@link Snapshot#of} roda na thread dona do domínio (EDT) e devolve valores desligados do agregado; todo o
 * resto (compactar, gravar, ler, validar, reconstruir) roda em outra thread, sem tocar no workspace. Um projeto lido só
 * vira uma {@link Automation} depois de TODAS as verificações (D-S006-07); nada parcial é devolvido.
 */
public final class ProjectFiles {

    /**
     * Fotografia consistente de uma automação para gravação: documentos JSON próprios (não os objetos do domínio).
     *
     * @param projectId identidade do projeto (mantida em "Salvar como")
     */
    public record Snapshot(String projectId, AutomationId automationId, String automationName, Instant createdAt, int steps,
                           Map<String, Object> documents) {
        /** Na thread dona do domínio. */
        public static Snapshot of(Automation a, String projectId) {
            Objects.requireNonNull(projectId, "projectId");
            return new Snapshot(projectId, a.id(), a.name(), a.createdAt(), a.stepCount(), ProjectCodec.documents(a));
        }

        /** Versão do formato que esta fotografia produz (a menor capaz de representá-la). */
        public String formatVersion() {
            return ProjectFormat.versionFor(documents);
        }
    }

    /** Projeto lido e reconstruído. */
    public record Loaded(Automation automation, ProjectPackage.Manifest manifest, ProjectRepository.Fingerprint fingerprint) {}

    private static final Set<String> INTEGRITY_REASONS = Set.of("DUPLICATE_ID", "DUPLICATE_RECORDED_EVENT", "EVIDENCE_STEP_MISMATCH", "EVIDENCE_ORDER",
            "EVIDENCE_NOT_ATTRIBUTED", "EVIDENCE_NOT_UNATTRIBUTED", "LOCATOR_NOT_FOUND", "BASELINE_INCONSISTENT", "PROVENANCE_INCONSISTENT",
            "VARIABLE_SCOPE_SESSION_NOT_FOUND", "VARIABLE_PRODUCER_NOT_FOUND");

    private final ProjectRepository repository;
    private final String producerVersion;

    public ProjectFiles(ProjectRepository repository, String producerVersion) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.producerVersion = Objects.requireNonNull(producerVersion, "producerVersion");
    }

    public ProjectRepository repository() {
        return repository;
    }

    /** Bytes do pacote para a fotografia. */
    public byte[] serialize(Snapshot s, ProjectFormat.Kind kind, ProjectPackage.RecoveryOrigin recoveryOf, Instant savedAt) throws ProjectException {
        return ProjectPackage.write(kind, s.projectId(), s.automationId().value().toString(), s.createdAt(), savedAt, producerVersion, recoveryOf,
                s.documents());
    }

    /**
     * Grava o projeto. O temporário é RELIDO com a leitura completa (pacote, documentos, regras do domínio, privacidade e
     * identidade) antes de substituir o destino: um arquivo que o próprio Studio não conseguiria abrir nunca é confirmado.
     */
    public ProjectRepository.Saved save(Path target, Snapshot s, ProjectRepository.Fingerprint expected, boolean overwrite, Instant savedAt, Clock clock)
            throws ProjectException {
        byte[] bytes = serialize(s, ProjectFormat.Kind.PROJECT, null, savedAt);
        return repository.save(target, bytes, expected, overwrite, written -> {
            Loaded back = read(written, clock);
            if (!back.automation().id().equals(s.automationId()) || !back.manifest().projectId().equals(s.projectId())
                    || back.automation().stepCount() != s.steps()) {
                throw new ProjectException(ProjectException.Error.WRITE_FAILED, "verificação da releitura");
            }
        });
    }

    /** Lê e reconstrói um projeto (ou uma fotografia de recuperação). Integral ou recusado. */
    public Loaded load(Path file, Clock clock) throws ProjectException {
        ProjectRepository.Fingerprint before = repository.fingerprintOf(file);
        if (before == null) throw new ProjectException(ProjectException.Error.NOT_FOUND, "");
        Loaded loaded = read(file, clock);
        ProjectRepository.Fingerprint after = repository.fingerprintOf(file);
        if (!before.equals(after)) throw new ProjectException(ProjectException.Error.CONFLICT, "arquivo alterado durante a leitura");
        return new Loaded(loaded.automation(), loaded.manifest(), after);
    }

    /** Leitura completa sem a conferência de alteração durante a leitura (arquivos do próprio Studio: temporário, recuperação). */
    Loaded loadRecoveryUnchecked(Path file, Clock clock) throws ProjectException {
        return read(file, clock);
    }

    private static Loaded read(Path file, Clock clock) throws ProjectException {
        ProjectPackage.Contents contents = ProjectPackage.read(file);
        AutomationRestoration.AutomationData data = ProjectCodec.read(contents.documents());
        if (!data.id().value().toString().equals(contents.manifest().automationId())) {
            throw new ProjectException(ProjectException.Error.INTEGRITY, "manifest.json: automationId");
        }
        Automation a;
        try {
            a = AutomationRestoration.restore(data, clock);
        } catch (AutomationRestoration.RestoreException e) {
            throw new ProjectException(INTEGRITY_REASONS.contains(e.reason()) ? ProjectException.Error.INTEGRITY : ProjectException.Error.INVALID_CONTENT,
                    e.where() + ": " + e.reason());
        }
        CapturedDataPolicy.check(a);
        return new Loaded(a, contents.manifest(), null);
    }
}
