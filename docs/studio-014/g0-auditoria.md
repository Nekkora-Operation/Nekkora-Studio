# STUDIO-014 — G0: auditoria da base real

Data: 2026-10-19. Feita antes de qualquer código desta missão.

## Base e trabalho paralelo

| Repositório | `main` | PRs abertos | Observação |
|---|---|---|---|
| Nekkora-Studio | `d34b7f9` (merge do PR #17 da STUDIO-013, inclui `ab42057`) | nenhum | branch desta missão: `feat/nekkora-studio-014`, criada de `origin/main` |
| Nekkora-Extension-Chrome | `1fe52c2` (merge do PR #13, inclui `f82d58f`) | nenhum | não há causa para mudar o código da extensão (ver D-S014-07) |
| Nekkora-RPA-Framework | `ac8133c` (merge do PR #2, `nekkora_rpa` 0.2.0) | nenhum | sem lacuna demonstrada que exija evolução (ver D-S014-08) |

A STUDIO-013 já entrou nas `main` (PDD v3/v4/v5, consentimento por site, "Capturar tela" no Recorder). Não há base
divergente, conflitos nem trabalho a reverter.

**Prompt-Mestre:** só existe `Nekkora-AI-Engine/NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md` (v0.3; SHA-256 no
`Nekkora-AI-Engine/prompts.json`). Não havia revisão mais nova; o arquivo não foi alterado.

**Chave da Anthropic:** nenhuma neste computador (`ANTHROPIC_API_KEY` ausente no processo, no usuário e na máquina).
O Caso D fica em contrato e mock, declarado como **"Integração real Anthropic não homologada"**.

## Matriz

| Capacidade pedida | Estado | Onde está (caminho real) | Testes atuais | Lacuna |
|---|---|---|---|---|
| Validação estrutural (V0) | **EXISTE** | `nekkora-studio-validation/.../AutomationValidator.java`, `RuleSet.java` (`V1_0`, `V1_1` = `LATEST`), `ValidationReport.java`, `ValidationStatus.java` (`INCOMPLETE`, `NOT_FULLY_ASSESSABLE`, `NO_STATIC_BLOCKERS`) | `AutomationValidatorTest`, `ValidationContractTest`, `ValidationContract11Test`, `ValidationRules11Test`, desempenho | só a camada do modelo; nada de PDD, capacidade, código ou execução |
| "Validar automação" (Ctrl+Shift+V) | **EXISTE** (só V0) | `StudioActions.VALIDATE`, `StudioController.validateActive`, `ValidationService` (relatório em memória + atualidade), `ValidationPanel` (aba "Validação") | `ValidationUiTest` e afins no app | sem camadas, sem correção, sem pedido de ajuda, nada persistido |
| Gerador oficial | **PARCIAL** | `nekkora-studio-codegen/.../PythonGenerator.java`, `Emitter.java`, `CodegenConfig.java` (`codegen-config/1.0.0`), `OutputWriter.java` (`.novo`/`.diff`), manifesto `codegen-manifest/1.0.0` | `PythonGeneratorTest`, `FormCase`, `LabReplicaSample` | só lote tabular: exige `perRecord`, `input.format` xlsx/csv e colunas; `RECORD_SESSION_SHAPE` bloqueia processo sem planilha |
| UI do gerador | **EXISTE (legada)** | `nekkora-studio-app/.../codegen/CodegenPresenter.java` (diálogo com caminho do `*.codegen.json` e da saída) | homologação E2E-001 | é o questionário técnico que a missão manda tirar do fluxo principal |
| "Desenvolver automação" | **NÃO EXISTE** | — | — | ação, fluxo assíncrono, explicação, resumo |
| `PDD_READY` persistido | **NÃO EXISTE** | o PDD tem pendências de exportação (`Pendings.java`), estados de texto `DERIVED`/`NEEDS_REVIEW`/`REVIEWED` (`DocText.java`) e `processRevision` (`ProcessProjection.java`) | `PddDraftTest`, `PddPdfTest` | nenhum gate; o rascunho fica em `<dados>/pdd/<automationId>/rascunho.json`, fora do `.nekkora` |
| Especificação funcional (regras, entradas/saídas, efeitos) | **NÃO EXISTE** | o PDD não tem campos de regra, efeito externo, origem de dado nem conclusão observável | — | precisa de artefato estruturado, versionado e ligado ao PDD (pedido explícito da missão) |
| Variáveis declaradas | **EXISTE** | `nekkora-studio-domain/.../variable/*` (`VariableOrigin` INPUT/CONSTANT/STEP_OUTPUT/UNBOUND, `Sensitivity`), `WorkspaceService.declareVariable/updateVariable`, `setStepParameters` | `VariableRepresentationTest`, testes do domínio e do app | nenhuma derivação automática; o Recorder não grava valor de FILL (a etapa fica sem valor → `FILL_WITHOUT_VALUE` no gerador) |
| Histórico/desfazer | **EXISTE** | `EditHistory`, `WorkspaceService.edit(...)` (antes/depois por operação) | testes do app | falta uma operação composta "aplicar variáveis derivadas" |
| Debug guiado | **EXISTE** | `nekkora-studio-guided` (`GuidedPlan.planRevision`), `TargetDecision` (`CONFIRMED` exigido pelo gerador) | STUDIO-009 | — |
| Catálogo de capacidades do framework | **NÃO EXISTE** | o gerador conhece o framework só pelo texto que emite (`Emitter`) e pela constante `nekkora-rpa[excel]==0.2.0` | — | catálogo gerado por introspecção + checagem da versão instalada |
| Framework `nekkora_rpa` 0.2.0 | **EXISTE** | `Nekkora-RPA-Framework/src/nekkora_rpa/`: `Actions` (click, fill, wait_visible, read_text, read_text_matching, navigate, download, mark/replaced…), `RpaProcess`, `Engine`, `SingleInputProvider`, `LoginModule`, `BasePage`, `tabular` | 264 testes (Windows e Linux) | — (processo de entrada única já existe: `SingleInputProvider`) |
| iframe no runtime/gerador | **BLOQUEADO** | `PythonGenerator.target`: `TARGET_IN_FRAME_NOT_SUPPORTED` | `PythonGeneratorTest` | runtime 0.2.0 resolve só o documento principal; fica bloqueado (Caso C) |
| Ferramentas Python (V4) | **PARCIAL** | ruff/mypy rodaram só nos roteiros da E2E-001 (`docs/e2e-001/tools/g5_validar.py`); o Studio não roda nada | — | V4 dentro do Studio, em processo isolado, sem rede |
| Testes/execução (V5/V6) | **NÃO EXISTE** no Studio | a ação "Executar" é futura e desabilitada (`StudioActions.FUTURE`) | — | V5 = testes de contrato do projeto gerado; V6 nunca é presumida |
| Provedor de IA | **NÃO EXISTE** | — | — | interface substituível, Anthropic opcional, mock rotulado |
| Laboratório do Caso A (login → consultar → ler → sair, sem iframe) | **PARCIAL** | `test-lab/pages/studio009/consulta.html` (consulta + oráculo `/api/studio009/estado`) não tem login; `studio012/login.html` leva a um portal com iframes | — | página estática nova no laboratório, reaproveitando o oráculo da STUDIO-009 (D-S014-07) |
| Réplica tabular (Caso B) | **EXISTE** | `Nekkora-RPA-Framework/lab/static/desafio` + `docs/e2e-001/evidence/projeto/rpa-challenge.{nekkora,codegen.json}` | homologação E2E-001 | — |

## Impactos e conclusões

- Nenhum PR em andamento; a missão parte de `main` sem conflitos.
- O que só aparece no Prompt-Mestre e **não existe** no produto: `PDD_READY`, especificação funcional, derivação de
  variáveis, configuração derivada, catálogo, ação de um clique, camadas V1–V6, pedidos de ajuda, provedor de IA.
- O gerador precisa de uma **versão nova** do contrato de configuração (`codegen-config/1.1.0`, execução única) sem
  mudar a 1.0.0 (o caminho tabular da E2E-001 continua byte a byte).
