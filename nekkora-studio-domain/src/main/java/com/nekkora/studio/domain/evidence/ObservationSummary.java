package com.nekkora.studio.domain.evidence;

import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Classification;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.origin.RecordingObservation;
import com.nekkora.studio.domain.origin.StepOrigin;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resumo ESTRUTURADO do que foi observado depois de uma etapa (STUDIO-005) — derivado, de forma determinística, das
 * evidências imutáveis da etapa. Não é texto livre e não é resultado de negócio: {@link #businessOutcome()} é sempre
 * {@link RecordingObservation.BusinessOutcome#NOT_VALIDATED}.
 *
 * <p>Distingue as ausências: não se aplica (etapa manual ou só proposta), observação indisponível (nenhuma evidência —
 * extensão sem a capacidade ou nada recebido), em andamento, concluída sem nenhum sinal (o que NÃO significa ausência
 * de erro), concluída com sinais e incompleta (interrompida, cobertura parcial ou limite).
 *
 * @param status          situação da observação
 * @param counts          evidências por categoria (sem os fins de janela)
 * @param error           algum indicador de erro (classificação), sem concluir falha
 * @param suppressed      mensagens com texto omitido por privacidade
 * @param cut             motivo do corte, quando a observação foi interrompida
 * @param limitations     limitações presentes nas evidências
 * @param lastObservedAt  instante da última evidência, se houver
 * @param businessOutcome sempre não validado
 */
public record ObservationSummary(Status status, Map<EvidenceType.Category, Integer> counts, boolean error, int suppressed, Limitation cut,
                                 Set<Limitation> limitations, Instant lastObservedAt, RecordingObservation.BusinessOutcome businessOutcome) {

    /** Situação da observação de uma etapa. */
    public enum Status {
        NOT_APPLICABLE("Não se aplica", "Etapa manual ou ação apenas proposta: nada foi executado na página."),
        UNAVAILABLE("Observação indisponível", "Nenhuma evidência foi recebida para esta etapa."),
        IN_PROGRESS("Observação em andamento", "A janela de observação desta etapa ainda não terminou."),
        NOTHING_OBSERVED("Nenhum sinal observado", "A janela terminou sem sinais visíveis. Isso não significa que não houve erro."),
        OBSERVED("Mudanças observadas", "A página mostrou sinais depois da ação. Nenhum deles é validação de negócio."),
        INCOMPLETE("Observação incompleta", "A observação foi interrompida ou teve cobertura parcial.");

        private final String label;
        private final String explanation;

        Status(String label, String explanation) {
            this.label = label;
            this.explanation = explanation;
        }

        public String label() {
            return label;
        }

        public String explanation() {
            return explanation;
        }
    }

    public ObservationSummary {
        counts = Map.copyOf(counts);
        limitations = Set.copyOf(limitations);
        businessOutcome = RecordingObservation.BusinessOutcome.NOT_VALIDATED; // nunca outra coisa nesta fase
    }

    /** Quantas evidências da categoria. */
    public int count(EvidenceType.Category c) {
        return counts.getOrDefault(c, 0);
    }

    /** Total de sinais observados (sem os fins de janela). */
    public int signals() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Optional<Instant> last() {
        return Optional.ofNullable(lastObservedAt);
    }

    /** Resumo derivado das evidências da etapa. */
    public static ObservationSummary of(AutomationStep step) {
        boolean executed = step.provenance().origin() == StepOrigin.RECORDED && step.provenance().recordingObservation()
                .map(o -> o.observation() == RecordingObservation.Observation.INTERACTION_OBSERVED).orElse(false);
        return of(step.evidence(), executed);
    }

    /** Resumo de uma lista de evidências; {@code executed} = a ação foi encaminhada à página. */
    public static ObservationSummary of(List<ObservedEvidence> evidence, boolean executed) {
        Map<EvidenceType.Category, Integer> counts = new EnumMap<>(EvidenceType.Category.class);
        Set<Limitation> limitations = EnumSet.noneOf(Limitation.class);
        boolean error = false;
        int suppressed = 0;
        boolean complete = false;
        boolean incomplete = false;
        Limitation cut = null;
        Instant last = null;
        for (ObservedEvidence e : evidence) {
            limitations.addAll(e.limitations());
            last = last == null || e.observedAt().isAfter(last) ? e.observedAt() : last;
            if (e.type() == EvidenceType.OBSERVATION_COMPLETE) {
                complete = true;
            } else if (e.type() == EvidenceType.OBSERVATION_INCOMPLETE) {
                incomplete = true;
                for (Limitation l : e.limitations()) {
                    if (l.cut() && cut == null) {
                        cut = l;
                    }
                }
            } else {
                counts.merge(e.type().category(), 1, Integer::sum);
                error |= e.classification() == Classification.ERROR_INDICATOR;
                if (e.message().availability() == ObservedEvidence.Message.Availability.SUPPRESSED) {
                    suppressed++;
                }
            }
        }
        Status status;
        if (!executed) {
            status = Status.NOT_APPLICABLE;
        } else if (evidence.isEmpty()) {
            status = Status.UNAVAILABLE;
        } else if (incomplete) {
            status = Status.INCOMPLETE;
        } else if (complete) {
            status = counts.isEmpty() ? Status.NOTHING_OBSERVED : Status.OBSERVED;
        } else {
            status = Status.IN_PROGRESS;
        }
        return new ObservationSummary(status, counts, error, suppressed, cut, limitations, last, RecordingObservation.BusinessOutcome.NOT_VALIDATED);
    }
}
