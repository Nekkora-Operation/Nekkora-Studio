package com.nekkora.studio.domain.variable;

import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.Texts;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regras de coerência de uma variável (STUDIO-008) — a MESMA conferência para a criação, a edição e a leitura de um
 * projeto. Tudo é conferido antes de qualquer alteração; nada é corrigido em silêncio.
 *
 * <ul>
 *   <li>nome com a sintaxe de {@link ValueSource#REFERENCE_NAME} (o ponto faz parte do nome);</li>
 *   <li>significado compatível com o tipo técnico; sensibilidade no mínimo a exigida pelo significado (CPF e e-mail são
 *       dados pessoais);</li>
 *   <li>autoridade coerente: um significado não especificado não pode estar "confirmado" nem "proposto";</li>
 *   <li>restrições só onde fazem sentido (comprimento e formato em texto, limites em números, escala em decimal, moeda
 *       em valor monetário — obrigatórias para ele);</li>
 *   <li>constante só não sensível, na forma canônica do tipo, dentro das restrições e sem aparência de dado sensível
 *       (um CPF real nunca é aceito como valor fixo).</li>
 * </ul>
 */
public final class VariableRules {
    /** Variáveis por automação. */
    public static final int MAX_VARIABLES = 2_000;
    /** Maior comprimento declarável. */
    public static final int MAX_DECLARED_LENGTH = 10_000;
    /** Maior escala declarável. */
    public static final int MAX_SCALE = 18;
    /** Maior constante de texto (o mesmo limite de um valor literal de etapa). */
    public static final int CONSTANT_MAX = ValueSource.LITERAL_MAX;

    private static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,18}");
    private static final Pattern DECIMAL = Pattern.compile("-?[0-9]{1,30}(\\.[0-9]{1,18})?");

    private VariableRules() {}

    /**
     * Confere o rascunho e devolve a versão normalizada (finalidade normalizada, constante na forma canônica).
     *
     * @throws DomainValidationException com o primeiro problema encontrado
     */
    public static VariableDraft check(VariableDraft d) {
        if (d.name() == null || !ValueSource.REFERENCE_NAME.matcher(d.name()).matches()) fail(ValidationProblem.VARIABLE_NAME_INVALID);
        String purpose = Texts.optionalDescription(d.purpose());
        if (DisplaySafety.looksSensitive(purpose)) fail(ValidationProblem.VARIABLE_PURPOSE_LOOKS_SENSITIVE);
        if (!d.semantic().accepts(d.type())) fail(ValidationProblem.VARIABLE_SEMANTIC_INCOMPATIBLE);
        boolean unspecified = d.semantic() == SemanticType.UNSPECIFIED;
        if (unspecified != (d.authority() == SemanticAuthority.UNSPECIFIED)) fail(ValidationProblem.VARIABLE_AUTHORITY_INVALID);
        if (!d.sensitivity().atLeast(d.semantic().minimumSensitivity())) fail(ValidationProblem.VARIABLE_SENSITIVITY_TOO_LOW);
        checkConstraints(d.type(), d.semantic(), d.constraints());
        VariableOrigin origin = d.origin();
        if (origin.kind() == VariableOrigin.Kind.CONSTANT) {
            if (!d.sensitivity().allowsConstant()) fail(ValidationProblem.VARIABLE_CONSTANT_FORBIDDEN);
            String canonical = canonical(d.type(), origin.constant(), d.constraints());
            if (DisplaySafety.looksSensitive(canonical)) fail(ValidationProblem.VARIABLE_CONSTANT_LOOKS_SENSITIVE);
            origin = VariableOrigin.constant(canonical);
        }
        return new VariableDraft(d.name(), d.type(), d.semantic(), d.authority(), purpose, d.scope(), origin, d.sensitivity(), d.constraints());
    }

    /** O problema da definição, sem lançar (para diagnósticos). */
    public static Optional<ValidationProblem> problem(VariableDefinition def) {
        try {
            VariableDraft checked = check(def.draft());
            return checked.equals(def.draft()) ? Optional.empty() : Optional.of(ValidationProblem.VARIABLE_CONSTANT_INVALID);
        } catch (DomainValidationException e) {
            return Optional.of(e.problem());
        }
    }

    private static void checkConstraints(VariableType type, SemanticType semantic, VariableConstraints c) {
        boolean text = type == VariableType.STRING;
        if (c.minLength() != null || c.maxLength() != null) {
            if (!text) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
            int min = c.minLength() == null ? 0 : c.minLength();
            int max = c.maxLength() == null ? MAX_DECLARED_LENGTH : c.maxLength();
            if (min < 0 || max < 0 || min > MAX_DECLARED_LENGTH || max > MAX_DECLARED_LENGTH || min > max) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
        }
        if (c.textFormat() != TextFormat.ANY && !text) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
        if (c.minimum() != null || c.maximum() != null) {
            if (!type.numeric()) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
            if (type == VariableType.INTEGER && (!integral(c.minimum()) || !integral(c.maximum()))) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
            if (c.minimum() != null && c.maximum() != null && c.minimum().compareTo(c.maximum()) > 0) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
        }
        if (c.scale() != null && (type != VariableType.DECIMAL || c.scale() < 0 || c.scale() > MAX_SCALE)) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
        if (c.currency() != null) {
            if (semantic != SemanticType.CURRENCY || !isCurrency(c.currency())) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
        }
        if (semantic == SemanticType.CURRENCY && (c.currency() == null || c.scale() == null)) fail(ValidationProblem.VARIABLE_CONSTRAINT_INVALID);
    }

    /**
     * Forma canônica de um valor fixo do tipo, conferida contra as restrições.
     *
     * @throws DomainValidationException {@link ValidationProblem#VARIABLE_CONSTANT_INVALID}
     */
    public static String canonical(VariableType type, String raw, VariableConstraints c) {
        if (raw == null) fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
        String value = switch (type) {
            case STRING -> {
                if (raw.length() > CONSTANT_MAX || raw.chars().anyMatch(Character::isISOControl)) fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
                int length = raw.codePointCount(0, raw.length());
                if ((c.minLength() != null && length < c.minLength()) || (c.maxLength() != null && length > c.maxLength())
                        || !c.textFormat().matches(raw)) {
                    fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
                }
                yield raw;
            }
            case INTEGER -> {
                if (!INTEGER.matcher(raw).matches()) fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
                yield Long.toString(Long.parseLong(raw));
            }
            case DECIMAL -> {
                if (!DECIMAL.matcher(raw).matches()) fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
                BigDecimal v = new BigDecimal(raw);
                if (c.scale() != null) {
                    try {
                        v = v.setScale(c.scale()); // exata: nunca arredonda em silêncio
                    } catch (ArithmeticException e) {
                        fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
                    }
                }
                yield v.toPlainString();
            }
            case BOOLEAN -> {
                if (!raw.equals("true") && !raw.equals("false")) fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
                yield raw;
            }
            case DATE -> {
                try {
                    yield LocalDate.parse(raw).toString();
                } catch (DateTimeParseException e) {
                    fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
                    yield raw;
                }
            }
        };
        if (type.numeric()) {
            BigDecimal n = new BigDecimal(value);
            if ((c.minimum() != null && n.compareTo(c.minimum()) < 0) || (c.maximum() != null && n.compareTo(c.maximum()) > 0)) {
                fail(ValidationProblem.VARIABLE_CONSTANT_INVALID);
            }
        }
        return value;
    }

    private static boolean integral(BigDecimal v) {
        return v == null || v.stripTrailingZeros().scale() <= 0;
    }

    private static boolean isCurrency(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) return false;
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void fail(ValidationProblem p) {
        throw new DomainValidationException(p);
    }
}
