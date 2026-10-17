# STUDIO-008 — Regras de validação `nekkora-validation-rules/1.1.0`

As regras 1.1.0 incluem todas as de 1.0.0 e acrescentam a conferência das referências de variável contra o catálogo.
O Studio usa `AutomationValidator(RuleSet.LATEST)`; `new AutomationValidator()` continua com as regras 1.0.0 e produz o
relatório 1.0.0 idêntico (fixture congelada, byte a byte).

| Regras | Relatório | Códigos |
|---|---|---|
| `nekkora-validation-rules/1.0.0` | `nekkora-studio/validation-report` 1.0.0 | os 40 da STUDIO-007 |
| `nekkora-validation-rules/1.1.0` | `nekkora-studio/validation-report` 1.1.0 | os 40 + 19 novos (acrescentados ao fim do catálogo; nenhum renomeado) |

Relatório 1.1.0 = 1.0.0 + escopo `VARIABLE`, campo `VARIABLE`, `variableId` em todo apontamento (só a identidade da
variável, nunca o nome). Ordem: automação → variáveis (ordem do catálogo) → sessões → etapas → regras.
`VARIABLE_RESOLUTION_UNAVAILABLE` continua no catálogo, mas as regras 1.1.0 não o emitem.

## Na etapa (campo Parâmetros)

| Código | Severidade | Limita | Quando |
|---|---|---|---|
| `VARIABLE_NOT_DECLARED` | Bloqueio | — | o nome referenciado não está declarado (nome exato) |
| `VARIABLE_OUT_OF_SCOPE` | Bloqueio | — | declarada para outra sessão |
| `VARIABLE_TYPE_INCOMPATIBLE` | Bloqueio | — | tipo técnico não aceito pela ação |
| `VARIABLE_ORIGIN_UNBOUND` | Bloqueio | — | variável sem origem declarada |
| `DECLARED_PRODUCER_MISSING` | Bloqueio | — | saída de etapa cuja produtora não existe ou está fora do escopo |
| `DECLARED_PRODUCER_AFTER_CONSUMER` | Bloqueio | — | produtora declarada depois da etapa que usa |
| `STEP_OUTPUT_SELF_CONSUMED` | Bloqueio | — | a etapa usa a saída que ela mesma produziria |
| `DATA_DEPENDENCY_CYCLE` | Bloqueio | — | ciclo de dependências declaradas |
| `STEP_OUTPUT_NOT_PROVEN` | Aviso | sim | saída de etapa anterior: declarada, não comprovada |
| `INPUT_VALUE_NOT_AVAILABLE` | Aviso | sim | entrada: só existe na execução |

Mantidos de 1.0.0 na etapa: `SENSITIVE_FIELD_WITHOUT_CREDENTIAL`, `CREDENTIAL_RESOLUTION_UNAVAILABLE`.

## Na variável (campo Variável)

| Código | Severidade | Limita | Quando |
|---|---|---|---|
| `VARIABLE_SCOPE_SESSION_MISSING` | Bloqueio | — | a sessão do escopo foi excluída |
| `VARIABLE_PRODUCER_MISSING` | Bloqueio | — | a etapa produtora foi excluída |
| `VARIABLE_PRODUCER_OUT_OF_SCOPE` | Bloqueio | — | a produtora foi movida para fora do escopo |
| `SENSITIVE_CONSTANT_FORBIDDEN` | Bloqueio | — | valor fixo em variável sensível ou com aparência de dado sensível (defesa: o domínio já recusa) |
| `VARIABLE_DECLARATION_INCONSISTENT` | Bloqueio | — | a declaração não respeita as regras atuais (defesa) |
| `VARIABLE_SEMANTIC_UNCONFIRMED` | Aviso | sim | significado proposto, não confirmado |
| `VARIABLE_SEMANTIC_UNSPECIFIED` | Informação | — | significado não especificado |
| `CONSTANT_STORED_UNENCRYPTED` | Informação | — | valor fixo gravado no arquivo (não criptografado) |
| `VARIABLE_UNUSED` | Informação | — | nenhuma etapa usa |
| `VARIABLE_REPRESENTATION_UNSPECIFIED` | Aviso | sim | revisão 1: o significado admite mais de uma representação (CPF: só dígitos ou com máscara) e o formato não foi declarado; o Studio não escolhe nem valida valores |

## Estados

Os mesmos da STUDIO-007: `INCOMPLETE` (há bloqueios), `NOT_FULLY_ASSESSABLE` (sem bloqueios, mas há limitações), e
`NO_STATIC_BLOCKERS` (as regras estáticas não encontraram bloqueios — nunca "pronta para executar"). Não existe
`READY_FOR_EXECUTION`, `AI_VERIFIED` nem equivalente; os schemas recusam esses valores (testado).

## Textos

Modelos fixos por código: nunca incluem nomes de variáveis, valores, nomes de etapas ou conteúdo de página. Na tela, o
lugar do apontamento ("Variável — cliente.cpf") vem do catálogo atual, localmente.
