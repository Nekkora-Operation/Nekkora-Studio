# STUDIO-008 — Semântica, tipagem e autoridade

## Declaração de uma variável

| Campo | Valores | Observação |
|---|---|---|
| Nome | `[A-Za-z][A-Za-z0-9_.-]{0,63}` | mesma sintaxe de `ValueSource.REFERENCE_NAME`; o ponto faz parte do nome; único na automação sem distinção de maiúsculas |
| Tipo técnico | `STRING`, `INTEGER`, `DECIMAL`, `BOOLEAN`, `DATE` | |
| Significado | `UNSPECIFIED`, `FREE_TEXT`, `IDENTIFIER`, `BRAZILIAN_CPF`, `EMAIL`, `CURRENCY`, `PERCENTAGE`, `DATE_ISO` | vocabulário fechado; nunca deduzido do nome nem da página |
| Situação do significado | `USER_CONFIRMED`, `PROPOSED_UNCONFIRMED`, `UNSPECIFIED` | quem sustenta o significado (`SemanticStatus`) |
| Finalidade | texto livre opcional (até 2.000) | contexto para pessoas; recusada se parecer dado sensível; não entra no contrato semântico |
| Escopo | automação inteira ou uma sessão | ver `scopes-and-data-flow.md` |
| Origem | `INPUT`, `CONSTANT`, `STEP_OUTPUT`, `UNBOUND` | ver abaixo |
| Sensibilidade | `PUBLIC`, `INTERNAL`, `PERSONAL_DATA`, `SECRET` | nunca reclassificada automaticamente |
| Restrições | obrigatória; comprimento e formato (texto: `ANY`, `DIGITS`, `LETTERS_AND_DIGITS`, `EMAIL_SHAPE`, `BRAZILIAN_CPF_MASKED`); mínimo/máximo (número); escala (decimal); moeda (monetário) | decimais como texto, sem arredondamento |

**Significado ≠ representação ≠ valor** (revisão 1): o significado confirmado diz o que a informação é; a representação
(`textFormat`) diz como a etapa espera recebê-la; só uma verificação futura, na execução, valida um valor. Para CPF, a
representação é `DIGITS` ou `BRAZILIAN_CPF_MASKED` (`SemanticType.representations()`); sem ela, a validação avisa
`VARIABLE_REPRESENTATION_UNSPECIFIED`. Ver `review-1.md`.

## Compatibilidade significado × tipo

| Significado | Tipos aceitos | Sensibilidade mínima |
|---|---|---|
| CPF | STRING | Dados pessoais |
| E-mail | STRING | Dados pessoais |
| Valor monetário | DECIMAL (exige moeda ISO 4217 e escala) | — |
| Percentual | DECIMAL, INTEGER | — |
| Data ISO | DATE | — |
| Texto livre, Identificador, Não especificado | qualquer | — |

Uma combinação incompatível é recusada pelo domínio (`VARIABLE_SEMANTIC_INCOMPATIBLE`,
`VARIABLE_SENSITIVITY_TOO_LOW`); nada é corrigido em silêncio.

## Autoridade (situação do significado)

- **Confirmado** (`USER_CONFIRMED`): o desenvolvedor marcou "Confirmo o significado desta variável". É sempre um ato
  explícito: num formulário novo a caixa vem desmarcada.
- **Proposto, não confirmado**: há um significado escolhido, sem confirmação. A validação aponta
  `VARIABLE_SEMANTIC_UNCONFIRMED` (aviso que limita a avaliação). Nesta missão nenhuma proposta vem de IA (não há
  geração por IA).
- **Não especificado**: sem significado; o domínio recusa "confirmado" ou "proposto" sem significado
  (`VARIABLE_AUTHORITY_INVALID`).

## Origem

| Origem | O que o Studio sabe | O que fica como limitação |
|---|---|---|
| Entrada | o valor será fornecido na execução | `INPUT_VALUE_NOT_AVAILABLE` (o valor nunca é conhecido nem guardado) |
| Valor fixo | o valor declarado, na forma canônica do tipo | só para `PUBLIC`/`INTERNAL`; recusado se parecer dado sensível; gravado no projeto **sem criptografia** (`CONSTANT_STORED_UNENCRYPTED`) |
| Saída de etapa | a etapa produtora DECLARADA | `STEP_OUTPUT_NOT_PROVEN` (nunca comprovada) |
| Sem origem | nada | `VARIABLE_ORIGIN_UNBOUND` (bloqueio nas etapas que usam) |

## Forma canônica do valor fixo

`INTEGER` `-?\d{1,18}` (sem zeros à esquerda); `DECIMAL` `-?\d{1,30}(\.\d{1,18})?` com a escala declarada aplicada
de forma exata (sem arredondar); `BOOLEAN` `true`/`false`; `DATE` ISO-8601 (`LocalDate`); `STRING` até 500 caracteres,
sem caracteres de controle, respeitando comprimento e formato. O leitor recusa um valor fora da forma canônica
(`TEXT_NOT_NORMALIZED`), em vez de normalizar.

## Tipos aceitos por ação (regras 1.1.0)

Preencher: texto, número e data (não booleano). Navegar e Pressionar tecla: só texto. Demais ações não usam valor
(`PARAMETER_NOT_APPLICABLE` já cobre).
