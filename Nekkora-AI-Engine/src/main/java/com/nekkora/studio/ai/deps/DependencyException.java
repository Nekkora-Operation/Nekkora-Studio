package com.nekkora.studio.ai.deps;

/** Falha da preparação do ambiente com o diagnóstico único (a mensagem nunca leva valores nem segredos). */
public final class DependencyException extends Exception {
    private static final long serialVersionUID = 1L;
    private final Diagnosis diagnosis;

    public DependencyException(Diagnosis diagnosis, String message) {
        super(message);
        this.diagnosis = diagnosis;
    }

    public Diagnosis diagnosis() {
        return diagnosis;
    }
}
