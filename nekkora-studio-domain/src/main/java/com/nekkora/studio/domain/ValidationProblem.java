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
    REFERENCE_NAME_INVALID(Field.VALUE, "Use letras, números, ponto, hífen ou sublinhado no nome da referência.");

    /** Campo do formulário ao qual o problema se refere (para posicionar a mensagem). */
    public enum Field { NONE, NAME, DESCRIPTION, SESSION, ACTION_TYPE, VALUE }

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
