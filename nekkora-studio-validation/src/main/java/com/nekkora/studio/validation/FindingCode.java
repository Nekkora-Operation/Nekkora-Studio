package com.nekkora.studio.validation;

/**
 * Catálogo ESTÁVEL de apontamentos da validação estrutural (STUDIO-007, regras {@value AutomationValidator#RULES_VERSION}).
 *
 * <p>Cada código fixa, de uma vez por todas: severidade, campo, se limita a avaliação ({@link ValidationStatus}),
 * explicação e orientação. Os textos são modelos CONTROLADOS: nunca incluem valores, nomes de variáveis ou credenciais,
 * conteúdo da página, expressões de localizadores nem mensagens do navegador. A ordem de declaração é a ordem das regras
 * dentro de um mesmo escopo (determinismo do relatório). Códigos nunca são renomeados: uma mudança de significado é um
 * código novo numa nova versão das regras.
 */
public enum FindingCode {
    // ------------------------------------------------------------------ automação
    AUTOMATION_HAS_NO_SESSIONS(Severity.BLOCKER, FindingField.STRUCTURE, false,
            "A automação não tem sessões nem etapas.",
            "Crie uma sessão e adicione as etapas do processo (ou grave-as com o Recorder)."),
    AUTOMATION_HAS_NO_STEPS(Severity.BLOCKER, FindingField.STRUCTURE, false,
            "A automação não tem nenhuma etapa.",
            "Adicione as etapas do processo a uma sessão (ou grave-as com o Recorder)."),
    UNATTRIBUTED_EVIDENCE(Severity.INFO, FindingField.OBSERVATION, false,
            "Há evidências observadas que não puderam ser associadas a uma etapa.",
            "Nada a corrigir na configuração: são registros de observação, não resultados de negócio."),

    // ------------------------------------------------------------------ sessão
    SESSION_EMPTY(Severity.WARNING, FindingField.STRUCTURE, false,
            "A sessão não tem etapas.",
            "Adicione etapas à sessão ou remova-a, se não fizer parte do processo."),

    // ------------------------------------------------------------------ etapa: tipo de ação
    ACTION_UNCLASSIFIED(Severity.BLOCKER, FindingField.ACTION_TYPE, false,
            "A ação desta etapa ainda não foi classificada.",
            "Edite a etapa e escolha o tipo de ação."),
    WAIT_CONDITION_NOT_MODELED(Severity.WARNING, FindingField.ACTION_TYPE, true,
            "O modelo atual não tem um contrato formal para a condição de espera; ela não pode ser avaliada.",
            "Descreva a condição na descrição da etapa. A configuração formal de condições virá numa missão futura."),
    OBSERVE_IS_NOT_VALIDATION(Severity.INFO, FindingField.ACTION_TYPE, false,
            "Uma etapa de observação registra o processo, mas não valida, por si só, nenhum resultado de negócio.",
            "Nada a corrigir; mantenha a validação de negócio fora desta etapa."),

    // ------------------------------------------------------------------ etapa: parâmetros
    REQUIRED_VALUE_MISSING(Severity.BLOCKER, FindingField.PARAMETERS, false,
            "Valor obrigatório não configurado.",
            "Edite a etapa e informe a origem do valor: valor fixo não sensível, variável ou credencial."),
    KEY_NOT_CONFIGURED(Severity.BLOCKER, FindingField.PARAMETERS, false,
            "A tecla a pressionar não está configurada.",
            "Edite a etapa e informe a tecla (por exemplo, Enter)."),
    KEY_NOT_RECOGNIZED(Severity.WARNING, FindingField.PARAMETERS, false,
            "A tecla configurada não está entre as teclas reconhecidas pelo Studio.",
            "Use o nome de uma tecla reconhecida (Enter, Tab, Escape, setas, F1 a F12…)."),
    NAVIGATION_TARGET_MISSING(Severity.BLOCKER, FindingField.PARAMETERS, false,
            "O endereço de navegação não está configurado.",
            "Edite a etapa e informe o endereço (valor fixo ou variável)."),
    NAVIGATION_TARGET_NOT_ADDRESS(Severity.WARNING, FindingField.PARAMETERS, false,
            "O valor fixo de navegação não tem a forma de um endereço http ou https.",
            "Confira o endereço configurado na etapa."),
    OPTION_NOT_CONFIGURED(Severity.BLOCKER, FindingField.PARAMETERS, false,
            "Opção de seleção não configurada.",
            "Edite a etapa e informe a opção a escolher."),
    PARAMETER_NOT_APPLICABLE(Severity.WARNING, FindingField.PARAMETERS, false,
            "A etapa tem um parâmetro que este tipo de ação não usa.",
            "Edite a etapa: confirme o tipo de ação ou remova o parâmetro."),
    VARIABLE_RESOLUTION_UNAVAILABLE(Severity.WARNING, FindingField.PARAMETERS, true,
            "Referência de variável presente, mas o Studio ainda não tem um catálogo de variáveis: não é possível comprovar que ela será resolvida.",
            "Mantenha a referência. A declaração e a resolução de variáveis virão numa missão futura."),
    CREDENTIAL_RESOLUTION_UNAVAILABLE(Severity.WARNING, FindingField.PARAMETERS, true,
            "Referência de credencial não resolvida: o Studio não tem cofre de credenciais integrado e não consulta nenhum cofre.",
            "Mantenha a referência. Nunca coloque o segredo na automação; a integração com cofre virá numa missão futura."),
    SENSITIVE_FIELD_WITHOUT_CREDENTIAL(Severity.WARNING, FindingField.PARAMETERS, false,
            "O campo foi identificado como sensível, mas o valor vem de uma variável, não de uma referência de credencial.",
            "Para senhas e segredos, use uma referência de credencial."),

    // ------------------------------------------------------------------ etapa: elemento
    ELEMENT_MISSING(Severity.BLOCKER, FindingField.ELEMENT, false,
            "Esta ação atua sobre um elemento, mas a etapa não tem referência de elemento.",
            "Grave a etapa com o Recorder para vincular o elemento. O editor ainda não vincula elementos a etapas criadas manualmente."),

    // ------------------------------------------------------------------ etapa: localizadores
    LOCATOR_MISSING(Severity.BLOCKER, FindingField.LOCATORS, false,
            "O elemento foi identificado, mas não há nenhum localizador para reencontrá-lo.",
            "Adicione um localizador manual (ficará como não verificado) ou grave a etapa de novo."),
    MANUAL_LOCATOR_ONLY(Severity.WARNING, FindingField.LOCATORS, false,
            "O único localizador disponível foi proposto manualmente e não foi verificado.",
            "A verificação ao vivo de localizadores virá numa missão futura; confira a expressão com cuidado."),
    LOCATOR_NOT_VALIDATED(Severity.WARNING, FindingField.LOCATORS, false,
            "Nenhum localizador capturado encontrou exatamente o elemento-alvo na captura.",
            "Revise os localizadores da etapa ou adicione um localizador manual."),
    LOCATOR_MULTIPLE_MATCHES(Severity.WARNING, FindingField.LOCATORS, false,
            "Na captura, os localizadores encontraram mais de um elemento: o alvo é ambíguo.",
            "Revise os localizadores da etapa ou adicione um localizador manual mais específico."),
    LOCATOR_VALIDATED_AT_CAPTURE(Severity.INFO, FindingField.LOCATORS, false,
            "Localizador validado na captura. Sua disponibilidade atual não foi verificada.",
            "Nada a corrigir. A validação foi histórica; a página pode ter mudado desde então."),
    LOCATOR_PREFERENCE_NOT_SET(Severity.INFO, FindingField.LOCATORS, false,
            "Há localizadores validados na captura, mas nenhum está escolhido como preferencial.",
            "Escolha um localizador preferencial na etapa, se quiser fixar qual será usado."),
    LOCATOR_STABILITY_UNKNOWN(Severity.INFO, FindingField.LOCATORS, false,
            "A estabilidade do localizador preferencial é desconhecida.",
            "Nada a corrigir agora; prefira localizadores estáveis quando houver opção."),
    LOCATOR_UNSTABLE(Severity.WARNING, FindingField.LOCATORS, false,
            "O localizador preferencial foi classificado como instável na captura.",
            "Escolha outro localizador preferencial, se houver, ou proponha um localizador manual mais estável."),
    LOCATOR_POSITIONAL(Severity.WARNING, FindingField.LOCATORS, false,
            "O localizador preferencial depende da posição do elemento na página.",
            "Prefira um localizador por atributo de teste, identificador, rótulo ou papel e nome."),
    MANUAL_LOCATOR_UNVERIFIED(Severity.INFO, FindingField.LOCATORS, false,
            "Há localizadores propostos manualmente que não foram verificados.",
            "Nada a corrigir agora; a verificação ao vivo virá numa missão futura."),
    OBSTACLE_HIGH(Severity.WARNING, FindingField.ELEMENT, false,
            "Obstáculo de automação de gravidade alta observado na captura.",
            "Revise o elemento da etapa; o obstáculo pode impedir reencontrar o mesmo alvo."),
    OBSTACLE_MEDIUM(Severity.WARNING, FindingField.ELEMENT, false,
            "Obstáculo de automação de gravidade média observado na captura.",
            "Revise o elemento da etapa."),
    OBSTACLE_LOW(Severity.INFO, FindingField.ELEMENT, false,
            "Obstáculo de automação de gravidade baixa observado na captura.",
            "Nada a corrigir agora."),
    RECORDER_NO_UNIQUE_LOCATOR(Severity.INFO, FindingField.LOCATORS, false,
            "O Recorder informou que nenhum localizador único foi encontrado na captura.",
            "Revise os localizadores da etapa."),
    RECORDER_SAFE_LOCATOR_UNAVAILABLE(Severity.INFO, FindingField.LOCATORS, false,
            "O Recorder identificou o elemento parcialmente: não foi possível produzir um localizador seguro e único.",
            "Adicione um localizador manual, se necessário. Nada da proteção de privacidade deve ser desativado para isso."),
    RECORDER_LIVE_CHECK_UNAVAILABLE(Severity.INFO, FindingField.ELEMENT, false,
            "A verificação na página real não foi possível na captura. A continuidade técnica de um elemento não comprova que "
                    + "ele representa o mesmo alvo funcional.",
            "Revise o elemento da etapa."),
    PRIVACY_LOCATORS_WITHHELD(Severity.INFO, FindingField.LOCATORS, false,
            "Localizadores com texto da página foram descartados pela política de privacidade. Isso não é defeito da captura.",
            "Use os localizadores estruturais restantes ou proponha um localizador manual sem dados da página."),
    PRIVACY_OPTION_WITHHELD(Severity.INFO, FindingField.PARAMETERS, false,
            "A opção escolhida na gravação não foi registrada pela política de privacidade. Isso não é defeito da captura.",
            "Configure a opção na etapa."),

    // ------------------------------------------------------------------ etapa: observação (STUDIO-005)
    OBSERVATION_SIGNALS(Severity.INFO, FindingField.OBSERVATION, false,
            "Foram observados sinais na página depois desta interação. O resultado de negócio permanece não validado.",
            "Nada a corrigir na configuração."),
    OBSERVATION_ERROR_INDICATOR(Severity.INFO, FindingField.OBSERVATION, false,
            "Foram observados indicadores de erro após esta interação. Isso não conclui que a operação falhou, nem que foi validada.",
            "Revise as evidências da etapa para entender o comportamento observado."),
    OBSERVATION_INCOMPLETE(Severity.INFO, FindingField.OBSERVATION, false,
            "A observação desta etapa foi interrompida ou teve cobertura parcial. Isso não indica configuração incorreta.",
            "Nada a corrigir na configuração."),
    OBSERVATION_IN_PROGRESS(Severity.INFO, FindingField.OBSERVATION, false,
            "A janela de observação desta etapa ainda não terminou.",
            "Valide de novo depois que a gravação terminar.");

    private final Severity severity;
    private final FindingField field;
    private final boolean limitsAssessment;
    private final String explanation;
    private final String guidance;

    FindingCode(Severity severity, FindingField field, boolean limitsAssessment, String explanation, String guidance) {
        this.severity = severity;
        this.field = field;
        this.limitsAssessment = limitsAssessment;
        this.explanation = explanation;
        this.guidance = guidance;
    }

    public Severity severity() {
        return severity;
    }

    public FindingField field() {
        return field;
    }

    /** O apontamento revela algo que o modelo atual não consegue avaliar (estado {@link ValidationStatus#NOT_FULLY_ASSESSABLE}). */
    public boolean limitsAssessment() {
        return limitsAssessment;
    }

    /** Explicação segura (modelo textual fixo). */
    public String explanation() {
        return explanation;
    }

    /** Orientação de correção (modelo textual fixo). */
    public String guidance() {
        return guidance;
    }
}
