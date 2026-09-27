package com.nekkora.ui.action;

import com.nekkora.ui.icon.IconName;
import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

/**
 * Ação da aplicação com identidade: id estável, rótulo, ícone, atalho e categoria. A mesma ação
 * alimenta botão, item de menu, atalho e — na V2 — a Command Palette (Ctrl+K), que vai listar
 * o {@link NekkoraActionRegistry}.
 *
 * <pre>{@code
 * NekkoraAction save = NekkoraAction.of("file.save", "Salvar", e -> salvar())
 *         .icon(IconName.SAVE).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_S)).category("Arquivo");
 * }</pre>
 */
public class NekkoraAction extends AbstractAction {
    public static final String ID = "nekkora.id";
    public static final String ICON = "nekkora.icon";
    public static final String CATEGORY = "nekkora.category";
    public static final String DANGER = "nekkora.danger";

    private final Consumer<ActionEvent> handler;

    public NekkoraAction(String id, String label, Consumer<ActionEvent> handler) {
        super(label);
        putValue(ID, Objects.requireNonNull(id));
        this.handler = Objects.requireNonNull(handler);
    }

    public static NekkoraAction of(String id, String label, Consumer<ActionEvent> handler) {
        return new NekkoraAction(id, label, handler);
    }

    public static NekkoraAction of(String id, String label, Runnable handler) {
        return new NekkoraAction(id, label, e -> handler.run());
    }

    public NekkoraAction icon(IconName icon) {
        putValue(ICON, icon);
        return this;
    }

    public NekkoraAction shortcut(KeyStroke key) {
        putValue(ACCELERATOR_KEY, key);
        return this;
    }

    public NekkoraAction category(String category) {
        putValue(CATEGORY, category);
        return this;
    }

    public NekkoraAction description(String description) {
        putValue(SHORT_DESCRIPTION, description);
        return this;
    }

    /** Marca como destrutiva (itens de menu em vermelho, confirmação recomendada). */
    public NekkoraAction danger() {
        putValue(DANGER, Boolean.TRUE);
        return this;
    }

    public String id() {
        return (String) getValue(ID);
    }

    public String label() {
        return (String) getValue(NAME);
    }

    public IconName iconName() {
        return (IconName) getValue(ICON);
    }

    public KeyStroke shortcutKey() {
        return (KeyStroke) getValue(ACCELERATOR_KEY);
    }

    public String categoryName() {
        return (String) getValue(CATEGORY);
    }

    public boolean isDanger() {
        return Boolean.TRUE.equals(getValue(DANGER));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isEnabled()) handler.accept(e);
    }
}
