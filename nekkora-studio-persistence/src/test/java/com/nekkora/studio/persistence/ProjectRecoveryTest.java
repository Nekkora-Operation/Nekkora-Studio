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
