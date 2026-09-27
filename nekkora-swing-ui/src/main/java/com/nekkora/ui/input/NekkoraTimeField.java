package com.nekkora.ui.input;

import com.nekkora.ui.icon.IconName;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import javax.swing.text.AbstractDocument;

/** Campo de hora {@code HH:mm} (24h). Setas ↑/↓ mudam os minutos (Shift: de 10 em 10). */
public class NekkoraTimeField extends NekkoraTextField {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public NekkoraTimeField() {
        super("hh:mm");
        setLeadingIcon(IconName.CLOCK);
        setColumns(5);
        ((AbstractDocument) getDocument()).setDocumentFilter(new MaskFilter("##:##"));
        onTextChange(t -> setError(!isValueValid()));
        NumericKeys.install(this, d -> setValue(getValue().orElse(LocalTime.of(0, 0)).plusMinutes(d)));
    }

    public Optional<LocalTime> getValue() {
        String t = getText();
        if (t.length() != 5) return Optional.empty();
        try {
            return Optional.of(LocalTime.parse(t, FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public void setValue(LocalTime time) {
        setText(time == null ? "" : FORMAT.format(time));
    }

    public boolean isValueValid() {
        String t = getText();
        return t.isEmpty() || (t.length() < 5 && hasFocus()) || getValue().isPresent();
    }
}
