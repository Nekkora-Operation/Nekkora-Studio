# NEKKORA-SPEC-OPTIONAL-COLUMNS-001 — Optional Fields & Contract-Preserving Data Flow

Estado: **COMPLETED_LOCALLY / APPROVED_BY_OWNER** (revisão técnica aprovada e aceite da titular em 22/10/2026 — ver
[encerramento.md](encerramento.md)). Publicação remota: **BLOQUEADA** (repositórios públicos; só local).

- Branch local `feat/spec-optional-columns-001`, criada do baseline aprovado da CODEGEN-QUALITY-001 (`425be58`).
  Commits: `4359c07` (implementação, testes, documentos) · `6828edc` (correção achada na interface; commit testado) ·
  commit da pasta de revisão (ver `git log`).
- Framework `nekkora-rpa` 0.3.0 **sem mudança** (já suporta `OutputColumn(..., required=False)`).

## Cenário

A planilha de biografias tem o cabeçalho "Morte", mas pessoa viva não tem data de falecimento. Antes, toda coluna era
obrigatória e o item era recusado. Agora o PDD revisado (especificação funcional) pode declarar uma coluna **opcional**,
com motivo; sem valor, a célula fica vazia sob o cabeçalho — nada é inventado, nada é removido, as obrigatórias continuam
exigidas. A regra é genérica (vale para qualquer coluna lida), nunca decidida pelo Studio ou pela IA.

## Gates

| Gate | Estado | Onde |
|---|---|---|
| G0 Inventário e contratos | OK | [inventory.md](inventory.md) |
| G1 Semântica revisável | OK | [contract.md](contract.md) |
| G2 PDD/UI (revisão, invalidação) | OK | Pendências + apêndice do PDD; [homologation.md](homologation.md) §2–3 |
| G3 Gerador | OK | [human-review/rodada-1/antes-depois.md](human-review/rodada-1/antes-depois.md) |
| G4 Manifesto/Quality Gate | OK | manifesto 1.5.0, V5 `OUTPUT_REQUIREMENT_CONSISTENT` |
| G5 Regressões | OK | 13 testes (`OptionalColumnsTest`); suítes Windows 780/0/0, Linux 780/0 (27 pulados declarados) |
| G6 Homologação real | OK, com achado F-OC-01 (aberto, fora do escopo; texto mantido) | interface 105/0; conferidor 29/30 (a divergência não é de opcionalidade) |
| G7 Revisão da titular | **aceita** (22/10/2026) | [encerramento.md](encerramento.md) |

Documentos: [decisions.md](decisions.md) (decisões, achados, pendências) · [security.md](security.md).
