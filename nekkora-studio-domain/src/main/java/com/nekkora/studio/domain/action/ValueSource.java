package com.nekkora.studio.domain.action;

import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Origem do valor usado por uma ação (ex.: {@link ActionType#FILL}). Senhas e dados confidenciais
 * nunca são guardados como texto: use {@link VariableReference} ou {@link CredentialReference}, que
 * apenas <b>nomeiam</b> o valor — o cofre de credenciais é de uma missão futura.
 */
public sealed interface ValueSource permits ValueSource.LiteralValue, ValueSource.VariableReference, ValueSource.CredentialReference {
    int LITERAL_MAX = 500;
    Pattern REFERENCE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");

    /** Texto do valor para apresentação (referências aparecem pelo nome, nunca pelo conteúdo). */
    String display();

    /** Valor literal não sensível (ex.: termo de pesquisa). Recusa textos com aparência de segredo. */
    record LiteralValue(String text) implements ValueSource {
        public LiteralValue {
            Objects.requireNonNull(text, "text");
            if (text.length() > LITERAL_MAX) throw new DomainValidationException(ValidationProblem.VALUE_TOO_LONG);
            if (DisplaySafety.looksSensitive(text)) throw new DomainValidationException(ValidationProblem.LITERAL_LOOKS_SENSITIVE);
        }

        @Override
        public String display() {
            return text;
        }
    }

    /** Referência a uma variável do processo (ex.: {@code cliente.cpf}). */
    record VariableReference(String name) implements ValueSource {
        public VariableReference {
            requireReferenceName(name);
        }

        @Override
        public String display() {
            return "Variável " + name;
        }
    }

    /** Referência a uma credencial guardada fora do modelo (ex.: {@code sistema.usuario}). */
    record CredentialReference(String alias) implements ValueSource {
        public CredentialReference {
            requireReferenceName(alias);
        }

        @Override
        public String display() {
            return "Credencial " + alias;
        }
    }

    private static void requireReferenceName(String name) {
        if (name == null || !REFERENCE_NAME.matcher(name).matches()) {
            throw new DomainValidationException(ValidationProblem.REFERENCE_NAME_INVALID);
        }
    }
}
