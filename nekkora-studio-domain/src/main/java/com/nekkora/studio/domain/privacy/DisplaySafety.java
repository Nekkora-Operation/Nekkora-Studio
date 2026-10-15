package com.nekkora.studio.domain.privacy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Barreira de apresentação do Studio. A sanitização principal de dados capturados pertence ao produtor
 * (a Chrome Extension aplica a política {@code nekkora-privacy/1} antes de exportar) — este utilitário
 * <b>não</b> duplica aquele motor: apenas impede que identificadores internos ou textos com aparência de
 * dado sensível cheguem a rótulos, diálogos e painéis, e que valores literais sensíveis entrem no modelo.
 */
public final class DisplaySafety {
    /** Política de privacidade reconhecida para dados vindos da Chrome Extension. */
    public static final String KNOWN_PRODUCER_POLICY = "nekkora-privacy/1";

    /** UUID em qualquer posição do texto. */
    public static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern CONTRACT_IDS = Pattern.compile("\\b(tgt-[A-Za-z0-9_-]+|doc:[A-Za-z0-9_:.-]+)");
    private static final Pattern LONG_HEX = Pattern.compile("\\b[0-9a-fA-F]{16,}\\b");
    private static final Pattern OPAQUE = Pattern.compile("[A-Za-z0-9_\\-]{24,}");

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern CNPJ = Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");
    private static final Pattern JWT = Pattern.compile("\\b[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}\\b");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    /** Trecho de JWT isolado (cabeçalho/carga em base64url começam por "eyJ", que é "{\"" codificado). */
    private static final Pattern JWT_PART = Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    private DisplaySafety() {}

    /** Texto contém algo com aparência de identificador interno (UUID, IDs de contrato, hashes, tokens opacos). */
    public static boolean looksLikeInternalIdentifier(String text) {
        if (text == null || text.isEmpty()) return false;
        if (UUID.matcher(text).find() || CONTRACT_IDS.matcher(text).find() || LONG_HEX.matcher(text).find()) return true;
        Matcher m = OPAQUE.matcher(text);
        while (m.find()) {
            String token = m.group();
            if (token.chars().anyMatch(Character::isDigit) && token.chars().anyMatch(Character::isLetter)) return true;
        }
        return false;
    }

    /** Texto contém algo com aparência de dado pessoal ou segredo (e-mail, CPF, CNPJ, cartão, JWT, Bearer). */
    public static boolean looksSensitive(String text) {
        if (text == null || text.isEmpty()) return false;
        if (EMAIL.matcher(text).find() || CPF.matcher(text).find() || CNPJ.matcher(text).find()
                || JWT.matcher(text).find() || JWT_PART.matcher(text).find() || BEARER.matcher(text).find()) {
            return true;
        }
        Matcher m = CARD.matcher(text);
        while (m.find()) {
            if (luhn(m.group().replaceAll("[ -]", ""))) return true;
        }
        return false;
    }

    /** Pode ser apresentado ao usuário sem expor identificadores internos nem dados sensíveis. */
    public static boolean isSafeToDisplay(String text) {
        return !looksLikeInternalIdentifier(text) && !looksSensitive(text);
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return digits.length() >= 13 && sum % 10 == 0;
    }
}
