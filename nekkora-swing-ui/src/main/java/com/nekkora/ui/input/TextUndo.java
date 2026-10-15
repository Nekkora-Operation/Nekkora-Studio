package com.nekkora.ui.input;

import com.nekkora.ui.action.NekkoraKeys;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/**
 * Desfazer/refazer a DIGITAÇÃO dentro de um campo (Ctrl+Z, Ctrl+Y e Ctrl+Shift+Z), restrito ao campo com foco. Assim,
 * dentro de um campo, esses atalhos nunca chegam a atalhos da janela (ex.: desfazer uma edição estrutural da aplicação).
 */
public final class TextUndo {
    static final String UNDO = "nekkora.text.undo";
    static final String REDO = "nekkora.text.redo";
    private static final String MANAGER = "nekkora.text.undoManager";

    private TextUndo() {}

    /** Instala o histórico de digitação no campo (acompanha a troca de documento). */
    public static void install(JTextComponent field) {
        UndoManager manager = new UndoManager();
        manager.setLimit(200);
        field.putClientProperty(MANAGER, manager);
        attach(field.getDocument(), manager);
        PropertyChangeListener docChange = e -> {
            manager.discardAllEdits();
            if (e.getOldValue() instanceof Document old) old.removeUndoableEditListener(manager);
            if (e.getNewValue() instanceof Document doc) attach(doc, manager);
        };
        field.addPropertyChangeListener("document", docChange);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(NekkoraKeys.shortcut(KeyEvent.VK_Z), UNDO);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(NekkoraKeys.shortcut(KeyEvent.VK_Y), REDO);
        field.getInputMap(JComponent.WHEN_FOCUSED).put(NekkoraKeys.shortcut(KeyEvent.VK_Z, KeyEvent.SHIFT_DOWN_MASK), REDO);
        field.getActionMap().put(UNDO, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (manager.canUndo()) manager.undo();
                } catch (CannotUndoException ignored) {
                    // nada a desfazer no campo
                }
            }
        });
        field.getActionMap().put(REDO, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (manager.canRedo()) manager.redo();
                } catch (CannotRedoException ignored) {
                    // nada a refazer no campo
                }
            }
        });
    }

    /** Histórico de digitação do campo (para testes). */
    public static UndoManager of(JTextComponent field) {
        return (UndoManager) field.getClientProperty(MANAGER);
    }

    /** Atalho de desfazer do campo (para testes de teclado). */
    public static KeyStroke undoKey() {
        return NekkoraKeys.shortcut(KeyEvent.VK_Z);
    }

    private static void attach(Document doc, UndoManager manager) {
        if (doc != null) doc.addUndoableEditListener(manager);
    }
}
