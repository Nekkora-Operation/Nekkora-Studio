package com.nekkora.studio.codegen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * NEKKORA-CODEGEN-QUALITY-001, rodada 3 (Q11) — nomes em INGLÊS para o código gerado a partir de texto em português (títulos
 * revisados do PDD, nomes de variáveis do modelo, rótulos de interface), por um glossário GOVERNADO e regras fixas — nunca
 * tradução livre. Mesma entrada → mesmo nome, sempre.
 *
 * <p>Regras de uma frase nominal: os conectivos {@code de/do/da/dos/das} separam grupos, que em inglês vêm em ordem inversa
 * ("termo de busca" → {@code search term}); dentro do grupo, o substantivo-núcleo vai para o fim ("nome completo" →
 * {@code full name}, "área segura" → {@code secure area}) e palavras já em inglês ou nomes próprios vão para o começo ("link
 * Logout" → {@code logout link}). Artigos e preposições somem. Plural e feminino regulares seguem o singular masculino do
 * glossário ("campos" → {@code fields}, "segura" → {@code secure}).
 *
 * <p>Palavra que não está no glossário nem é inglesa conhecida NÃO é inventada: fica como está (ASCII) e volta em
 * {@link Phrase#untranslated()}, para o Studio apontar a pendência e o manifesto registrar.
 */
public final class EnglishNames {

    /** Identificador do glossário (vai ao manifesto; toda mudança de tradução sobe a versão). */
    public static final String GLOSSARY = "nekkora-studio/naming-pt-en/1.2.0";

    private EnglishNames() {}

    /** Classe gramatical no glossário. */
    enum Pos { NOUN, ADJECTIVE, PRE_ADJECTIVE, VERB }

    record Entry(String english, Pos pos) {}

    private static final Map<String, Entry> GLOSSARY_ENTRIES = new LinkedHashMap<>();
    /** 1.1.0 (R4-02): o substantivo quando o valor é uma DATA ("nascimento" → {@code birth date}); classe {@code d}. */
    private static final Map<String, String> DATE_ENTRIES = new LinkedHashMap<>();
    private static final Set<String> ENGLISH = new HashSet<>();
    private static int longestPhrase = 1;

    static {
        load("naming/glossary-pt-en.tsv", line -> {
            String[] f = line.split("\t");
            if (f.length != 3) throw new IllegalStateException("glossário: linha inválida");
            if (f[2].equals("d")) {
                if (DATE_ENTRIES.put(f[0], f[1]) != null) throw new IllegalStateException("glossário: entrada de data repetida");
                for (String w : f[1].split(" ")) ENGLISH.add(w);
                return;
            }
            Pos pos = switch (f[2]) {
                case "n" -> Pos.NOUN;
                case "a" -> Pos.ADJECTIVE;
                case "p" -> Pos.PRE_ADJECTIVE;
                case "v" -> Pos.VERB;
                default -> throw new IllegalStateException("glossário: classe desconhecida");
            };
            if (GLOSSARY_ENTRIES.put(f[0], new Entry(f[1], pos)) != null) throw new IllegalStateException("glossário: entrada repetida");
            longestPhrase = Math.max(longestPhrase, f[0].split(" ").length);
            for (String w : f[1].split(" ")) ENGLISH.add(w);
        });
        load("naming/english-words.txt", ENGLISH::add);
    }

    private static void load(String resource, java.util.function.Consumer<String> sink) {
        try (InputStream in = Objects.requireNonNull(EnglishNames.class.getResourceAsStream(resource), resource);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                sink.accept(line.strip());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Conectivos que ligam um grupo ao seguinte ("termo DE busca", "dados DA ficha"). */
    static final Set<String> CONNECTIVES = Set.of("de", "do", "da", "dos", "das");

    /** Artigos e preposições (português e os artigos ingleses): não entram no nome. */
    static final Set<String> DROPPED = Set.of("o", "a", "os", "as", "um", "uma", "uns", "umas", "em", "no", "na", "nos", "nas", "pelo", "pela", "pelos", "pelas",
            "por", "para", "pra", "com", "ao", "aos", "se", "que", "sobre", "the", "an");

    /**
     * Resultado de uma tradução: as palavras inglesas (minúsculas, ASCII) e as palavras de origem que ficaram sem tradução.
     */
    public record Phrase(List<String> words, List<String> untranslated) {
        public Phrase {
            words = List.copyOf(words);
            untranslated = List.copyOf(untranslated);
        }

        public String snake() {
            return String.join("_", words);
        }

        public String pascal() {
            StringBuilder b = new StringBuilder();
            for (String w : words) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            return b.toString();
        }

        public String text() {
            return String.join(" ", words);
        }

        public boolean isEmpty() {
            return words.isEmpty();
        }

        public Phrase then(Phrase other) {
            List<String> w = new ArrayList<>(words);
            w.addAll(other.words);
            Set<String> u = new LinkedHashSet<>(untranslated);
            u.addAll(other.untranslated);
            return new Phrase(w, new ArrayList<>(u));
        }

        public static Phrase of(String... words) {
            List<String> w = new ArrayList<>();
            for (String x : words) for (String y : x.split("[ _]")) if (!y.isEmpty()) w.add(y);
            return new Phrase(w, List.of());
        }
    }

    private record Token(List<String> english, Pos pos, boolean portuguese, String source) {
        boolean untranslated() {
            return !portuguese && english.size() == 1 && english.get(0).equals(source) && !isEnglish(source) && !source.chars().allMatch(Character::isDigit);
        }
    }

    /** Palavra inglesa conhecida, inclusive no plural regular ("fields", "entries", "boxes"). */
    static boolean isEnglish(String w) {
        if (ENGLISH.contains(w)) return true;
        if (w.endsWith("ies") && ENGLISH.contains(w.substring(0, w.length() - 3) + "y")) return true;
        if (w.endsWith("es") && ENGLISH.contains(w.substring(0, w.length() - 2))) return true;
        return w.endsWith("s") && ENGLISH.contains(w.substring(0, w.length() - 1));
    }

    // ------------------------------------------------------------------------------------------------ frases nominais

    /** Frase nominal em inglês ("Nome completo" → {@code full name}; "termo_de_busca" → {@code search term}). */
    public static Phrase noun(String text) {
        return noun(text, false);
    }

    /**
     * Rodada 4 (R4-02): o mesmo, sabendo se o VALOR nomeado é uma data. Com {@code date}, o substantivo da tabela de
     * contexto de data (classe {@code d} do glossário) substitui o comum ("Nascimento" lido como data →
     * {@code birth date}); nome que já diz data ("Data de nascimento" → {@code birth date}) fica como está.
     */
    public static Phrase noun(String text, boolean date) {
        Phrase plain = nounIn(text, false);
        if (!date || plain.words().contains("date") || plain.words().contains("day")) return plain;
        return nounIn(text, true);
    }

    private static Phrase nounIn(String text, boolean date) {
        List<String> w = CodeNaming.words(text == null ? "" : text.replace('_', ' '));
        List<List<String>> parts = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        for (String x : w) {
            if (x.equals("e") || x.equals("and")) {
                if (!cur.isEmpty()) parts.add(cur);
                cur = new ArrayList<>();
            } else {
                cur.add(x);
            }
        }
        if (!cur.isEmpty()) parts.add(cur);
        Phrase out = new Phrase(List.of(), List.of());
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out = out.then(Phrase.of("and"));
            out = out.then(conjunct(parts.get(i), date));
        }
        return out;
    }

    /** Identificador inglês de um nome em snake_case do modelo ({@code termo_de_busca} → {@code search_term}). */
    public static Phrase identifier(String name) {
        return noun(name);
    }

    /** Identificador de um valor que pode ser data (R4-02): {@code nascimento} lido como data → {@code birth_date}. */
    public static Phrase identifier(String name, boolean date) {
        return noun(name, date);
    }

    private static Phrase conjunct(List<String> w, boolean date) {
        List<Token> tokens = tokens(w, date);
        List<List<Token>> groups = new ArrayList<>();
        List<Token> group = new ArrayList<>();
        for (Token t : tokens) {
            if (t.portuguese() && t.english().isEmpty() && CONNECTIVES.contains(t.source())) {
                if (!group.isEmpty()) groups.add(group);
                group = new ArrayList<>();
            } else if (!t.english().isEmpty()) {
                group.add(t);
            }
        }
        if (!group.isEmpty()) groups.add(group);
        List<String> words = new ArrayList<>();
        Set<String> untranslated = new LinkedHashSet<>();
        for (int g = groups.size() - 1; g >= 0; g--) {
            List<Token> gr = groups.get(g);
            int head = -1;
            for (int i = 0; i < gr.size(); i++) {
                if (gr.get(i).portuguese() && gr.get(i).pos() == Pos.NOUN) {
                    head = i;
                    break;
                }
            }
            List<Token> ordered = new ArrayList<>();
            if (head < 0) {
                ordered.addAll(gr);
            } else {
                // já em inglês / nome próprio primeiro, depois os modificadores portugueses na ordem, o núcleo por último
                for (int i = 0; i < gr.size(); i++) if (i != head && !gr.get(i).portuguese()) ordered.add(gr.get(i));
                for (int i = 0; i < gr.size(); i++) if (i != head && gr.get(i).portuguese()) ordered.add(gr.get(i));
                ordered.add(gr.get(head));
            }
            for (Token t : ordered) {
                words.addAll(t.english());
                if (t.untranslated()) untranslated.add(t.source());
            }
        }
        return new Phrase(words, new ArrayList<>(untranslated));
    }

    /** Palavras → tokens: expressões do glossário (a mais longa primeiro), depois palavra a palavra. */
    private static List<Token> tokens(List<String> w, boolean date) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < w.size()) {
            boolean matched = false;
            for (int n = Math.min(longestPhrase, w.size() - i); n >= 2; n--) {
                String phrase = String.join(" ", w.subList(i, i + n));
                Entry e = GLOSSARY_ENTRIES.get(phrase);
                if (e != null && e.pos() != Pos.VERB) {
                    out.add(new Token(List.of(e.english().split(" ")), e.pos(), true, phrase));
                    i += n;
                    matched = true;
                    break;
                }
            }
            if (matched) continue;
            String dated = date ? DATE_ENTRIES.get(w.get(i)) : null;
            out.add(dated != null ? new Token(List.of(dated.split(" ")), Pos.NOUN, true, w.get(i)) : word(w.get(i)));
            i++;
        }
        return out;
    }

    private static Token word(String x) {
        if (CONNECTIVES.contains(x)) return new Token(List.of(), Pos.NOUN, true, x);
        if (DROPPED.contains(x)) return new Token(List.of(), Pos.NOUN, true, x);
        Entry e = GLOSSARY_ENTRIES.get(x);
        if (e != null && e.pos() != Pos.VERB) return new Token(List.of(e.english().split(" ")), e.pos(), true, x);
        // plural regular: "campos" → campo + s; "nacionalidades" → nacionalidade + s; "opcoes" → opcao (ões → ão)
        for (String singular : singulars(x)) {
            Entry s = GLOSSARY_ENTRIES.get(singular);
            if (s == null || s.pos() == Pos.VERB) continue;
            if (s.pos() == Pos.NOUN) return new Token(plural(s.english()), Pos.NOUN, true, x);
            return new Token(List.of(s.english().split(" ")), s.pos(), true, x);
        }
        // feminino regular de adjetivo: "segura" → seguro; "completa" → completo
        if (x.endsWith("a") && x.length() > 2) {
            Entry m = GLOSSARY_ENTRIES.get(x.substring(0, x.length() - 1) + "o");
            if (m != null && (m.pos() == Pos.ADJECTIVE || m.pos() == Pos.PRE_ADJECTIVE)) return new Token(List.of(m.english().split(" ")), m.pos(), true, x);
        }
        if (x.endsWith("as") && x.length() > 3) {
            Entry m = GLOSSARY_ENTRIES.get(x.substring(0, x.length() - 2) + "o");
            if (m != null && (m.pos() == Pos.ADJECTIVE || m.pos() == Pos.PRE_ADJECTIVE)) return new Token(List.of(m.english().split(" ")), m.pos(), true, x);
        }
        // rótulo que é um verbo no infinitivo ("Entrar", "Consultar", "Sair" num botão): a tradução do verbo, sem virar núcleo
        if (e != null) return new Token(List.of(e.english().split(" ")), Pos.VERB, true, x);
        // já em inglês, número ou palavra sem tradução (fica como está e é apontada)
        return new Token(List.of(x), Pos.NOUN, false, x);
    }

    private static List<String> singulars(String x) {
        List<String> out = new ArrayList<>();
        if (x.endsWith("oes") && x.length() > 4) out.add(x.substring(0, x.length() - 3) + "ao");
        if (x.endsWith("es") && x.length() > 3) out.add(x.substring(0, x.length() - 2));
        if (x.endsWith("s") && x.length() > 2) out.add(x.substring(0, x.length() - 1));
        return out;
    }

    /** Plural inglês regular da última palavra. */
    static List<String> plural(String english) {
        List<String> w = new ArrayList<>(List.of(english.split(" ")));
        String last = w.get(w.size() - 1);
        String p;
        if (last.equals("data") || last.equals("information") || last.endsWith("s")) p = last;
        else if (last.endsWith("y") && last.length() > 1 && "aeiou".indexOf(last.charAt(last.length() - 2)) < 0) p = last.substring(0, last.length() - 1) + "ies";
        else if (last.endsWith("x") || last.endsWith("ch") || last.endsWith("sh")) p = last + "es";
        else p = last + "s";
        w.set(w.size() - 1, p);
        return w;
    }

    // ------------------------------------------------------------------------------------------------ verbos e métodos

    /** Verbo inglês de um infinitivo português do glossário ("pesquisar" → {@code search}; "entrar" → {@code sign in}). */
    public static Optional<Phrase> verb(String portuguese) {
        Entry e = GLOSSARY_ENTRIES.get(portuguese);
        if (e == null || e.pos() != Pos.VERB) return Optional.empty();
        return Optional.of(Phrase.of(e.english()));
    }

    /**
     * Nome de método a partir do título de um BLOCO do PDD que começa por verbo no infinitivo: verbo + 1ª palavra de
     * conteúdo ("Pesquisar o artigo pelo nome" → {@code search article}). Vazio se não começa por verbo do glossário.
     */
    public static Optional<Phrase> method(String blockTitle) {
        if (blockTitle == null || CodeNaming.genericTitle(blockTitle)) return Optional.empty();
        List<String> w = CodeNaming.words(blockTitle);
        if (w.isEmpty()) return Optional.empty();
        Optional<Phrase> verb = verb(w.get(0));
        if (verb.isEmpty()) return Optional.empty();
        for (int i = 1; i < w.size(); i++) {
            String x = w.get(i);
            if (CodeNaming.STOPWORDS.contains(x) || CodeNaming.GENERIC.contains(x) || !Character.isLetter(x.charAt(0))) continue;
            return Optional.of(verb.get().then(noun(x)));
        }
        return verb;
    }

    /** 3ª pessoa do singular de um verbo inglês do glossário, para docstrings ("search" → "searches", "sign in" → "signs in"). */
    public static String thirdPerson(String verbPhrase) {
        String[] w = verbPhrase.split(" ", 2);
        String v = w[0];
        String c;
        if (v.endsWith("s") || v.endsWith("x") || v.endsWith("ch") || v.endsWith("sh") || v.endsWith("o")) c = v + "es";
        else if (v.endsWith("y") && v.length() > 1 && "aeiou".indexOf(v.charAt(v.length() - 2)) < 0) c = v.substring(0, v.length() - 1) + "ies";
        else c = v + "s";
        return w.length == 1 ? c : c + " " + w[1];
    }

    /** O glossário conhece esta palavra portuguesa (substantivo/adjetivo/verbo)? */
    static boolean known(String portuguese) {
        return GLOSSARY_ENTRIES.containsKey(portuguese);
    }
}
