package com.nekkora.studio.pdd;

import com.nekkora.studio.pdd.ProcessProjection.StepFacts;
import java.util.List;

/**
 * STUDIO-010 G3 — modelos DETERMINÍSTICOS de texto, em português, sobre fatos já seguros da projeção. Não há IA nem
 * texto inventado: sem nome seguro do alvo, a instrução é neutra e marcada para revisão. Nunca citam valor, variável,
 * credencial, opção escolhida, URL, seletor ou identificador técnico. Não afirmam resultado de negócio.
 */
public final class Instructions {
    private Instructions() {}

    /** Instrução de uma etapa. */
    public static DocText of(StepFacts s) {
        String n = s.targetName();
        boolean named = !n.isEmpty();
        String q = "“" + n + "”";
        return switch (s.action()) {
            case FILL -> "PASSWORD_INPUT".equals(s.targetKind()) ? DocText.derived("Informar a senha")
                    : named ? DocText.derived("Informar o campo " + q) : DocText.needsReview("Informar o campo");
            case CLICK -> named ? DocText.derived(("LINK".equals(s.targetKind()) ? "Abrir " : "Selecionar ") + q)
                    : DocText.needsReview("Selecionar o elemento indicado");
            case SELECT_OPTION -> named ? DocText.derived("Escolher uma opção em " + q) : DocText.needsReview("Escolher uma opção");
            case CHECK -> named ? DocText.derived("Marcar " + q) : DocText.needsReview("Marcar a opção indicada");
            case UNCHECK -> named ? DocText.derived("Desmarcar " + q) : DocText.needsReview("Desmarcar a opção indicada");
            case PRESS_KEY -> s.key().isEmpty() ? DocText.needsReview("Pressionar a tecla indicada") : DocText.derived("Pressionar a tecla " + s.key());
            case NAVIGATE -> DocText.derived("Abrir a tela da aplicação");
            case WAIT_FOR -> DocText.derived("Aguardar a atualização da tela");
            case OBSERVE -> DocText.derived("Observar a tela exibida");
            default -> DocText.needsReview("Executar a etapa " + s.number());
        };
    }

    /**
     * Título derivado de um bloco, pela COMPOSIÇÃO das ações (nunca por objetivo de negócio): "Preencher os campos e
     * selecionar “Entrar”". Composição sem modelo → neutro e para revisão.
     */
    public static DocText blockTitle(List<StepFacts> steps) {
        StepFacts last = steps.get(steps.size() - 1);
        boolean allFill = steps.stream().allMatch(s -> s.action() == com.nekkora.studio.domain.action.ActionType.FILL);
        boolean fillThenClick = steps.size() > 1 && last.action() == com.nekkora.studio.domain.action.ActionType.CLICK
                && steps.subList(0, steps.size() - 1).stream().allMatch(s -> s.action() == com.nekkora.studio.domain.action.ActionType.FILL);
        if (fillThenClick && !last.targetName().isEmpty()) return DocText.derived("Preencher os campos e selecionar “" + last.targetName() + "”");
        if (allFill) return DocText.derived(steps.size() == 1 ? "Preencher o campo" : "Preencher os campos");
        if (steps.size() == 1) {
            DocText one = of(last);
            return one.state() == DocText.State.DERIVED ? one : DocText.needsReview("Etapa " + last.number());
        }
        if (steps.stream().allMatch(s -> s.action() == com.nekkora.studio.domain.action.ActionType.OBSERVE
                || s.action() == com.nekkora.studio.domain.action.ActionType.WAIT_FOR)) {
            return DocText.derived("Observar a tela");
        }
        return DocText.needsReview("Etapas " + steps.get(0).number() + " a " + last.number());
    }

    /** Título de seção: o nome da sessão só se passar pela política de texto; senão neutro e para revisão. */
    public static DocText sectionTitle(ProcessProjection.SectionFacts s) {
        return s.safeName().isEmpty() ? DocText.needsReview("Sessão " + s.number()) : DocText.derived(s.safeName());
    }
}
