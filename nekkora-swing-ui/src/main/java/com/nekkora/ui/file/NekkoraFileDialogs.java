package com.nekkora.ui.file;

import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Diálogos de arquivo (abrir, salvar, escolher pasta) com o visual do tema. Apenas
 * apresentação: ler/gravar é responsabilidade da aplicação.
 */
public final class NekkoraFileDialogs {
    private NekkoraFileDialogs() {}

    /** @param extensions ex.: {@code "json", "xml"} (vazio = todos) */
    public static Optional<Path> openFile(Component parent, String title, String description, String... extensions) {
        JFileChooser fc = chooser(title, JFileChooser.FILES_ONLY, description, extensions);
        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? Optional.of(fc.getSelectedFile().toPath()) : Optional.empty();
    }

    /** Salvar: acrescenta a extensão se o usuário não digitou. */
    public static Optional<Path> saveFile(Component parent, String title, String suggestedName, String extension) {
        JFileChooser fc = chooser(title, JFileChooser.FILES_ONLY, extension == null ? null : extension.toUpperCase(),
                extension == null ? new String[0] : new String[] {extension});
        if (suggestedName != null) fc.setSelectedFile(new File(suggestedName));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        File f = fc.getSelectedFile();
        if (extension != null && !f.getName().toLowerCase().endsWith("." + extension.toLowerCase())) {
            f = new File(f.getParentFile(), f.getName() + "." + extension);
        }
        return Optional.of(f.toPath());
    }

    public static Optional<Path> selectFolder(Component parent, String title) {
        JFileChooser fc = chooser(title, JFileChooser.DIRECTORIES_ONLY, null);
        return fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? Optional.of(fc.getSelectedFile().toPath()) : Optional.empty();
    }

    private static JFileChooser chooser(String title, int mode, String description, String... extensions) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(mode);
        if (extensions != null && extensions.length > 0) {
            fc.setFileFilter(new FileNameExtensionFilter(
                    (description == null ? "Arquivos" : description) + " (*." + String.join(", *.", extensions) + ")", extensions));
        }
        return fc;
    }
}
