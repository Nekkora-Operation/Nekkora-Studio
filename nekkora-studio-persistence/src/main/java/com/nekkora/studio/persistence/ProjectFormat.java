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
 * <p><b>STUDIO-008</b>: o formato 1.1.0 acrescenta UMA entrada, {@link #VARIABLES} (catálogo de variáveis, modelo
 * 1.1.0). O escritor grava a MENOR versão capaz de representar o conteúdo ({@link #versionFor}): uma automação sem
 * variáveis continua sendo escrita em 1.0.0, byte a byte como antes. O leitor aceita 1.0.x (sem {@code variables.json},
 * modelo 1.0.0) e 1.1.x (com {@code variables.json}, modelo 1.1.0); qualquer versão mais nova é recusada.
 *
 * <p>Os limites abaixo são os do LEITOR: o arquivo declara os limites com que foi escrito, mas o leitor nunca confia
 * nisso.
 */
public final class ProjectFormat {
    public static final String FORMAT = "nekkora-studio/project";
    /** Versão base do formato do pacote (SemVer) — a de um projeto sem variáveis. */
    public static final String FORMAT_VERSION = "1.0.0";
    /** Formato com catálogo de variáveis (STUDIO-008). */
    public static final String FORMAT_VERSION_1_1 = "1.1.0";
    /** Formato com decisões sobre o alvo e alvo substituto (STUDIO-009); catálogo de variáveis opcional. */
    public static final String FORMAT_VERSION_1_2 = "1.2.0";
    /** Versão mais nova que esta versão do Studio lê e escreve. */
    public static final String LATEST_FORMAT_VERSION = FORMAT_VERSION_1_2;
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
    /** Catálogo de variáveis (só no formato 1.1.x). */
    public static final String VARIABLES = "variables.json";

    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    /** Única forma de nome de entrada aceita: impede caminhos absolutos, {@code ..}, profundidade e nomes arbitrários. */
    static final Pattern ENTRY_NAME = Pattern.compile("(manifest|metadata|automation|sessions|variables)\\.json|steps/" + UUID + "\\.json|evidence/(" + UUID
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
    /** Entradas no pacote: 5 documentos fixos (com o catálogo de variáveis) + uma por etapa + evidências por etapa + não atribuídas. */
    public static final int MAX_ENTRIES = 6 + 2 * MAX_STEPS;

    /** Tipo do pacote: projeto confirmado pelo usuário ou fotografia de recuperação (D-S006-14). */
    public enum Kind { PROJECT, RECOVERY }

    private ProjectFormat() {}

    /**
     * A versão do formato é lida por esta versão do Studio: MAJOR 1 e MINOR 0 ou 1 (o PATCH não muda a estrutura). Mais
     * nova que a {@link #LATEST_FORMAT_VERSION} é {@code NEWER} — nunca lida em parte.
     */
    static Compatibility compatibility(String version) {
        if (version == null || !version.matches("\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}")) return Compatibility.INVALID;
        String[] p = version.split("\\.");
        String[] c = LATEST_FORMAT_VERSION.split("\\.");
        String[] base = FORMAT_VERSION.split("\\.");
        int major = Integer.parseInt(p[0]);
        int minor = Integer.parseInt(p[1]);
        int cMajor = Integer.parseInt(c[0]);
        int cMinor = Integer.parseInt(c[1]);
        if (major > cMajor || (major == cMajor && minor > cMinor)) return Compatibility.NEWER;
        if (major < Integer.parseInt(base[0]) || (major == Integer.parseInt(base[0]) && minor < Integer.parseInt(base[1]))) return Compatibility.OLDER;
        return Compatibility.SUPPORTED;
    }

    private static int minor(String supportedVersion) {
        return Integer.parseInt(supportedVersion.split("\\.")[1]);
    }

    /** {@code variables.json} é obrigatório (1.1.x). Só para versões {@code SUPPORTED}. */
    static boolean requiresVariables(String supportedVersion) {
        return minor(supportedVersion) == 1;
    }

    /** {@code variables.json} pode existir (1.1.x obrigatório; 1.2.x opcional; 1.0.x nunca). */
    static boolean allowsVariables(String supportedVersion) {
        return minor(supportedVersion) >= 1;
    }

    /** STUDIO-009: o formato representa decisões sobre o alvo e alvo substituto (1.2.x). */
    static boolean hasTargetDecisions(String supportedVersion) {
        return minor(supportedVersion) >= 2;
    }

    /** Versão do modelo de automação exigida por uma versão de formato suportada. */
    static String modelFor(String supportedVersion) {
        return switch (minor(supportedVersion)) {
            case 0 -> ModelVersion.CURRENT;
            case 1 -> ModelVersion.V1_1;
            default -> ModelVersion.V1_2;
        };
    }

    /** A versão (de um arquivo lido) já representa o catálogo de variáveis — gravar nela não é migração. */
    public static boolean representsVariables(String version) {
        return compatibility(version) == Compatibility.SUPPORTED && allowsVariables(version);
    }

    /** A versão (de um arquivo lido) já representa decisões sobre o alvo — gravar nela não é migração (STUDIO-009). */
    public static boolean representsTargetDecisions(String version) {
        return compatibility(version) == Compatibility.SUPPORTED && hasTargetDecisions(version);
    }

    /** Versão do formato que acompanha um modelo de automação (a menor que o representa). */
    public static String formatForModel(String modelVersion) {
        if (ModelVersion.V1_2.equals(modelVersion)) return FORMAT_VERSION_1_2;
        return ModelVersion.V1_1.equals(modelVersion) ? FORMAT_VERSION_1_1 : FORMAT_VERSION;
    }

    /** Menor versão do formato capaz de representar os documentos (D-S008-06; STUDIO-009: pelo modelo declarado). */
    public static String versionFor(java.util.Map<String, ?> documents) {
        Object automation = documents.get(AUTOMATION);
        if (automation instanceof java.util.Map<?, ?> a && ModelVersion.V1_2.equals(a.get("modelVersion"))) return FORMAT_VERSION_1_2;
        return documents.containsKey(VARIABLES) ? FORMAT_VERSION_1_1 : FORMAT_VERSION;
    }

    enum Compatibility { SUPPORTED, NEWER, OLDER, INVALID }
}
