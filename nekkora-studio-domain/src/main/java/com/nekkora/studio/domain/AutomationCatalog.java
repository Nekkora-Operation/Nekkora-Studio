package com.nekkora.studio.domain;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Conjunto ordenado das automações de um ambiente de trabalho (na STUDIO-001, mantido em memória).
 * Garante nomes únicos e preserva a ordem de criação. Não é um singleton: cada aplicação/teste tem o seu.
 */
public final class AutomationCatalog {
    private final Clock clock;
    private final List<Automation> automations = new ArrayList<>();
    private final List<Automation> view = Collections.unmodifiableList(automations);

    public AutomationCatalog(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Cria uma automação e a acrescenta ao catálogo. */
    public Automation create(String name, String description) {
        Texts.checkName(name).ifPresent(p -> {
            throw new DomainValidationException(p);
        });
        requireUniqueName(name, null);
        Automation automation = Automation.create(name, description, clock);
        automations.add(automation);
        return automation;
    }

    /**
     * Acrescenta uma automação RECONSTRUÍDA (STUDIO-006: projeto aberto). A identidade não pode existir no catálogo e o
     * nome precisa ser único — nada é renomeado nem substituído em silêncio.
     */
    public void adopt(Automation automation) {
        Objects.requireNonNull(automation, "automation");
        if (find(automation.id()).isPresent()) throw new DomainValidationException(ValidationProblem.AUTOMATION_ALREADY_OPEN);
        requireUniqueName(automation.name(), null);
        automations.add(automation);
    }

    /**
     * Substitui, NA MESMA POSIÇÃO, a automação de mesma identidade por uma versão RECONSTRUÍDA (STUDIO-006: recarregar do
     * arquivo). Tudo é validado antes da troca: se a identidade não está no catálogo ou o nome da substituta já pertence a
     * outra automação, nada muda. Devolve a automação substituída.
     */
    public Automation replace(Automation replacement) {
        Objects.requireNonNull(replacement, "replacement");
        int index = -1;
        for (int i = 0; i < automations.size(); i++) {
            if (automations.get(i).id().equals(replacement.id())) index = i;
        }
        if (index < 0) throw new DomainValidationException(ValidationProblem.AUTOMATION_NOT_FOUND);
        requireUniqueName(replacement.name(), replacement.id());
        return automations.set(index, replacement);
    }

    /** Retira uma automação do catálogo (STUDIO-006: fechar projeto). */
    public boolean remove(AutomationId id) {
        return automations.removeIf(a -> a.id().equals(id));
    }

    /** Renomeia/descreve uma automação mantendo a unicidade de nomes. */
    public void updateDetails(AutomationId id, String name, String description) {
        Automation automation = require(id);
        Texts.checkName(name).ifPresent(p -> {
            throw new DomainValidationException(p);
        });
        requireUniqueName(name, id);
        automation.updateDetails(name, description);
    }

    public List<Automation> all() {
        return view;
    }

    public Optional<Automation> find(AutomationId id) {
        return automations.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public Automation require(AutomationId id) {
        return find(id).orElseThrow(() -> new DomainValidationException(ValidationProblem.AUTOMATION_NOT_FOUND));
    }

    public int size() {
        return automations.size();
    }

    public boolean isEmpty() {
        return automations.isEmpty();
    }

    public Clock clock() {
        return clock;
    }

    private void requireUniqueName(String candidate, AutomationId except) {
        for (Automation a : automations) {
            if (!a.id().equals(except) && Texts.sameName(a.name(), candidate)) {
                throw new DomainValidationException(ValidationProblem.AUTOMATION_NAME_IN_USE);
            }
        }
    }
}
