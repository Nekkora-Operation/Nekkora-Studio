# STUDIO-001 — Composição da interface

Todas as telas são compostas com a Nekkora Swing UI: nenhuma cor, fonte, borda, espaçamento ou posição absoluta é
decidida pelo Studio (verificado por `DesignLintTest` em `nekkora-studio-ui` e `nekkora-studio-app`).

## Composição principal

```text
┌──┬──────────────────────────────────────────────────────────────────────────────────────────────┐
│  │ Automações › Consulta de clientes      + Nova automação  ≡ Nova sessão  ☰ Nova etapa │ ⏺ ▶ 💾 │ ◧ ◨ ☀ │  NekkoraToolbar
│🐱├────────────────┬──────────────────────────────────────────────┬──────────────────────────────┤
│  │ AUTOMAÇÕES   + │ [Consulta de clientes ×]                     │ PROPRIEDADES                 │
│◫ │ Consulta de    │  ▣ Consulta de clientes  [Rascunho em memória]│ ✎ Preencher campo de pesquisa│
│  │ clientes       │    3 sessões · 7 etapas                       │   Etapa 05 de 07   [Manual]  │
│  │ 3 sessões ·    │  [Nova sessão] [Nova etapa] Propriedades…     │ ▾ Geral                      │
│  │ 7 etapas       │  ┌ Autenticação          Sessão 1 · 3 etapas ┐│   Nome / Descrição / Tipo    │
│  │                │  │ ✎ 01 Preencher usuário                    ││   Sessão / Posição / Origem  │
│  │                │  │   Preencher · Manual · Elemento não vinc. ││ ▾ Elemento                   │
│  │                │  └───────────────────────────────────────────┘│ ▸ Contexto                   │
│  │                │  ┌ Consulta …                                ┐│ ▾ Ações  [Ver detalhes]      │
├──┴────────────────┴──────────────────────────────────────────────┴──────────────────────────────┤
│ ✎ Rascunho em memória   ⚠ Alterações não salvas   ⧉ 1 automação em memória            ☀ Sakura Dark │  NekkoraStatusBar
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Capturas reais: `reports/studio-001/evidence/`.

## Componentes reutilizados (Design System)

`NekkoraFrame`, `NekkoraWorkbench`, `NekkoraSidebar` (recolhida), `NekkoraToolbar`/`NekkoraToolbarButton`,
`NekkoraBreadcrumb`, `NekkoraNavigator`, `NekkoraList`, `NekkoraWorkArea`/`NekkoraTabs`, `NekkoraCard`,
`NekkoraStack`, `NekkoraPanel`, `NekkoraScrollPane`, `NekkoraEmptyState`, `NekkoraHeading`, `NekkoraText`,
`NekkoraCaption`, `NekkoraParagraph`, `NekkoraIconMarker`, `NekkoraBadge`, `NekkoraStatusBadge`,
`NekkoraIconButton`, `NekkoraButton`, `NekkoraInspector`, `NekkoraPropertiesGroup`, `NekkoraKeyValueView`,
`NekkoraSection`, `NekkoraDialog`, `NekkoraConfirmationDialog`, `NekkoraFormField`, `NekkoraTextField`,
`NekkoraTextArea`, `NekkoraComboBox`, `NekkoraToast`, `NekkoraTooltips`, `NekkoraPopupMenu`, `NekkoraStatusBar`,
`NekkoraActionRegistry`/`NekkoraAction`/`NekkoraKeys`, `ThemeManager`, `Scale`.

`NekkoraIdentifierLabel` **não** é usado nas telas funcionais (continua só no Showcase).

## Componentes novos (`nekkora-studio-ui`, específicos do produto)

| Componente | Base | Papel |
|---|---|---|
| `AutomationNavigator` | `NekkoraNavigator` + `NekkoraList` + `NekkoraEmptyState` | lista as automações reais; estado vazio com "Nova automação" |
| `AutomationOverview` | `NekkoraPanel`/`NekkoraStack` em `NekkoraScrollPane` | cabeçalho da automação, ações, sessões; estado vazio de sessões |
| `LogicalSessionView` | `NekkoraCard` + `NekkoraList` | uma sessão: título, "Sessão n · k etapas", ações, etapas numeradas; estado vazio da sessão |
| `PropertiesPresenter` | `NekkoraInspector` + `NekkoraPropertiesGroup` | propriedades de automação, sessão ou etapa (somente leitura) |
| `ElementPresentation`, `StepPresentation`, `StudioText`, `ActionTypeStyle` | — | textos e intenções visuais seguros, a partir do domínio |
| `NameDescriptionForm`, `StepForm`, `StepDetailsForm` | `NekkoraFormField` etc. | formulários; mensagens de validação vindas do domínio |
| `StudioDialogs` / `FormSpec` / `StudioForms` | `NekkoraDialog` | diálogos (Esc cancela, Enter/Ctrl+Enter confirma, conteúdo rolável limitado à tela) |

Nenhum componente novo entrou na biblioteca genérica: todos dependem do domínio do Studio.

## Etapa na lista

```text
✎  05   Preencher campo de pesquisa
        Preencher · Manual · Elemento não vinculado
```

Ícone do tipo + número visual + nome; linha secundária: tipo, origem e estado básico — **em texto**, sem depender de
cor. A sessão selecionada mostra o selo "✓ Selecionada".

## Estados vazios

| Onde | Texto | Próximo passo |
|---|---|---|
| Navegação | "Nenhuma automação" | botão "Nova automação" |
| Área central | "Nenhuma automação aberta" | botão "Nova automação" |
| Automação sem sessões | "Nenhuma sessão ainda" | botão "Nova sessão" |
| Sessão sem etapas | "Nenhuma etapa nesta sessão." | "Adicionar etapa" |
| Propriedades | "Nada selecionado" | — |

## Diálogos

| Diálogo | Campos | Confirmar |
|---|---|---|
| Nova automação | Nome*, Descrição | Criar automação |
| Nova sessão | Nome da sessão*, Descrição | Criar sessão |
| Renomear sessão | Nome da sessão* (preenchido) | Renomear |
| Nova etapa | Sessão* (sugere a selecionada), Nome*, Tipo de ação*, Descrição | Adicionar etapa |
| Detalhes da etapa | Nome*, Tipo*, Descrição + Resumo, Elemento, Contexto (somente leitura) | Aplicar alterações |
| Fechar sem salvar? | aviso de perda das alterações em memória | Fechar e descartar (Enter não confirma) |

## Navegação e teclado

| Atalho | Ação |
|---|---|
| Ctrl+N | Nova automação |
| Ctrl+Shift+N | Nova sessão |
| Ctrl+E | Nova etapa |
| F2 | Renomear a sessão selecionada |
| Enter (lista de etapas) / duplo clique | Detalhes da etapa |
| Setas | mover na lista de automações / etapas |
| Ctrl+B / Ctrl+Alt+B | recolher navegação / propriedades |
| Ctrl+W, Ctrl+Tab | fechar / alternar abas |
| Ctrl+Shift+T | próximo tema |
| Esc | cancelar diálogo |
| Tab / Shift+Tab | percorre campos (inclusive áreas de texto) e controles |

Botões da toolbar seguem o padrão do FlatLaf e ficam fora da ordem de Tab; todas as suas ações têm atalho e um
equivalente focável no conteúdo (estado vazio, visão da automação, propriedades).

## Funcionalidades futuras

Gravar, Executar e Salvar aparecem **desabilitados** na toolbar, com dica explicando que chegam em versões futuras.
Não há resultado simulado. A barra de status informa "Rascunho em memória" e "Alterações não salvas".
