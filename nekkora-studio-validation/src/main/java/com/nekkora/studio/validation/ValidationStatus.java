package com.nekkora.studio.validation;

/**
 * Estado ESTRUTURAL de uma automação depois da validação (STUDIO-007). Nenhum estado promete execução: não existe
 * "pronta para executar" — o Studio ainda não tem executor, e regras estáticas não verificam a página real.
 *
 * <p>Precedência: qualquer {@link Severity#BLOCKER} → {@link #INCOMPLETE}; senão, qualquer apontamento cujo código limita
 * a avaliação ({@link FindingCode#limitsAssessment()}) → {@link #NOT_FULLY_ASSESSABLE}; senão {@link #NO_STATIC_BLOCKERS}.
 */
public enum ValidationStatus {
    INCOMPLETE("Configuração incompleta", "Foram encontrados bloqueios de configuração."),
    NOT_FULLY_ASSESSABLE("Avaliação parcial", "Sem bloqueios encontrados, mas há aspectos que o modelo atual não consegue avaliar "
            + "(por exemplo, variáveis e credenciais sem resolução demonstrável)."),
    NO_STATIC_BLOCKERS("Sem bloqueios estáticos", "As regras estáticas implementadas não encontraram bloqueios. Isso NÃO significa "
            + "que a automação está pronta para execução nem que funcionará na página real.");

    private final String label;
    private final String explanation;

    ValidationStatus(String label, String explanation) {
        this.label = label;
        this.explanation = explanation;
    }

    public String label() {
        return label;
    }

    public String explanation() {
        return explanation;
    }
}
