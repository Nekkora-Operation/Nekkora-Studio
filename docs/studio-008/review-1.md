# STUDIO-008 — Revisão 1 do PR #11

Revisão técnica independente do PR #11 (HEAD revisado `2db65cf`, código `6d24d51`): domínio, Data Flow Engine,
persistência, migração e interface aprovados; dois achados corrigidos aqui, no mesmo PR, sem merge.

| Achado | Classe | Situação |
|---|---|---|
| P1 — a API Java do contrato semântico alcança o valor fixo e a finalidade | bloqueante | corrigido |
| P2 — CPF com significado confirmado sem representação declarada | recomendado | corrigido |

## Ordem do trabalho

1. **Reprodução primeiro** (commit `018d435`): testes novos escritos contra o código revisado, com códigos e formatos
   pelo NOME para compilarem nele — todos falham. Saída: `evidence/revisao-1/R1-antes-da-correcao.txt`.
2. **Correção** e regressão dos módulos afetados (domínio, persistência, validação, semântica, UI): verde.
3. **Homologação real focada** (Windows, Robot) só do que mudou na interface: 12/12.
4. **Regressão completa** Windows e Linux (números em `reports/studio-008-final-report.md` §16).

## P1 — contrato semântico seguro também como API Java

**Antes**: `SemanticContract.Variable(VariableDefinition definition, Producer producer, List<StepId> consumers)`. O JSON
era escrito à mão e não lia a finalidade nem o valor fixo, mas qualquer consumidor Java chegava a eles:

```
contract.variables()[0].definition().origin().constant()         = https://lab.exemplo.test/portal
contract.variables()[0].definition().purpose()                   = Endereço do portal de laboratório
contract.variables()[0].definition().draft().toString()          = VariableDraft[... purpose=Endereço do portal ...]
```

(saída real do teste no código revisado, com os dados fictícios da fixture). Também eram alcançáveis
`VariableDefinition`, `VariableDraft`, `VariableOrigin`, `VariableScope` e `VariableConstraints` do domínio.

**Depois**: o contrato só guarda CÓPIAS próprias e imutáveis, exatamente com o que o JSON publica:

```java
record Variable(VariableId id, String name, VariableType technicalType, SemanticType semanticType,
                SemanticStatus semanticStatus, Sensitivity sensitivity, Scope scope, Origin origin,
                Constraints constraints, List<StepId> consumers)
record Scope(VariableScope.Kind kind, SessionId session)
record Origin(VariableOrigin.Kind kind, boolean constantDeclared, Producer producer)   // nunca o valor
record Constraints(boolean required, Integer minLength, Integer maxLength, TextFormat textFormat,
                   BigDecimal minimum, BigDecimal maximum, Integer scale, String currency)
```

Do domínio, só identidades (`AutomationId`, `SessionId`, `StepId`, `VariableId`) e vocabulários fechados (enums). O
escritor JSON passou a ler só essas cópias. **A fixture `semantic-contract/1.0.0` continua idêntica byte a byte.**

**Prova** (`SemanticContractApiTest`): percorre por reflexão TODO caminho público a partir do contrato (componentes de
records, elementos de coleções, `Optional` e métodos públicos sem argumentos, inclusive `toString()` e `toJson()`) e
confere que (1) nenhum texto alcançado contém o valor fixo, a finalidade, o literal, o apelido de credencial ou nomes de
automação/sessões/etapas/referências não declaradas; (2) nenhum tipo do domínio além de identidades e enums é
alcançável; (3) a origem tem só `kind`, `constantDeclared` e `producer`.

Achado no caminho: o `toString()` padrão de `VariableDraft` mostrava a finalidade. Ele agora segue a mesma regra de
`VariableDefinition` (só tipo e significado), com teste em `VariableDomainTest`.

## P2 — significado confirmado ≠ representação declarada ≠ valor validado

| Camada | O que diz | Quem estabelece |
|---|---|---|
| Significado confirmado | a informação é um CPF | `semanticType` + `semanticStatus = USER_CONFIRMED` |
| Representação declarada | como a etapa espera recebê-lo: só dígitos (`12345678909`) ou com máscara (`123.456.789-09`) | `constraints.textFormat` = `DIGITS` ou `BRAZILIAN_CPF_MASKED` |
| Valor validado | um valor concreto satisfaz a declaração | só uma verificação futura, na execução — nunca o Studio desta missão |

- **Novo formato fechado** `TextFormat.BRAZILIAN_CPF_MASKED` ("Máscara de CPF (000.000.000-00)", `[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}`),
  no FIM do enum. Confere só a forma, não os dígitos verificadores.
- **`SemanticType.representations()`**: formatos que fixam a representação quando o significado admite mais de uma — hoje
  só `BRAZILIAN_CPF` → {`DIGITS`, `BRAZILIAN_CPF_MASKED`}. Os demais não exigem: e-mail (uma forma), data ISO 8601 (a
  própria norma), valor monetário (escala e moeda já obrigatórias pelas regras da variável); identificador, texto livre,
  porcentagem e não especificado não têm representação canônica no vocabulário atual (ver limitações).
- **Validação 1.1.0**: `VARIABLE_REPRESENTATION_UNSPECIFIED` — **Aviso**, limita a avaliação (`NOT_FULLY_ASSESSABLE`),
  campo `VARIABLE`, acrescentado no fim do catálogo. Vale para significado confirmado ou proposto (os dois apontamentos
  convivem). Não é bloqueio, não torna o CPF obrigatório e não impõe máscara: o Studio aponta, o desenvolvedor escolhe.
- **Contrato semântico**: incerteza `REPRESENTATION_UNSPECIFIED` (fim do vocabulário). Com a representação declarada, a
  variável de entrada continua com `INPUT_VALUE_UNKNOWN` — representação declarada não é valor validado.
- **Interface**: nenhum componente novo; a lista "Formato (texto)" da aba Restrições ganha a opção (vem do enum) e o
  painel mostra "Restrições: máscara de CPF (000.000.000-00)" (só a inicial em minúscula — os rótulos antigos ficam
  iguais).

### Contratos tocados (todos introduzidos neste PR, ainda não integrados)

| Contrato | Mudança | Fixture |
|---|---|---|
| `nekkora-project` 1.1.0 | `TextFormat` + `BRAZILIAN_CPF_MASKED` (aditivo) | inalteradas |
| `validation-report` 1.1.0 / regras 1.1.0 | código + `VARIABLE_REPRESENTATION_UNSPECIFIED` (aditivo, fim) | **regenerada com autorização da usuária (2026-10-17)**: o exemplo tem um CPF sem formato → +1 apontamento, avisos 4 → 5; o resto é renumeração consistente dos UUIDs normalizados (`evidence/revisao-1/R1-fixture-validation-report-1.1.0-diff.txt`) |
| `semantic-contract` 1.0.0 | `textFormat` + `BRAZILIAN_CPF_MASKED`; incerteza + `REPRESENTATION_UNSPECIFIED` (aditivos) | **idêntica** (o CPF do exemplo já é só dígitos) |

Contratos 1.0.0 da STUDIO-006/007 e o relatório 1.0.0: intocados (as regras 1.0.0 nunca emitem o código novo — testado).
Um teste existente deste PR mudou de expectativa: `ValidationRules11Test.entrada` (CPF confirmado sem formato agora tem
o aviso de representação).

## Homologação real focada (12/12)

`Studio008Review1Homologation`, mesmas travas do roteiro da 008 (dados isolados, Robot, foco conferido antes de cada
atalho, sem navegador, dados fictícios). Evidências em `evidence/revisao-1/`.

| Verificação | Captura |
|---|---|
| aba Restrições oferece "Somente dígitos" e "Máscara de CPF"; o Studio não escolhe ("Qualquer texto") | `S008-R1-01` |
| Ctrl+Shift+V real: aviso de representação (nunca bloqueio); explicação e "Como corrigir" no painel, sem valores | `S008-R1-02` |
| contrato com `REPRESENTATION_UNSPECIFIED` ao lado de `INPUT_VALUE_UNKNOWN` | `S008-R1-contrato-sem-formato.json` |
| editar a variável pelo diálogo real e escolher a máscara; linha "Declaração" do painel | `S008-R1-03`, `S008-R1-04` |
| validar de novo: o aviso some; a entrada continua não disponível | `S008-R1-05` |
| contrato com `BRAZILIAN_CPF_MASKED` e sem a incerteza | `S008-R1-contrato-com-mascara.json` |
| Ctrl+Z / Ctrl+Y reais: formato e aviso voltam e saem juntos | — |
| salvar: `.nekkora` 1.1.0 com o formato, sem campo de valor; logs sem nomes de variáveis | — |

A homologação completa da 008 (40/40) não foi repetida: os fluxos integrados (gravação, migração, abertura de projetos
antigos, renomeação, painéis) não mudaram; a interface só ganhou uma opção vinda do enum e a forma do rótulo de
restrição, ambas cobertas acima. A troca do `toString()` de `VariableDraft` foi feita depois desta homologação e não
aparece na interface.
