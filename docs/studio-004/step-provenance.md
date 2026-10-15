# STUDIO-004 — Procedência: observação original × alterações manuais

## Modelo

```
AutomationStep
├── identidade ─────────── StepId (nunca muda)
├── procedência ───────── StepProvenance (origem, chaves internas, RecordingObservation) — imutável
├── observação original ─ CapturedBaseline (nome, descrição, tipo, parâmetros, preferencial) — imutável, só RECORDED
│                         capturedElement(): ElementReference como foi recebido — imutável
├── estado efetivo ────── nome, descrição, tipo, parâmetros, element() (com o preferencial escolhido),
│                         localizadores propostos, comentários — editável pelas operações de Automation
└── revisão manual ────── ManualRevision: campos diferentes do capturado, propostas manuais, instante da última
                          alteração (editedAt por campo)
```

- O `CapturedBaseline` é criado quando uma etapa `RECORDED` entra no modelo. É exatamente o que o Recorder entregou, já
  sanitizado na extensão: nenhuma gravação transmite valores digitados, então o baseline não contém segredos.
- `ManualRevision` é **calculada** comparando o estado efetivo com o baseline. Restaurar um campo ao original faz ele
  deixar de constar.
- Etapas manuais (inclusive duplicadas) não têm baseline: "Alterada manualmente" não se aplica a elas.

## Regras

| Situação | Origem | "Alterada manualmente" |
|---|---|---|
| Gravada e não editada | `RECORDED` | Não |
| Gravada e editada | `RECORDED` (a edição **não** a transforma em `MANUAL`) | Sim (campos listados) |
| Criada manualmente | `MANUAL` | não se aplica |
| Duplicada manualmente | `MANUAL`, com `derivedFrom` interno | não se aplica |

- Editar **não** altera `RecordingObservation`: modo, interação, evidências e limitações ficam como foram observados.
- O resultado de negócio continua **Não validado** (`BusinessOutcome.NOT_VALIDATED`) depois de qualquer edição. Nada é
  executado e nada é validado.
- Nenhum identificador técnico da gravação (gravação, evento, alvo, nó, captura) aparece na interface.

## Na interface

- **Painel de propriedades:** o badge "Alterada manualmente" e o grupo **Origem e alterações**:
  - origem ("Gravada pelo Nekkora Recorder");
  - "Alterada manualmente: Sim/Não";
  - para cada campo alterado, "Nome original" e "Nome atual";
  - "Última alteração";
  - botões "Restaurar nome", "Restaurar localizador preferencial" etc.
- **Editor, aba Origem e alterações:** a observação da gravação e a lista "Valores capturados × atuais", campo a campo,
  com os badges "Capturado/Alterado" e "Restaurar original" (que coloca o valor capturado no formulário).

## Restaurar o valor original

`Automation.restoreOriginal(stepId, campo)`:

1. identifica o campo (`StepField`);
2. recupera o valor capturado (sanitizado na origem);
3. valida a compatibilidade com o estado atual. Restaurar os parâmetros de um `SELECT_OPTION` que virou `CLICK`, por
   exemplo, é recusado até o tipo ser restaurado também;
4. aplica só aquele campo (nunca a etapa inteira);
5. registra uma revisão e uma entrada no histórico.

Segredos que nunca foram guardados não voltam: restaurar os parâmetros de "Preencher campo Senha" devolve "nenhum
parâmetro".

## Evidência

- Domínio: `AutomationEditingTest.procedencia`, `restaurar` e `restaurarSenhaNaoTrazSegredo`.
- Aplicação: `EditorStudioTest.procedenciaERestauracao` e `nadaSimulado`.
- Homologação real, cenário 9 (`S004-09-origem-e-alteracoes.png`, `S004-09-procedencia-no-painel.png`).
