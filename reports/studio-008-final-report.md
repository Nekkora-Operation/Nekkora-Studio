# NEKKORA-STUDIO-008 — Relatório final

**Semantic Variables & Data Flow Engine.** Branch `feat/nekkora-studio-008`, base `main` em `beae03f`. PR aberto
**sem merge** (o merge é da usuária).

Resumo: o Studio passa a ter variáveis DECLARADAS por automação (nome, tipo, significado e quem o sustenta, escopo,
origem, sensibilidade, restrições — nunca valores de execução), uso DERIVADO pelo nome exato, produtora só DECLARADA,
validação estática do fluxo de dados (regras 1.1.0), formato `.nekkora` 1.1.0 com migração confirmada e cópia do
original, contrato semântico 1.0.0 determinístico e sem segredos, e a interface "Variáveis e dependências". Nada é
executado; nada é comprovado em execução; nenhum estado "pronto para executar" ou "verificado por IA" existe.

Restrições respeitadas: sem merge pelo agente; sem dados reais (só fictícios); sem mudanças na extensão; sem geração
de robôs por IA; sem executar automações; STUDIO-009 não iniciada; credencial continua só apelido (nenhum cofre
acessado); nada reclassificado nem recuperado da política `nekkora-privacy/2`; goldens não regenerados; fixtures
1.0.0 preservadas byte a byte, exceto os 4 ajustes autorizados.

## 1. Baseline

`beae03f` (HEAD da `main`): Windows `./mvnw clean verify` 464/464; Linux `tools/golden-linux.sh verify` 463 + 1
pulado; golden 4/4.

## 2. Gate G0 — auditoria e autorização

`docs/studio-008/decisions.md`. A estratégia "pacote e modelo 1.1.0" contradizia o contrato 1.0.0 congelado (que
fixava 1.1.0 como "versão mais nova desconhecida"). A missão foi parada e a usuária **autorizou em 2026-10-16** os 4
ajustes, antes de qualquer código de produção:

| # | Artefato existente | Ajuste | Justificativa |
|---|---|---|---|
| 1 | `nekkora-project/1.0.0/fixtures/invalid/formato-mais-novo.json` | exemplo "mais novo" 1.1.0 → **1.2.0** (expectativa `NEWER_VERSION` mantida) | 1.1.0 passou a ser suportado |
| 2 | `ProjectValidationTest.s006049Versao` | caso `1.1.0 + campoDoFuturo` → **1.2.0** (mesma expectativa) | idem |
| 3 | `ValidationContractTest.schemaIgualAoCodigo` | schema 1.0.0 comparado com códigos/escopos/campos **das regras 1.0.0** (+ conferência de que os novos só foram acrescentados ao fim); teste novo para o schema 1.1.0 | catálogo único com códigos acrescentados; mesma força |
| 4 | `ValidationStudioTest.s007034a036` | variável usada sem declaração → **`VARIABLE_NOT_DECLARED`** | mudança intencional das regras 1.1.0 (o texto antigo dizia "o Studio ainda não tem catálogo") |

Nenhum outro teste existente foi alterado em expectativa. Mudanças técnicas sem efeito de expectativa:
`ProjectFormatContractTest.deterministic/resolve` passaram de `private` a pacote (reuso pelo teste 1.1.0).

## 3. Arquitetura

`docs/studio-008/architecture.md`. Catálogo dentro do agregado (`Automation`, incluído no `Snapshot`: Undo/Redo,
não salvo, bloqueio de gravação e recuperação sem código paralelo); `VariableRules` como regra única (criação, edição,
leitura); `DataFlowAnalysis` linear; módulo novo `nekkora-studio-semantics`. Nenhuma mudança em Bridge, Recorder,
extensão ou Native Host.

## 4. Gates

| Gate | Entrega | Onde |
|---|---|---|
| G0 | auditoria, decisões, autorização | `decisions.md` §1–3 |
| G1 | domínio: declaração, regras, operações atômicas (renomear, excluir com descarte confirmado), reconstrução | `domain/variable/*`, `Automation`, `AutomationRestoration`; `VariableDomainTest` |
| G2 | regras 1.1.0, análise de dados, relatório 1.1.0 | `validation-rules.md`; `ValidationRules11Test`, `ValidationContract11Test` |
| G3 | formato 1.1.0, leitor 1.0/1.1, migração com confirmação e cópia | `versioning-and-migration.md`; `ProjectFormat11ContractTest`, `VariableStudioTest` |
| G4 | contrato semântico 1.0.0 | `semantic-contract.md`; `SemanticContractTest` |
| G5 | interface | `ui-ux.md`; `VariablesUiTest`, `Variables150Test` |
| G6 | regressão, desempenho, homologação, documentação, PR | este relatório |

## 5. Formato e versionamento

- Escritor grava a **menor versão** que representa o conteúdo: sem variáveis → 1.0.0, com exatamente os mesmos
  documentos de antes (conferido entrada por entrada contra a fixture 1.0.0 congelada); com variáveis → 1.1.0
  (`variables.json`).
- Leitor estrito: 1.0.x proíbe `variables.json`; 1.1.x exige e não aceita catálogo vazio; modelo coerente com o
  formato; 1.2+ é `NEWER_VERSION`. Regras do domínio reaplicadas a cada variável lida.
- Contrato 1.1.0: schema, 2 fixtures válidas (projeto e recuperação, relidas e reescritas idênticas) e 17 inválidas.
- Migração: só ao gravar sobre o próprio arquivo 1.0; confirmação explícita; cópia conferida
  `<nome> (formato 1.0.0).nekkora` (` 2`, ` 3`… — nunca substitui); conflito se o original mudou; "Salvar como" em
  outro arquivo não migra; abrir/validar/fechar não altera um projeto 1.0.0.

## 6. Validação 1.1.0

19 códigos novos (10 na etapa, 9 na variável), escopo e campo `VARIABLE`, `variableId` no relatório 1.1.0.
`new AutomationValidator()` continua produzindo o relatório 1.0.0 idêntico à fixture congelada. Tabela completa em
`validation-rules.md`. O que o motor **não** comprova: que uma entrada existirá, que uma etapa produz a saída
declarada, que o valor respeita as restrições em execução — tudo isso fica como limitação (`NOT_FULLY_ASSESSABLE`),
nunca como garantia.

## 7. Contrato semântico

`nekkora-studio/semantic-contract` 1.0.0: variáveis, uso derivado, produtora (`DECLARED`, `STATICALLY_CONSISTENT`,
`INVALID`, sempre `UNVERIFIED_AT_RUNTIME`), etapas por identidade e tipo de ação, incertezas e a lista de exclusões.
Determinístico (fixture congelada), local, sem segredos, separado do relatório de validação, só leitura.

## 8. Privacidade

`privacy.md`. Nenhum valor de execução em lugar algum; valor fixo só para público/interno, recusado se parecer
sensível, com aviso "não criptografado"; logs só com códigos e contagens; relatório e contrato sem nomes de
etapas/sessões nem valores. Conferido por testes e na homologação (logs varridos).

## 9. Testes

Novos: `VariableDomainTest` (11), `ProjectFormat11ContractTest` (7), `ValidationRules11Test` (8),
`ValidationContract11Test` (4), `SemanticContractTest` (6), `VariableStudioTest` (3), `VariablesUiTest` (9),
`Variables150Test` (1, execução `scale-150`), `VariablesPerformanceTest` (1). Plano: `test-plan.md`.

### Regressão final (commit `6d24d51`)

| Ambiente | Comando | Resultado |
|---|---|---|
| Windows 11, Java 21.0.12 | `./mvnw clean verify` | **BUILD SUCCESS — 514 testes, 0 falhas, 0 pulados** (base: 464) |
| Linux (Temurin 21.0.12.1 fixado por digest, Docker, root) | `tools/golden-linux.sh verify` | **BUILD SUCCESS — 514 executados, 513 aprovados, 1 pulado** (`AtomicSaveTest`, permissão de arquivo sob root — o mesmo da base) |
| Golden screenshots | — | 4/4 nos dois ambientes; não regenerados |

## 10. Desempenho

`performance.md` (`VariablesPerformanceTest`, 10/100/1.000/5.000 etapas). Com 5.000 etapas e 1.000 variáveis:
validação 1.1 8,6 ms; análise de fluxo 1,1 ms; contrato semântico 17,9 ms; linhas do painel 5,9 ms; renomear uma
variável usada por 4.000 etapas 81,5 ms; desfazer 0,47 ms; salvar com releitura 287 ms; abrir 155 ms. Na
homologação real: aba Variáveis 0,67 s, validar 78 ms, renomear 7 ms na EDT.

## 11. Homologação real (Windows)

`homologation.md` e `docs/studio-008/evidence/`: **40 verificações, 0 falhas**, cenários 1–8 da missão (CPF sem
valor; uso em Preencher; fora de escopo e sem declaração; saída de etapa não comprovada; renomear com Ctrl+Z/Ctrl+Y;
salvar 1.1 com migração confirmada e cópia, reabrir e ler 1.0; projeto antigo não sobrescrito, não executado, sem
pedido de credenciais; quatro temas e 5.000 etapas). 24 capturas (só a janela do Studio/diálogo), log, métricas,
logs da aplicação e o contrato semântico do cenário.

Ocorrências: execuções anteriores foram interrompidas por janelas de outros programas (menu Iniciar, Nekkora Agent) e
teclas que não chegaram à JVM; numa delas, antes das travas, até três Ctrl+N podem ter ido para outra janela em
primeiro plano. O roteiro passou a nunca clicar com outro programa ativo e a sondar o teclado antes de cada atalho. A
execução registrada foi feita com a máquina livre (a pedido, com a concordância da usuária), sem repetição de atalho.
Capturas de falha das tentativas interrompidas foram apagadas e não entram nas evidências.

## 12. Defeitos encontrados e corrigidos durante a missão

| Defeito | Correção |
|---|---|
| Classe `SemanticAuthority` violava a regra de escopo "nenhuma classe *auth*" (`StudioScopeTest`) | renomeada para `SemanticStatus` (D-S008-09) |
| Item novo no menu Automação quebrava `ValidationStudioTest.s007048e049` (fora dos ajustes autorizados) | ação fora do menu (D-S008-10) |
| Botão novo na barra de ferramentas empurrava "Executar" para fora da janela a 1366 px (visto nas capturas) | ação movida para o painel de propriedades da automação (D-S008-10) |
| "Versão do modelo 1.1.0 (anterior)" no painel de propriedades | 1.0.0 e 1.1.0 são atuais (D-S008-17) |
| Teste "1.0.0 byte a byte" comparava o ZIP compactado e falhou no Linux (zlib diferente) | comparação por entrada descompactada (D-S008-12) |
| Rótulo "Dado pessoal" no texto de ajuda ≠ "Dados pessoais" do domínio | texto alinhado |

## 13. Critérios de aceite

| Critério | Situação |
|---|---|
| Modelo de variáveis no domínio, sem valores de execução | atendido |
| Uso derivado; produtor só declarado; sem inferência | atendido |
| Validator com regras 1.1.0 e códigos novos; relatório 1.0.0 intacto | atendido |
| `.nekkora` 1.1.0 + leitor 1.0.0; schema e fixtures 1.1.0; fixtures 1.0.0 preservadas | atendido (4 ajustes autorizados) |
| Migração com confirmação e cópia; projeto antigo nunca sobrescrito em silêncio | atendido |
| Contrato semântico determinístico, local, sem segredos, separado | atendido |
| UI com 4 temas, 150%, Undo/Redo, integração com o editor de etapa | atendido |
| Desempenho 10/100/1.000/5.000 | atendido |
| Regressão Windows e Linux | atendido |
| Homologação real com os 8 cenários | atendido (40/40) |
| Sem READY_FOR_EXECUTION / AI_VERIFIED | atendido (schemas recusam; testado) |
| PR aberto sem merge | ver descrição do PR |

## 14. Limitações e riscos residuais

`known-limitations.md`. Principais: nada é comprovado em execução; produtora só declarada; uma referência por etapa e
fluxo linear; valor fixo em texto claro (só não sensível); excluir/mover produtora não corrige a declaração (é
apontado); arquivos 1.1.0 não abrem em versões anteriores do Studio (há a cópia 1.0.0); contrato semântico não é
exportado pela interface; `Studio007Homologation` (histórico, fora do `verify`) espera as regras 1.0.0 para
variáveis (D-S008-16).

## 15. Preparação para as próximas missões

O contrato semântico e a `DataFlowAnalysis` são a base para consumo programático (por exemplo, uma futura geração ou
execução) sem expor valores. Qualquer evolução que comprove produtoras em execução deve gerar códigos e estados
novos — nunca reinterpretar `STEP_OUTPUT_NOT_PROVEN` ou `UNVERIFIED_AT_RUNTIME`. STUDIO-009 não foi iniciada.
