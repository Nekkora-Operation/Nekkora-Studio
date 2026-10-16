package com.nekkora.studio.persistence;

import com.nekkora.studio.domain.ModelVersion;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import java.util.regex.Pattern;

/**
 * Contrato do arquivo de projeto {@code .nekkora} (STUDIO-006). Três versões INDEPENDENTES (D-S006-02, §27):
 * <ul>
 *   <li>{@link #FORMAT} / {@link #FORMAT_VERSION} — estrutura do pacote (entradas, manifesto, documentos);</li>
 *   <li>{@link ModelVersion#SCHEMA} / {@link ModelVersion#CURRENT} — contrato do modelo de automação;</li>
 *   <li>{@link #PRIVACY_POLICY} — garantias sobre as informações CAPTURADAS.</li>
 * </ul>
 * Mudar uma classe Java não muda o formato: os documentos são escritos e lidos por código próprio
 * ({@link ProjectCodec}), nunca por serialização automática dos objetos do domínio.
 *
 * <p>Os limites abaixo são os do LEITOR: o arquivo declara os limites com que foi escrito, mas o leitor nunca confia
 * nisso.
 */
public final class ProjectFormat {
    public static final String FORMAT = "nekkora-studio/project";
    /** Versão do formato do pacote (SemVer). Esta versão do Studio lê e escreve exatamente 1.0.x. */
    public static final String FORMAT_VERSION = "1.0.0";
    public static final String PRIVACY_POLICY = CapturePrivacy.VERSION;
    public static final String EXTENSION = "nekkora";
    public static final String SUFFIX = "." + EXTENSION;

    public static final String MANIFEST = "manifest.json";
    public static final String METADATA = "metadata.json";
    public static final String AUTOMATION = "automation.json";
    public static final String SESSIONS = "sessions.json";
    public static final String STEPS_DIR = "steps/";
    public static final String EVIDENCE_DIR = "evidence/";
    public static final String UNATTRIBUTED = EVIDENCE_DIR + "unattributed.json";

    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    /** Única forma de nome de entrada aceita: impede caminhos absolutos, {@code ..}, profundidade e nomes arbitrários. */
    static final Pattern ENTRY_NAME = Pattern.compile("(manifest|metadata|automation|sessions)\\.json|steps/" + UUID + "\\.json|evidence/(" + UUID
            + "|unattributed)\\.json");
    static final Pattern STEP_ENTRY = Pattern.compile("steps/(" + UUID + ")\\.json");
    static final Pattern EVIDENCE_ENTRY = Pattern.compile("evidence/(" + UUID + ")\\.json");

    /** Tamanho máximo do arquivo {@code .nekkora} (compactado). */
    public static final long MAX_FILE_BYTES = 128L * 1024 * 1024;
    /** Tamanho máximo de UMA entrada depois de descompactada (lido de fato, não o declarado). */
    public static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    /** Soma máxima das entradas descompactadas. */
    public static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    /** Etapas por projeto aceitas pelo leitor. */
    public static final int MAX_STEPS = 20_000;
    /** Sessões por projeto aceitas pelo leitor. */
    public static final int MAX_SESSIONS = 2_000;
    /** Entradas no pacote: 4 documentos fixos + uma por etapa + evidências por etapa + não atribuídas. */
    public static final int MAX_ENTRIES = 5 + 2 * MAX_STEPS;

    /** Tipo do pacote: projeto confirmado pelo usuário ou fotografia de recuperação (D-S006-14). */
    public enum Kind { PROJECT, RECOVERY }

    private ProjectFormat() {}

    /** A versão do formato é lida por esta versão do Studio (mesmo MAJOR e MINOR; o PATCH não muda a estrutura). */
    static Compatibility compatibility(String version) {
        if (version == null || !version.matches("\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}")) return Compatibility.INVALID;
        String[] p = version.split("\\.");
        String[] c = FORMAT_VERSION.split("\\.");
        int major = Integer.parseInt(p[0]);
        int minor = Integer.parseInt(p[1]);
        int cMajor = Integer.parseInt(c[0]);
        int cMinor = Integer.parseInt(c[1]);
        if (major > cMajor || (major == cMajor && minor > cMinor)) return Compatibility.NEWER;
        if (major < cMajor || minor < cMinor) return Compatibility.OLDER;
        return Compatibility.SUPPORTED;
    }

    enum Compatibility { SUPPORTED, NEWER, OLDER, INVALID }
}
