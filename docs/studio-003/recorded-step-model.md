# STUDIO-003 — Modelo da etapa gravada

As estruturas existentes foram preservadas, com evoluções **compatíveis** e documentadas.

```
AutomationStep (RECORDED)
├── name / description          ("Preencher campo Usuário" / "Interação observada… resultado de negócio não validado")
├── ActionType                  CLICK | FILL | SELECT_OPTION | CHECK | UNCHECK | PRESS_KEY (novo) | UNKNOWN
├── ElementReference            (STUDIO-001) — como veio da extensão
│   ├── accessibleName, semanticKind, tag, htmlId/htmlName (apresentáveis só se seguros)
│   ├── locators[] (preferencial só se validado: matchesTarget && matchCount == 1)
│   ├── accessPath[] (MAIN_DOCUMENT → IFRAME → SHADOW_ROOT → ELEMENT)
│   ├── context[] (IN_FORM, IN_ROW, IN_DIALOG, IN_LIST_ITEM)
│   ├── stabilityNotes[] / obstacles[] (ex.: ELEMENT_UNRESOLVED, AMBIGUOUS_TARGET)
│   ├── sensitive (senha: nenhum valor literal aceito)
│   └── correlation (TARGET_ID, NODE_ID, CAPTURE_ID, DOCUMENT_ID, RECORDING_ID, RECORDED_EVENT_ID — internas)
├── ActionParameters            OptionChoice (select) | ValueSource.LiteralValue("Enter") (PRESS_KEY) | vazio (FILL)
├── StepContext                 ("Navegador", título, URL sanitizada)
├── observedOutcome             ("Envio de formulário observado; a página mudou de documento (resultado de negócio não validado).")
└── StepProvenance
    ├── origin = RECORDED
    ├── correlation
    └── recording: RecordingObservation (novo)
        ├── mode           CAPTURE_ONLY ("Capturar") | CAPTURE_AND_INTERACT ("Capturar e interagir")
        ├── observation    PROPOSED (ação NÃO executada) | INTERACTION_OBSERVED (encaminhada à página)
        ├── businessOutcome NOT_VALIDATED (sempre, nesta fase)
        ├── evidence[]     códigos (FORM_SUBMITTED, NAVIGATION_FOLLOWED, VIA_KEYBOARD…)
        └── limitations[]  códigos (AMBIGUOUS_TARGET, ELEMENT_UNRESOLVED, NO_UNIQUE_LOCATOR…)
```

## Evoluções compatíveis do domínio

| Mudança | Compatibilidade |
|---|---|
| `ActionType.PRESS_KEY` ("Pressionar tecla", aceita valor = a tecla) | acréscimo; aparece também na criação manual. Justificativa: um envio por Enter não é um clique, e registrar CLICK seria inventar |
| radio = `CHECK` ("Marcar opção Pessoa física") | sem tipo novo: marcar a opção do grupo é a semântica de CHECK; o grupo fica no contexto |
| `StepProvenance.recording` (`RecordingObservation`) | componente novo do record; o construtor `(origin, correlation)` continua; só existe com `origin == RECORDED`; CAPTURE_ONLY exige PROPOSED |
| `CorrelationKeys.Key.RECORDED_EVENT_ID` | acréscimo ao enum |
| `Automation.discardRecording(recordingId)` | operação nova (cancelamento); remove só as etapas RECORDED da gravação, numa alteração (revisão +1) |

## Valor futuro

Uma etapa FILL gravada nasce **sem valor** (`ActionParameters.none()`). O valor futuro virá do `ValueSource` que o
usuário configurar (variável, credencial protegida, valor explícito), sem cofre nesta missão. Para campo sensível, o
domínio recusa literal (`LITERAL_ON_SENSITIVE_TARGET`).

## Apresentação

Painel de propriedades e diálogo "Ver detalhes":

- **Geral**: nome, descrição, tipo, sessão, posição, origem "Gravada".
- **Elemento**: nome, categoria, tag, id e name HTML (quando seguros), localizador preferencial com estabilidade,
  trajetória, contexto, estabilidade e obstáculos.
- **Gravação**: modo, interação ("Proposta — a ação não foi executada" ou "Interação observada e encaminhada à
  página"), resultado de negócio "Não validado", evidências e limitações.
- **Contexto**: aplicação, tela, endereço e resultado observado.

Nenhum identificador interno é mostrado (`RecorderStepsStudioTest`, padrão de IDs).
