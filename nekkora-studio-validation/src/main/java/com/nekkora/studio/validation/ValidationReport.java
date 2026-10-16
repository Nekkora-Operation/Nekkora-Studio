package com.nekkora.studio.validation;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.StepId;
import java.util.List;
import java.util.Objects;

/**
 * Relatório da validação estrutural de UMA automação (STUDIO-007, contrato {@value #SCHEMA} {@value #VERSION}).
 *
 * <p>Imutável e determinístico: o mesmo estado da automação, com a mesma versão das regras, produz o mesmo relatório —
 * sem instantes, sem identificadores gerados. Derivado (pode ser recalculado a qualquer momento) e por isso NÃO é
 * persistido no projeto {@code .nekkora}. Não contém valores, nomes, credenciais, conteúdo da página nem localizadores.
 */
public final class ValidationReport {
    public static final String SCHEMA = "nekkora-studio/validation-report";
    public static final String VERSION = "1.0.0";

    /** Totais do relatório. */
    public record Summary(int sessions, int steps, int blockers, int warnings, int infos, int stepsWithoutFindings, int stepsWithBlockers) {

        public int count(Severity s) {
            return switch (s) {
                case BLOCKER -> blockers;
                case WARNING -> warnings;
                case INFO -> infos;
            };
        }
    }

    private final AutomationId automationId;
    private final String rulesVersion;
    private final ValidationStatus status;
    private final Summary summary;
    private final List<Finding> findings;

    ValidationReport(AutomationId automationId, String rulesVersion, int sessions, int steps, List<Finding> findings) {
        this.automationId = Objects.requireNonNull(automationId, "automationId");
        this.rulesVersion = Objects.requireNonNull(rulesVersion, "rulesVersion");
        this.findings = List.copyOf(findings);
        int blockers = 0;
        int warnings = 0;
        int infos = 0;
        boolean limited = false;
        java.util.Set<StepId> withFindings = new java.util.HashSet<>();
        java.util.Set<StepId> withBlockers = new java.util.HashSet<>();
        for (Finding f : this.findings) {
            switch (f.severity()) {
                case BLOCKER -> blockers++;
                case WARNING -> warnings++;
                case INFO -> infos++;
            }
            limited |= f.code().limitsAssessment();
            if (f.step() != null) {
                withFindings.add(f.step());
                if (f.severity() == Severity.BLOCKER) withBlockers.add(f.step());
            }
        }
        this.summary = new Summary(sessions, steps, blockers, warnings, infos, steps - withFindings.size(), withBlockers.size());
        this.status = blockers > 0 ? ValidationStatus.INCOMPLETE : limited ? ValidationStatus.NOT_FULLY_ASSESSABLE : ValidationStatus.NO_STATIC_BLOCKERS;
    }

    public AutomationId automationId() {
        return automationId;
    }

    /** Versão das regras que produziram o relatório. */
    public String rulesVersion() {
        return rulesVersion;
    }

    public ValidationStatus status() {
        return status;
    }

    public Summary summary() {
        return summary;
    }

    /** Apontamentos em ordem determinística: automação → sessões → etapas → regras. */
    public List<Finding> findings() {
        return findings;
    }

    /** Apontamentos de uma etapa (na ordem das regras). */
    public List<Finding> forStep(StepId step) {
        return findings.stream().filter(f -> step.equals(f.step())).toList();
    }

    /** Serialização JSON canônica (contrato {@value #SCHEMA} {@value #VERSION}). */
    public String toJson() {
        return ValidationReportJson.write(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ValidationReport r && automationId.equals(r.automationId) && rulesVersion.equals(r.rulesVersion)
                && findings.equals(r.findings) && summary.equals(r.summary) && status == r.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(automationId, rulesVersion, findings, summary, status);
    }

    @Override
    public String toString() {
        return "ValidationReport[" + status + ", " + summary + "]";
    }
}
