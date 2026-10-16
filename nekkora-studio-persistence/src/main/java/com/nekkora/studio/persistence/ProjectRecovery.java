package com.nekkora.studio.persistence;

import com.nekkora.studio.domain.AutomationId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fotografias de RECUPERAÇÃO (STUDIO-006, §23/§24, D-S006-14): cópias temporárias de alterações ainda não salvas, num
 * diretório próprio do Studio (separado da Chrome Extension), no MESMO formato validado do projeto ({@code kind =
 * RECOVERY}). Nunca sobrescrevem o projeto original e não são um salvamento.
 *
 * <p>Retenção documentada: até {@value #MAX_VERSIONS} fotografias por automação e {@link #RETENTION} de idade. Uma
 * fotografia só é apagada por um salvamento confirmado que a supera, por descarte autorizado pelo usuário, ou por
 * vencer a retenção. Uma fotografia ANTERIOR ao estado confirmado do seu arquivo de origem (salvamento posterior a ela,
 * mesmo que a limpeza daquele salvamento não tenha terminado) nunca é oferecida como recuperação pendente.
 *
 * <p>As exclusões passam pelas {@link ProjectRepository.FileOps} do repositório e informam o resultado: o Studio nunca
 * afirma uma limpeza que não aconteceu.
 *
 * <p>Arquivos: {@code <dir>/<automationId>/<instante>-g<geração>.nekkora}.
 */
public final class ProjectRecovery {
    public static final int MAX_VERSIONS = 3;
    public static final Duration RETENTION = Duration.ofDays(7);
    private static final Pattern FILE = Pattern.compile("(\\d{1,19})-g(\\d{1,19})\\.nekkora");

    /** Fotografia válida encontrada (já lida e reconstruída com todas as verificações). */
    public record Candidate(Path file, Instant savedAt, long generation, ProjectFiles.Loaded loaded) {}

    private final Path dir;
    private final ProjectFiles files;

    public ProjectRecovery(Path dir, ProjectFiles files) {
        this.dir = Objects.requireNonNull(dir, "dir").toAbsolutePath().normalize();
        this.files = Objects.requireNonNull(files, "files");
    }

    /** Onde as fotografias ficam (informado ao usuário). */
    public Path directory() {
        return dir;
    }

    /** Grava uma fotografia (gravação atômica e verificada) e mantém só as {@value #MAX_VERSIONS} mais recentes. */
    public Path write(ProjectFiles.Snapshot s, long generation, ProjectPackage.RecoveryOrigin origin, Instant savedAt, Clock clock)
            throws ProjectException {
        Path folder = folder(s.automationId());
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw ProjectRepository.map(e, ProjectException.Error.WRITE_FAILED);
        }
        Path target = folder.resolve(savedAt.toEpochMilli() + "-g" + generation + ProjectFormat.SUFFIX);
        byte[] bytes = files.serialize(s, ProjectFormat.Kind.RECOVERY, origin, savedAt);
        files.repository().save(target, bytes, null, true, written -> {
            ProjectFiles.Loaded back = files.loadRecoveryUnchecked(written, clock);
            if (back.manifest().kind() != ProjectFormat.Kind.RECOVERY || !back.automation().id().equals(s.automationId())) {
                throw new ProjectException(ProjectException.Error.WRITE_FAILED, "verificação da recuperação");
            }
        });
        prune(folder);
        return target;
    }

    /**
     * Fotografias disponíveis: para cada automação, a mais recente que passa por TODAS as verificações. Arquivos vencidos
     * são apagados; arquivos inválidos ficam (não são oferecidos) até vencerem.
     */
    public List<Candidate> scan(Clock clock) {
        List<Candidate> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        Instant limit = clock.instant().minus(RETENTION);
        try (Stream<Path> folders = Files.list(dir)) {
            for (Path folder : (Iterable<Path>) folders::iterator) {
                AutomationId id = automation(folder);
                if (id == null) continue;
                for (Path f : versions(folder)) {
                    try {
                        if (Files.getLastModifiedTime(f).toInstant().isBefore(limit)) {
                            delete(f);
                            continue;
                        }
                    } catch (IOException e) {
                        continue;
                    }
                }
                List<Path> all = versions(folder);
                for (int i = 0; i < all.size(); i++) {
                    Path f = all.get(i);
                    Matcher m = FILE.matcher(f.getFileName().toString());
                    if (!m.matches()) continue;
                    Candidate c;
                    try {
                        ProjectFiles.Loaded l = files.load(f, clock);
                        if (l.manifest().kind() != ProjectFormat.Kind.RECOVERY || !l.automation().id().equals(id)) continue;
                        c = new Candidate(f, l.manifest().savedAt(), Long.parseLong(m.group(2)), l);
                    } catch (ProjectException | RuntimeException e) {
                        continue; // inválida: não é oferecida; fica até vencer a retenção
                    }
                    if (superseded(c, clock)) {
                        // esta e as anteriores a ela são mais antigas que o estado confirmado: apagadas, nunca oferecidas
                        for (Path older : all.subList(i, all.size())) delete(older);
                    } else {
                        out.add(c);
                    }
                    break; // só a mais recente válida importa
                }
                deleteIfEmpty(folder);
            }
        } catch (IOException e) {
            return out;
        }
        out.sort(Comparator.comparing(Candidate::savedAt));
        return out;
    }

    /**
     * A fotografia é ANTERIOR ao estado confirmado do seu arquivo de origem: o arquivo existe, é um projeto válido da
     * MESMA automação, não é o conteúdo-base da fotografia e foi salvo estritamente depois dela. Na dúvida (origem
     * desconhecida, ilegível, de outra automação ou com o mesmo instante), nada comprova a superação e a fotografia
     * continua oferecida: esconder alterações mais novas seria pior do que oferecer uma antiga.
     */
    private boolean superseded(Candidate c, Clock clock) {
        ProjectPackage.RecoveryOrigin o = c.loaded().manifest().recoveryOf();
        if (o == null || o.projectPath() == null) return false;
        try {
            Path project = Path.of(o.projectPath());
            if (!Files.isRegularFile(project)) return false;
            ProjectFiles.Loaded p = files.load(project, clock);
            return p.manifest().kind() == ProjectFormat.Kind.PROJECT && p.automation().id().equals(c.loaded().automation().id())
                    && !p.fingerprint().equals(o.base()) && p.manifest().savedAt().isAfter(c.savedAt());
        } catch (ProjectException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Descarta as fotografias da automação até a geração informada (salvamento confirmado que as supera).
     *
     * @return {@code true} só se todas as fotografias visadas deixaram de existir
     */
    public boolean discard(AutomationId id, long upToGeneration) {
        Path folder = folder(id);
        if (!Files.exists(folder)) return true;
        List<Path> found;
        try (Stream<Path> s = Files.list(folder)) {
            found = s.toList();
        } catch (IOException e) {
            return false;
        }
        boolean ok = true;
        for (Path f : found) {
            Matcher m = FILE.matcher(f.getFileName().toString());
            if (m.matches() && Long.parseLong(m.group(2)) <= upToGeneration) ok &= delete(f);
        }
        deleteIfEmpty(folder);
        return ok;
    }

    /** Descarte autorizado de todas as fotografias de uma automação; {@code true} só se nenhuma restou. */
    public boolean discardAll(AutomationId id) {
        return discard(id, Long.MAX_VALUE);
    }

    /** Limpa TODAS as fotografias (pedido explícito do usuário); {@code true} só se nenhuma restou. */
    public boolean clear() {
        if (!Files.isDirectory(dir)) return true;
        boolean ok = true;
        try (Stream<Path> folders = Files.list(dir)) {
            for (Path folder : (Iterable<Path>) folders::iterator) {
                AutomationId id = automation(folder);
                if (id != null) ok &= discardAll(id);
            }
        } catch (IOException e) {
            return false;
        }
        return ok;
    }

    /** Quantidade de fotografias guardadas. */
    public int count() {
        int n = 0;
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> folders = Files.list(dir)) {
            for (Path folder : (Iterable<Path>) folders::iterator) {
                if (automation(folder) != null) n += versions(folder).size();
            }
        } catch (IOException e) {
            return n;
        }
        return n;
    }

    private Path folder(AutomationId id) {
        return dir.resolve(id.value().toString());
    }

    private static AutomationId automation(Path folder) {
        if (!Files.isDirectory(folder)) return null;
        try {
            UUID u = UUID.fromString(folder.getFileName().toString());
            return u.toString().equals(folder.getFileName().toString()) ? new AutomationId(u) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Fotografias da pasta, da mais recente para a mais antiga (pelo instante do nome). */
    private static List<Path> versions(Path folder) {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> FILE.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparingLong((Path p) -> {
                        Matcher m = FILE.matcher(p.getFileName().toString());
                        return m.matches() ? Long.parseLong(m.group(1)) : 0;
                    }).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private void prune(Path folder) {
        List<Path> all = versions(folder);
        for (int i = MAX_VERSIONS; i < all.size(); i++) delete(all.get(i));
    }

    /** @return {@code true} se o arquivo não existe mais (o que falhar fica para a próxima limpeza ou para a retenção) */
    private boolean delete(Path f) {
        try {
            files.repository().ops().deleteIfExists(f);
            return !Files.exists(f);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteIfEmpty(Path folder) {
        try (Stream<Path> s = Files.list(folder)) {
            if (s.findAny().isEmpty()) Files.deleteIfExists(folder);
        } catch (IOException e) {
            // pasta em uso ou já removida
        }
    }
}
