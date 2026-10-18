package com.nekkora.studio.codegen;

import java.util.ArrayList;
import java.util.List;

/**
 * Quebra determinística de linhas Python acima de {@link #MAX} colunas (perfil do framework, ruff E501): uma chamada
 * que termina a linha vira uma chamada com um argumento por linha (vírgula final, como o formatador do Python); um
 * comentário no fim da linha sobe para a linha de cima. Só reorganiza espaços em branco: o código continua o mesmo.
 * Linha que não é chamada (ou cujo argumento sozinho já excede o limite) fica como está.
 */
final class PyWrap {
    static final int MAX = 120;

    private PyWrap() {}

    static String wrap(String src) {
        StringBuilder out = new StringBuilder();
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            out.append(line(lines[i]));
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String line(String l) {
        if (l.length() <= MAX) return l;
        String indent = l.substring(0, l.length() - l.stripLeading().length());
        String code = l;
        String comment = null;
        int hash = commentStart(l);
        if (hash >= 0) {
            comment = l.substring(hash).strip();
            code = l.substring(0, hash).stripTrailing();
        }
        int open = callOpening(code);
        if (open < 0) return l;
        List<String> args = splitArgs(code.substring(open + 1, code.length() - 1));
        if (args.isEmpty()) return l;
        String inner = indent + "    ";
        StringBuilder b = new StringBuilder();
        if (comment != null) b.append(indent).append(comment).append('\n');
        b.append(code, 0, open + 1).append('\n');
        for (String a : args) b.append(inner).append(a).append(",\n");
        b.append(indent).append(')');
        return b.toString();
    }

    /** Início de um comentário fora de string, ou -1. */
    private static int commentStart(String l) {
        char q = 0;
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            if (q != 0) {
                if (c == '\\') i++;
                else if (c == q) q = 0;
            } else if (c == '"' || c == '\'') {
                q = c;
            } else if (c == '#') {
                return i;
            }
        }
        return -1;
    }

    /** Parêntese de abertura (fora de string, profundidade 0) cujo par fecha exatamente no fim da linha; -1 se não houver. */
    private static int callOpening(String code) {
        if (!code.endsWith(")")) return -1;
        char q = 0;
        int depth = 0;
        int candidate = -1;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (q != 0) {
                if (c == '\\') i++;
                else if (c == q) q = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                q = c;
            } else if (c == '(' || c == '[' || c == '{') {
                if (depth == 0 && c == '(' && i > 0 && isCallee(code.charAt(i - 1))) candidate = i;
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0 && i == code.length() - 1) return candidate;
                if (depth == 0) candidate = -1;
            }
        }
        return -1;
    }

    private static boolean isCallee(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ')' || c == ']';
    }

    /** Argumentos de nível 0 (vírgulas fora de string e de parênteses/colchetes/chaves). */
    private static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        char q = 0;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q != 0) {
                if (c == '\\') i++;
                else if (c == q) q = 0;
                continue;
            }
            if (c == '"' || c == '\'') q = c;
            else if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i).strip());
                start = i + 1;
            }
        }
        String last = s.substring(start).strip();
        if (!last.isEmpty()) out.add(last);
        return out;
    }
}
