# STUDIO-007 — Regras de parâmetros

Base: `ActionParameters` (valor e opção), `ValueSource` (`LiteralValue`, `VariableReference`, `CredentialReference`) e
`OptionChoice`.

| Situação | Código | Severidade |
|---|---|---|
| `FILL` sem valor | `REQUIRED_VALUE_MISSING` | bloqueio |
| `FILL` com literal | — | (nenhum apontamento de parâmetro) |
| `FILL` com variável | `VARIABLE_RESOLUTION_UNAVAILABLE` | aviso, limita a avaliação |
| `FILL` com credencial | `CREDENTIAL_RESOLUTION_UNAVAILABLE` | aviso, limita a avaliação |
| campo sensível (`ElementReference.sensitive`) com variável | `SENSITIVE_FIELD_WITHOUT_CREDENTIAL` | aviso |
| `SELECT_OPTION` sem opção | `OPTION_NOT_CONFIGURED` | bloqueio |
| idem, opção omitida pela privacidade na gravação | + `PRIVACY_OPTION_WITHHELD` | informação (só enquanto faltar) |
| `PRESS_KEY` sem tecla / tecla desconhecida | `KEY_NOT_CONFIGURED` / `KEY_NOT_RECOGNIZED` | bloqueio / aviso |
| `NAVIGATE` sem valor / literal sem http(s) | `NAVIGATION_TARGET_MISSING` / `NAVIGATION_TARGET_NOT_ADDRESS` | bloqueio / aviso |
| parâmetro que o tipo não aceita | `PARAMETER_NOT_APPLICABLE` | aviso |

## Etapas gravadas

O Recorder nunca guarda o que foi digitado (`valueCaptured=false`, STUDIO-003). Toda etapa `FILL` gravada chega sem
valor e recebe `REQUIRED_VALUE_MISSING` até o usuário configurar uma variável ou credencial. Isso é intencional: o
motor não "completa" a etapa e não sugere guardar o valor digitado.

## Segurança

- Nenhuma dependência é resolvida inventando valores; nenhum valor real é introduzido para "fazer passar".
- O relatório nunca contém o literal, o nome da variável nem o apelido da credencial (ver [privacy.md](privacy.md)).

Testes: `AutomationValidatorTest` S007-006 a 015, `ValidationContractTest.s007027SemDadosPrivados`.
