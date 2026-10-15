package com.nekkora.studio.domain.action;

import com.nekkora.studio.domain.DomainValidationException;
import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.Objects;

/**
 * Opção escolhida em uma ação {@link ActionType#SELECT_OPTION}. É um parâmetro subordinado à mesma
 * etapa — "Selecionar São Paulo no campo Estado" é <b>uma</b> etapa cujo alvo é o {@code select} e cuja
 * opção é {@code São Paulo}/{@code SP}; o {@code option} não vira uma etapa separada.
 *
 * @param label texto visível da opção (ex.: "São Paulo")
 * @param value valor técnico da opção, quando conhecido (ex.: "SP"); pode ser vazio
 */
public record OptionChoice(String label, String value) {
    /** Limite de cada texto da opção (o mesmo aplicado pela extensão). */
    public static final int TEXT_MAX = 120;

    /**
     * Validação na fronteira de domínio (STUDIO-004, revisão): rótulo e valor técnico passam pela barreira de
     * privacidade — nada com aparência de dado sensível (e-mail, CPF, cartão, JWT, Bearer) nem de identificador interno
     * (UUID, IDs de contrato, hashes) é aceito, venha a opção do Recorder, do editor ou de qualquer outro produtor. As
     * opções sanitizadas pelo Recorder (STUDIO-003) já passam por essa mesma barreira.
     */
    public OptionChoice {
        Objects.requireNonNull(label, "label");
        value = value == null ? "" : value;
        for (String t : new String[] {label, value}) {
            if (t.length() > TEXT_MAX || t.chars().anyMatch(Character::isISOControl)) {
                throw new DomainValidationException(ValidationProblem.OPTION_INVALID);
            }
            if (!t.isEmpty() && !DisplaySafety.isSafeToDisplay(t)) throw new DomainValidationException(ValidationProblem.OPTION_LOOKS_SENSITIVE);
        }
    }
}
