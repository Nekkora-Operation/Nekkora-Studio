package com.nekkora.studio.ai.quality;

import com.nekkora.studio.domain.StepId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * STUDIO-014 — um apontamento de uma camada da garantia de qualidade, com a CLASSE de tratamento:
 * <ul>
 *   <li>{@link Kind#BLOCKER}/{@link Kind#WARNING}/{@link Kind#INFO}: diagnóstico (o que a camada achou);</li>
 *   <li>{@link Kind#AUTO_FIX_SAFE}: correção determinística, de baixo risco e reversível (antes/depois registrados);</li>
 *   <li>{@link Kind#PROPOSE_REVIEW}: correção que muda semântica — só com "Revisar e aplicar";</li>
 *   <li>{@link Kind#HELP_REQUIRED}: falta uma decisão que o Studio não pode tirar das evidências ({@link AssistanceRequest});</li>
 *   <li>{@link Kind#SECURITY_BLOCKED}: a política não permite seguir; nada tenta contornar.</li>
 * </ul>
 * Textos sem valores, sem URLs e sem conteúdo de página: só códigos, rótulos seguros e orientação.
 */
public record QualityIssue(String code, Layer layer, Kind kind, StepId step, String blockId, String title, String detail, String guidance,
                           AssistanceRequest help, Fix fix, String capability) {

    public enum Kind {
        BLOCKER("× Bloqueio"), WARNING("! Aviso"), INFO("i Informação"), AUTO_FIX_SAFE("✓ Correção segura"), PROPOSE_REVIEW("~ Revisar e aplicar"),
        HELP_REQUIRED("? Precisa de ajuda"), SECURITY_BLOCKED("⛔ Bloqueado por segurança");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Impede avançar (gerar ou declarar a camada aprovada). */
        public boolean blocking() {
            return this == BLOCKER || this == HELP_REQUIRED || this == SECURITY_BLOCKED || this == PROPOSE_REVIEW;
        }
    }

    /** Uma correção oferecida (segura ou para revisar), descrita sem valores. */
    public record Fix(String id, String label, String before, String after) {
        public Fix {
            Objects.requireNonNull(id);
            Objects.requireNonNull(label);
        }
    }

    public QualityIssue {
        Objects.requireNonNull(code);
        Objects.requireNonNull(layer);
        Objects.requireNonNull(kind);
        blockId = blockId == null ? "" : blockId;
        title = title == null ? "" : title;
        detail = detail == null ? "" : detail;
        guidance = guidance == null ? "" : guidance;
        capability = capability == null ? "" : capability;
        if (kind == Kind.HELP_REQUIRED && help == null) throw new IllegalArgumentException("pedido de ajuda sem a pergunta");
        if ((kind == Kind.AUTO_FIX_SAFE || kind == Kind.PROPOSE_REVIEW) && fix == null) throw new IllegalArgumentException("correção sem descrição");
    }

    public static QualityIssue of(String code, Layer layer, Kind kind, StepId step, String title, String detail, String guidance) {
        return new QualityIssue(code, layer, kind, step, null, title, detail, guidance, null, null, null);
    }

    public QualityIssue withBlock(String block) {
        return new QualityIssue(code, layer, kind, step, block, title, detail, guidance, help, fix, capability);
    }

    public QualityIssue withCapability(String cap) {
        return new QualityIssue(code, layer, kind, step, blockId, title, detail, guidance, help, fix, cap);
    }

    public Optional<AssistanceRequest> assistance() {
        return Optional.ofNullable(help);
    }

    public Optional<Fix> correction() {
        return Optional.ofNullable(fix);
    }

    /** Identidade estável do apontamento (código + lugar), para retomar e para não repetir a mesma pergunta. */
    public String id() {
        return code + (step == null ? "" : "@" + step.value()) + (blockId.isEmpty() ? "" : "#" + blockId);
    }

    public static List<QualityIssue> blocking(List<QualityIssue> all) {
        return all.stream().filter(i -> i.kind().blocking()).toList();
    }
}
