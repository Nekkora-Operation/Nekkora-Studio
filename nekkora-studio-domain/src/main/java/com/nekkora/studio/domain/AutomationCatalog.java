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
