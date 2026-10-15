# STUDIO-002 — Floating Recorder

Código: `nekkora-studio-ui/.../recorder/FloatingRecorderPanel` (conteúdo), `FloatingRecorderWindow` (janela),
`RecordingSetupPanel` (diálogo de início), `RecorderText` (textos); `nekkora-studio-app/.../recorder/RecorderPresenter`
(liga o Recorder à interface).

## Conteúdo

```
┌ Nekkora Recorder                                  × ┐
│ Gravação técnica controlada pelo Studio             │
│ (●) Captura técnica ativa          ← estado CONFIRMADO│
│ [aviso, quando houver problema]                     │
│ Automação  Consulta de clientes                     │
│ Sessão     [Autenticação ▾]        ← troca sem nova captura
│ Aba        Sistema de testes · 127.0.0.1            │
│ Captura    Recorder acompanhando a página           │
│ Cobertura  Página acompanhada por completo          │
│ Modo: ( ) Capturar  (•) Capturar e interagir        │
│ Configuração para a próxima etapa: os sensores de   │
│ interação chegam na STUDIO-003.                     │
│ [Pausar] [Finalizar]                                │
│ [Nova sessão] [Cancelar] ([Encerrar no Studio])     │
│ Captura de ações: próxima versão. Esta gravação     │
│ acompanha a página, mas ainda não cria etapas.      │
│ [x] Manter acima das outras janelas      [Recolher] │
└─────────────────────────────────────────────────────┘
```

Ao concluir ou cancelar, o mesmo espaço mostra o **resumo técnico**: duração, capturas técnicas, sincronizações,
recuperações, cobertura e interrupção, se houve. Também diz que "Nenhuma etapa foi criada" e que "O PageMap não foi
exportado". O botão **Voltar ao Studio** fecha o resumo.

| Estado | Pausar/Continuar | Finalizar/Cancelar | Modo | Sessão |
|---|---|---|---|---|
| Iniciando… / Pausando… / Retomando… / Finalizando… | ocupado (indicador) | — | — | — |
| Captura técnica ativa | Pausar | sim | sim | sim |
| Pausada | Continuar | sim | sim | sim |
| Captura interrompida (página) | Tentar retomar | sim | sim | sim |
| Captura interrompida (conexão perdida) | — | — | — | — (só **Encerrar no Studio**) |
| Modo aguardando confirmação | — | sim | bloqueado, com "Aguardando a confirmação da extensão…" | sim |

O botão **Gravar** da toolbar mostra **Gravando** somente depois da confirmação da extensão. Nos outros casos mostra
"Gravação pausada", "Gravação interrompida", "Encerrando gravação" ou "Gravar". Com uma gravação existente, o botão
reabre o Recorder.

## Comportamento da janela

| Requisito | Implementação | Evidência |
|---|---|---|
| Acessível durante a navegação | `JDialog` não modal, vinculado à janela do Studio; acima das outras janelas por padrão | homologação: "Always on top ligado por padrão" |
| Acima das outras janelas sob controle | "Manter acima das outras janelas" (padrão ligado). Diálogos do Studio rebaixam o Recorder temporariamente. Para diálogos do Chrome (ex.: pedido de permissão), o usuário desmarca a opção ou recolhe o Recorder | homologação |
| Não capturar o foco | aparece **não focável** e passa a aceitar foco 400 ms depois. Só recebe o foco quando o usuário clica nele | homologação: "Floating Recorder não roubou o foco". Na primeira rodada esse defeito foi **encontrado** (a janela recebia o foco) e corrigido |
| Movível | arrastando o cabeçalho, como nos diálogos do design system | homologação S002-024 |
| Minimizar e restaurar | "Recolher" esconde a janela (a gravação continua); o botão "Gravando" do Studio a restaura | homologação S002-024; `RecorderStudioTest` |
| Fechar não encerra em silêncio | ×, Alt+F4 → "A gravação continua ativa": Manter a gravação / Cancelar gravação / Finalizar | `RecorderStudioTest.fecharESairComGravacao` |
| Tema atual | os mesmos componentes e tokens do Studio, trocados ao vivo | capturas nos 4 temas (`S002-07-tema-*.png`); `S002-025` |
| Teclado e acessibilidade | botões e controles focáveis; selo com nome acessível ("Estado da gravação: …") e descrição ("Estado confirmado pela extensão" / "Aguardando a confirmação da extensão"); estados sempre também em texto | `RecorderStudioTest`; `StudioScopeTest` |

## Design System

Só componentes do Nekkora Swing UI: `NekkoraDialogPanel`, `NekkoraStatusBadge`, `NekkoraInlineAlert`,
`NekkoraKeyValueView`, `NekkoraComboBox`, `NekkoraRadioButton`, `NekkoraButton`, `NekkoraCheckBox`, `NekkoraParagraph`,
`NekkoraText`. Os avisos usam parágrafo com quebra de linha, porque a limitação precisa ser lida por inteiro (a primeira
rodada mostrou o texto truncado com "…"). A janela e o diálogo são específicos do Recorder e ficaram no módulo de
interface do Studio; nenhum componente do design system foi alterado. As regras de lint (`DesignLintTest` da
interface e da aplicação) passam.

## Diálogo "Iniciar gravação"

- Sessão de destino, pré-escolhida pela seleção atual (a gravação pertence a essa sessão).
- Abas do Chrome: autorizadas primeiro, depois as que pedem autorização (com o host) e, por último, as páginas internas
  do navegador. Só autorizadas, e sem outra gravação do Studio, podem ser escolhidas.
- Sem conexão, aparece "Aguardando conexão com a extensão" e a orientação para ligar "Conectar ao Nekkora Studio" no
  painel. "Atualizar abas" reconsulta.
- Para site não autorizado: "Sites não autorizados não podem ser gravados. Abra a Nekkora Extension no Chrome e
  conceda acesso ao site; depois atualize a lista."
- Modo e os dois avisos da versão.
- Nenhuma URL e nenhuma referência interna aparecem: `RecorderUiTest.abasEScolha`.
