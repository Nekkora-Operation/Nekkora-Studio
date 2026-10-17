# STUDIO-008 — Contrato semântico `nekkora-studio/semantic-contract` 1.0.0

Módulo `nekkora-studio-semantics`. `SemanticContract.of(automation).toJson()`.

- **Determinístico**: o mesmo estado produz os mesmos bytes (sem instantes, sem identidades geradas); fixture congelada.
- **Local**: derivado em memória; não é persistido nem enviado. O app só o usa nos testes e na homologação.
- **Sem segredos**: ver `privacy.md`; a lista `exclusions` vai no próprio documento.
- **Separado do relatório de validação**: não tem estado, apontamentos nem severidades; o relatório diz o que falta, o
  contrato descreve o que foi declarado e derivado.
- **Não certifica execução**: toda produtora declarada leva `"runtimeVerification": "UNVERIFIED_AT_RUNTIME"`.
- **Seguro também como API Java** (revisão 1, D-S008-18): `variables()` devolve cópias próprias
  (`Variable`, `Scope`, `Origin {kind, constantDeclared, producer}`, `Constraints`); nenhum caminho público alcança a
  definição do domínio, o valor fixo ou a finalidade (`SemanticContractApiTest` percorre todos por reflexão).

## Estrutura

```
schema, version, automationId, modelVersion, dataFlowRules ("nekkora-validation-rules/1.1.0"),
summary {variables, steps, variableReferences, undeclaredReferences, uncertainties},
variables[] {variableId, name, technicalType, semanticType, semanticStatus, sensitivity,
             scope {kind, sessionId}, origin {kind, constantDeclared, producer {stepId, status, runtimeVerification} | null},
             constraints {...}, consumers [stepId]},
steps[] {stepId, sessionId, position {session, step}, actionType, value (NONE|LITERAL|VARIABLE|CREDENTIAL),
         reference (DECLARED|UNDECLARED|null), variableId},
uncertainties[] {code, variableId, stepId},
exclusions[]
```

## Situação da produtora

| Status | Significado |
|---|---|
| `DECLARED` | declarada; nenhuma etapa usa a variável |
| `STATICALLY_CONSISTENT` | existe, está no escopo, vem antes de todas as consumidoras, sem ciclo e com todas as consumidoras no escopo |
| `INVALID` | qualquer incoerência estática (inexistente, fora do escopo, depois de uma consumidora, a própria etapa, ciclo, consumidora fora do escopo) |

## Incertezas (vocabulário fechado)

`INPUT_VALUE_UNKNOWN`, `STEP_OUTPUT_UNVERIFIED`, `ORIGIN_UNBOUND`, `SEMANTIC_UNCONFIRMED`, `SEMANTIC_UNSPECIFIED`,
`UNDECLARED_REFERENCE`, `OUT_OF_SCOPE_REFERENCE`, `CREDENTIAL_UNRESOLVED`, `REPRESENTATION_UNSPECIFIED` (revisão 1:
o significado admite mais de uma representação — CPF só dígitos ou com máscara — e a variável não declara qual). Ordem:
variáveis (catálogo) e depois etapas.

Schema e fixture: `nekkora-studio-semantics/src/test/resources/contracts/semantic-contract/1.0.0/`.
