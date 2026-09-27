package com.nekkora.ui.file;

import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.theme.Scale;
import java.awt.BorderLayout;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JPanel;

/** Campo de caminho: texto editável + botão "Procurar…" que abre o diálogo adequado. */
public class NekkoraFilePathField extends JPanel {

    public enum Mode { OPEN_FILE, SAVE_FILE, FOLDER }

    private final NekkoraTextField field;
    private final NekkoraButton browse;
    private final Mode mode;
    private String[] extensions = new String[0];
    private boolean mustExist;
    private Consumer<Path> onChange;

    public NekkoraFilePathField(Mode mode, String placeholder) {
        super(new BorderLayout(Scale.px(Spacing.SPACE_3), 0));
        setOpaque(false);
        this.mode = mode;
        field = new NekkoraTextField(placeholder);
        field.setLeadingIcon(mode == Mode.FOLDER ? IconName.FOLDER : IconName.FILE);
        browse = NekkoraButton.secondary("Procurar…");
        browse.getAccessibleContext().setAccessibleName(mode == Mode.FOLDER ? "Procurar pasta" : "Procurar arquivo");
        browse.addActionListener(e -> choose());
        field.onTextChange(t -> {
            validatePath();
            if (onChange != null) getPath().ifPresent(onChange);
        });
        add(field, BorderLayout.CENTER);
        add(browse, BorderLayout.EAST);
    }

    public void setExtensions(String... extensions) {
        this.extensions = extensions;
    }

    /** Marca erro se o caminho digitado não existir. */
    public void setMustExist(boolean mustExist) {
        this.mustExist = mustExist;
        validatePath();
    }

    public void onPathChange(Consumer<Path> listener) {
        this.onChange = listener;
    }

    public Optional<Path> getPath() {
        String t = field.getText().strip();
        if (t.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Path.of(t));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    public void setPath(Path path) {
        field.setText(path == null ? "" : path.toString());
    }

    public NekkoraTextField getTextField() {
        return field;
    }

    private void validatePath() {
        String t = field.getText().strip();
        boolean invalid = !t.isEmpty() && (getPath().isEmpty() || (mustExist && !Files.exists(getPath().get())));
        field.setError(invalid);
    }

    private void choose() {
        Optional<Path> chosen = switch (mode) {
            case FOLDER -> NekkoraFileDialogs.selectFolder(this, "Selecionar pasta");
            case SAVE_FILE -> NekkoraFileDialogs.saveFile(this, "Salvar como", null, extensions.length > 0 ? extensions[0] : null);
            default -> NekkoraFileDialogs.openFile(this, "Abrir arquivo", null, extensions);
        };
        chosen.ifPresent(this::setPath);
    }
}
