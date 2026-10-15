# STUDIO-002 — Arquitetura: Recorder Controller, Floating Recorder e Studio Bridge

## Visão geral

```
┌──────────────────────────── NEKKORA STUDIO (este repositório) ─────────────────────────────┐
│  nekkora-studio-domain   Automation → LogicalSession → AutomationStep   (intocado)          │
│  nekkora-studio-bridge   BridgeServer · BridgeDirectory · BridgeAuth · BridgeFrames · Json  │  Java puro
│  nekkora-studio-recorder RecorderController · RecorderState/Transitions · TechnicalRecording│  Java puro
│  nekkora-studio-ui       FloatingRecorderPanel/Window · RecordingSetupPanel · RecorderText  │  Swing + DS
│  nekkora-studio-app      RecorderPresenter (EDT) · Swing{RecorderPrompts,RecorderWindowHost}│
└──────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                       │ nekkora-bridge/1 — Unix Domain Socket (TCP 127.0.0.1 como alternativa)
                                       │ descoberta restrita ao usuário · HMAC mútuo · batimentos
┌──────────────────────────────────────▼─────────────── Nekkora-Extension-Chrome ────────────┐
│  Native Host (Java 21, iniciado pelo Chrome): leitor · MOTOR · CONTROLE · escritor único   │
│     StudioLink (cliente do Bridge) — lista explícita, contrato validado nos dois sentidos   │
└──────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                       │ nekkora-nm/1 (Native Messaging) — acréscimos studio.*
┌──────────────────────────────────────▼─────────────────────────────────────────────────────┐
│  Service worker: studio-link (opt-in no painel) · studio-recording (estado autoritativo,   │
│  consumidor "studio-recorder:<id>", concessão, idempotência) → CaptureSession → página      │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Quem decide o quê.** A extensão é a fonte da verdade da gravação técnica: consumidor, estado, cobertura e concessão.
O Studio controla e apresenta, e só mostra um estado depois que a extensão o confirma. O host faz a ponte e valida: não
guarda estado de gravação.

## Camadas e dependências

| Módulo | Depende de | Não pode depender de |
|---|---|---|
| `nekkora-studio-bridge` | — (Java puro, sem bibliotecas) | domínio, Swing, design system |
| `nekkora-studio-recorder` | domain, bridge | Swing, design system, ui/app |
| `nekkora-studio-ui` | domain, recorder, swing-ui | app |
| `nekkora-studio-app` | todos acima | — |

As regras são verificadas por `StudioScopeTest.camadas`, estendido na STUDIO-002 para os módulos novos.

## Decisões arquiteturais

| Decisão | Definição | Onde |
|---|---|---|
| D-STUDIO-002-01 | Bridge local autenticado | `BridgeServer` + `BridgeAuth`; `docs/studio-002/security.md` |
| D-STUDIO-002-02 | Consumidor independente do Recorder | extensão: `studio-recorder:<id>` na CaptureSession |
| D-STUDIO-002-03 | Finalização separada da exportação | `summary.pageMapExported = false` (constante no contrato) |
| D-STUDIO-002-04 | Unix Domain Socket preferencial, TCP local como alternativa controlada | validado no Windows 11 (ver `security.md`); TCP só para caminho maior que 100 caracteres ou UDS indisponível |
| D-STUDIO-002-05 | Native Host bidirecional sem bloqueio | extensão: `HostRuntime`, `ChromeOutboundWriter` |
| D-STUDIO-002-06 | Protocolo `nekkora-bridge/1` versionado | `contracts/studio-bridge.schema.json` (extensão) + fixtures copiadas aqui |
| D-STUDIO-002-07 | Batimento e concessão temporária | batimentos de 5 s; concessão de 30 s renovada a cada 10 s |
| D-STUDIO-002-08 | Permissões controladas pela extensão | o Studio orienta; a autorização é dada na extensão ou no Chrome |
| D-STUDIO-002-09 | Estados confirmados pelo navegador | estados "-ING"; `RecorderTransitions` |
| D-STUDIO-002-10 | Swing atualizado só na EDT | `RecorderPresenter` (`invokeLater`); `S002-023` |
| D-STUDIO-002-11 | Gravação técnica distinta da gravação de ações | nenhuma etapa criada; avisos na interface |
| D-STUDIO-002-12 | Preservação da arquitetura e dos contratos | PageMap 0.2.0 e `recording.session.*` intocados |

Os desvios em relação ao texto da missão, com justificativa, estão em [lifecycle.md](lifecycle.md) e
[known-limitations.md](known-limitations.md).

## Fluxo principal

1. O Studio abre (sem login) e publica o Bridge, fora da EDT, antes da janela. A barra de status mostra "Navegador:
   aguardando conexão com a extensão".
2. No Chrome, o usuário liga "Conectar ao Nekkora Studio" no painel da extensão. O host procura o Studio, autentica, e o
   Studio mostra "Navegador: conectado (Nekkora Extension 0.4.0)".
3. O usuário seleciona a automação e a sessão e clica **Gravar**. O diálogo lista as abas (título mascarado e host), e as
   não autorizadas aparecem desabilitadas, com a orientação de autorizar pela extensão.
4. **Iniciar gravação** → o Recorder vai a STARTING e abre o Floating Recorder ("Iniciando…"). A extensão inscreve o
   consumidor e estabelece a captura → RECORDING. Só então o botão vira **Gravando**.
5. Pausar, continuar, modo, nova sessão ou troca de sessão, finalizar e cancelar acontecem pelo Floating Recorder, cada
   um confirmado pela extensão.
6. Finalizar → resumo técnico (sem PageMap e sem etapas) → **Voltar ao Studio**.

## Threads

| Thread | Faz | Nunca faz |
|---|---|---|
| EDT | renderiza fotografias; chama `RecorderController` (que só enfileira) | esperar o navegador (`Future.get`) |
| `nekkora-recorder` | todas as transições de estado do Recorder | tocar componentes Swing |
| `nekkora-bridge-accept`, `-handshake`, `-reader`, `-timer` | sockets, autenticação, correlação, batimentos | estado do Recorder (só entrega à fila do Recorder) |

Não há laços de espera cruzados: o Bridge completa futuros; o Recorder reage na própria fila; a interface recebe cópias
imutáveis por `invokeLater`.
