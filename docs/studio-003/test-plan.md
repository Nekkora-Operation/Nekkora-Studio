# STUDIO-003 — Plano e matriz de testes

Níveis de evidência:

- **N**: Node na extensão (`interaction-normalizer.test.js`, `studio-steps.test.js`).
- **J**: Java do Native Host (`RecorderCommandsTest`, `StepPrivacyTest`, `BridgeContractTest`).
- **U**: unidade e aplicação do Studio (`RecordedStepsTest`, `RecorderStepsTest`, `RecorderStepsStudioTest`,
  `RecordedStepDomainTest`, `StudioUiTest`).
- **C**: cruzado Studio ⇄ Native Host real (`RealNativeHostBridgeTest.etapasPeloHostReal`).
- **B**: Chrome real com o Studio de laboratório (`Studio003InteractionsIT`; eventos confiáveis via WebDriver/CDP;
  todo evento validado contra o contrato).
- **H**: homologação real no Windows (`Studio003Homologation`: Studio real, Chrome for Testing, host instalado,
  mouse e teclado do sistema operacional).

| Caso | Verificação | Evidências | Estado |
|---|---|---|---|
| S003-001 | Início de gravação ativa o sensor | N "S003-001/046"; B `start()` (sensor ativo em todos os cenários); H | ✅ |
| S003-002 | Finalização remove os listeners | N (stop no fim); B `s003Login` (sensor inativo e 0 ouvintes depois de finalizar), `s003Pause`, `s003Cancel`, `s003StudioLost` | ✅ |
| S003-003 | CAPTURE_ONLY não executa clique | N R11; B `s003CaptureOnly` (contador continua 0; mouse e Enter); H cenário 3 | ✅ |
| S003-004 | CAPTURE_AND_INTERACT executa clique normalmente | B (login navega, contador incrementa, SPA filtra); H "a página seguiu para a consulta" | ✅ |
| S003-005 | Alternância entre modos | N "troca de modo consolida antes"; N `studio-steps` S003-005; B `s003CaptureOnly`; H cenário 3 | ✅ |
| S003-006 | Clique em botão | N R1; U nomes; B `s003Login`; H | ✅ |
| S003-007 | Clique em link | N R1; B `s003Consulta` (LINK "Clientes"); H cenário 2 | ✅ |
| S003-008 | Elemento com role=button | B `s003Consulta` (`span#exportar` → BUTTON) | ✅ |
| S003-009 | Clique em filho de botão identifica o botão | B `s003PermissionRevoked` (ícone de "Ajuda") e `s003FramesShadow` (ícone no shadow); H cenário 4 | ✅ |
| S003-010 | Preenchimento de input | N R3; B; H | ✅ |
| S003-011 | Preenchimento de textarea | B `s003Consulta` (`obs`); B `s003Performance` (300 caracteres) | ✅ |
| S003-012 | Contenteditable | B `s003Consulta` (`nota`); privacidade do conteúdo corrigida na captura técnica | ✅ |
| S003-013 | Senha sem valor literal | N; J; U; B `assertNoSecretAnywhere`; H (domínio, tela, armazenamento e logs) | ✅ |
| S003-014 | Digitação consolidada em uma etapa | N R3; B (`events ≥ caracteres`); B 300 caracteres → 1 FILL | ✅ |
| S003-015 | Clique de foco não duplicado com FILL | N R2; B (nenhum CLICK no campo) | ✅ |
| S003-016 | Select e option formam uma etapa | N R6; U `RecorderStepsStudioTest.s003SelectEEnter`; B; H cenário 2 | ✅ |
| S003-017 | Checkbox marcado | N R5; B (marcado pela label) | ✅ |
| S003-018 | Checkbox desmarcado | N R5; B (desmarcado no controle) | ✅ |
| S003-019 | Radio button | N R5; U nome "Marcar opção…"; B (`pf`) | ✅ |
| S003-020 | Submit sem duplicação | N R7 e R4 (envio implícito); B (FORM_SUBMITTED no CLICK e no PRESS_KEY, um único CLICK); H | ✅ |
| S003-021 | Navegação tradicional | N (navegação consolida; perda declarada); B login → consulta; H | ✅ |
| S003-022 | Navegação SPA | N (contexto posterior); B `s003Spa`; H cenário 5 | ✅ |
| S003-023 | Elemento em iframe | B `s003FramesShadow` (trajetória com IFRAME); H cenário 4 | ✅ |
| S003-024 | Elemento em Shadow DOM | B (trajetória com SHADOW_ROOT); H | ✅ |
| S003-025 | Iframe sem permissão | B (nada do iframe de site-x; cobertura PARTIAL; `actionCapture` PARTIAL); H | ✅ |
| S003-026 | Localizador validado | J `RecorderCommandsTest` (único + verificação ao vivo); N (LIVE); U (preferencial só se validado); B; H | ✅ |
| S003-027 | Elemento ambíguo | N R10; J (ids duplicados sem identidade inventada); U; B (`detalhes` → UNKNOWN); H | ✅ |
| S003-028 | Ordem entre ações | N; U (`StepInbox`, fora de ordem → em ordem); B (15 etapas em ordem); H | ✅ |
| S003-029 | Deduplicação de eventos | N; U (reemissão não duplica); B `s003Reconnect` | ✅ |
| S003-030 | Retransmissão após reconexão | N (sync); U (lacuna detectada pela renovação); B `s003Reconnect`; H cenário 7 | ✅ |
| S003-031 | Eventos após pausa são ignorados | N (STALE_EPOCH); B `s003Pause` | ✅ |
| S003-032 | Retomada sem duplicação | N; B `s003Pause` | ✅ |
| S003-033 | Mudança de sessão lógica | N (fronteira); U `RecorderStepsTest`, `RecorderStepsStudioTest`; B `s003Sessions`; H cenário 6 | ✅ |
| S003-034 | StepIntake recebe etapas RECORDED | U `RecorderStepsStudioTest`; H | ✅ |
| S003-035 | Interface atualiza sem bloquear a EDT | U (`RepaintManager` verificador: 0 violações com eventos de outra thread) | ✅ |
| S003-036 | IDs internos não aparecem | U (padrão de IDs na tela e no Floating Recorder); H | ✅ |
| S003-037 | Valores sensíveis não vazam | N; J `StepPrivacyTest`; C (host real); B; H | ✅ |
| S003-038 | Cancelamento preserva etapas anteriores | U domínio `discardRecording`, `RecorderStepsStudioTest`; B `s003Cancel`; H | ✅ |
| S003-039 | Perda do Studio | B `s003StudioLost` (concessão expira, sensores param); S002-018 preservado | ✅ |
| S003-040 | Permissão revogada | B `s003PermissionRevoked`; H cenário 7 | ✅ |
| S003-041 | Finalização com eventos pendentes | N (espera as confirmações; STEPS_UNCONFIRMED; retenção para sync) | ✅ |
| S003-042 | Fila cheia / backpressure | N (100 pendentes → DEGRADED STEP_BACKLOG_FULL, nada descartado, retomada depois do ACK) | ✅ |
| S003-043 | Eventos programáticos não são confundidos com ações humanas | N (UNTRUSTED, PROGRAMMATIC_CHANGE); B (`automatico`); H | ✅ |
| S003-044 | Inspeção manual continua independente | B `s003Inspector` (SUSPENDED durante, INSPECT depois, seleção funciona) | ✅ |
| S003-045 | Nenhum PageMap completo trafega por evento | N (evento < 4 KB, sem nós); J fixtures inválidas (`pageMap`, `nodes`); B (< 4 KB) | ✅ |
| S003-046 | Regressão dos contratos anteriores | J (fixtures da STUDIO-002 inalteradas e válidas); B `s003Compat`; U (extensão anterior → gravação técnica); suítes completas | ✅ |

## Defeitos encontrados por testes reais (cada um com regressão)

| Defeito | Encontrado por | Regressão |
|---|---|---|
| `.click()` programático num checkbox virava CHECK (o Chrome entrega o `change` como confiável) | B `s003Consulta` | N "change sem ativação"; B |
| Enter num campo: o clique sintético do envio implícito virava CLICK e "roubava" o `submit` | B `s003Consulta` | N "envio implícito por Enter"; B |
| Texto digitado em `contenteditable` aparecia no delta da captura técnica e no log do service worker | B `assertNoSecretAnywhere` | B (privacidade na consulta) |
| Nova base técnica depois de navegar tornava a captura de ações PARTIAL | B `s003Login` | N "nova base técnica não torna a captura parcial" |
| Contagem do limite de pendentes ignorava as etapas ainda em correlação | N (backpressure) | N |
| Studio só considerava etapas ativas depois da primeira etapa (rota não enviada); Studio novo + extensão antiga teria o início recusado | U `RecorderStepsTest` | U "sem destino de etapas", `FakeBrowser.legacy` |
| Contexto "no formulário formulário" | H (captura) | U `StudioUiTest` |

## Testes da STUDIO-002 com premissa alterada (nenhum enfraquecido)

| Teste | Antes | Agora |
|---|---|---|
| `RecorderStudioTest.gravarSoComConfirmacao…` | o painel dizia "Captura de ações: próxima versão" e "Configuração para a próxima etapa" | o navegador simulado é da STUDIO-002 (sem `steps/1`): o painel diz "Captura de ações indisponível nesta gravação" e explica o que cada modo faz |
| `StudioScopeTest.semFuncionalidadesFicticias` | uma exceção exata para "Gravando" (botão) | + uma exceção exata (`RecorderText`) para o rótulo do Recorder com RECORDING confirmado **e** captura de ações ativa. "etapas gravadas" e "cliques capturados" continuam proibidos |

## Revisão técnica (PRs #4 e #5)

| Achado | Testes (todos falharam antes da correção, exceto o controle) | Situação |
|---|---|---|
| P1 extensão — modo confirmado sem estar ativo | N `REV-P1a` × 7 (modo recusado, `{active:false}`, sem restauração, início recusado e inativo, iframe; mais 1 controle) | ✅ |
| P1 extensão — evento atrasado na sessão errada | N `REV-P1b` × 2 (espera de 1,5 s esgotada; várias trocas) | ✅ |
| P1 Studio — sessão alterada antes da confirmação | U `RecorderStepsTest.revTrocaDeSessao…` × 4 (confirmada com atrasado, recusada, tempo esgotado, desconexão); app `revTrocaDeSessaoPendenteNaTela` | ✅ |
| Complemento no Studio — resposta `SENSOR_NOT_CONFIRMED` | U `RecorderStepsTest.revModoSemConfirmacao…` × 2, `revRetomadaSemConfirmacaoDoSensorNaoVoltaAPausaFalsa` (3 falharam antes) | ✅ |
| P2 extensão — limite de 100 não rígido | N `REV-P2` (130 interações seguidas: 100 admitidas, 30 recusadas como perda declarada) | ✅ |
