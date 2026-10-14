package com.nekkora.studio.domain;

import java.util.Objects;

/** Operação de domínio recusada por uma regra de validação. A mensagem é segura para o usuário. */
public final class DomainValidationException extends RuntimeException {
    private final ValidationProblem problem;

    public DomainValidationException(ValidationProblem problem) {
        super(Objects.requireNonNull(problem, "problem").message());
        this.problem = problem;
    }

    public ValidationProblem problem() {
        return problem;
    }
}
