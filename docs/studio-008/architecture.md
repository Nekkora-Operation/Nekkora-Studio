# STUDIO-008 — Arquitetura

Semantic Variables & Data Flow Engine: o Studio passa a ter um **catálogo de variáveis declaradas** por automação, uma
**análise estática de fluxo de dados** e um **contrato semântico** derivado. Nada é executado, nada é observado na página
e nenhum valor de execução existe no Studio.

## Peças e dependências

```
nekkora-studio-domain        variable/*  (VariableDefinition, VariableDraft, VariableRules, tipos e vocabulários)
                             Automation  (catálogo dentro do agregado: declarar, editar, renomear, excluir; Snapshot)
        ▲
nekkora-studio-persistence   formato .nekkora 1.1.0 (variables.json), leitor 1.0.x/1.1.x, migração com cópia
nekkora-studio-validation    RuleSet 1.0/1.1, DataFlowAnalysis, 19 códigos novos, relatório 1.1.0
        ▲
nekkora-studio-semantics     SemanticContract 1.0.0 (novo módulo, Java puro)
nekkora-studio-ui            VariablesPanel, VariableForm, RenameVariableForm, editor de etapa (variáveis declaradas)
nekkora-studio-app           WorkspaceService (operações), StudioController/StudioView (aba Variáveis), ProjectService (migração)
```

- **Domínio** (`com.nekkora.studio.domain.variable`): declaração imutável; regras de coerência únicas
  (`VariableRules.check`) usadas na criação, na edição e na leitura de um projeto.
- **Agregado**: o catálogo vive em `Automation` e entra no `Snapshot` — Undo/Redo, "alterações não salvas", bloqueio
  durante a gravação e recuperação funcionam sem código paralelo (D-S008-01).
- **Uso derivado**: uma etapa usa uma variável quando sua referência efetiva (`ValueSource.VariableReference`) tem
  exatamente o nome declarado (D-S008-05). Não há "vínculo" armazenado além do próprio nome.
- **Produtor declarado**: só a origem `STEP_OUTPUT` com a etapa escolhida pelo desenvolvedor. O Studio não comprova
  que a etapa produz o valor.
- **Análise** (`DataFlowAnalysis`): uma passada linear sobre sessões e etapas, índices montados uma vez (sem consultas
  quadráticas), ciclos por encadeamento (cada etapa tem no máximo uma referência).
- **Validação** (`AutomationValidator(RuleSet.V1_1)`): o Studio usa as regras 1.1.0; `new AutomationValidator()`
  continua produzindo o relatório 1.0.0 idêntico.
- **Contrato semântico**: derivado em memória, separado do relatório de validação, sem valores e sem nomes de
  etapas/sessões; não é persistido.

## Fluxo de uma edição

`VariablesPanel`/`VariableForm` → `StudioController` → `WorkspaceService.declareVariable/updateVariable/renameVariable/
removeVariable` → `edit(...)` (bloqueio de gravação, fotografia antes/depois, histórico, `markChanged`, eventos) →
`Automation` (regras do domínio). Toda recusa volta como `ValidationProblem` e é mostrada no campo certo do formulário.

## O que não muda

Extensão, Bridge, Recorder e Native Host: nenhuma alteração. O formato 1.0.0, o relatório 1.0.0 e as fixtures
congeladas continuam idênticos (exceto os 4 ajustes autorizados em `decisions.md` §3).
