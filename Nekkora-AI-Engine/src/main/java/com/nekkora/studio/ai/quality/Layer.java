package com.nekkora.studio.ai.quality;

/** Camadas da garantia de qualidade (STUDIO-014). Cada uma diz o que VERIFICA; nenhuma vale pela outra. */
public enum Layer {
    V0_MODEL("V0", "Modelo", "Validação estrutural do modelo (regras da STUDIO-007/008). Não executa nada."),
    V1_PDD("V1", "PDD", "PDD e especificação funcional da revisão atual (gate PDD_READY)."),
    V2_CAPABILITY("V2", "Capacidades", "Ações e alvos traduzíveis para o nekkora_rpa desta versão."),
    V3_PLAN("V3", "Plano", "Plano de arquitetura, variáveis e vínculo das etapas verificado."),
    V4_CODE("V4", "Código Python", "Projeto gerado: compilação, importação, ruff e mypy, executados de verdade."),
    V5_TESTS("V5", "Testes", "Testes de contrato do projeto gerado (sem navegador, sem rede)."),
    /** CODEGEN-QUALITY-001: critérios OBJETIVOS de legibilidade e rastreio do código gerado; não é o aceite humano. */
    VQ_GENERATION("VQ", "Qualidade da geração", "Nomes com evidência, step_id ↔ etapa, docstrings, localizadores e falhas silenciadas (manifesto e AST)."),
    V6_EXECUTION("V6", "Execução real", "Resultado conferido no sistema-alvo por um oráculo independente.");

    private final String code;
    private final String label;
    private final String scope;

    Layer(String code, String label, String scope) {
        this.code = code;
        this.label = label;
        this.scope = scope;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String scope() {
        return scope;
    }
}
