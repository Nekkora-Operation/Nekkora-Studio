package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * STUDIO-010 G3 — política de TEXTO do PDD.
 *
 * <ul>
 *   <li>{@link #safeLabel}: texto vindo do modelo (nome do alvo, nome da sessão) só entra no documento sem revisão se for
 *       metadado de interface pela {@code nekkora-privacy/2} ({@link CapturePrivacy#interfaceText}: todo termo no
 *       vocabulário fechado) E não tiver aparência de identificador interno ou dado sensível.</li>
 *   <li>{@link #problems}: texto ESCRITO ou revisado no editor (do desenvolvedor ou do agente de teste) ainda passa por
 *       um bloqueio de padrões — sequência de dígitos, CPF/CNPJ/e-mail/cartão/token, identificador técnico, URL,
 *       seletor, expressão de variável, caracteres de controle. Qualquer problema impede a exportação.</li>
 * </ul>
 * Nenhum texto do documento é inventado por IA: os modelos de instrução são determinísticos ({@link Instructions}).
 */
public final class PddText {
    /** Tamanho máximo de um título ou de uma instrução. */
    public static final int MAX_LENGTH = 160;

    private static final Pattern DIGITS = Pattern.compile("\\d{3,}");
    private static final Pattern URL = Pattern.compile("(?i)\\b(https?://|www\\.|file:|data:)|\\b[a-z0-9-]+\\.(com|br|net|org|io|local)\\b");
    private static final Pattern SELECTOR = Pattern.compile("(//|\\[@|\\[data-|\\[name=|#[A-Za-z][\\w-]*|\\.[a-z][\\w-]*\\s*[>{]|>>|::|\\$\\(|xpath|css=)");
    private static final Pattern VARIABLE = Pattern.compile("(\\$\\{|\\{\\{|\\}\\}|\\b[a-z][a-z0-9_]*\\.[a-z][a-z0-9_.]*\\b|\\b[a-z]+_[a-z0-9_]+\\b)");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n]]");

    private PddText() {}

    /** Texto do modelo apresentável sem revisão (forma limpa) ou vazio. */
    public static Optional<String> safeLabel(String modelText) {
        if (modelText == null || modelText.isBlank()) return Optional.empty();
        String t = CapturePrivacy.interfaceText(modelText);
        if (t == null || !DisplaySafety.isSafeToDisplay(t) || !problems(t).isEmpty()) return Optional.empty();
        return Optional.of(t);
    }

    /** Problemas de um texto do documento (vazio = pode ir ao PDF). Só códigos — o texto nunca é repetido. */
    public static List<String> problems(String text) {
        List<String> p = new ArrayList<>();
        if (text == null || text.isBlank()) {
            p.add("TEXT_EMPTY");
            return p;
        }
        if (text.length() > MAX_LENGTH) p.add("TEXT_TOO_LONG");
        if (CONTROL.matcher(text).find() || text.contains("\n")) p.add("CONTROL_CHARACTER");
        if (DIGITS.matcher(text).find()) p.add("DIGIT_SEQUENCE");
        if (DisplaySafety.looksSensitive(text)) p.add("SENSITIVE_PATTERN");
        if (DisplaySafety.looksLikeInternalIdentifier(text)) p.add("INTERNAL_IDENTIFIER");
        if (URL.matcher(text).find()) p.add("URL");
        if (SELECTOR.matcher(text).find()) p.add("SELECTOR");
        if (VARIABLE.matcher(text).find()) p.add("VARIABLE_EXPRESSION");
        return p;
    }
}
