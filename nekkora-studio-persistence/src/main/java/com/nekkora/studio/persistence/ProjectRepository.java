package com.nekkora.studio.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;

/**
 * Gravação ATÔMICA e leitura de arquivos de projeto (STUDIO-006, D-S006-06/14, §18). Um projeto confirmado nunca é
 * sobrescrito enquanto o novo conteúdo é serializado:
 * <ol>
 *   <li>o novo conteúdo vai para um arquivo temporário NA MESMA PASTA (mesmo sistema de arquivos);</li>
 *   <li>é sincronizado em disco ({@code force});</li>
 *   <li>é verificado — o chamador relê o temporário com a leitura completa antes de confirmar ({@link Verifier});</li>
 *   <li>o arquivo de destino é conferido contra a última versão conhecida (alteração externa = conflito);</li>
 *   <li>a substituição é atômica ({@code ATOMIC_MOVE}). Sem suporte a ela, usa-se cópia de segurança: o anterior é
 *       renomeado, o novo entra no lugar e, se algo falhar, o anterior volta — com a limitação documentada de que há um
 *       intervalo em que o nome de destino pode não existir;</li>
 *   <li>a pasta é sincronizada quando o sistema permite.</li>
 * </ol>
 * Em qualquer falha o arquivo anterior permanece e o temporário é apagado. Sucesso só é devolvido depois da substituição.
 */
public final class ProjectRepository {

    /** Operações de arquivo (substituíveis nos testes para simular falhas reais: disco cheio, falha na substituição…). */
    public interface FileOps {
        Path createTemp(Path dir, String prefix, String suffix) throws IOException;

        /** Grava tudo e sincroniza o conteúdo em disco. */
        void writeAndSync(Path file, byte[] data) throws IOException;

        void move(Path from, Path to, CopyOption... options) throws IOException;

        void deleteIfExists(Path file) throws IOException;

        byte[] readAll(Path file) throws IOException;

        /** Sincroniza a entrada da pasta (melhor esforço; nem todo sistema permite). */
        void syncDirectory(Path dir);

        static FileOps system() {
            return SystemFileOps.INSTANCE;
        }
    }

    /** Releitura de verificação do temporário (estrutura, domínio e privacidade) antes de confirmar. */
    @FunctionalInterface
    public interface Verifier {
        void verify(Path written) throws ProjectException;
    }

    /** Identidade do conteúdo de um arquivo de projeto (detecção de alteração externa). */
    public record Fingerprint(long size, String sha256) {
        public Fingerprint {
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    /** Gravação confirmada. {@code atomic} = substituição atômica (falso quando a cópia de segurança foi usada). */
    public record Saved(Path path, Fingerprint fingerprint, boolean atomic) {}

    private final FileOps ops;

    public ProjectRepository() {
        this(FileOps.system());
    }

    public ProjectRepository(FileOps ops) {
        this.ops = Objects.requireNonNull(ops, "ops");
    }

    /** Operações de arquivo em uso (a recuperação apaga as fotografias pelas mesmas operações). */
    FileOps ops() {
        return ops;
    }

    /**
     * Grava {@code bytes} em {@code target}.
     *
     * @param expected  conteúdo que o Studio conhece no destino — última leitura/gravação ou, em Salvar como sobre um arquivo
     *                  existente, o conteúdo lido ANTES da confirmação de sobrescrita; conferido de novo imediatamente antes
     *                  da troca (diferente = conflito). {@code null} = o destino não deveria existir, a não ser que
     *                  {@code overwrite}
     * @param overwrite substituir sem conferir o conteúdo do destino — só para arquivos do próprio Studio (fotografias de
     *                  recuperação, de nome único); arquivos do usuário sempre usam {@code expected}
     */
    public Saved save(Path target, byte[] bytes, Fingerprint expected, boolean overwrite, Verifier verifier) throws ProjectException {
        Objects.requireNonNull(target, "target");
        Path file = target.toAbsolutePath().normalize();
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ProjectFormat.SUFFIX)) {
            throw new ProjectException(ProjectException.Error.WRONG_EXTENSION, "");
        }
        if (bytes.length > ProjectFormat.MAX_FILE_BYTES) throw new ProjectException(ProjectException.Error.TOO_LARGE, "");
        Path dir = file.getParent();
        if (dir == null || !Files.isDirectory(dir)) throw new ProjectException(ProjectException.Error.DIRECTORY_MISSING, "");
        if (Files.exists(file) && !Files.isWritable(file)) throw new ProjectException(ProjectException.Error.READ_ONLY, "");
        checkTarget(file, expected, overwrite);
        cleanupStale(file);
        Path temp = null;
        try {
            temp = ops.createTemp(dir, "." + file.getFileName() + ".", ".tmp");
            ops.writeAndSync(temp, bytes);
            Fingerprint written = fingerprint(ops.readAll(temp));
            if (!written.equals(fingerprint(bytes))) throw new ProjectException(ProjectException.Error.WRITE_FAILED, "verificação");
            if (verifier != null) verifier.verify(temp);
            checkTarget(file, expected, overwrite); // de novo, imediatamente antes de substituir
            boolean atomic = replace(temp, file);
            temp = null;
            ops.syncDirectory(dir);
            return new Saved(file, written, atomic);
        } catch (IOException e) {
            throw map(e, ProjectException.Error.WRITE_FAILED);
        } finally {
            if (temp != null) {
                try {
                    ops.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // o temporário é um arquivo oculto ".<nome>.…tmp" na mesma pasta; documentado em atomic-save.md
                }
            }
        }
    }

    /** Idade mínima de um temporário para ser considerado abandonado (encerramento no meio de uma gravação). */
    static final java.time.Duration STALE = java.time.Duration.ofMinutes(10);

    /**
     * Apaga temporários ABANDONADOS deste projeto ({@code .<nome>.….tmp}, só os antigos — nunca o de uma gravação em
     * andamento) e cópias de segurança antigas ({@code .<nome>.….bak}) SOMENTE quando o arquivo principal existe: sem ele,
     * a cópia pode ser a única versão confirmada e fica no disco.
     */
    void cleanupStale(Path file) {
        Path dir = file.getParent();
        String prefix = "." + file.getFileName() + ".";
        java.time.Instant limit = java.time.Instant.now().minus(STALE);
        try (java.util.stream.Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (!n.startsWith(prefix) || !Files.isRegularFile(p)) continue;
                boolean tmp = n.endsWith(".tmp");
                boolean bak = n.endsWith(".bak") && Files.exists(file);
                if ((tmp || bak) && Files.getLastModifiedTime(p).toInstant().isBefore(limit)) ops.deleteIfExists(p);
            }
        } catch (IOException | RuntimeException e) {
            // limpeza é melhor esforço: nunca impede a gravação
        }
    }

    /** Conteúdo atual do arquivo (para leitura e detecção de conflito); {@code null} se não existe. */
    public Fingerprint fingerprintOf(Path file) throws ProjectException {
        try {
            if (!Files.exists(file)) return null;
            if (Files.size(file) > ProjectFormat.MAX_FILE_BYTES) throw new ProjectException(ProjectException.Error.TOO_LARGE, "");
            return fingerprint(ops.readAll(file));
        } catch (IOException e) {
            throw map(e, ProjectException.Error.CORRUPTED);
        }
    }

    public static Fingerprint fingerprint(byte[] data) {
        return new Fingerprint(data.length, ProjectPackage.sha256(data));
    }

    private void checkTarget(Path file, Fingerprint expected, boolean overwrite) throws ProjectException {
        Fingerprint current = fingerprintOf(file);
        if (expected == null) {
            if (current != null && !overwrite) throw new ProjectException(ProjectException.Error.CONFLICT, "destino já existe");
        } else if (!expected.equals(current)) {
            throw new ProjectException(ProjectException.Error.CONFLICT, current == null ? "arquivo removido" : "arquivo alterado");
        }
    }

    /** @return {@code true} se a substituição foi atômica */
    private boolean replace(Path temp, Path file) throws IOException, ProjectException {
        try {
            ops.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            // sem substituição atômica: cópia de segurança do anterior, troca e restauração em caso de falha
            Path backup = null;
            if (Files.exists(file)) {
                backup = ops.createTemp(file.getParent(), "." + file.getFileName() + ".", ".bak");
                ops.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                ops.move(temp, file);
            } catch (IOException failed) {
                if (backup != null) {
                    try {
                        ops.move(backup, file, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException restoreFailed) {
                        // o anterior continua íntegro no arquivo de cópia de segurança (nome informado ao usuário)
                        throw new ProjectException(ProjectException.Error.REPLACE_FAILED, "cópia de segurança: " + backup.getFileName(), failed);
                    }
                }
                throw new ProjectException(ProjectException.Error.REPLACE_FAILED, "", failed);
            }
            if (backup != null) ops.deleteIfExists(backup);
            return false;
        }
    }

    /** Traduz uma falha de E/S em código estável (a mensagem do sistema não vai para a interface nem para logs). */
    static ProjectException map(IOException e, ProjectException.Error fallback) {
        String reason = e instanceof FileSystemException f && f.getReason() != null ? f.getReason() : String.valueOf(e.getMessage());
        String r = reason.toLowerCase(Locale.ROOT);
        ProjectException.Error error;
        if (e instanceof NoSuchFileException) {
            error = ProjectException.Error.NOT_FOUND;
        } else if (r.contains("no space") || r.contains("not enough space") || r.contains("disk full") || r.contains("espaço")) {
            error = ProjectException.Error.DISK_FULL;
        } else if (r.contains("being used by another process") || r.contains("sendo usado por outro processo") || r.contains("locked")) {
            error = ProjectException.Error.IN_USE;
        } else if (r.contains("read-only") || r.contains("somente leitura")) {
            error = ProjectException.Error.READ_ONLY;
        } else if (e instanceof AccessDeniedException) {
            error = ProjectException.Error.ACCESS_DENIED;
        } else {
            error = fallback;
        }
        return new ProjectException(error, "", e);
    }

    /** Implementação real. */
    private enum SystemFileOps implements FileOps {
        INSTANCE;

        @Override
        public Path createTemp(Path dir, String prefix, String suffix) throws IOException {
            return Files.createTempFile(dir, prefix, suffix);
        }

        @Override
        public void writeAndSync(Path file, byte[] data) throws IOException {
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.wrap(data);
                while (buf.hasRemaining()) ch.write(buf);
                ch.force(true);
            }
        }

        @Override
        public void move(Path from, Path to, CopyOption... options) throws IOException {
            Files.move(from, to, options);
        }

        @Override
        public void deleteIfExists(Path file) throws IOException {
            Files.deleteIfExists(file);
        }

        @Override
        public byte[] readAll(Path file) throws IOException {
            return Files.readAllBytes(file);
        }

        @Override
        public void syncDirectory(Path dir) {
            try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
                ch.force(true);
            } catch (IOException | UnsupportedOperationException e) {
                // Windows não permite abrir pasta como canal: a substituição já é durável pelo MoveFileEx
            }
        }
    }
}
