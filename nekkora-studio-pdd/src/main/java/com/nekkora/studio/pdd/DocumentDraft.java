package com.nekkora.studio.pdd;

import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.domain.StepId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * STUDIO-010 — rascunho do PDD visual ({@code nekkora-studio/pdd-visual/1.0.0}; 1.1.0 desde a STUDIO-012 PDD-01, só quando
 * uma novidade é usada — ver {@link #needsV11()}). Imutável: cada edição devolve um
 * rascunho novo (o editor guarda o histórico para desfazer/refazer). Nunca altera a automação: só referencia etapas e
 * sessões pelos identificadores e guarda textos do DOCUMENTO.
 */
public record DocumentDraft(AutomationId automationId, String processRevision, List<Section> sections, Map<String, SafeCapture> captures) {

    public static final String SCHEMA = "nekkora-studio/pdd-visual";
    public static final String VERSION = "1.0.0";
    public static final String VERSION_V11 = "1.1.0";

    /** Seção = sessão lógica, na ordem da automação. */
    public record Section(SessionId sessionId, int number, DocText title, List<Block> blocks) {
        public Section {
            Objects.requireNonNull(sessionId);
            Objects.requireNonNull(title);
            blocks = List.copyOf(blocks);
        }
    }

    /**
     * Bloco documental: uma ou mais etapas CONSECUTIVAS da mesma sessão e da mesma tela, com título, instruções numeradas
     * (uma por etapa) e, se houver, a captura sanitizada do estado da página correspondente.
     *
     * @param screenKey   tela (opaca) das etapas, vazia se desconhecida
     * @param captureSha  captura associada (chave em {@link DocumentDraft#captures()}) ou vazio
     */
    public record Block(String blockId, SessionId sessionId, List<StepId> stepIds, DocText title, List<Instruction> instructions, String screenKey,
                        String captureSha) {
        public Block {
            Objects.requireNonNull(blockId);
            Objects.requireNonNull(sessionId);
            stepIds = List.copyOf(stepIds);
            if (stepIds.isEmpty()) throw new IllegalArgumentException("bloco sem etapas");
            Objects.requireNonNull(title);
            instructions = List.copyOf(instructions);
            screenKey = screenKey == null ? "" : screenKey;
            captureSha = captureSha == null ? "" : captureSha;
        }

        public Optional<String> capture() {
            return captureSha.isEmpty() ? Optional.empty() : Optional.of(captureSha);
        }
    }

    /**
     * Instrução: o que fazer numa etapa, em texto funcional seguro.
     *
     * @param kind {@code OBSERVED} (etapa gravada: ação observada) ou {@code DECLARED} (etapa manual: intenção declarada)
     */
    public record Instruction(StepId stepId, int ordinal, DocText text, Kind kind) {
        public enum Kind { OBSERVED, DECLARED }

        public Instruction {
            Objects.requireNonNull(stepId);
            Objects.requireNonNull(text);
            Objects.requireNonNull(kind);
        }
    }

    /** Políticas visuais aceitas (a v1 continua válida para rascunhos e PDFs anteriores). */
    public static final List<String> VISUAL_POLICIES = List.of("nekkora-visual/1", "nekkora-visual/2");

    /**
     * Imagem JÁ sanitizada ({@code nekkora-visual/1} ou {@code /2}): só a referência e os metadados — os pixels ficam no
     * armazenamento auxiliar, endereçados pelo sha-256 do PNG.
     *
     * @param ref      chave da captura no rascunho (o que o bloco referencia). Rascunhos 1.0.0: igual ao {@code sha256}; no
     *                 1.1.0 cada captura tem a sua ({@code cap-…}), e dois blocos podem ter imagens idênticas sem colidir
     * @param forSteps etapas do bloco no momento da captura (a captura não vale para outras etapas)
     * @param crop     recorte escolhido no Studio (px do dispositivo) ou {@code null} = recorte automático. É METADADO: o PNG
     *                 verificado não muda; o recorte só reduz a área mostrada
     */
    public record SafeCapture(String ref, String sha256, int width, int height, double devicePixelRatio, Instant observedAt, int pageEpoch, String policy,
                              String screenKey, List<StepId> forSteps, List<Annotation> annotations, List<String> limitations, List<Region> regions,
                              Crop crop) {
        public SafeCapture {
            Objects.requireNonNull(sha256);
            if (!sha256.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 inválido");
            ref = ref == null ? sha256 : ref;
            if (!ref.equals(sha256) && !ref.matches("cap-[0-9a-f]{32}")) throw new IllegalArgumentException("referência inválida");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("dimensões inválidas");
            Objects.requireNonNull(observedAt);
            if (!VISUAL_POLICIES.contains(policy)) throw new IllegalArgumentException("política visual desconhecida");
            screenKey = screenKey == null ? "" : screenKey;
            forSteps = List.copyOf(forSteps);
            annotations = List.copyOf(annotations);
            limitations = List.copyOf(limitations);
            regions = regions == null ? List.of() : List.copyOf(regions);
            if ("nekkora-visual/1".equals(policy) && regions.stream().anyMatch(r -> !r.legacy())) throw new IllegalArgumentException("região fora da v1");
            if (crop != null && (crop.x() + crop.width() > width || crop.y() + crop.height() > height)) throw new IllegalArgumentException("recorte fora da imagem");
        }

        /** Forma da STUDIO-010 (rascunho 1.0.0): referência = sha-256, sem recorte. */
        public SafeCapture(String sha256, int width, int height, double devicePixelRatio, Instant observedAt, int pageEpoch, String policy, String screenKey,
                List<StepId> forSteps, List<Annotation> annotations, List<String> limitations, List<Region> regions) {
            this(sha256, sha256, width, height, devicePixelRatio, observedAt, pageEpoch, policy, screenKey, forSteps, annotations, limitations, regions, null);
        }

        public SafeCapture withCrop(Crop c) {
            return new SafeCapture(ref, sha256, width, height, devicePixelRatio, observedAt, pageEpoch, policy, screenKey, forSteps, annotations, limitations,
                    regions, c);
        }

        public SafeCapture withAnnotations(List<Annotation> list) {
            return new SafeCapture(ref, sha256, width, height, devicePixelRatio, observedAt, pageEpoch, policy, screenKey, forSteps, list, limitations, regions,
                    crop);
        }

        /** Usa algo que só existe no rascunho 1.1.0 (política v2, região nova, recorte, marcador manual, referência própria)? */
        boolean needsV11() {
            return !ref.equals(sha256) || crop != null || !"nekkora-visual/1".equals(policy) || annotations.stream().anyMatch(a -> a.manual())
                    || regions.stream().anyMatch(r -> !r.legacy());
        }
    }

    /**
     * Recorte manual (px do dispositivo, inteiros) sobre a imagem sanitizada, com quem o escolheu. Tamanho mínimo para o
     * bloco continuar legível no PDF.
     */
    public record Crop(int x, int y, int width, int height, String reviewer) {
        public static final int MIN_WIDTH = 48;
        public static final int MIN_HEIGHT = 32;

        public Crop {
            if (x < 0 || y < 0 || width < MIN_WIDTH || height < MIN_HEIGHT) throw new IllegalArgumentException("recorte inválido");
            if (!DocText.REVIEWERS.contains(reviewer)) throw new IllegalArgumentException("revisor inválido");
        }
    }

    /**
     * Região da imagem sanitizada (px do dispositivo): {@code CARRIER} (pixels reais de um rótulo de interface) ou
     * {@code FIELD} (moldura sintética); na v2 também {@code SURFACE} (com o {@code tone}: LIGHT/MID/DARK), {@code MEDIA},
     * {@code FRAME} e {@code CONTROL}, todas sintéticas. Guardada para REVERIFICAR os pixels antes de cada exportação.
     */
    public record Region(String kind, int x, int y, int width, int height, String tone) {
        public static final List<String> KINDS = List.of("SURFACE", "MEDIA", "FRAME", "CONTROL", "FIELD", "CARRIER");
        public static final List<String> TONES = List.of("LIGHT", "MID", "DARK");

        public Region {
            if (!KINDS.contains(kind)) throw new IllegalArgumentException("tipo de região");
            if (x < 0 || y < 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("região inválida");
            if ("SURFACE".equals(kind) != (tone != null) || (tone != null && !TONES.contains(tone))) throw new IllegalArgumentException("tom de região");
        }

        public Region(String kind, int x, int y, int width, int height) {
            this(kind, x, y, width, height, null);
        }

        /** Tipo que já existia na v1. */
        boolean legacy() {
            return "CARRIER".equals(kind) || "FIELD".equals(kind);
        }
    }

    /**
     * Marcador sobre a imagem: retângulo NORMALIZADO ([0,1] sobre o viewport) do alvo de uma etapa.
     * {@code AUTOMATIC}: veio da extensão; {@code PLACED} só quando o alvo foi comprovado na mesma época da imagem, senão
     * {@code OMITTED} com o motivo em código. {@code MANUAL}: posicionado no Studio por quem revisa ({@code reviewer}) para
     * uma etapa SEM marcador automático — decisão visual, nunca prova de identidade do elemento.
     */
    public record Annotation(StepId stepId, Status status, double x, double y, double width, double height, String reason, Provenance provenance,
                             String reviewer) {
        public enum Status { PLACED, OMITTED }

        public enum Provenance { AUTOMATIC, MANUAL }

        public Annotation {
            Objects.requireNonNull(stepId);
            Objects.requireNonNull(status);
            reason = reason == null ? "" : reason;
            provenance = provenance == null ? Provenance.AUTOMATIC : provenance;
            reviewer = reviewer == null ? "" : reviewer;
            if (status == Status.PLACED && (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 1.0001 || y + height > 1.0001)) {
                throw new IllegalArgumentException("retângulo fora da imagem");
            }
            if (provenance == Provenance.MANUAL && (status != Status.PLACED || !DocText.REVIEWERS.contains(reviewer) || !reason.isEmpty())) {
                throw new IllegalArgumentException("marcador manual inválido");
            }
            if (provenance == Provenance.AUTOMATIC && !reviewer.isEmpty()) throw new IllegalArgumentException("revisor só em marcador manual");
        }

        public Annotation(StepId stepId, Status status, double x, double y, double width, double height, String reason) {
            this(stepId, status, x, y, width, height, reason, Provenance.AUTOMATIC, "");
        }

        public static Annotation manual(StepId stepId, double x, double y, double width, double height, String reviewer) {
            return new Annotation(stepId, Status.PLACED, x, y, width, height, "", Provenance.MANUAL, reviewer);
        }

        public boolean manual() {
            return provenance == Provenance.MANUAL;
        }
    }

    public DocumentDraft {
        Objects.requireNonNull(automationId);
        Objects.requireNonNull(processRevision);
        sections = List.copyOf(sections);
        captures = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(captures));
    }

    /** {@code documentRevision}: sha-256 do JSON canônico do rascunho. */
    public String revision() {
        return Canonical.sha256(DraftJson.write(this));
    }

    public List<Block> blocks() {
        List<Block> all = new ArrayList<>();
        sections.forEach(s -> all.addAll(s.blocks()));
        return all;
    }

    public Optional<Block> block(String blockId) {
        return blocks().stream().filter(b -> b.blockId().equals(blockId)).findFirst();
    }

    /** Pendências (códigos) que impedem exportar; vazio = pronto para exportar. */
    public List<Pending> pending(ProcessProjection current) {
        return Pendings.of(this, current);
    }

    /**
     * Pendência: código, lugar ({@code blockId} ou {@code secao-<n>}; vazio para o rascunho todo), instrução (0 = nenhuma)
     * e se IMPEDE a exportação ({@code blocking}) ou só é avisada no documento (ex.: imagem ou marcador indisponível).
     */
    public record Pending(String code, String place, int instruction, boolean blocking) {}

    /** Mapa auxiliar imutável: captura pela sua referência. */
    static Map<String, SafeCapture> with(Map<String, SafeCapture> m, SafeCapture c) {
        Map<String, SafeCapture> out = new LinkedHashMap<>(m);
        out.put(c.ref(), c);
        return out;
    }

    /** O rascunho usa algo do 1.1.0? Sem novidade, ele continua sendo gravado byte a byte como 1.0.0. */
    public boolean needsV11() {
        return captures.values().stream().anyMatch(SafeCapture::needsV11);
    }

    /** Sha-256 dos PNGs referenciados pelos blocos (o que o armazenamento precisa manter). */
    public java.util.Set<String> referencedImages() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Block b : blocks()) b.capture().map(captures::get).ifPresent(c -> out.add(c.sha256()));
        return out;
    }
}
