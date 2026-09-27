package com.nekkora.ui.action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Registro de ações da aplicação. Base da busca de comandos (Command Palette, V2): as telas
 * registram ações aqui em vez de espalhar atalhos soltos.
 */
public class NekkoraActionRegistry {
    private final Map<String, NekkoraAction> actions = new LinkedHashMap<>();

    public NekkoraAction register(NekkoraAction action) {
        NekkoraAction previous = actions.putIfAbsent(action.id(), action);
        if (previous != null && previous != action) {
            throw new IllegalArgumentException("Ação já registrada: " + action.id());
        }
        return action;
    }

    public Optional<NekkoraAction> find(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    public Collection<NekkoraAction> all() {
        return List.copyOf(actions.values());
    }

    /** Busca simples por rótulo/categoria/id (sem acentos, sem caixa). */
    public List<NekkoraAction> search(String query) {
        String q = normalize(query);
        List<NekkoraAction> out = new ArrayList<>();
        for (NekkoraAction a : actions.values()) {
            String hay = normalize(a.label() + " " + (a.categoryName() == null ? "" : a.categoryName()) + " " + a.id());
            if (q.isEmpty() || hay.contains(q)) out.add(a);
        }
        return out;
    }

    /** Liga os atalhos de todas as ações à janela de {@code root}. */
    public void bindShortcuts(JComponent root) {
        for (NekkoraAction a : actions.values()) {
            KeyStroke ks = a.shortcutKey();
            if (ks == null) continue;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, a.id());
            root.getActionMap().put(a.id(), a);
        }
    }

    static String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }
}
