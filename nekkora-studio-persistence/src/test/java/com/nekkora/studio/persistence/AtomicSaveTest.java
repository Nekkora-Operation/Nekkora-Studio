package com.nekkora.studio.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-006 §18/§33: um projeto confirmado nunca é destruído por uma falha de gravação. */
class AtomicSaveTest {

    @TempDir
    Path dir;

    private final RichAutomation.TickClock clock = new RichAutomation.TickClock("2026-10-16T12:00:00Z");
    private final String pid = UUID.randomUUID().toString();

    /** Operações reais com falhas injetáveis (o que o sistema operacional faria). */
    static final class Faulty implements ProjectRepository.FileOps {
        final ProjectRepository.FileOps real = ProjectRepository.FileOps.system();
        IOException onWrite;
        boolean corruptWrite;
        IOException onMove;
        boolean noAtomic;
        int failMoveNumber = -1;
        int moves;

        @Override
        public Path createTemp(Path d, String prefix, String suffix) throws IOException {
            return real.createTemp(d, prefix, suffix);
        }

        @Override
        public void writeAndSync(Path file, byte[] data) throws IOException {
            if (onWrite != null) {
                real.writeAndSync(file, Arrays.copyOf(data, data.length / 3)); // gravou uma parte antes da falha
                throw onWrite;
            }
            real.writeAndSync(file, corruptWrite ? Arrays.copyOf(data, data.length - 10) : data);
        }

        @Override
        public void move(Path from, Path to, CopyOption... options) throws IOException {
            moves++;
            if (noAtomic && Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) throw new AtomicMoveNotSupportedException(from.toString(),
                    to.toString(), "sem suporte");
            if (moves == failMoveNumber || (onMove != null && failMoveNumber < 0)) throw onMove != null ? onMove : new IOException("falha simulada");
            real.move(from, to, options);
        }

        @Override
        public void deleteIfExists(Path file) throws IOException {
            real.deleteIfExists(file);
        }

        @Override
        public byte[] readAll(Path file) throws IOException {
            return real.readAll(file);
        }

        @Override
        public void syncDirectory(Path d) {
            real.syncDirectory(d);
        }
    }

    private ProjectFiles files(ProjectRepository.FileOps ops) {
        return new ProjectFiles(new ProjectRepository(ops), "teste");
    }

    private ProjectRepository.Saved firstSave(Path file) throws Exception {
        return files(ProjectRepository.FileOps.system()).save(file, ProjectFiles.Snapshot.of(RichAutomation.build(clock), pid), null, false,
                Instant.now(), clock);
    }

    private ProjectFiles.Snapshot edited() {
        Automation a = RichAutomation.build(clock);
        a.updateDetails("Consulta de Clientes v2", "outra versão");
        return ProjectFiles.Snapshot.of(a, pid);
    }

    private List<String> leftovers() throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".tmp") || n.endsWith(".bak")).toList();
        }
    }

    @Test
    @DisplayName("S006-038/044 — salvar: substituição atômica, conteúdo novo confirmado, nenhum temporário restante")
    void s006038e044Salvar() throws Exception {
        Path file = dir.resolve("p.nekkora");
        ProjectRepository.Saved first = firstSave(file);
        assertTrue(first.atomic());
        ProjectFiles f = files(ProjectRepository.FileOps.system());
        ProjectRepository.Saved second = f.save(file, edited(), first.fingerprint(), false, Instant.now(), clock);
        assertTrue(second.atomic());
        assertEquals("Consulta de Clientes v2", f.load(file, clock).automation().name());
        assertEquals(List.of(), leftovers());
    }

    @Test
    @DisplayName("S006-039/040 — salvar como: destino novo; destino existente só com sobrescrita confirmada")
    void s006039e040SalvarComo() throws Exception {
        Path file = dir.resolve("p.nekkora");
        firstSave(file);
        Path other = dir.resolve("copia.nekkora");
        Files.writeString(other, "arquivo de outra pessoa");
        ProjectFiles f = files(ProjectRepository.FileOps.system());
        ProjectException e = assertThrows(ProjectException.class, () -> f.save(other, edited(), null, false, Instant.now(), clock));
        assertEquals(ProjectException.Error.CONFLICT, e.error(), "sem confirmação não sobrescreve");
        assertEquals("arquivo de outra pessoa", Files.readString(other));
        f.save(other, edited(), null, true, Instant.now(), clock);
        assertEquals(pid, f.load(other, clock).manifest().projectId(), "mesma identidade do projeto");
        assertEquals(ProjectException.Error.WRONG_EXTENSION,
                assertThrows(ProjectException.class, () -> f.save(dir.resolve("p.zip"), edited(), null, false, Instant.now(), clock)).error());
    }

    @Test
    @DisplayName("S006-041 — arquivo alterado (ou removido) fora do Studio: conflito, nada sobrescrito")
    void s006041AlteradoExternamente() throws Exception {
        Path file = dir.resolve("p.nekkora");
        ProjectRepository.Saved first = firstSave(file);
        Files.write(file, Files.readAllBytes(file), java.nio.file.StandardOpenOption.APPEND); // outro programa mexeu
        byte[] external = Files.readAllBytes(file);
        ProjectFiles f = files(ProjectRepository.FileOps.system());
        assertEquals(ProjectException.Error.CONFLICT,
                assertThrows(ProjectException.class, () -> f.save(file, edited(), first.fingerprint(), false, Instant.now(), clock)).error());
        assertArrayEquals(external, Files.readAllBytes(file));
        Files.delete(file);
        assertEquals(ProjectException.Error.CONFLICT,
                assertThrows(ProjectException.class, () -> f.save(file, edited(), first.fingerprint(), false, Instant.now(), clock)).error());
    }

    @Test
    @DisplayName("S006-042/043 — falha de escrita e disco cheio: anterior intacto, temporário apagado, erro estável")
    void s006042e043FalhaDeEscrita() throws Exception {
        Path file = dir.resolve("p.nekkora");
        ProjectRepository.Saved first = firstSave(file);
        byte[] before = Files.readAllBytes(file);
        Faulty full = new Faulty();
        full.onWrite = new FileSystemException("p.nekkora", null, "There is not enough space on the disk");
        assertEquals(ProjectException.Error.DISK_FULL,
                assertThrows(ProjectException.class, () -> files(full).save(file, edited(), first.fingerprint(), false, Instant.now(), clock)).error());
        Faulty io = new Faulty();
        io.onWrite = new IOException("falha de E/S");
        assertEquals(ProjectException.Error.WRITE_FAILED,
                assertThrows(ProjectException.class, () -> files(io).save(file, edited(), first.fingerprint(), false, Instant.now(), clock)).error());
        Faulty corrupt = new Faulty();
        corrupt.corruptWrite = true;
        assertEquals(ProjectException.Error.WRITE_FAILED,
                assertThrows(ProjectException.class, () -> files(corrupt).save(file, edited(), first.fingerprint(), false, Instant.now(), clock)).error(),
                "gravado diferente do pedido: não confirma");
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(List.of(), leftovers());
    }

    @Test
    @DisplayName("S006-044 — falha na própria substituição (arquivo em uso): anterior intacto")
    void s006044FalhaNaSubstituicao() throws Exception {
        Path file = dir.resolve("p.nekkora");
        ProjectRepository.Saved first = firstSave(file);
        byte[] before = Files.readAllBytes(file);
        Faulty inUse = new Faulty();
        inUse.onMove = new FileSystemException("p.nekkora", null, "The process cannot access the file because it is being used by another process");
        assertEquals(ProjectException.Error.IN_USE,
                assertThrows(ProjectException.class, () -> files(inUse).save(file, edited(), first.fingerprint(), false, Instant.now(), clock)).error());
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(List.of(), leftovers());
    }

    @Test
    @DisplayName("S006-045 — sem substituição atômica: cópia de segurança; se a troca falha, o anterior VOLTA")
    void s006045SemAtomicidade() throws Exception {
        Path file = dir.resolve("p.nekkora");
        ProjectRepository.Saved first = firstSave(file);
        byte[] before = Files.readAllBytes(file);
        Faulty failing = new Faulty();
        failing.noAtomic = true;
        failing.failMoveNumber = 3; // 1ª: atômica (recusada); 2ª: anterior → cópia; 3ª: novo → destino (falha)
        failing.onMove = null;
        ProjectException e = assertThrows(ProjectException.class,
                () -> files(failing).save(file, edited(), first.fingerprint(), false, Instant.now(), clock));
        assertEquals(ProjectException.Error.REPLACE_FAILED, e.error());
        assertArrayEquals(before, Files.readAllBytes(file), "anterior restaurado da cópia de segurança");
        assertEquals(List.of(), leftovers());
        Faulty ok = new Faulty();
        ok.noAtomic = true;
        ProjectRepository.Saved saved = files(ok).save(file, edited(), first.fingerprint(), false, Instant.now(), clock);
        assertFalse(saved.atomic(), "substituição por cópia de segurança declarada");
        assertEquals("Consulta de Clientes v2", files(ProjectRepository.FileOps.system()).load(file, clock).automation().name());
        assertEquals(List.of(), leftovers());
    }

    @Test
    @DisplayName("§33 — pasta inexistente, arquivo somente leitura")
    void pastaEArquivo() throws Exception {
        ProjectFiles f = files(ProjectRepository.FileOps.system());
        assertEquals(ProjectException.Error.DIRECTORY_MISSING,
                assertThrows(ProjectException.class, () -> f.save(dir.resolve("nao/existe/p.nekkora"), edited(), null, false, Instant.now(), clock)).error());
        Path file = dir.resolve("ro.nekkora");
        ProjectRepository.Saved first = firstSave(file);
        assertTrue(file.toFile().setWritable(false));
        try {
            // Superusuário (ex.: o contêiner Linux de referência roda como root) ignora a permissão: o próprio sistema
            // considera o arquivo gravável e o Studio segue o sistema. A recusa só pode ser exercitada sem esse privilégio
            // (Windows e usuário comum; a homologação real cobre o somente leitura do Windows).
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(file),
                    "arquivo somente leitura continua gravável para este usuário (superusuário): parte não aplicável");
            assertEquals(ProjectException.Error.READ_ONLY,
                    assertThrows(ProjectException.class, () -> f.save(file, edited(), first.fingerprint(), false, Instant.now(), clock)).error());
        } finally {
            file.toFile().setWritable(true);
        }
    }

    @Test
    @DisplayName("§33 interrupção — temporário abandonado por um encerramento no meio da gravação é limpo; anterior intacto")
    void temporarioAbandonado() throws Exception {
        Path file = dir.resolve("p.nekkora");
        ProjectRepository.Saved first = firstSave(file);
        byte[] before = Files.readAllBytes(file);
        Path stale = dir.resolve(".p.nekkora.123.tmp");
        Files.write(stale, Arrays.copyOf(before, 50)); // o processo morreu depois de escrever parte do temporário
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(ProjectRepository.STALE).minusSeconds(60)));
        Path recent = dir.resolve(".p.nekkora.456.tmp");
        Files.write(recent, new byte[] {1}); // de uma gravação possivelmente em andamento: preservado
        assertArrayEquals(before, Files.readAllBytes(file), "o confirmado não foi afetado pelo encerramento");
        files(ProjectRepository.FileOps.system()).save(file, edited(), first.fingerprint(), false, Instant.now(), clock);
        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(recent));
    }
}
