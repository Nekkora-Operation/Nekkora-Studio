package com.nekkora.studio.domain;

import java.util.Optional;

/** Regras de texto compartilhadas por automações, sessões e etapas (nomes e descrições). */
public final class Texts {
    public static final int NAME_MAX = 120;
    public static final int DESCRIPTION_MAX = 2000;

    private Texts() {}

    /** Problema do nome, se houver — usado também pelos formulários para validar antes de confirmar. */
    public static Optional<ValidationProblem> checkName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) return Optional.of(ValidationProblem.NAME_REQUIRED);
        if (name.length() > NAME_MAX) return Optional.of(ValidationProblem.NAME_TOO_LONG);
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) return Optional.of(ValidationProblem.NAME_INVALID_CHARACTERS);
        }
        return Optional.empty();
    }

    public static Optional<ValidationProblem> checkDescription(String raw) {
        return normalizeOptional(raw).length() > DESCRIPTION_MAX ? Optional.of(ValidationProblem.DESCRIPTION_TOO_LONG) : Optional.empty();
    }

    /** Nome normalizado (sem espaços nas pontas) ou {@link DomainValidationException}. */
    public static String requireName(String raw) {
        checkName(raw).ifPresent(p -> {
            throw new DomainValidationException(p);
        });
        return raw.strip();
    }

    /** Descrição opcional normalizada: nunca nula, vazia quando ausente. */
    public static String optionalDescription(String raw) {
        checkDescription(raw).ifPresent(p -> {
            throw new DomainValidationException(p);
        });
        return normalizeOptional(raw);
    }

    /** Comparação de nomes para unicidade: sem diferenciar maiúsculas e espaços nas pontas. */
    static boolean sameName(String a, String b) {
        return a.strip().equalsIgnoreCase(b.strip());
    }

    private static String normalizeOptional(String raw) {
        return raw == null ? "" : raw.strip();
    }
}
