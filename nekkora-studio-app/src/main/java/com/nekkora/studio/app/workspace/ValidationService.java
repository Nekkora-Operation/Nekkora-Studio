package com.nekkora.studio.app.workspace;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.ValidationReport;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.SwingUtilities;

/**
 * Validação estrutural no Studio (STUDIO-007): roda o {@link AutomationValidator} sobre o estado EFETIVO atual da
 * automação e guarda o último relatório de cada uma, sabendo dizer se ele ainda corresponde ao estado atual.
 *
 * <p>Somente leitura: validar não edita a automação, não entra no desfazer/refazer, não muda a geração de persistência
 * (não marca "não salvo") e não grava nada em disco — o relatório não faz parte do projeto {@code .nekkora}.
 *
 * <p>Consistência: o domínio é confinado à EDT e o validador roda nela, de uma vez (5.000 etapas em poucos
 * milissegundos): nenhuma alteração pode acontecer no meio da análise. Atualidade: um relatório é atual enquanto a
 * automação for o MESMO objeto (recarregar troca o objeto) e o estado de persistência estiver na MESMA geração — a
 * geração muda com toda alteração a salvar, inclusive evidências da STUDIO-005, que não mudam a revisão do domínio. Na
 * dúvida o relatório é dado como desatualizado; nunca o contrário.
 */
public final class ValidationService {

    /** Último relatório de uma automação e se ele ainda descreve o estado atual. */
    public record Result(ValidationReport report, boolean current) {}

    private record Stamp(ValidationReport report, Automation automation, ProjectState project, long generation) {}

    private final StudioState state;
    /** STUDIO-008: as regras mais novas (1.1.0: variáveis declaradas e fluxo de dados). */
    private final AutomationValidator validator = new AutomationValidator(com.nekkora.studio.validation.RuleSet.LATEST);
    private final Map<AutomationId, Stamp> results = new HashMap<>();

    public ValidationService(StudioState state) {
        this.state = Objects.requireNonNull(state);
    }

    /** Valida o estado atual (na EDT) e guarda o relatório. */
    public ValidationReport validate(AutomationId id) {
        requireEdt();
        Automation a = state.catalog().require(id);
        ProjectState p = state.project(id).orElse(null);
        long generation = p == null ? -1 : p.generation();
        ValidationReport report = validator.validate(a);
        results.put(id, new Stamp(report, a, p, generation));
        state.fire(id, StudioChange.VALIDATION);
        return report;
    }

    /** Último relatório da automação, com a indicação de atualidade. */
    public Optional<Result> result(AutomationId id) {
        Stamp s = results.get(id);
        if (s == null) return Optional.empty();
        boolean sameAutomation = state.catalog().find(id).map(a -> a == s.automation()).orElse(false);
        ProjectState p = state.project(id).orElse(null);
        boolean current = sameAutomation && p == s.project() && (p == null ? s.generation() == -1 : p.generation() == s.generation());
        return Optional.of(new Result(s.report(), current));
    }

    /** Esquece o relatório (automação fechada). */
    public void forget(AutomationId id) {
        results.remove(id);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("A validação lê o domínio na thread de interface (EDT).");
    }
}
