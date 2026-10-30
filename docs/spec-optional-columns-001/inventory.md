# G0 — Inventário e contratos reais (NEKKORA-SPEC-OPTIONAL-COLUMNS-001)

Data: 21/10/2026 · Studio: branch `feat/spec-optional-columns-001`, criada de `425be58` (baseline aprovado da
CODEGEN-QUALITY-001, só local) · Framework: `Nekkora-RPA-Framework` `main` = `ca34da8`, `nekkora_rpa.__version__ = "0.3.0"`.

Tudo abaixo foi lido no código-fonte (caminho:linha). Nada foi presumido de documentação.

## 1. Framework (`nekkora_rpa` 0.3.0) — a célula opcional JÁ EXISTE

| Peça | Assinatura / comportamento real | Onde |
|---|---|---|
| `OutputColumn` | `@dataclass(frozen=True) OutputColumn(header: str, field: str, kind: ColumnKind = TEXT, required: bool = True, max_chars: int = 2000)` | `src/nekkora_rpa/xlsx.py:108-116` |
| `ColumnKind` | `TEXT`, `ISO_DATE` (texto `AAAA-MM-DD` de data existente), `URL` | `xlsx.py:87-92` |
| `XlsxSchema` | `XlsxSchema(columns, key_field, sheet="Dados", max_rows=10_000)`; a coluna-chave PRECISA ser `required` (`schema_key_invalid`) | `xlsx.py:119-160` |
| Revisão do esquema | `XlsxSchema.revision()` inclui `c.required` no JSON canônico (mudar a obrigatoriedade muda a revisão) | `xlsx.py:172-181` |
| Valor da célula (`_clean`) | `None` → `""`; `str` normalizado (NFC, espaços colapsados, controles removidos); texto vazio: `required` → `XlsxOutputError("missing_<field>")`, opcional → `""`. Valor não vazio é validado pelo tipo: `ISO_DATE` exige `AAAA-MM-DD` de data existente (`date_invalid_<field>`), `URL` (`url_invalid_<field>`); tipo não `str`/`date` → `value_type_<field>` | `xlsx.py:185-210` |
| Escrita | célula `""` NÃO é escrita (`if value == "": continue`): fica verdadeiramente vazia sob o cabeçalho | `xlsx.py:293-312` |
| Releitura | `read_back` devolve `""` para célula vazia; cabeçalho idêntico, nenhuma fórmula, nenhuma célula extra | `xlsx.py:260-291` |
| Gravação | atômica (temporário → reabre e confere → `os.replace`), `CommitPolicy.ALL_OR_NOTHING` padrão: sem planilha parcial | `xlsx.py:1-20`, `:325-380` |
| `Actions.exists(key, *, step_id)` | sem espera; `True` = exatamente um elemento de identidade verificada; `False` = nenhum; ambíguo / identidade divergente = ERRO (nunca "não existe") | `src/nekkora_rpa/libs/actions.py:534-550` |
| `Actions.read_items` | lê o alvo verificado; ausente → `TARGET_NOT_FOUND` após o prazo; ambíguo → `TARGET_AMBIGUOUS` | `actions.py:637-655`, `adapters/playwright/resolver.py:4-11` |
| `parse_long_date(text, *, language="pt") -> str \| None` | data por extenso → `AAAA-MM-DD` ou `None` (incompleta/ambígua) | `src/nekkora_rpa/text.py:69` |
| `BusinessRuleRejected(reason: str, *, step_id: str \| None = None)` | recusa de negócio; `reason` é código do processo (token), nunca valor | `src/nekkora_rpa/errors/exceptions.py:89-97` |
| Entrada tabular | `tabular.Column(header, required=True, ...)` — célula vazia em coluna obrigatória = `EMPTY_REQUIRED` (lado da ENTRADA; não muda nesta missão) | `src/nekkora_rpa/tabular.py:50-56`, `:114-116` |

Conferido também no pacote INSTALADO pelo próprio Studio (ambiente preparado da homologação da CQ-001,
`…/ambientes/<hash>/0.3.0-excel/venv/Lib/site-packages/nekkora_rpa/xlsx.py`): mesmas linhas de `OutputColumn`
(`nekkora_rpa-0.3.0.dist-info`).

**Conclusão de G0 para o runtime:** nenhuma mudança no framework é necessária. `OutputColumn(..., required=False)` já
produz a célula vazia com o cabeçalho preservado, e um valor presente continua validado pelo tipo. A versão homologada
0.3.0 não é alterada nem republicada.

Catálogo de capacidades do Studio (`Nekkora-AI-Engine/catalog/nekkora-rpa-0.3.0.json`): `xlsx.schema` (`XlsxSchema`,
`:700`), `xlsx.result_sink`, `actions.exists`. `OutputColumn` não é uma capacidade separada no catálogo (é um tipo de
dado de `xlsx.schema`); `required` faz parte dele desde a 0.3.0. Nenhuma versão nova → o fail-closed
`FRAMEWORK_CATALOG_MISSING` continua como está.

## 2. Studio — de onde vêm hoje as colunas da planilha

| Peça | Estado atual | Onde |
|---|---|---|
| Especificação funcional | `FunctionalSpec` (`nekkora-studio/functional-spec` 1.1.0; 1.0.0 lida). Campos: `EFFECT, POSTCONDITION, CONCLUSION, START_URL, DATA_FLOW, OUTPUT_COLUMN`. `OUTPUT_COLUMN` por etapa Observar: `COLUMN:<cabeçalho>`, `FALLBACK:<StepId>` ou `NONE`. Decisão = campo, etapa, valor, ator (`DEVELOPER`/`TEST_AGENT`), base (`ANSWER`/`PROPOSAL_ACCEPTED`), fingerprint da etapa, data | `Nekkora-AI-Engine/.../ai/spec/FunctionalSpec.java:33-131` |
| Resolução | `SpecResolver.columns`: decisão válida (fingerprint igual) → senão rótulo da linha (âncora gravada, `DERIVED_LABEL`) → endereço da página (`URL`) → pergunta. Revisão da coluna derivada: `SPEC_OUTPUT_COLUMN_REVIEW` (aviso) | `ai/spec/SpecResolver.java:99-164` |
| Planejador | `DevelopmentPlanner.plan` (determinístico): `Read(kind, optional, expected)` com `optional = tem alvo && (é alternativa || tem alternativa)` — **opcional hoje = "par de fonte alternativa"**; `Column(header, field, kind, step, fallbackStep)` — **sem obrigatoriedade** | `ai/plan/DevelopmentPlanner.java:166-228` |
| Configuração de geração | `SingleConfig` 1.2.0: `Read`, `Column`, `Output` (leitura estrita, chaves fechadas) | `nekkora-studio-codegen/.../SingleConfig.java:40-99`, `:311`, `:348` |
| IR | `CodePlan.Field(name, optional, source)` (`str \| None` quando a leitura é opcional); `Helper.TEXT_IF_PRESENT/DATE_IF_PRESENT`; `Column(field, header, primary, fallback, fallbackLabel)` sem obrigatoriedade; leitura opcional espera antes pela 1ª leitura OBRIGATÓRIA do grupo | `CodePlan.java:83-106`, `:233-262`, `:436-451` |
| Emissor | `_text_if_present`/`_date_if_present` (`exists()` → `None`); `_date` recusa data incompleta (`DATA_INCOMPLETA_OU_AMBIGUA`); `build_spreadsheet_row` exige TODAS as colunas (`DADO_OBRIGATORIO_VAZIO:<campo>`) | `SemanticEmitter.java:149-166`, `:494-522` |
| `main.py` | `OutputColumn("<cabeçalho>", "<campo>", ColumnKind.X)` — **`required=` nunca emitido** (todas obrigatórias pelo padrão do framework) | `SingleEmitter.java:242-261` |
| Manifesto | 1.4.0; `output.columns` = header/field/kind/stepId/fallbackStepId | `SingleEmitter.java:31`, `:402-418` |
| Apêndice do PDD | "Dados do processo" gerado da especificação resolvida; frase fixa "dado obrigatório vazio … rejeita o item" | `ai/spec/DataFlowDocument.java:27-65`, `app/pdd/PddPresenter.java:1153` |
| Gate / código desatualizado | `PddGate` grava `specRevision`; `AutomationQualityGate.codeState` marca `CODE_OUTDATED` quando o PDD ou a especificação mudam (revisão diferente da do manifesto) | `ai/gate/PddGate.java:50-108`, `ai/quality/AutomationQualityGate.java:285-307` |
| Interface | as decisões da especificação são respondidas na janela "Desenvolver automação" → aba Pendências (`DevelopmentPresenter.resolve` grava a decisão com ator, base e fingerprint e desenvolve de novo). INFO não é listado em Pendências | `app/develop/DevelopmentPresenter.java:553-556`, `:690-733` |
| Persistência | especificação em `<dados do Studio>/desenvolvimento/<automationId>/especificacao.json` (`DevelopmentStore`); PDD em `<dados>/pdd/<id>/rascunho.json`; o `.nekkora` (modelo) não guarda colunas | `ai/store/DevelopmentStore.java:20-87` |
| `VariableConstraints.required` | existe no modelo e na interface ("Valor obrigatório"), mas nenhum planejador/gerador lê; NÃO é a fonte desta regra (não foi revisado como contrato de saída) | `nekkora-studio-domain/.../VariableConstraints.java:22` |
| Conferência da planilha (teste autorizado) | `conferir_planilha.py` reabre com o esquema DECLARADO no `main.py` (`read_back`) | `Nekkora-AI-Engine/src/main/resources/nekkora/ai/python/conferir_planilha.py` |

## 3. Matriz suportado × ausente

| Capacidade | Framework 0.3.0 | Studio (antes desta missão) |
|---|---|---|
| Coluna de saída opcional (célula vazia, cabeçalho mantido) | SIM (`OutputColumn.required=False`) | NÃO (nunca emitido) |
| Decisão revisada de obrigatoriedade por coluna | — | NÃO (só cabeçalho / alternativa / fora) |
| Leitura que pode faltar sem erro (`exists` → `None`) | SIM | SÓ para par de fonte alternativa |
| Ausente × ambíguo × identidade divergente | SIM (`exists` erra no ambíguo/divergente) | SIM (usa `exists`) |
| Data presente e inválida → erro | SIM (`date_invalid_*` no sink) | SIM (`_date` recusa antes) |
| Texto só com espaços = ausência | SIM (normalização → `""`) | SIM (`_filled`) |
| Chave da planilha opcional | recusado (`schema_key_invalid`) | — |
| Gravação atômica / sem parcial | SIM | — |
| Regra no manifesto | — | NÃO |
| Mudança de regra invalida código/gate | — | SIM pela `specRevision` (qualquer decisão nova) |

## 4. Consequência para o desenho (G1–G4)

- A regra mora onde as colunas já moram: **uma decisão nova da especificação funcional por coluna**
  (`OUTPUT_REQUIREMENT`), presa ao fingerprint da etapa, com ator e base, versionada (`functional-spec` 1.2.0 só quando
  usada) e mostrada no apêndice "Dados do processo" do PDD. Sem decisão → `REQUIRED` (legado intacto).
- Do Studio ao Python: `SingleConfig.Column.required` (configuração 1.3.0 só quando há coluna opcional) → leitura
  opcional (`exists`) → `build_spreadsheet_row` exige só as obrigatórias → `OutputColumn(..., required=False)`.
- Emissor tabular (RPA Challenge): não tem planilha de saída por colunas (resultado JSON por linha) — fora do alcance
  desta regra; nada muda nele.
