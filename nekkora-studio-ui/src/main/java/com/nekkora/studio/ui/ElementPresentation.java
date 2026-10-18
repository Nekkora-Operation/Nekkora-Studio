package com.nekkora.studio.ui;

import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Linhas apresentáveis de uma {@link ElementReference}. Chaves de correlação nunca entram; textos técnicos
 * passam por {@link DisplaySafety}; {@code id}/{@code name} HTML só aparecem quando o domínio os libera.
 */
public final class ElementPresentation {
    /** Linha "rótulo: valor". */
    public record Row(String label, String value) {}

    private static final Map<String, String> KIND_LABELS = Map.ofEntries(
            Map.entry("TEXT_INPUT", "Campo de texto"), Map.entry("EMAIL_INPUT", "Campo de e-mail"),
            Map.entry("PASSWORD_INPUT", "Campo de senha"), Map.entry("NUMBER_INPUT", "Campo numérico"),
            Map.entry("DATE_INPUT", "Campo de data"), Map.entry("SEARCH_INPUT", "Campo de pesquisa"),
            Map.entry("TEXTAREA", "Área de texto"), Map.entry("BUTTON", "Botão"), Map.entry("LINK", "Link"),
            Map.entry("CHECKBOX", "Caixa de seleção"), Map.entry("RADIO", "Opção exclusiva"), Map.entry("SWITCH", "Interruptor"),
            Map.entry("SELECT", "Lista de seleção"), Map.entry("COMBOBOX", "Caixa de combinação"), Map.entry("MENUITEM", "Item de menu"),
            Map.entry("TAB", "Aba"), Map.entry("TABLE_ROW", "Linha de tabela"), Map.entry("TABLE_CELL", "Célula de tabela"),
            Map.entry("DIALOG", "Diálogo"), Map.entry("MODAL", "Janela modal"), Map.entry("FORM", "Formulário"),
            Map.entry("IFRAME", "Quadro (iframe)"), Map.entry("UNKNOWN", "Não identificado"));

    private static final Map<String, String> RELATION_LABELS = Map.of(
            "IN_ROW", "na linha", "IN_TABLE", "na tabela", "IN_DIALOG", "no diálogo", "IN_FORM", "no formulário",
            "IN_FIELD_GROUP", "no grupo", "IN_LIST_ITEM", "no item de lista", "IN_REGION", "na região", "IN_CONTAINER", "em");

    private ElementPresentation() {}

    /** Nome legível de uma categoria semântica do PageMap. */
    public static String kindLabel(String kind) {
        String label = KIND_LABELS.get(kind);
        if (label != null) return label;
        String human = kind.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return human.isEmpty() ? StudioText.NOT_AVAILABLE : Character.toUpperCase(human.charAt(0)) + human.substring(1);
    }

    /** STUDIO-009: resumo de uma linha ("Campo de pesquisa · Código do registro"), em texto seguro. */
    public static String title(ElementReference e) {
        String name = StudioText.technical(e.accessibleName());
        return kindLabel(e.semanticKind()) + (name.isBlank() || StudioText.NOT_AVAILABLE.equals(name) ? "" : " · " + name);
    }

    public static List<Row> rows(ElementReference e) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Elemento", StudioText.technical(e.accessibleName())));
        rows.add(new Row("Categoria", kindLabel(e.semanticKind())));
        if (!e.tag().isBlank()) rows.add(new Row("Tag HTML", StudioText.technical("<" + e.tag() + ">")));
        e.displayableHtmlId().ifPresent(v -> rows.add(new Row("id HTML", v)));
        e.displayableHtmlName().ifPresent(v -> rows.add(new Row("name HTML", v)));
        if (e.sensitive()) rows.add(new Row("Privacidade", "Elemento sensível — nenhum valor digitado é guardado"));
        rows.add(new Row("Localizador", e.preferredLocator().map(ElementPresentation::locator)
                .orElse(e.locators().isEmpty() ? "Nenhum candidato" : e.locators().size() + " candidato(s), nenhum validado — requer revisão")));
        e.preferredLocator().ifPresent(c -> rows.add(new Row("Por que este", why(c))));
        if (!e.accessPath().isEmpty()) rows.add(new Row("Escopo", scope(e.accessPath())));
        if (!e.accessPath().isEmpty()) rows.add(new Row("Trajetória", path(e.accessPath())));
        if (!e.context().isEmpty()) rows.add(new Row("Contexto", context(e.context())));
        if (!e.stabilityNotes().isEmpty()) rows.add(new Row("Estabilidade", safeJoin(e.stabilityNotes(), "; ")));
        if (!e.obstacles().isEmpty()) {
            rows.add(new Row("Obstáculos", safeJoin(e.obstacles().stream().map(ObservedObstacle::description).toList(), "; ")));
        }
        return rows;
    }

    private static String locator(LocatorCandidate c) {
        return STRATEGY_LABELS.getOrDefault(c.strategy().name(), c.strategy().name()) + ": " + StudioText.technical(c.description()) + " · "
                + c.stability().label();
    }

    /**
     * STUDIO-012: justificativa curta da estratégia preferida. Identificador do desenvolvedor ou nome de interface únicos
     * identificam; caminho estrutural/posicional só localiza nesta página — fica em revisão.
     */
    public static String why(LocatorCandidate c) {
        String unique = c.validated() ? "único e validado na captura" : "não validado na captura";
        return switch (c.strategy()) {
            case ID, NAME, TEST_ATTRIBUTE -> "identificador do desenvolvedor aceito pela política, " + unique;
            case LABEL, ROLE_NAME, TEXT -> "nome de interface do vocabulário, " + unique;
            case ROW_CONTEXT, CONTEXTUAL, ATTRIBUTE -> "contexto e atributos estruturais, " + unique + "; confira antes de executar";
            case XPATH_RELATIVE, POSITIONAL -> "caminho estrutural/posicional (sem id, name ou rótulo utilizáveis), " + unique
                    + " — requer revisão: muda se a página mudar";
        };
    }

    /** STUDIO-012: documento em que o elemento está ("Documento principal" / "Dentro de iframe (2 níveis)"). */
    public static String scope(List<AccessStep> steps) {
        long frames = steps.stream().filter(s -> s.type() == AccessStep.Type.IFRAME).count();
        boolean shadow = steps.stream().anyMatch(s -> s.type() == AccessStep.Type.SHADOW_ROOT);
        String base = frames == 0 ? "Documento principal" : frames == 1 ? "Dentro de iframe" : "Dentro de iframe (" + frames + " níveis)";
        return shadow ? base + ", em Shadow DOM" : base;
    }

    private static final Map<String, String> STRATEGY_LABELS = Map.ofEntries(
            Map.entry("TEST_ATTRIBUTE", "Atributo de teste"), Map.entry("ID", "ID"), Map.entry("NAME", "Name"), Map.entry("LABEL", "Rótulo"),
            Map.entry("ROLE_NAME", "Papel e nome"), Map.entry("CONTEXTUAL", "Contexto"), Map.entry("ROW_CONTEXT", "Linha de tabela"),
            Map.entry("ATTRIBUTE", "Atributo"), Map.entry("TEXT", "Texto"), Map.entry("XPATH_RELATIVE", "XPath relativo"),
            Map.entry("POSITIONAL", "Posição"));

    /** STUDIO-004: linha de um localizador capturado ("ID: id "btn-login" · Provavelmente estável"). */
    public static String locatorLine(LocatorCandidate c) {
        String technical = c.expression().isBlank() ? "" : " (" + StudioText.technical(c.expression()) + ")";
        return STRATEGY_LABELS.getOrDefault(c.strategy().name(), c.strategy().name()) + ": " + StudioText.technical(c.description()) + technical
                + " · " + c.stability().label();
    }

    /** STUDIO-004: situação de um localizador capturado, sem inventar validação. */
    public static String locatorStatus(LocatorCandidate c) {
        if (c.validated()) return "Validado na captura: encontrou exatamente o elemento";
        if (!c.matchesTarget()) return "Não encontrou o elemento na captura";
        return "Candidato alternativo, não validado: " + c.matchCount() + " correspondências na captura";
    }

    /**
     * STUDIO-004: localizadores da referência efetiva, marcando o preferencial ("✓ preferencial", "○ candidato") e os
     * propostos manualmente ("não verificado").
     */
    public static List<Row> locatorRows(ElementReference e, List<com.nekkora.studio.domain.element.ManualLocator> manual) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < e.locators().size(); i++) {
            LocatorCandidate c = e.locators().get(i);
            String mark = i == e.preferredLocatorIndex() ? "✓ Preferencial" : "○ Candidato";
            rows.add(new Row(mark, locatorLine(c) + " — " + locatorStatus(c)));
        }
        for (com.nekkora.studio.domain.element.ManualLocator m : manual) {
            rows.add(new Row("Proposto manualmente", m.syntax().label() + ": " + StudioText.technical(m.expression()) + " — "
                    + m.verification().label()));
        }
        return rows;
    }

    private static String path(List<AccessStep> steps) {
        return steps.stream().map(s -> s.type() == AccessStep.Type.MAIN_DOCUMENT && s.label().isBlank() ? "Documento principal"
                : StudioText.technical(s.label())).collect(Collectors.joining(" → "));
    }

    private static String context(List<ContextAnchor> anchors) {
        return anchors.stream().map(a -> {
            String rel = RELATION_LABELS.getOrDefault(a.relation(), "em");
            String name = a.name().isBlank() ? kindLabel(a.kind()).toLowerCase(java.util.Locale.ROOT) : StudioText.technical(a.name());
            // "no formulário" + nome genérico "formulário" não se repete ("no formulário formulário")
            return rel.toLowerCase(java.util.Locale.ROOT).endsWith(" " + name.toLowerCase(java.util.Locale.ROOT)) ? rel : rel + " " + name;
        }).collect(Collectors.joining(", "));
    }

    private static String safeJoin(List<String> items, String sep) {
        return items.stream().map(StudioText::technical).collect(Collectors.joining(sep));
    }
}
