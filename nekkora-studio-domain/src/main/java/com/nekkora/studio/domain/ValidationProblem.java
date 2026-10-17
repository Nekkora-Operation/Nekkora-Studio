package com.nekkora.studio.domain;

/**
 * Problemas de validação das operações de domínio. As mensagens são destinadas ao usuário: claras,
 * em português e <b>sem identificadores internos</b>.
 */
public enum ValidationProblem {
    NAME_REQUIRED(Field.NAME, "Informe um nome."),
    NAME_TOO_LONG(Field.NAME, "Use no máximo " + Texts.NAME_MAX + " caracteres no nome."),
    NAME_INVALID_CHARACTERS(Field.NAME, "O nome não pode conter quebras de linha nem caracteres de controle."),
    AUTOMATION_NAME_IN_USE(Field.NAME, "Já existe uma automação com este nome."),
    SESSION_NAME_IN_USE(Field.NAME, "Já existe uma sessão com este nome nesta automação."),
    DESCRIPTION_TOO_LONG(Field.DESCRIPTION, "Use no máximo " + Texts.DESCRIPTION_MAX + " caracteres na descrição."),
    AUTOMATION_NOT_FOUND(Field.NONE, "A automação não está mais disponível."),
    AUTOMATION_ALREADY_OPEN(Field.NONE, "Esta automação já está aberta no Studio."),
    SESSION_REQUIRED(Field.SESSION, "Escolha a sessão à qual a etapa pertence."),
    SESSION_NOT_FOUND(Field.SESSION, "A sessão escolhida não existe nesta automação."),
    STEP_NOT_FOUND(Field.NONE, "A etapa não existe nesta automação."),
    ACTION_TYPE_REQUIRED(Field.ACTION_TYPE, "Escolha o tipo de ação."),
    VALUE_NOT_ACCEPTED(Field.VALUE, "Este tipo de ação não recebe valor."),
    OPTION_NOT_ACCEPTED(Field.VALUE, "Apenas a ação \"Selecionar opção\" recebe uma opção escolhida."),
    LITERAL_ON_SENSITIVE_TARGET(Field.VALUE,
            "Campos sensíveis não guardam valores digitados. Use uma variável ou uma referência de credencial."),
    LITERAL_LOOKS_SENSITIVE(Field.VALUE,
            "O valor parece conter um dado sensível. Use uma variável ou uma referência de credencial."),
    VALUE_TOO_LONG(Field.VALUE, "O valor é longo demais."),
    REFERENCE_NAME_INVALID(Field.VALUE, "Use letras, números, ponto, hífen ou sublinhado no nome da referência."),
    OPTION_INVALID(Field.VALUE, "Use até 120 caracteres no rótulo e no valor técnico da opção, sem quebras de linha."),
    OPTION_LOOKS_SENSITIVE(Field.VALUE,
            "A opção parece conter um dado sensível ou um identificador interno. Use o texto visível e o valor técnico da lista, sem dados pessoais nem tokens."),
    // ---- STUDIO-004: edição avançada
    SESSION_POSITION_INVALID(Field.NONE, "A posição escolhida para a sessão não existe."),
    STEP_POSITION_INVALID(Field.SESSION, "A posição escolhida para a etapa não existe nesta sessão."),
    STEP_HAS_NO_ELEMENT(Field.LOCATOR, "Esta etapa não tem elemento vinculado."),
    LOCATOR_NOT_FOUND(Field.LOCATOR, "O localizador escolhido não existe nesta etapa."),
    LOCATOR_NOT_VALIDATED(Field.LOCATOR, "Só um localizador validado na captura pode ser o preferencial."),
    MANUAL_LOCATOR_INVALID(Field.LOCATOR,
            "Informe uma expressão de até " + 500 + " caracteres, sem quebras de linha (observação de até 200)."),
    MANUAL_LOCATOR_LOOKS_SENSITIVE(Field.LOCATOR, "A expressão parece conter um dado sensível. Use um atributo que não identifique pessoas."),
    COMMENT_REQUIRED(Field.COMMENT, "Escreva o comentário."),
    COMMENT_TOO_LONG(Field.COMMENT, "Use no máximo " + 1000 + " caracteres no comentário."),
    COMMENT_INVALID_CHARACTERS(Field.COMMENT, "O comentário contém caracteres de controle não permitidos."),
    COMMENT_LOOKS_SENSITIVE(Field.COMMENT, "O comentário parece conter um dado sensível (e-mail, documento, cartão ou token). Comentários não guardam segredos."),
    COMMENT_NOT_FOUND(Field.COMMENT, "O comentário não existe mais nesta etapa."),
    NO_CAPTURED_ORIGINAL(Field.NONE, "Esta etapa não tem valores originais de gravação."),
    AUTOMATION_RECORDING(Field.NONE,
            "Esta automação está sendo gravada. Finalize ou cancele a gravação para editar ou reorganizar com segurança."),
    NOTHING_TO_UNDO(Field.NONE, "Não há alteração para desfazer nesta automação."),
    NOTHING_TO_REDO(Field.NONE, "Não há alteração para refazer nesta automação."),
    // ---- STUDIO-005: evidências observadas
    EVIDENCE_STEP_NOT_FOUND(Field.NONE, "A etapa desta evidência não existe nesta automação."),
    EVIDENCE_NOT_APPLICABLE(Field.NONE, "Esta etapa foi apenas proposta (a ação não foi executada): não há consequência para observar."),
    EVIDENCE_LIMIT_REACHED(Field.NONE, "Limite de evidências atingido para esta etapa."),
    HISTORY_CONFLICT(Field.NONE,
            "A automação mudou por outro caminho depois desta alteração (por exemplo, recebeu etapas do Recorder). O histórico de edição foi descartado para não aplicar uma alteração sobre um estado diferente."),
    // ---- STUDIO-008: variáveis declaradas
    VARIABLE_NAME_INVALID(Field.NAME, "Use letras, números, ponto, hífen ou sublinhado no nome da variável, começando por uma letra (até 64 caracteres)."),
    VARIABLE_NAME_IN_USE(Field.NAME, "Já existe uma variável com este nome nesta automação (o nome vale para todos os escopos)."),
    VARIABLE_NOT_FOUND(Field.NONE, "A variável não existe nesta automação."),
    VARIABLE_IN_USE(Field.NONE, "A variável é usada por etapas. Para excluí-la, confirme o descarte dos vínculos (as etapas ficam sem valor)."),
    VARIABLE_SEMANTIC_INCOMPATIBLE(Field.VALUE, "O significado escolhido não é compatível com o tipo técnico."),
    VARIABLE_SENSITIVITY_TOO_LOW(Field.VALUE, "Este significado exige uma classificação de sensibilidade mais alta (por exemplo, dados pessoais)."),
    VARIABLE_AUTHORITY_INVALID(Field.VALUE, "Escolha o significado antes de confirmá-lo; sem significado, não há o que confirmar."),
    VARIABLE_CONSTRAINT_INVALID(Field.VALUE, "As restrições não são coerentes com o tipo (comprimento, formato, limites, escala ou moeda)."),
    VARIABLE_FORMAT_INCOMPATIBLE(Field.VALUE, "O formato contradiz o significado (ex.: máscara de CPF num e-mail). Escolha um formato compatível."),
    VARIABLE_CONSTANT_FORBIDDEN(Field.VALUE, "Variáveis com dados pessoais ou segredos não podem ter valor fixo: o projeto não é criptografado."),
    VARIABLE_CONSTANT_INVALID(Field.VALUE, "O valor fixo não é válido para o tipo ou não respeita as restrições."),
    VARIABLE_CONSTANT_LOOKS_SENSITIVE(Field.VALUE, "O valor fixo parece conter um dado sensível. Declare a variável como entrada."),
    VARIABLE_SCOPE_SESSION_NOT_FOUND(Field.SESSION, "A sessão do escopo não existe nesta automação."),
    VARIABLE_PRODUCER_NOT_FOUND(Field.VALUE, "A etapa produtora não existe nesta automação."),
    VARIABLE_PRODUCER_OUT_OF_SCOPE(Field.VALUE, "A etapa produtora fica fora da sessão do escopo da variável."),
    VARIABLE_PURPOSE_LOOKS_SENSITIVE(Field.DESCRIPTION, "A finalidade parece conter um dado sensível. Descreva o significado sem exemplos reais."),
    VARIABLE_LIMIT_REACHED(Field.NONE, "Limite de variáveis atingido nesta automação."),
    // ---- STUDIO-009: decisões sobre o alvo (debug guiado)
    TARGET_DECISION_AUTHORITY(Field.NONE, "Uma decisão do desenvolvedor não pode ser substituída por um agente de teste."),
    TARGET_INCOMPATIBLE_WITH_ACTION(Field.LOCATOR, "O elemento escolhido não é compatível com a ação da etapa (ex.: preencher um botão). Nada foi alterado."),
    TARGET_REPLACEMENT_INVALID(Field.LOCATOR, "A referência escolhida não pode substituir o alvo (sem política de privacidade reconhecida).");

    /** Campo do formulário ao qual o problema se refere (para posicionar a mensagem). */
    public enum Field { NONE, NAME, DESCRIPTION, SESSION, ACTION_TYPE, VALUE, LOCATOR, COMMENT }

    private final Field field;
    private final String message;

    ValidationProblem(Field field, String message) {
        this.field = field;
        this.message = message;
    }

    public Field field() {
        return field;
    }

    /** Mensagem para o usuário. */
    public String message() {
        return message;
    }
}
