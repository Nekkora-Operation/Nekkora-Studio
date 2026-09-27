package com.nekkora.ui.input;

import com.formdev.flatlaf.FlatClientProperties;
import com.nekkora.ui.icon.IconName;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 * Campo de busca: ícone de lupa, botão limpar, {@code Esc} limpa, {@code Enter} busca na hora.
 * {@link #onSearch} é chamado com atraso (debounce) enquanto o usuário digita.
 */
public class NekkoraSearchField extends NekkoraTextField {
    private final Timer debounce;
    private Consumer<String> onSearch;

    public NekkoraSearchField() {
        this("Buscar…");
    }

    public NekkoraSearchField(String placeholder) {
        super(placeholder);
        setLeadingIcon(IconName.SEARCH);
        setClearable(true);
        putClientProperty(FlatClientProperties.TEXT_FIELD_CLEAR_CALLBACK, (Runnable) this::clear);
        getAccessibleContext().setAccessibleName(placeholder);
        debounce = new Timer(250, e -> fire());
        debounce.setRepeats(false);
        onTextChange(t -> debounce.restart());
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "nekkora.clear");
        getActionMap().put("nekkora.clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (getText().isEmpty()) transferFocus();
                else clear();
            }
        });
        addActionListener(e -> {
            debounce.stop();
            fire();
        });
    }

    /** Recebe o termo buscado (já sem espaços nas pontas). */
    public void onSearch(Consumer<String> listener) {
        this.onSearch = listener;
    }

    /** Atraso do debounce, em ms (padrão 250). */
    public void setDebounceDelay(int millis) {
        debounce.setInitialDelay(millis);
    }

    public void clear() {
        setText("");
        debounce.stop();
        fire();
    }

    private void fire() {
        if (onSearch != null) onSearch.accept(getText().strip());
    }
}
