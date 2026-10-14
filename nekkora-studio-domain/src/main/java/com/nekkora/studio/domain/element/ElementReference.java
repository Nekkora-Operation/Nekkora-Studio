package com.nekkora.studio.domain.element;

import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Referência pequena a um elemento-alvo, recebida de um produtor técnico (Chrome Extension/PageMap).
 * O Studio <b>não</b> recalcula identidade, localizadores nem trajetórias: guarda apenas o necessário
 * para apresentar a etapa e para reencontrar o elemento no futuro — nunca um PageMap inteiro.
 *
 * <p>Privacidade: {@link #htmlId()} e {@link #htmlName()} só são apresentáveis quando o produtor declara
 * uma política de sanitização reconhecida ({@link #privacyPolicy()}) e o texto passa por
 * {@link DisplaySafety}; elementos {@link #sensitive()} (ex.: senha) nunca têm valor literal guardado.
 * Chaves de correlação ({@link #correlation()}) jamais são exibidas.
 */
public final class ElementReference {
    /** Categoria semântica desconhecida (valor {@code UNKNOWN} de {@code elementKind}). */
    public static final String UNKNOWN_KIND = "UNKNOWN";

    private final String accessibleName;
    private final String semanticKind;
    private final String tag;
    private final String htmlId;
    private final String htmlName;
    private final List<LocatorCandidate> locators;
    private final int preferredLocator;
    private final List<AccessStep> accessPath;
    private final List<ContextAnchor> context;
    private final List<String> stabilityNotes;
    private final List<ObservedObstacle> obstacles;
    private final boolean sensitive;
    private final String privacyPolicy;
    private final String sourceContract;
    private final CorrelationKeys correlation;

    private ElementReference(Builder b) {
        this.accessibleName = b.accessibleName == null ? "" : b.accessibleName;
        this.semanticKind = b.semanticKind == null || b.semanticKind.isBlank() ? UNKNOWN_KIND : b.semanticKind;
        this.tag = b.tag == null ? "" : b.tag;
        this.htmlId = b.htmlId;
        this.htmlName = b.htmlName;
        this.locators = List.copyOf(b.locators);
        if (b.preferredLocator >= locators.size()) throw new IllegalArgumentException("preferredLocator fora da lista");
        if (b.preferredLocator >= 0 && !locators.get(b.preferredLocator).validated()) {
            throw new IllegalArgumentException("o localizador preferencial precisa estar validado");
        }
        this.preferredLocator = b.preferredLocator;
        this.accessPath = List.copyOf(b.accessPath);
        this.context = List.copyOf(b.context);
        this.stabilityNotes = List.copyOf(b.stabilityNotes);
        this.obstacles = List.copyOf(b.obstacles);
        this.sensitive = b.sensitive;
        this.privacyPolicy = b.privacyPolicy;
        this.sourceContract = b.sourceContract;
        this.correlation = b.correlation == null ? CorrelationKeys.none() : b.correlation;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Nome acessível (ex.: "Usuário"). */
    public String accessibleName() {
        return accessibleName;
    }

    /** Categoria semântica — valor de {@code elementKind} do PageMap (ex.: {@code TEXT_INPUT}, {@code SELECT}). */
    public String semanticKind() {
        return semanticKind;
    }

    public String tag() {
        return tag;
    }

    /** Atributo HTML {@code id} conforme recebido (pode ser nulo). Para exibir, use {@link #displayableHtmlId()}. */
    public Optional<String> htmlId() {
        return Optional.ofNullable(htmlId);
    }

    public Optional<String> htmlName() {
        return Optional.ofNullable(htmlName);
    }

    /** {@code id} HTML apresentável: produtor com política reconhecida e texto seguro. */
    public Optional<String> displayableHtmlId() {
        return displayable(htmlId);
    }

    public Optional<String> displayableHtmlName() {
        return displayable(htmlName);
    }

    public List<LocatorCandidate> locators() {
        return locators;
    }

    /** Localizador preferencial, apenas quando validado pelo produtor. */
    public Optional<LocatorCandidate> preferredLocator() {
        return preferredLocator < 0 ? Optional.empty() : Optional.of(locators.get(preferredLocator));
    }

    /** Trajetória de acesso (documento principal → ... → elemento). */
    public List<AccessStep> accessPath() {
        return accessPath;
    }

    /** Âncoras de contexto necessárias ao reencontro (linha, modal, formulário...). */
    public List<ContextAnchor> context() {
        return context;
    }

    public List<String> stabilityNotes() {
        return stabilityNotes;
    }

    public List<ObservedObstacle> obstacles() {
        return obstacles;
    }

    /** Elemento sensível (ex.: senha): o Studio nunca guarda valor literal para ele. */
    public boolean sensitive() {
        return sensitive;
    }

    /** Política de sanitização declarada pelo produtor (ex.: {@code nekkora-privacy/1}); vazio se desconhecida. */
    public Optional<String> privacyPolicy() {
        return Optional.ofNullable(privacyPolicy);
    }

    /** Contrato de origem dos dados (ex.: {@code nekkora-pagemap/0.2.0}). */
    public Optional<String> sourceContract() {
        return Optional.ofNullable(sourceContract);
    }

    public CorrelationKeys correlation() {
        return correlation;
    }

    private Optional<String> displayable(String value) {
        if (value == null || value.isBlank() || sensitive) return Optional.empty();
        if (!DisplaySafety.KNOWN_PRODUCER_POLICY.equals(privacyPolicy)) return Optional.empty();
        return DisplaySafety.isSafeToDisplay(value) ? Optional.of(value) : Optional.empty();
    }

    @Override
    public String toString() {
        return "ElementReference[" + semanticKind + " \"" + accessibleName + "\"]";
    }

    /** Construtor fluente — usado pela criação manual, por testes e, futuramente, pelo adaptador do PageMap. */
    public static final class Builder {
        private String accessibleName;
        private String semanticKind;
        private String tag;
        private String htmlId;
        private String htmlName;
        private final List<LocatorCandidate> locators = new ArrayList<>();
        private int preferredLocator = -1;
        private final List<AccessStep> accessPath = new ArrayList<>();
        private final List<ContextAnchor> context = new ArrayList<>();
        private final List<String> stabilityNotes = new ArrayList<>();
        private final List<ObservedObstacle> obstacles = new ArrayList<>();
        private boolean sensitive;
        private String privacyPolicy;
        private String sourceContract;
        private CorrelationKeys correlation;

        private Builder() {}

        public Builder accessibleName(String v) {
            accessibleName = v;
            return this;
        }

        public Builder semanticKind(String v) {
            semanticKind = v;
            return this;
        }

        public Builder tag(String v) {
            tag = v;
            return this;
        }

        public Builder htmlId(String v) {
            htmlId = v;
            return this;
        }

        public Builder htmlName(String v) {
            htmlName = v;
            return this;
        }

        public Builder addLocator(LocatorCandidate c) {
            locators.add(Objects.requireNonNull(c));
            return this;
        }

        /** Marca o último localizador adicionado como preferencial (precisa estar validado). */
        public Builder preferLastLocator() {
            preferredLocator = locators.size() - 1;
            return this;
        }

        public Builder addAccessStep(AccessStep s) {
            accessPath.add(Objects.requireNonNull(s));
            return this;
        }

        public Builder addContext(ContextAnchor a) {
            context.add(Objects.requireNonNull(a));
            return this;
        }

        public Builder addStabilityNote(String note) {
            stabilityNotes.add(Objects.requireNonNull(note));
            return this;
        }

        public Builder addObstacle(ObservedObstacle o) {
            obstacles.add(Objects.requireNonNull(o));
            return this;
        }

        public Builder sensitive(boolean v) {
            sensitive = v;
            return this;
        }

        public Builder privacyPolicy(String v) {
            privacyPolicy = v;
            return this;
        }

        public Builder sourceContract(String v) {
            sourceContract = v;
            return this;
        }

        public Builder correlation(CorrelationKeys v) {
            correlation = v;
            return this;
        }

        public ElementReference build() {
            return new ElementReference(this);
        }
    }
}
