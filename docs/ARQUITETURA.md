# Arquitetura — Nekkora Swing UI

## Objetivo

Quando o Studio for reconstruído, **quase nenhuma tela deverá decidir cor, fonte, borda, espaçamento ou
comportamento visual**. Telas compõem componentes e escolhem intenções; a biblioteca é a autoridade visual.

## Camadas

```
Tokens      nekkora-design-tokens   ColorToken, Spacing, Radius, BorderWidth, Elevation, TypeStyle,
                                    FontRole/FontWeight, ControlSize, Motion
Temas       nekkora-design-tokens   Palette + ThemeMode → ThemeFactory → NekkoraTheme (4 oficiais em NekkoraThemes)
Primitivas  nekkora-swing-ui        theme (ThemeManager, NekkoraLaf, NekkoraFonts, Scale, Tokens),
                                    icon (NekkoraIcon, IconName, SvgPath), core (Tone, Status, Severity), internal
Componentes nekkora-swing-ui        typography, button, input, container, navigation, data, feedback, dialog,
                                    state, technical, file, workflow, search, brand, action
Padrões     nekkora-swing-ui        studio: Workbench (Navegador | Área | Inspector), Navigator, Inspector,
                                    PropertiesGroup, WorkArea, StatusBar
Studio      nekkora-studio-*        domínio (Java puro), UI do produto e aplicação — ver docs/studio-001/
```

Dependências só descem: tokens não conhecem Swing; a UI não conhece o domínio (Automation, Process,
Evidence, Worker, PDD, API, auth… **não existem aqui**). O sistema de status é genérico (`Status.SUCCESS,
RUNNING, PENDING, WARNING, FAILED, PAUSED, DISABLED, DRAFT, INFO`) e cada aplicação mapeia seus estados.

## Tokens semânticos

Componentes pedem **papéis**: `background.canvas/surface/elevated/sunken`, `text.primary/secondary/muted/
disabled`, `border.subtle/default/strong/focus`, `action.primary/primaryHover/primaryPressed/secondary/danger`,
`status.success/warning/error/info` (+ `Soft` e `Solid`), `selection.background`, `focus.ring`, `brand.*`.

Cada tema declara só a **paleta** (as mesmas 8 cores da nekkora-lib web). `ThemeFactory` deriva todos os
papéis por regras de modo (escuro/claro), como os blocos `color-mix()` da versão web. `ThemeContrastTest`
garante contraste AA para texto, ações, status e foco em todos os temas.

Escalas: `space.1..12` (2–64px), `radius.small/medium/large/full`, `elevation.0/1/2/overlay`,
controles `SMALL 24 · MEDIUM 30 · LARGE 36`, movimento `FAST 120 · NORMAL 180 · SLOW 260 ms`.

## Tipografia

Sora (display/títulos), Inter (interface/dados) e JetBrains Mono (código/IDs), **distribuídas com a lib**
(OFL). Fallbacks por papel em `FontRole`. Níveis: `DISPLAY_L, DISPLAY_M, H1, H2, H3, BODY_L, BODY, BODY_S,
LABEL, CAPTION, OVERLINE, MONOSPACE`.

## Tema e Look and Feel

`ThemeManager.setTheme(...)` troca **ao vivo**: reinstala o LaF, chama `FlatLaf.updateUI()` em todas as
janelas e notifica `ThemeListener`s. Componentes resolvem tokens **no momento de pintar** e reaplicam
fontes/cores em `updateUI()` — nada fica preso ao tema antigo. A escolha é salva em `Preferences`.

**FlatLaf é infraestrutura**, não identidade: usamos HiDPI, UI delegates e decorações de janela, mas
`NekkoraUIDefaults` é o único lugar que mapeia tokens para chaves Swing (JTable, JTree, scrollbars,
menus, JOptionPane, JFileChooser…). Botões, badges, switch, checkbox/radio, sidebar, cards, stepper,
logo e ícones são pintados pela própria lib. O produto não depende do Metal nem se parece com
"FlatLaf com rosa".

## Ícones e marca

95 ícones duotone (traço + camada translúcida) extraídos do mesmo desenho da nekkora-lib web
(`tools/extract-icons.mjs`) + ícones de desktop (`tools/extra-icons.txt`), vetoriais e nítidos em qualquer
DPI. `NekkoraIcon.of(IconName.SEARCH, IconSize.M)`; a cor segue o componente (como `currentColor`) ou um
token. `NekkoraLogo` (FULL, COMPACT, SYMBOL, WORDMARK, APP_ICON × BRAND, MONO, INVERSE) fica separado dos
componentes funcionais; `NekkoraLogo.appIconImages()` gera os ícones de janela.

## DPI, resoluções e movimento

Todo valor em px lógicos passa por `Scale.px()`; ícones e logo são vetoriais. Testado em 100% e 150%
(`-Dflatlaf.uiScale=1.5`). Layouts são por gerenciadores (sem posicionamento absoluto) e o Workbench
funciona de 1366×768 a 4K com painéis redimensionáveis e recolhíveis.
Movimento discreto ("explica estado, não decora"): hover/foco sem animação, switch e toasts com
`Motion.FAST/NORMAL`, spinners só rodam visíveis; `NekkoraUI.setReducedMotion(true)` ou
`-Dnekkora.reducedMotion=true`.

## Teclado e acessibilidade

Tab/Shift+Tab em tudo que é interativo, foco sempre visível (anel `focus.ring`), Enter/Espaço acionam,
Esc fecha diálogos/popups/limpa busca, setas em listas, árvores, sidebar, radios e campos numéricos,
Shift+F10/tecla de menu abrem menus de contexto. Componentes pintados herdam `NekkoraComponent`
(nome, papel e estados acessíveis). Status nunca depende só de cor (ícone + texto). Rótulos de
formulário são associados aos controles (`setLabelFor`).

## Preparado para a V2

- **Command Palette (Ctrl+K):** `NekkoraAction` + `NekkoraActionRegistry` (id, rótulo, ícone, atalho,
  categoria, busca sem acentos) e a base visual de `NekkoraGlobalSearch`/`NekkoraSearchResult`.
- **DatePicker:** `NekkoraDateField` já valida e aceita setas; o calendário entra como popover.
- Fora da lib por decisão: canvas/editor de processos, nós, conectores, minimapa → `nekkora-studio-ui`.
