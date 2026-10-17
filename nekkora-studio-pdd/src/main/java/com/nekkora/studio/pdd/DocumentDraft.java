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
 * STUDIO-010 — rascunho do PDD visual ({@code nekkora-studio/pdd-visual/1.0.0}). Imutável: cada edição devolve um
 * rascunho novo (o editor guarda o histórico para desfazer/refazer). Nunca altera a automação: só referencia etapas e
 * sessões pelos identificadores e guarda textos do DOCUMENTO.
 */
public record DocumentDraft(AutomationId automationId, String processRevision, List<Section> sections, Map<String, SafeCapture> captures) {

    public static final String SCHEMA = "nekkora-studio/pdd-visual";
    public static final String VERSION = "1.0.0";

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

    /**
     * Imagem JÁ sanitizada (política {@code nekkora-visual/1}): só a referência e os metadados — os pixels ficam no
     * armazenamento auxiliar, endereçados pelo sha-256 do PNG.
     *
     * @param forSteps etapas do bloco no momento da captura (a captura não vale para outras etapas)
     */
    public record SafeCapture(String sha256, int width, int height, double devicePixelRatio, Instant observedAt, int pageEpoch, String policy,
                              String screenKey, List<StepId> forSteps, List<Annotation> annotations, List<String> limitations) {
        public SafeCapture {
            Objects.requireNonNull(sha256);
            if (!sha256.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 inválido");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("dimensões inválidas");
            Objects.requireNonNull(observedAt);
            if (!"nekkora-visual/1".equals(policy)) throw new IllegalArgumentException("política visual desconhecida");
            screenKey = screenKey == null ? "" : screenKey;
            forSteps = List.copyOf(forSteps);
            annotations = List.copyOf(annotations);
            limitations = List.copyOf(limitations);
        }
    }

    /**
     * Marcador sobre a imagem: retângulo NORMALIZADO ([0,1] sobre o viewport) do alvo de uma etapa, vindo da extensão.
     * {@code PLACED} só quando o alvo foi comprovado na mesma época da imagem; senão {@code OMITTED} com o motivo em código.
     */
    public record Annotation(StepId stepId, Status status, double x, double y, double width, double height, String reason) {
        public enum Status { PLACED, OMITTED }

        public Annotation {
            Objects.requireNonNull(stepId);
            Objects.requireNonNull(status);
            reason = reason == null ? "" : reason;
            if (status == Status.PLACED && (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 1.0001 || y + height > 1.0001)) {
                throw new IllegalArgumentException("retângulo fora da imagem");
            }
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

    /** Mapa auxiliar imutável: captura por sha. */
    static Map<String, SafeCapture> with(Map<String, SafeCapture> m, SafeCapture c) {
        Map<String, SafeCapture> out = new LinkedHashMap<>(m);
        out.put(c.sha256(), c);
        return out;
    }
}
