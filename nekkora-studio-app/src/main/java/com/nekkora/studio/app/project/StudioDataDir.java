package com.nekkora.studio.app.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Diretório de dados LOCAIS do Studio (STUDIO-006): configurações, projetos recentes e fotografias de recuperação.
 * Próprio do Studio, separado da Chrome Extension e do canal Bridge. Ordem: propriedade {@code nekkora.studio.dataDir},
 * variável {@code NEKKORA_STUDIO_DATA_DIR} (testes e homologação isolados da instalação pessoal), {@code
 * %LOCALAPPDATA%\Nekkora\Studio\data} no Windows, {@code $XDG_DATA_HOME/nekkora-studio} ou {@code
 * ~/.local/share/nekkora-studio}.
 */
public final class StudioDataDir {
    private StudioDataDir() {}

    public static Path resolve() {
        String explicit = System.getProperty("nekkora.studio.dataDir");
        if (explicit == null || explicit.isBlank()) explicit = System.getenv("NEKKORA_STUDIO_DATA_DIR");
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit).toAbsolutePath().normalize();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(System.getProperty("user.home"), "AppData", "Local");
            return base.resolve("Nekkora").resolve("Studio").resolve("data");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) return Path.of(xdg, "nekkora-studio");
        return Path.of(System.getProperty("user.home"), ".local", "share", "nekkora-studio");
    }

    /** Grava um arquivo pequeno de configuração de forma atômica (temporário + substituição). */
    static void writeAtomically(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), "." + file.getFileName() + ".", ".tmp");
        try {
            Files.write(tmp, data);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
