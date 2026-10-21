package com.nekkora.studio.codegen;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — regras de NOME do código gerado, puras e determinísticas (as mesmas para o planejador e
 * para o gerador). Os nomes vêm de evidência já aprovada — títulos REVISADOS do PDD, nomes de variáveis do modelo,
 * rótulos de interface do alvo — e nunca de invenção: sem evidência, o chamador usa um nome provisório e o Quality Gate
 * aponta {@code NOME_SEM_EVIDENCIA}. Saída sempre ASCII, válida em Python, fora das palavras reservadas e dos membros que
 * a classe-base do framework já usa.
 */
public final class CodeNaming {
    private CodeNaming() {}

    /** Artigos, preposições e contrações: não carregam significado num nome. */
    static final Set<String> STOPWORDS = Set.of("o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "no", "na", "nos", "nas",
            "em", "pelo", "pela", "pelos", "pelas", "por", "para", "pra", "com", "e", "ou", "ao", "aos", "se", "que", "sobre", "the", "of", "to", "and", "in");

    /** Palavras que, sozinhas, não dizem o que a tela ou o passo faz (título genérico do PDD). */
    static final Set<String> GENERIC = Set.of("etapa", "etapas", "bloco", "blocos", "processo", "processos", "sessao", "sessoes", "tela", "telas", "passo",
            "passos", "parte", "partes", "pagina", "paginas", "acao", "acoes", "item", "itens", "geral", "outros", "outras", "diversos");

    static final Set<String> PY_RESERVED = Set.of("false", "none", "true", "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield", "match", "case", "type", "self", "cls", "print", "input", "id", "list", "dict", "str", "set", "object");

    /** Membros de {@code BasePage}/{@code RpaProcess} que um método gerado nunca pode sobrescrever. */
    static final Set<String> RESERVED_MEMBERS = Set.of("act", "k", "prefix", "identity", "is_current", "wait_loaded", "name", "process", "init", "end",
            "recover", "reconcile", "requires_browser", "required_secrets");

    /**
     * Marcadores de plural/gênero colados a uma palavra viram parte dela, não uma palavra nova (D-E2E2-12):
     * "Campo(s)" → "Campos"; "Autor(a)" → "Autor".
     */
    public static String foldMarkers(String text) {
        return text.replaceAll("(\\p{L})\\((s|es)\\)", "$1$2").replaceAll("(\\p{L})\\((a|as|o|os)\\)", "$1");
    }

    /** Palavras ASCII minúsculas do texto (sem acento), na ordem. */
    public static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String folded = Normalizer.normalize(foldMarkers(text), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (String w : folded.split("[^a-z0-9]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** Palavras de conteúdo (sem artigos e preposições). */
    public static List<String> contentWords(String text) {
        return words(text).stream().filter(w -> !STOPWORDS.contains(w)).toList();
    }

    /** Título vazio ou feito só de palavras genéricas ("Etapas do processo", "Sessão 2", "Bloco do processo"). */
    public static boolean genericTitle(String text) {
        List<String> c = contentWords(text);
        return c.isEmpty() || c.stream().allMatch(w -> GENERIC.contains(w) || w.chars().allMatch(Character::isDigit));
    }

    /** Verbo no infinitivo (pesquisar, ler, conferir, preencher, pôr) — o começo típico de um título de passo do PDD. */
    public static boolean infinitive(String word) {
        return word != null && word.length() >= 2 && word.matches("[a-z]+(ar|er|ir|or)") && !word.matches("(lugar|par|mar|bar|mulher|colher|valor|setor|maior|menor|melhor|pior|anterior|posterior|exterior|interior|superior|inferior)");
    }

    /**
     * Substantivo que nomeia uma TELA a partir do título da seção do PDD: a 1ª palavra de conteúdo que não é genérica
     * ("Pesquisa do artigo" → {@code pesquisa}; "Extração dos dados" → {@code extracao}). Vazio = sem evidência.
     */
    public static Optional<String> screenNoun(String sectionTitle) {
        if (sectionTitle == null || genericTitle(sectionTitle)) return Optional.empty();
        for (String w : contentWords(sectionTitle)) {
            if (!GENERIC.contains(w) && Character.isLetter(w.charAt(0))) return Optional.of(w);
        }
        return Optional.empty();
    }

    /**
     * Nome de método a partir do título de um BLOCO do PDD que começa por verbo no infinitivo: verbo + 1ª palavra de
     * conteúdo ("Pesquisar o artigo pelo nome" → {@code pesquisar_artigo}; "Ler o endereço e os dados da ficha" →
     * {@code ler_endereco}). Vazio se o título é genérico ou não começa por verbo.
     */
    public static Optional<String> methodFromTitle(String blockTitle) {
        if (blockTitle == null || genericTitle(blockTitle)) return Optional.empty();
        List<String> w = words(blockTitle);
        if (w.isEmpty() || !infinitive(w.get(0))) return Optional.empty();
        String verb = w.get(0);
        for (int i = 1; i < w.size(); i++) {
            String x = w.get(i);
            if (STOPWORDS.contains(x) || GENERIC.contains(x) || !Character.isLetter(x.charAt(0))) continue;
            return Optional.of(verb + "_" + x);
        }
        return Optional.of(verb);
    }

    /** {@code snake_case} ASCII seguro (até {@code max} caracteres); vazio se não sobrar letra. */
    public static String snake(String text, int max) {
        String s = String.join("_", words(text));
        if (s.length() > max) s = s.substring(0, max).replaceAll("_+$", "");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) return "";
        return s;
    }

    /** {@code PascalCase} ASCII ("extracao" → {@code Extracao}; "nome completo" → {@code NomeCompleto}). */
    public static String pascal(String text) {
        StringBuilder b = new StringBuilder();
        for (String w : words(text)) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    /** Nome Python seguro: palavra reservada, nome embutido comum ou membro da classe-base ganha {@code _}. */
    public static String safe(String name) {
        if (PY_RESERVED.contains(name) || RESERVED_MEMBERS.contains(name)) return name + "_";
        return name;
    }

    /** Único dentro de {@code taken} (sufixo {@code _2}, {@code _3}… na ordem de chegada); acrescenta a {@code taken}. */
    public static String unique(String base, Collection<String> taken, String separator) {
        String out = base;
        for (int i = 2; taken.contains(out); i++) out = base + separator + i;
        taken.add(out);
        return out;
    }

    /** Texto legível de um identificador ("nome_completo" → "nome completo"). */
    public static String human(String identifier) {
        return identifier.replace('_', ' ').strip();
    }

    /** Nome técnico opaco ou mecânico que a saída nova não deve ter (o Quality Gate usa a mesma regra). */
    public static boolean opaque(String name) {
        return name.matches("(?i)(tela|pagina)\\d+|etapas_sessao_\\d+|fill|press_key|click|observe|wait_for|navigate|campo_s|alvo_.*|[a-z]|tmp|aux|var\\d*|"
                + "dados\\d+|metodo\\d*|processo\\d*");
    }
}
