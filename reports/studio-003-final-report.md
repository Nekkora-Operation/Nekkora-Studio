# NEKKORA-STUDIO-003 — Relatório final (Nekkora Studio)

**Captura funcional de interações.** Branch `feat/nekkora-studio-003`. O lado da extensão está no PR do repositório
Nekkora-Extension-Chrome (`feat/nekkora-studio-003-interactions`), com o relatório
`reports/studio-003-interactions-final-report.md`. Os dois PRs são separados e **nenhum merge foi feito**.

## 1. Baseline inicial

| Item | Valor |
|---|---|
| Studio | `d64f8a0` (main; merge do PR #3 pela usuária) |
| Extensão | `1bf84c5` (main; merge do PR #4) |
| `./mvnw clean verify` (Windows) | **218** testes, 0 falhas |
| `tools/golden-linux.sh verify` (Temurin 21.0.12.1+1 fixado por digest) | **218** testes, 0 falhas, 0 ignorados |
| Extensão (`scripts/run-tests.ps1 -Suite all`) | Java 224 · Node 44 · navegador 197, 0 falhas |

## 2. Arquitetura implementada

Detalhes e decisões D-STUDIO-003-01 a 14 em `docs/studio-003/architecture.md`.

- Extensão: sensor (content script) → normalizador determinístico → pipeline (sequência, correlação pelo motor Java,
  outbox, ACK) → `studio.recording.step`.
- Native Host: privacidade reaplicada e schema estrito.
- Studio:
  - `RecorderController`, com `StepInbox` (ordem e deduplicação), rota → sessão lógica e `RecordedStepDrafts`;
  - `WorkspaceStepSink` → `StepIntake` na EDT;
  - ACK só depois do domínio.

O Studio **não** analisa DOM nem recalcula localizadores.

## 3. Alterações nos contratos

`nekkora-bridge/1`, somente acréscimos (`Nekkora-Extension-Chrome/docs/studio-003/bridge-contract.md`):

- capacidade `steps/1` e rota inicial no `start`;
- comandos `studio.recording.steps.ack`, `steps.sync` e `route`;
- evento `studio.recording.step` estrito, sem campo de valor;
- `actionCapture` como `NOT_AVAILABLE`, `AVAILABLE` ou `PARTIAL`;
- `steps` em Recording, Renew e Summary.

A cópia do contrato em `nekkora-studio-bridge/src/test/resources/contracts` é idêntica à da extensão (42 fixtures
válidas e 24 inválidas; as da STUDIO-002 não mudaram). `BridgeProtocol` foi estendido, e o
`BridgeContractFixturesTest` confere o vocabulário.

## 4. Sensores de interação

Ficam na extensão (`docs/studio-003/interaction-recorder.md` lá):

- ouvintes passivos em CAPTURE_AND_INTERACT;
- interceptação em CAPTURE_ONLY (Enter e Espaço como seleção; Tab e atalhos livres);
- alvo funcional pelo caminho composto (Shadow DOM, label → controle, ícone → botão);
- prioridade explícita sobre o Inspector (SUSPENDED e depois de volta).

## 5. Regras de normalização

R1 a R11, determinísticas, com 24 testes de normalização na extensão. Nomes em português no Studio
(`docs/studio-003/interaction-normalization.md`).

## 6. Integração com o domínio

`docs/studio-003/step-intake.md` e `recorded-step-model.md`:

- `StepIntake` na EDT;
- procedência RECORDED com `RecordingObservation` (modo, proposta × interação observada, resultado de negócio NÃO
  validado, evidências e limitações);
- `PRESS_KEY` acrescentado ao catálogo (justificado);
- radio = CHECK;
- `Automation.discardRecording` no cancelamento.

## 7. Privacidade

`docs/studio-003/privacy.md`. Valor digitado nunca entra:

- o contrato não tem o campo, e o parse recusa `valueCaptured != false`;
- o domínio recusa literal em campo sensível;
- nomes e opções passam por `DisplaySafety`;
- id e name de campo sensível são descartados;
- identificadores internos não aparecem na interface.

## 8. Tratamento de duplicações

`StepInbox` descarta sequência ou `eventId` já processados (reemissão com a nota `RESENT`, reconexão). A rota e a
operação são idempotentes por `operationId`. Etapas de outra gravação ou de gravação encerrada são ignoradas.

## 9. Tratamento de eventos perdidos

- Lacuna na sequência: as etapas fora de ordem ficam retidas e o Studio pede `steps.sync`.
- `renew`, `status` e o ACK informam `lastSequence`; se o Studio recebeu menos, também pede `steps.sync`.
- Perda declarada pela extensão (documento substituído com preenchimento aberto): aparece no Floating Recorder e no
  resumo (`INTERACTIONS_LOST`). Nada é inventado.
- Sem persistência definitiva, não há promessa de recuperar etapas depois do encerramento abrupto do Studio
  (`known-limitations.md`).

## 10. Testes

| Módulo | Antes | Depois | Falhas |
|---|---|---|---|
| design-tokens | 24 | 24 | 0 |
| swing-ui (4 golden, **não regenerados**) | 45 | 45 | 0 |
| showcase | 5 | 5 | 0 |
| studio-domain | 47 | **52** (+5 `RecordedStepDomainTest`) | 0 |
| studio-bridge | 22 | 22 (contrato copiado; vocabulário estendido) | 0 |
| studio-recorder | 22 | **37** (+7 `RecordedStepsTest`, +7 `RecorderStepsTest`, +1 cruzado `etapasPeloHostReal`) | 0 |
| studio-ui | 14 | 14 (asserção de contexto acrescentada) | 0 |
| studio-app (inclui `Scale150Test` em JVM própria) | 39 | **44** (+5 `RecorderStepsStudioTest`) | 0 |
| **Total Windows** (`./mvnw clean verify`) | **218** | **243** | **0** |
| **Total Linux** (`tools/golden-linux.sh verify`, extensão `8fb730e` ao lado) | 218 | **243**, 0 ignorados | **0** |

Matriz S003-001 a S003-046 completa: `docs/studio-003/test-plan.md`.

## 11. Evidências reais

Homologação real no Windows 11 (`Studio003Homologation`):

- Studio de verdade;
- Chrome for Testing 155;
- Native Host instalado por `install-host.ps1`;
- **mouse e teclado do sistema operacional** nas páginas, por meio do Robot.

Resultado: **29 verificações, 0 falhas**. Log em `reports/studio-003/evidence/S003-homologacao-log.txt`.

Automação produzida pela gravação real:

```
Consulta de clientes
├── Autenticação: Preencher campo Usuário · Preencher campo Senha · Clicar no botão Entrar
├── Consulta:     Clicar no link Clientes · Selecionar São Paulo no campo Estado · Preencher campo Pesquisa ·
│                 Clicar no botão Pesquisar · Clicar no botão Adicionar linha (proposta, não executada) ·
│                 Clicar no botão Preencher automaticamente · Interação não classificada em <span>
└── Componentes:  Preencher campo Nome (iframe) · Clicar no botão Salvar cadastro (iframe) ·
                  Preencher campo Usuário do componente (shadow) · Clicar no botão → Entrar (shadow) ·
                  Clicar no botão Clientes · Preencher campo Filtro · Clicar no botão Filtrar (SPA) ·
                  Clicar no botão Filtrar (depois da reconexão, sem duplicar)
```

| Captura | O que mostra |
|---|---|
| `S003-01-login-etapas.png` | 3 etapas do login na sessão Autenticação; Floating Recorder "3 registradas"; a página seguiu para a consulta |
| `S003-02-consulta-detalhes-select.png` | detalhes de "Selecionar São Paulo no campo Estado" (opção como parâmetro) |
| `S003-03-capture-only-proposta.png` | CAPTURE_ONLY: destaque "Recorder · button", "0 linha(s)" (não executou), etapa proposta |
| `S003-04-resumo-finalizado.png` | "Gravação concluída", 10 etapas registradas, captura de ações completa, PageMap não exportado |
| `S003-05-iframe-trajetoria.png` | etapa do iframe com trajetória; cobertura parcial (iframe sem permissão) |
| `S003-06-desconectado.png` | conexão perdida: "Captura interrompida", nada dado como finalizado |
| `S003-07-aba-fechada.png` | aba fechada → estado real |
| `S003-08-permissao-revogada.png` | permissão revogada → captura interrompida |
| `S003-09-detalhes-senha-sem-valor.png` | "Preencher campo Senha": elemento sensível, sem valor; grupo Gravação (modo, interação, resultado não validado) |
| `S003-10-tema-*.png` | Floating Recorder nos 4 temas |
| `S003-11-automacao-final.png` | a automação completa |

As capturas foram recortadas às janelas do Studio e do Chrome, e o gerenciador de senhas do Chrome foi desligado no
perfil temporário. Os dados digitados são fictícios.

## 12. Desempenho

Do Chrome real, na extensão (`S003-performance.json`):

| Medida | Resultado |
|---|---|
| Correlação do elemento por etapa | p50 11 ms, p95 12 ms, máximo 14 ms (21 amostras) |
| Clique até a etapa no Studio de laboratório | p50 813 ms, p95 826 ms. Inclui o clique do WebDriver e a janela de 400 ms que espera o contexto posterior (submit, navegação) |
| Digitação de 300 caracteres | **1** etapa (301 eventos consolidados) |
| Tamanho do evento | cerca de 2,4 KB (nenhum PageMap) |
| Descartes | contados por motivo no resumo |

A tela atualiza só a sessão afetada (atualização incremental da STUDIO-002), sem reconstruir todas as sessões. A
entrega ao domínio é uma operação na EDT por etapa. Medição no Studio: os testes de aplicação entregam etapas vindas
de outra thread com 0 violações de EDT.

## 13. Falhas encontradas e 14. Correções realizadas

| Falha (encontrada por teste real) | Correção |
|---|---|
| Programático `.click()` em checkbox virava CHECK (o Chrome marca o `change` como confiável) | ativação real obrigatória (`PROGRAMMATIC_CHANGE`) — extensão |
| Enter num campo: o clique sintético do envio implícito virava CLICK | absorvido; o submit é evidência do PRESS_KEY — extensão |
| Texto de `contenteditable` nos deltas da captura técnica e no log do service worker | conteúdo editável tratado como valor de campo — extensão |
| Navegação marcava a captura de ações como PARTIAL (`RECOVERIES_OCCURRED`) | só limitações de ações contam — extensão |
| Limite de pendentes ignorava etapas em correlação | conta produzidas e não confirmadas — extensão |
| Studio só ativava o roteamento depois da primeira etapa; Studio novo + extensão antiga teria o início recusado | o anúncio da extensão ativa o roteamento; início refeito sem `steps/1` diante de BAD_REQUEST — Studio |
| Literais com "etapa gravada" em mensagens de log e exceção (barrados pelo `StudioScopeTest`) | mensagens reescritas — Studio |
| "no formulário formulário" | relação sem repetição — Studio |
| Roteiro de homologação: a janela do Studio cobria parte do Chrome; o clique caía no Studio | o roteiro dá o foco ao Chrome pela barra de endereço antes de clicar na página |

## 15. Limitações e 16. Pendências

`docs/studio-003/known-limitations.md`:

- sem persistência definitiva;
- resultado de negócio não validado (STUDIO-005);
- edição avançada na STUDIO-004;
- FILL sem valor (ValueSource futuro);
- ordem entre iframes de outra origem pela recepção;
- o título da aba no Floating Recorder é o do início da gravação;
- homologação com janela só no Windows.

Nenhuma pendência bloqueante.

## 17. Preparação para a STUDIO-004

- Cada etapa gravada já tem `ElementReference` completo (localizadores com validação, trajetória, contexto,
  obstáculos), procedência com `RecordingObservation` e chaves de correlação (`TARGET_ID` para reencontro). A edição
  avançada pode exibir e ajustar sem recalcular nada.
- `ValueSource` está pronto para associar variáveis e credenciais às etapas FILL gravadas (nascem sem valor).
- `discardRecording` e a numeração recalculada mostram que operações em lote sobre etapas gravadas podem ser
  transacionais.
- Os dados para a STUDIO-005 (contexto e resultado) já existem: evidências (`FORM_SUBMITTED`,
  `NAVIGATION_FOLLOWED`), contexto posterior e `observedOutcome`.
