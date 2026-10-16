# STUDIO-003 — Privacidade no Studio

> **CHROME-SECURITY-001:** a política reconhecida passou a ser `nekkora-privacy/2`, e o Studio a **reaplica** a tudo o que recebe (`CapturePrivacy`, `RecordedStep.guard`). Referências marcadas com a `nekkora-privacy/1` não são exibidas como seguras. Ver o relatório `reports/chrome-security-001-final-report.md` no repositório da extensão.

A sanitização principal pertence ao produtor: a extensão, em três barreiras (content script, service worker e
Native Host), descritas em `Nekkora-Extension-Chrome/docs/studio-003/privacy.md`. O Studio acrescenta as suas.

| Onde | Regra |
|---|---|
| `RecordedStep.parse` | recusa a etapa com `valueCaptured != false`: valor digitado nunca entra |
| `RecordedStepDrafts` | nomes, títulos e opções só entram se `DisplaySafety.isSafeToDisplay`. Opção com dado pessoal: a etapa fica sem a opção ("Selecionar opção no campo Contato"). Campo sensível: `htmlId` e `htmlName` descartados |
| Domínio | FILL gravado nasce sem valor. Literal em elemento sensível é recusado (`LITERAL_ON_SENSITIVE_TARGET`). `LiteralValue` com aparência de dado sensível é recusado |
| `ElementReference` | `id` e `name` HTML só são apresentáveis com a política `nekkora-privacy/1` declarada e texto seguro; nunca para elemento sensível |
| Interface | `CorrelationKeys` nunca são exibidas (`toString` não revela valores). Os textos passam por `StudioText.technical` e `DisplaySafety` |
| Logs | mensagens de log do Recorder e da `WorkspaceStepSink` não incluem o conteúdo das etapas, só o tipo do problema |

## Identificadores que nunca aparecem

`recordingId`, `routeRef`, `eventId`, `targetId`, `nodeId`, `captureId`, `documentId`, `pageSessionId`,
`instanceId` e `operationId`.

Verificação:

- `RecorderStepsStudioTest`: o padrão de identificadores internos é aplicado à tela e ao Floating Recorder.
- `RecordedStepsTest`: nomes e descrições não contêm identificadores.
- Homologação real: os textos da janela inteira foram conferidos.

## Senhas

A homologação real digitou um usuário e uma senha fictícios no Chrome. Os dois foram procurados em:

- domínio (nomes, descrições, valores, resultado observado, nome acessível e id dos elementos);
- textos da janela do Studio;
- `chrome.storage.session` da extensão;
- logs do Native Host.

Nenhum dos dois foi encontrado (ver `reports/studio-003-final-report.md`).
