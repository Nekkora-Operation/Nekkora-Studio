package com.nekkora.studio.ui;

import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.evidence.EvidenceType;
import com.nekkora.studio.domain.evidence.EvidenceVocabulary.Limitation;
import com.nekkora.studio.domain.evidence.ObservationSummary;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.ui.ElementPresentation.Row;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evidências observadas em texto (STUDIO-005) — as mesmas no painel de propriedades e na aba "Evidências" do diálogo de
 * detalhes. Somente leitura: o editor não altera evidências. Nunca diz "sucesso" nem "falha": uma mensagem de erro
 * observada aparece como "mudança observada" com o indicador, e o resultado de negócio continua "Não validado".
 */
public final class EvidencePresentation {

    /** Formato da linha do tempo: horário real com milissegundos. */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss,SSS", Locale.forLanguageTag("pt-BR")).withZone(ZoneId.systemDefault());

    /** Item da linha do tempo: ordem técnica (sequência), horário real, título e detalhes. */
    public record TimelineEntry(long sequence, String time, String title, String detail) {
    }

    private EvidencePresentation() {
    }

    public static String time(Instant at) {
        return TIME.format(at);
    }

    /** Linhas do resumo estruturado da observação. */
    public static List<Row> summary(AutomationStep step) {
        ObservationSummary s = step.observation();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Observação", s.status().label()));
        rows.add(new Row("Situação", s.status().explanation()));
        if (s.status() == ObservationSummary.Status.NOT_APPLICABLE) {
            rows.add(new Row("Resultado de negócio", s.businessOutcome().label()));
            return rows;
        }
        for (EvidenceType.Category c : EvidenceType.Category.values()) {
            int n = s.count(c);
            if (n > 0) {
                rows.add(new Row(category(c), Integer.toString(n)));
            }
        }
        if (s.error()) {
            rows.add(new Row("Indicador de erro", "Observado na página — não é validação de negócio"));
        }
        if (s.suppressed() > 0) {
            rows.add(new Row("Textos omitidos", s.suppressed() + " (privacidade)"));
        }
        if (s.cut() != null) {
            rows.add(new Row("Interrompida", s.cut().label()));
        }
        List<String> lim = s.limitations().stream().filter(l -> !l.cut() && l != Limitation.MESSAGE_SUPPRESSED).map(Limitation::label).sorted().toList();
        if (!lim.isEmpty()) {
            rows.add(new Row("Limitações", String.join(", ", lim)));
        }
        s.last().ifPresent(t -> rows.add(new Row("Última observação", time(t))));
        rows.add(new Row("Resultado de negócio", s.businessOutcome().label()));
        return rows;
    }

    /** Linha do tempo em ordem técnica (sequência das evidências), com o horário real de cada uma. */
    public static List<TimelineEntry> timeline(List<ObservedEvidence> evidence) {
        List<TimelineEntry> out = new ArrayList<>();
        for (ObservedEvidence e : evidence) {
            out.add(new TimelineEntry(e.sequence(), time(e.observedAt()), title(e), detail(e)));
        }
        return out;
    }

    /** Título de uma evidência: o tipo; um indicador de erro aparece como mudança observada, nunca como falha. */
    public static String title(ObservedEvidence e) {
        return e.type().label() + (e.classification() != null && e.type() != EvidenceType.VALIDATION_FEEDBACK ? " · " + e.classification().label() : "");
    }

    /** Detalhes de uma evidência em texto seguro. */
    public static String detail(ObservedEvidence e) {
        List<String> parts = new ArrayList<>();
        switch (e.message().availability()) {
            case AVAILABLE -> parts.add("“" + e.message().text() + "”");
            case SUPPRESSED -> parts.add("Texto omitido por privacidade");
            default -> {
            }
        }
        if (e.field() != null) {
            parts.add("campo " + e.field());
        }
        if (e.region() != null) {
            parts.add(e.region());
        }
        if (e.state() != null) {
            parts.add(e.state().attribute().wire() + ": " + e.state().from().label() + " → " + e.state().to().label());
        }
        if (e.navigation() != null) {
            parts.add((e.navigation().newDocument() ? "novo documento" : "mesmo documento") + (e.navigation().url() == null ? "" : " · " + e.navigation().url()));
        }
        if (e.type().windowEnd()) {
            parts.add(e.signalsObserved() == 0 ? "nenhum sinal na janela" : e.signalsObserved() + " sinal(is) na janela");
        }
        if (!e.attributed()) {
            parts.add(e.candidates() + " etapas candidatas — nenhuma escolhida");
        }
        parts.add(e.confidence().label().toLowerCase(Locale.ROOT));
        if (e.document().frame()) {
            parts.add("em iframe");
        }
        if (e.document().inShadow()) {
            parts.add("em Shadow DOM");
        }
        for (Limitation l : e.limitations()) {
            if (l != Limitation.MESSAGE_SUPPRESSED) {
                parts.add(l.label().toLowerCase(Locale.ROOT));
            }
        }
        return String.join(" · ", parts);
    }

    /** Selo curto para a lista de etapas (vazio quando não há o que dizer). */
    public static String badge(AutomationStep step) {
        ObservationSummary s = step.observation();
        return switch (s.status()) {
            case OBSERVED -> s.error() ? "Indicador de erro observado" : "Mudanças observadas";
            case INCOMPLETE -> "Observação incompleta";
            case NOTHING_OBSERVED -> "Nenhum sinal observado";
            case IN_PROGRESS -> "Observando…";
            default -> "";
        };
    }

    private static String category(EvidenceType.Category c) {
        return switch (c) {
            case FORM -> "Envios de formulário";
            case NAVIGATION -> "Navegações";
            case MESSAGE -> "Mensagens";
            case VALIDATION -> "Validações";
            case LOADING -> "Carregamentos";
            case STATE -> "Mudanças de estado";
            case CONTENT -> "Conteúdo atualizado";
            case AMBIGUOUS -> "Ambíguas";
            case WINDOW -> "Janelas";
        };
    }
}
