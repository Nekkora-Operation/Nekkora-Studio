# NEKKORA-SPEC-OPTIONAL-COLUMNS-001 — Aceite e encerramento local

Data: 22/10/2026 · Branch `feat/spec-optional-columns-001` (só local), criada de `425be58` (baseline da CODEGEN-QUALITY-001).

Estado: **COMPLETED_LOCALLY / APPROVED_BY_OWNER / REMOTE_PUBLICATION_BLOCKED**

## Decisões

- **Revisão técnica independente da rodada 1:** aprovada no escopo de colunas opcionais, sem pedir rodada 2. A revisão
  conferiu o ZIP (43 arquivos, SHA256SUMS 20/20, manifestos 18/18, AST 10/10, `final` = `desenvolver`, planilha
  sanitizada) e declarou que não reproduziu testes Java, navegador, interface nem suítes.
- **Aceite humano da titular (G7, HUMAN_REVIEW_ACCEPTED): SATISFEITO.** Declaração na conversa, junto do parecer:
  > "sobre a visão humana, está aprovada também"

  Aceite próprio desta missão (não reaproveita o da CODEGEN-QUALITY-001). Cobre a rodada 1 apresentada; não é autorização de
  push, PR, merge, pacote ou mudança de visibilidade.

## Baseline aceito

| Item | Valor |
|---|---|
| Implementação | `4359c07` |
| Correção achada na interface (commit testado) | `6828edc` |
| Pasta de revisão | `a390664` + `29fdeea` (bytes estáveis, `-text`) |
| Amostra | `human-review/rodada-1/final` (= `desenvolver`), manifesto 1.5.0, `nekkora-rpa[excel]==0.3.0` |
| Regra | "Morte" → `OPTIONAL:ABSENT_FOR_SOME_ITEMS` (especificação funcional); demais colunas `REQUIRED` |

## Testes: executados × reportados

**Executados por mim** (commit `6828edc`): Windows 780/780, 0 pulados; Linux 780 com 0 falhas e **27 pulados** (753
aprovados — os pulados não contam: 26 dependem do Python do framework, ausente no contêiner; 1 não se aplica a root);
`OptionalColumnsTest` 13/13 no Windows; interface real 105/105; conferidor independente 29/30.

**Reportados pela revisão independente**: as conferências sobre o ZIP listadas acima.

## F-OC-01 (aberto, fora do escopo)

"Nome completo" da pessoa viva com a chamada de nota visível (`[nota 1]`); o conferidor a remove → 29/30. Decisão
registrada conforme a recomendação do parecer: **o texto extraído fica como está** (nenhuma limpeza automática, nenhuma
mudança no framework nem no conferidor). Remover chamadas de nota exigiria uma regra de normalização explícita por
campo/fonte, com testes (texto genuíno com `[nota 1]`, HTML com referência, falso positivo), em missão própria e só se
autorizada.

## Fora deste aceite

- Push, PR remoto, merge, tag, release, PyPI/TestPyPI, mudança de visibilidade: bloqueados (decisão separada da titular).
- Integrações Studio↔Agent e Agent↔Platform: não iniciadas.
- Obrigatoriedade de colunas de ENTRADA e tipos numéricos/booleanos: extensões futuras ([decisions.md](decisions.md)).
