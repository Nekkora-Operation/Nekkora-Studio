# NEKKORA-CODEGEN-QUALITY-001 — Relatório (parcial, em curso)

**Estado da missão: AWAITING_HUMAN_READABILITY** — o aceite é exclusivamente da titular ("está entendivel").
**Publicação remota: REMOTE_PUBLICATION_BLOCKED_OWNER_DECISION** — Studio, Agent, Framework e Extensão estavam PÚBLICOS
em 2026-10-20 23:30Z; nenhum push/PR de código proprietário; branch só local `feat/codegen-quality-001`.

## Matriz de gates

| Gate | Estado | Evidência |
|---|---|---|
| G-1 visibilidade | BLOQUEADO (decisão da titular) | `docs/codegen-quality-001/auditoria-g0.md` |
| G0 auditoria | feito | idem (dívida com origem no gerador; contratos a preservar) |
| G1 contratos/desenho | feito | `desenho.md`, `decisoes.md` |
| G2 oráculo manual + laboratório | feito (réplica sintética) | `referencia-manual/` (rotulada, hashes), oráculo independente 18/18 |
| G3 Q01 nomes + traceId | feito | `CodegenQualityRulesTest`, `CodegenQualityFlowTest.semantico/rastreioEstavel` |
| G4 Q02 arquitetura/estilo | feito | Pages por evidência, dataclass, helpers, docstrings, equivalência |
| G5 Q03 localizadores | feito | `LocatorPolicy`, `ancoraAmbigua` (TARGET_AMBIGUOUS, nunca o primeiro) |
| G6 Quality Gate + UI | feito | camada VQ, "Revisar código", prévia da arquitetura |
| G7 regressões | Windows: ver suíte; Linux: pendente | `homologacao.md` |
| G8 interface real | PENDENTE (precisa da máquina livre) | — |
| G9 revisão humana | PENDENTE | `human-review/` |
| G10 PRs | BLOQUEADO por G-1 | patches locais |

## Impacto em contratos

- Manifesto `codegen-manifest` 1.3.0 (aditivo: `trace`, `architecture`, `locators`, `steps[].traceId`); consumidores
  (contrato V5, proveniência do teste autorizado, registro de rastreio) leem 1.1/1.2/1.3.
- `step_id` legível no código; StepId no manifesto e em `selectors.json`. Checkpoints não dependem do `step_id`.
- Requisitos do framework, CLI do `main.py`, árvore de arquivos e `OutputWriter` inalterados. Tabular 1.0.0 intocado.
- Camada nova VQ no relatório de qualidade; D13 soberano.
- Nenhuma mudança no Framework, na Extensão, no Agent; nenhuma publicação.
