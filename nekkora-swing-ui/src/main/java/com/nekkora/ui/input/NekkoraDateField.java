package com.nekkora.ui.input;

import com.nekkora.ui.icon.IconName;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import javax.swing.text.AbstractDocument;

/**
 * Campo de data com máscara {@code dd/MM/aaaa}. Valida a data (inclusive dias inexistentes).
 * Setas ↑/↓ mudam o dia. O seletor de calendário virá em uma versão futura.
 */
public class NekkoraDateField extends NekkoraTextField {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public NekkoraDateField() {
        super("dd/mm/aaaa");
        setLeadingIcon(IconName.CALENDAR);
        setColumns(9);
        ((AbstractDocument) getDocument()).setDocumentFilter(new MaskFilter("##/##/####"));
        onTextChange(t -> setError(!isValueValid()));
        NumericKeys.install(this, d -> setValue(getValue().orElse(LocalDate.now()).plusDays(d)));
    }

    public Optional<LocalDate> getValue() {
        String t = getText();
        if (t.length() != 10) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(t, FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public void setValue(LocalDate date) {
        setText(date == null ? "" : FORMAT.format(date));
    }

    /** Vazio ou data completa e válida. */
    public boolean isValueValid() {
        String t = getText();
        return t.isEmpty() || (t.length() < 10 && hasFocus()) || getValue().isPresent();
    }
}
