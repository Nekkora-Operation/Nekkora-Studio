package com.nekkora.studio.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Diretório local do Bridge (arquivo de descoberta, socket e trava de instância), restrito ao usuário atual.
 *
 * <p>No Windows a ACL é DEFINIDA (não apenas herdada de {@code %LOCALAPPDATA%}): uma única entrada, do dono, herdável
 * por arquivos e subpastas — assim o socket e o arquivo de descoberta nascem com a mesma restrição. Em seguida a ACL
 * EFETIVA é relida e conferida; qualquer outro principal com permissão faz a publicação falhar de forma segura.
 */
public final class BridgeDirectory {

    private BridgeDirectory() {
    }

    /**
     * Mesmo algoritmo do Native Host: propriedade {@code nekkora.bridge.dir}, variável {@code NEKKORA_BRIDGE_DIR},
     * {@code %LOCALAPPDATA%\Nekkora\Studio\bridge} no Windows, senão {@code $XDG_RUNTIME_DIR/nekkora-studio} ou
     * {@code ~/.local/share/nekkora/studio/bridge}.
     */
    public static Path defaultDirectory() {
        String explicit = System.getProperty("nekkora.bridge.dir");
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("NEKKORA_BRIDGE_DIR");
        }
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        if (windows()) {
            String local = System.getenv("LOCALAPPDATA");
            Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(System.getProperty("user.home"), "AppData", "Local");
            return base.resolve("Nekkora").resolve("Studio").resolve("bridge");
        }
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isBlank()) {
            return Path.of(runtime, "nekkora-studio");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "nekkora", "studio", "bridge");
    }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Cria (se preciso), restringe ao usuário e confere a ACL efetiva. */
    public static Path prepare(Path dir) throws BridgeException {
        try {
            Files.createDirectories(dir);
            if (Files.isSymbolicLink(dir)) {
                throw new BridgeException(BridgeError.INSECURE_DIRECTORY, "o diretório do Bridge não pode ser um link simbólico");
            }
            restrict(dir, true);
            verifyPrivate(dir);
            return dir;
        } catch (BridgeException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BridgeException(BridgeError.INSECURE_DIRECTORY, "não foi possível restringir o diretório do Bridge: " + e.getMessage());
        }
    }

    /** Define permissões somente do dono (ACL herdável no Windows; 700/600 em POSIX). */
    static void restrict(Path p, boolean directory) throws IOException {
        AclFileAttributeView acl = Files.getFileAttributeView(p, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            UserPrincipal owner = Files.getOwner(p, LinkOption.NOFOLLOW_LINKS);
            AclEntry.Builder b = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (directory) {
                b.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
            }
            acl.setAcl(List.of(b.build()));
            return;
        }
        PosixFileAttributeView posix = Files.getFileAttributeView(p, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }

    /** Confere a permissão EFETIVA: dono = usuário atual e nenhum outro principal com acesso. */
    public static void verifyPrivate(Path p) throws BridgeException {
        try {
            UserPrincipal owner = Files.getOwner(p, LinkOption.NOFOLLOW_LINKS);
            UserPrincipal me = p.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
            if (!owner.equals(me)) {
                throw new BridgeException(BridgeError.INSECURE_DIRECTORY, p.getFileName() + " não pertence ao usuário atual");
            }
            AclFileAttributeView acl = Files.getFileAttributeView(p, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (acl != null) {
                for (AclEntry e : acl.getAcl()) {
                    if (e.type() == AclEntryType.ALLOW && !e.principal().equals(owner)) {
                        throw new BridgeException(BridgeError.INSECURE_DIRECTORY, p.getFileName() + " acessível a " + e.principal().getName());
                    }
                }
                return;
            }
            PosixFileAttributeView posix = Files.getFileAttributeView(p, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (posix != null) {
                Set<PosixFilePermission> perms = posix.readAttributes().permissions();
                for (PosixFilePermission x : perms) {
                    if (x.name().startsWith("GROUP") || x.name().startsWith("OTHERS")) {
                        throw new BridgeException(BridgeError.INSECURE_DIRECTORY, p.getFileName() + " acessível a grupo/outros");
                    }
                }
            }
        } catch (BridgeException e) {
            throw e;
        } catch (IOException e) {
            throw new BridgeException(BridgeError.INSECURE_DIRECTORY, "ACL ilegível: " + e.getMessage());
        }
    }
}
