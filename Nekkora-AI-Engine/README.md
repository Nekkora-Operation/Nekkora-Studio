# Nekkora AI Engine

Módulo `nekkora-studio-ai-engine` do Nekkora Studio (STUDIO-014): desenvolvimento assistido **sem questionário** e
**garantia de qualidade verificável**. Nada aqui executa ações em sistemas; a IA é opcional e só propõe.

## Prompt-Mestre em uso (lugar único)

| Arquivo | Papel |
|---|---|
| [`NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md`](NEKKORA-PROMPT-MESTRE-DESENVOLVIMENTO-RPA-v003.md) | Prompt-Mestre v0.3 (comportamento ALVO do agente; não prova que uma capacidade exista no produto). |
| [`prompts.json`](prompts.json) | Índice: versão vigente (`current`) e o SHA-256 de cada arquivo (quebras de linha normalizadas para LF). |

O jar leva estes mesmos arquivos (o Maven os copia sem edição). `PromptRepository` recusa um prompt cujo hash não confere.
Os relatórios identificam o prompt como `v003 (0.3, sha256:…)`. Para publicar uma versão nova: acrescente o arquivo,
registre-o em `prompts.json` (sem apagar as anteriores) e mude `current`.

## Capacidades reais do framework

| Arquivo | Papel |
|---|---|
| [`catalog/nekkora-rpa-0.2.0.json`](catalog/nekkora-rpa-0.2.0.json) | Catálogo GERADO do `nekkora_rpa` instalado: import, assinatura, resumo do código, arquivo e linha no pacote, seção da documentação, efeito e limitações. |
| [`tools/gerar_catalogo.py`](tools/gerar_catalogo.py) | Gera/confere o catálogo: `<python-com-nekkora_rpa> tools/gerar_catalogo.py --framework-repo <Nekkora-RPA-Framework> [--check]`. |
| [`tools/capacidades-curadoria.json`](tools/capacidades-curadoria.json) | O que a introspecção não sabe: finalidade, efeito, limitações, seção da documentação. |
| `src/main/resources/nekkora/ai/python/sonda_framework.py` | A mesma introspecção, usada pelo Studio para comparar o catálogo com o Python escolhido. |
| `src/main/resources/nekkora/ai/python/contrato_projeto.py` | Testes de contrato do projeto gerado (V5). |

## Componentes (`com.nekkora.studio.ai`)

| Pacote | Responsabilidade |
|---|---|
| `prompt` | `PromptRepository` |
| `capability` | `FrameworkCapabilityCatalog` (recusa capacidade inexistente: `UNSUPPORTED_CAPABILITY`), `CapabilityCheck` (V2) |
| `context` | `ProjectContext` — fotografia tipada e minimizada (sem valores, localizadores, URLs completas, credenciais, pixels) |
| `spec` | `FunctionalSpec` (decisões versionadas por StepId), `SpecResolver`, `EffectVocabulary` |
| `gate` | `PddGate` (`PDD_READY`/`PDD_BLOCKED`) e `GateRecord` (revisões exatas) |
| `plan` | `VariableDeriver`, `DevelopmentPlanner` (determinístico), `DevelopmentPlan` |
| `generation` | `GenerationOrchestrator` (fases, gerador oficial, gravação protegida) |
| `pycheck` | `PythonChecks` (V4/V5 reais, isolados, sem rede) |
| `quality` | `AutomationQualityGate` (V0–V6), `QualityReport`, `QualityIssue`, `AssistanceRequest` |
| `provider` | `AIProvider`, `AnthropicProvider` (opcional), `AiAssist`, `PlanVerifier`, `AIProviders` |
| `store` | `DevelopmentStore` (`<dados>/desenvolvimento/<automationId>/`) |

Documentação da missão: [`docs/studio-014/README.md`](../docs/studio-014/README.md) ·
[decisões](../docs/studio-014/decisions.md) · [segurança e privacidade](../docs/studio-014/security-and-privacy.md) ·
[limitações](../docs/studio-014/known-limitations.md).
