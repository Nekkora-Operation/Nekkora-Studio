package com.nekkora.studio.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.domain.Automation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** STUDIO-006 §23: fotografias de recuperação — formato validado, retenção documentada, nunca o projeto original. */
class ProjectRecoveryTest {

    @TempDir
    Path dir;

    private final RichAutomation.TickClock clock = new RichAutomation.TickClock("2026-10-16T12:00:00Z");
    private final ProjectFiles files = new ProjectFiles(new ProjectRepository(), "teste");

    private List<Path> stored(ProjectRecovery r, Automation a) throws Exception {
        Path folder = r.directory().resolve(a.id().value().toString());
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> s = Files.list(folder)) {
            return s.sorted().toList();
        }
    }

    @Test
    @DisplayName("até 3 fotografias por automação; a mais recente válida é oferecida, com a origem e o conteúdo base")
    void retencaoPorVersoes() throws Exception {
        ProjectRecovery r = new ProjectRecovery(dir.resolve("recovery"), files);
        Automation a = RichAutomation.build(clock);
        String pid = UUID.randomUUID().toString();
        ProjectRepository.Fingerprint base = new ProjectRepository.Fingerprint(10, "ab".repeat(32));
        for (int g = 1; g <= 5; g++) {
            a.addComment(RichAutomation.id(a, "Abrir Clientes"), "versão " + g);
            r.write(ProjectFiles.Snapshot.of(a, pid), g, new ProjectPackage.RecoveryOrigin("C:/projetos/consulta.nekkora", base),
                    Instant.parse("2026-10-16T12:00:00Z").plusSeconds(g), clock);
        }
        assertEquals(3, stored(r, a).size());
        List<ProjectRecovery.Candidate> c = r.scan(new RichAutomation.TickClock("2026-10-16T12:10:00Z"));
        assertEquals(1, c.size());
        assertEquals(5, c.get(0).generation());
        assertEquals(ProjectFormat.Kind.RECOVERY, c.get(0).loaded().manifest().kind());
        assertEquals(base, c.get(0).loaded().manifest().recoveryOf().base());
        assertTrue(c.get(0).loaded().automation().step(RichAutomation.id(a, "Abrir Clientes")).orElseThrow().comments().toString().contains("versão 5"));
        r.discard(a.id(), 4);
        assertEquals(1, stored(r, a).size(), "salvamento confirmado da geração 4 supera as anteriores");
        r.discardAll(a.id());
        assertFalse(Files.exists(r.directory().resolve(a.id().value().toString())));
    }

    @Test
    @DisplayName("fotografia corrompida não é oferecida (a anterior válida é); vencida pela retenção é apagada")
    void invalidaEVencida() throws Exception {
        ProjectRecovery r = new ProjectRecovery(dir.resolve("recovery"), files);
        Automation a = RichAutomation.build(clock);
        String pid = UUID.randomUUID().toString();
        r.write(ProjectFiles.Snapshot.of(a, pid), 1, new ProjectPackage.RecoveryOrigin(null, null), Instant.parse("2026-10-16T12:00:01Z"), clock);
        Path newest = r.write(ProjectFiles.Snapshot.of(a, pid), 2, new ProjectPackage.RecoveryOrigin(null, null), Instant.parse("2026-10-16T12:00:02Z"), clock);
        Files.write(newest, new byte[] {'P', 'K', 3, 4, 0, 0}); // corrompida (ex.: disco cheio durante a escrita por outro processo)
        List<ProjectRecovery.Candidate> c = r.scan(new RichAutomation.TickClock("2026-10-16T12:10:00Z"));
        assertEquals(1, c.size());
        assertEquals(1, c.get(0).generation(), "a válida anterior");
        assertTrue(Files.exists(newest), "inválida não é apagada antes da retenção");
        for (Path p : stored(r, a)) Files.setLastModifiedTime(p, FileTime.from(Instant.now().minus(ProjectRecovery.RETENTION).minusSeconds(3600)));
        assertTrue(r.scan(new RichAutomation.TickClock(Instant.now().toString())).isEmpty());
        assertEquals(0, r.count(), "vencidas apagadas");
    }

    @Test
    @DisplayName("fotografia superada por um salvamento confirmado POSTERIOR não é oferecida (e é apagada); a mais nova que o salvamento é")
    void superadaPorSalvamentoConfirmado() throws Exception {
        ProjectRecovery r = new ProjectRecovery(dir.resolve("recovery"), files);
        Automation a = RichAutomation.build(clock);
        String pid = UUID.randomUUID().toString();
        Path project = dir.resolve("consulta.nekkora");
        ProjectRepository.Saved first = files.save(project, ProjectFiles.Snapshot.of(a, pid), null, false, Instant.parse("2026-10-16T12:00:00Z"), clock);
        a.addComment(RichAutomation.id(a, "Abrir Clientes"), "ainda não salvo");
        ProjectPackage.RecoveryOrigin origin = new ProjectPackage.RecoveryOrigin(project.toString(), first.fingerprint());
        r.write(ProjectFiles.Snapshot.of(a, pid), 1, origin, Instant.parse("2026-10-16T12:00:05Z"), clock);
        RichAutomation.TickClock later = new RichAutomation.TickClock("2026-10-16T12:10:00Z");
        assertEquals(1, r.scan(later).size(), "mais nova que o projeto confirmado: oferecida");
        // salvamento confirmado DEPOIS da fotografia (em outra sessão, ou a limpeza não chegou a terminar)
        files.save(project, ProjectFiles.Snapshot.of(a, pid), first.fingerprint(), false, Instant.parse("2026-10-16T12:00:10Z"), clock);
        assertTrue(r.scan(later).isEmpty(), "superada pelo estado confirmado: não é oferecida");
        assertEquals(0, r.count(), "e é apagada");
        // outro arquivo no mesmo caminho (outra automação) não supera nada
        Automation b = RichAutomation.build(clock);
        r.write(ProjectFiles.Snapshot.of(a, pid), 2, origin, Instant.parse("2026-10-16T12:00:20Z"), clock);
        files.save(project, ProjectFiles.Snapshot.of(b, UUID.randomUUID().toString()), null, true, Instant.parse("2026-10-16T12:00:30Z"), clock);
        assertEquals(1, r.scan(later).size(), "o arquivo agora é de outra automação: a fotografia continua oferecida");
    }

    @Test
    @DisplayName("descarte informa quando uma fotografia não pôde ser apagada (nunca afirma uma limpeza que não aconteceu)")
    void descarteInformaFalha() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean fail = new java.util.concurrent.atomic.AtomicBoolean();
        ProjectRepository.FileOps real = ProjectRepository.FileOps.system();
        ProjectRepository.FileOps ops = new ProjectRepository.FileOps() {
            public Path createTemp(Path d, String prefix, String suffix) throws java.io.IOException {
                return real.createTemp(d, prefix, suffix);
            }

            public void writeAndSync(Path file, byte[] data) throws java.io.IOException {
                real.writeAndSync(file, data);
            }

            public void move(Path from, Path to, java.nio.file.CopyOption... options) throws java.io.IOException {
                real.move(from, to, options);
            }

            public void deleteIfExists(Path file) throws java.io.IOException {
                if (fail.get()) throw new java.nio.file.FileSystemException(file.toString(), null, "being used by another process");
                real.deleteIfExists(file);
            }

            public byte[] readAll(Path file) throws java.io.IOException {
                return real.readAll(file);
            }

            public void syncDirectory(Path d) {
                real.syncDirectory(d);
            }
        };
        ProjectRecovery r = new ProjectRecovery(dir.resolve("recovery"), new ProjectFiles(new ProjectRepository(ops), "teste"));
        Automation a = RichAutomation.build(clock);
        String pid = UUID.randomUUID().toString();
        r.write(ProjectFiles.Snapshot.of(a, pid), 1, new ProjectPackage.RecoveryOrigin(null, null), Instant.parse("2026-10-16T12:00:01Z"), clock);
        fail.set(true);
        assertFalse(r.discardAll(a.id()), "falhou: não afirma que limpou");
        assertEquals(1, r.count());
        fail.set(false);
        assertTrue(r.discardAll(a.id()));
        assertEquals(0, r.count());
        assertTrue(r.discardAll(a.id()), "nada a apagar também é sucesso");
    }

    @Test
    @DisplayName("um arquivo de PROJETO colocado na pasta de recuperação não é oferecido como recuperação")
    void projetoNaoERecuperacao() throws Exception {
        ProjectRecovery r = new ProjectRecovery(dir.resolve("recovery"), files);
        Automation a = RichAutomation.build(clock);
        Path folder = r.directory().resolve(a.id().value().toString());
        Files.createDirectories(folder);
        files.save(folder.resolve("1760616000000-g1.nekkora"), ProjectFiles.Snapshot.of(a, UUID.randomUUID().toString()), null, false, Instant.now(), clock);
        assertTrue(r.scan(clock).isEmpty());
    }
}
