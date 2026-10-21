# NEKKORA-CODEGEN-QUALITY-001 — Auditoria G-1 / G0 (linha de base)

Data: 2026-10-20 (UTC 23:30). Executor: Claude Code (sessão local, Windows 11).

## G-1 — visibilidade (consultada pela API do GitHub, não presumida)

| Repositório | Visibilidade em 2026-10-20 23:30Z |
|---|---|
| Nekkora-Operation/Nekkora-Studio | **public** |
| Nekkora-Operation/Nekkora-Agent | **public** |
| Nekkora-Operation/Nekkora-RPA-Framework | public |
| Nekkora-Operation/Nekkora-Extension-Chrome | public |

Estado: **`REMOTE_PUBLICATION_BLOCKED_OWNER_DECISION`**. Nenhum push, branch remota ou PR de código proprietário do
Studio/AI Engine foi feito. Branch local `feat/codegen-quality-001`. A visibilidade NÃO foi alterada.

## Bases (depois dos merges da PACKAGING-001)

| Repo | `main` | Observação |
|---|---|---|
| Studio | `1cbf669` (merge #23) | #22 e #23 merged |
| Framework | `ca34da8` (merge #6), `nekkora_rpa` 0.3.0 na venv do laboratório | #5 e #6 merged |
| Extensão | `bbcf96d` (merge #16) | não alterada nesta missão |

## Dívida real encontrada (amostras do código gerado da E2E-002, `docs/e2e-002/evidence/codigo-gerado/`)

| Sintoma | Origem no gerador |
|---|---|
| `a = self.act`, `self.k("fill")`, `identity = "fill"`, chave `processo.pesquisa.press_key` | `Modern.method` (apelido de uma letra); `SingleGenerator.key` caía no NOME DA AÇÃO quando o alvo não tinha rótulo |
| `TelaPesquisa` contendo a busca E a ficha | `DevelopmentPlanner`: tela = resumo opaco (modelo de endereço + título). Na Wikipédia os dois têm o modelo `/{…}/{…}` e título vazio (privacidade) → uma Page só, embora o Enter tenha registrado `DOCUMENT_CHANGED newDocument=true` |
| `etapas_sessao_3` | `methodName`: `PddText.safeLabel("Extração")` vazio (fora do vocabulário) → nome por número de sessão; os títulos REVISADOS do PDD ("Extração dos dados") não eram usados |
| `campo_s` | slug de "Campo(s)" sem a regra D-E2E2-12 (marcador de plural) |
| `step_id="d209853d-…"` e `# etapa <UUID> · OBSERVE` em cada linha | `Modern`/`SingleEmitter` |
| `if a.exists(...): ... else: saidas[...] = ""` repetido; `parse_long_date(...) or ""` | leitura opcional e data inválida reduzidas a `""` (depois `DADO_INCOMPLETO` genérico) |
| `class Processo` | nome fixo, mesmo com nome de negócio validado |
| main.py: `ap`, `a`, `d` | variáveis de uma letra |

## Contratos a preservar (inventário)

- `PDD_READY` (gate determinístico, revisões amarradas); D13 `FRAMEWORK_CATALOG_MISSING` soberano.
- `OutputWriter` (`WRITTEN`/`UNCHANGED`/`PROTECTED` + `.novo`/`.diff`; hashes no manifesto).
- Requisitos: configuração 1.1.0 → `nekkora-rpa==0.2.0`; 1.2.0 → `nekkora-rpa[excel]==0.3.0` (campo `framework` do
  manifesto, lido pelo Dependency Manager e pela resolução de catálogo).
- `selectors.json` (`nekkora-rpa/selectors` 1.1.0; `provenance.studioStepId` aceito pelo framework).
- Consumidores do `step_id`: `contrato_projeto.py` (V5, `STEP_IDS_TRACEABLE`), `RunProvenance` (proveniência do teste
  autorizado: eventos `step_id` ↔ `provenance.studioStepId`). Checkpoints do framework NÃO guardam `step_id`
  (`core/checkpoints.py`, `core/engine.py`).
- Framework: `step_id` aceita token `^[A-Za-z0-9][A-Za-z0-9_.:-]{0,79}$` em 0.2.0 (`23cc6de`) e 0.3.0.
- `XlsxResultSink` (tudo ou nada, antifórmula, chave sem repetição), `TabularInputProvider`, execução supervisionada.

## Linha de base executável ("antes")

Amostras do gerador de `main` para os casos sintéticos (`CodegenSamplesTest`): fichas (lista → planilha, 0.3.0) e
consulta (execução única, 0.2.0). No laboratório do framework (`/leitura/`, dados fictícios), o código "antes" grava
3/3 linhas e o oráculo independente confere 18/18 células; o caso errado é recusado (`CONFERENCIA_FALHOU`) sem gravar a
planilha. A análise AST nova (`qualidade_codigo.py`) acha no "antes": 64 problemas (fichas) e 32 (consulta) — UUID no
código, comentário por etapa, `step_id` sem mapa, nomes genéricos, docstrings ausentes, `or ""`.
