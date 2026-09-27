# Componentes — Nekkora Swing UI V1

Todos em `com.nekkora.ui.*`. Todos aparecem no Showcase com seus estados.

| Categoria | Componentes |
|---|---|
| Fundamentos | `ThemeManager`, `Tokens`, `NekkoraFonts`, `Scale`, `NekkoraIcon` (`IconName`, `IconSize` S/M/L/XL/XXL, `IconCategory`), `NekkoraLogo` |
| Tipografia | `NekkoraHeading` (DISPLAY_L…H3), `NekkoraText`, `NekkoraLabel` (+ `overline`), `NekkoraCaption`, `NekkoraCodeText`, `NekkoraParagraph` |
| Botões | `NekkoraButton` (PRIMARY, SECONDARY, GHOST, DANGER, LINK · SMALL/MEDIUM/LARGE · ícone+texto · carregando · alternável), `NekkoraIconButton` |
| Campos | `NekkoraTextField`, `NekkoraPasswordField`, `NekkoraSearchField`, `NekkoraTextArea`, `NekkoraFormField` (rótulo, obrigatório/opcional, ajuda, erro), `NekkoraIntegerField`, `NekkoraDecimalField`, `NekkoraDateField`, `NekkoraTimeField`, `MaskFilter` |
| Seleção | `NekkoraComboBox`, `NekkoraCheckBox` (indeterminado), `NekkoraRadioButton` (+ `group`), `NekkoraSwitch` |
| Contêineres | `NekkoraPanel` (fundo/respiro/contorno por papel), `NekkoraSurface`, `NekkoraStack`, `NekkoraCard`, `NekkoraSection`, `NekkoraScrollPane` (+ `verticalOnly`), `NekkoraSplitPane`, `NekkoraDivider`, `NekkoraCollapsiblePanel` |
| Navegação | `NekkoraSidebar` / `SidebarGroup` / `SidebarItem`, `NekkoraToolbar` / `ToolbarButton`, `NekkoraTabs` (sublinhada/card, fecháveis), `NekkoraBreadcrumb`, `NekkoraMenu`, `NekkoraPopupMenu`, `NekkoraMenuItem`, `NekkoraContextMenu` |
| Dados | `NekkoraTable` + `NekkoraTableModel` (colunas TEXT/SECONDARY/MONO/NUMBER/STATUS, ordenação, hover, vazio, menu por linha), `NekkoraTree` + `NekkoraTreeNode`, `NekkoraList`, `NekkoraKeyValueView`, `NekkoraBadge`, `NekkoraTag`, `NekkoraStatusBadge`, `NekkoraAvatar`, `NekkoraIconMarker` |
| Feedback | `NekkoraTooltips`, `NekkoraToast`, `NekkoraBanner`, `NekkoraInlineAlert`, `NekkoraProgressBar`, `NekkoraSpinner`, `NekkoraSkeleton` — `Severity` INFO/SUCCESS/WARNING/ERROR |
| Diálogos | `NekkoraDialog`, `NekkoraDialogPanel`, `NekkoraConfirmationDialog` (`confirm`, `confirmDanger` com digitação), `NekkoraModalForm`, `NekkoraPopover` |
| Estados | `NekkoraEmptyState`, `NekkoraErrorState`, `NekkoraLoadingState`, `NekkoraNoResultsState`, `NekkoraPermissionState`, `NekkoraOfflineState` |
| Status | `Status` SUCCESS, RUNNING, PENDING, WARNING, FAILED, PAUSED, DISABLED, DRAFT, INFO · `Tone` |
| Técnico | `NekkoraCodeBlock`, `NekkoraLogViewer`, `NekkoraJsonViewer`, `NekkoraMonospaceTextArea`, `NekkoraCopyableValue`, `NekkoraIdentifierLabel` |
| Arquivos | `NekkoraFileDialogs` (abrir/salvar/pasta), `NekkoraFilePathField`, `NekkoraRecentFileItem` |
| Fluxo | `NekkoraStepper` (horizontal/vertical, erro), `NekkoraTimeline` |
| Busca | `NekkoraSearchField`, `NekkoraGlobalSearch`, `NekkoraSearchResult`, `SearchItem` |
| Ações | `NekkoraAction`, `NekkoraActionRegistry`, `NekkoraKeys` (atalhos multiplataforma) |
| Estúdio | `NekkoraWorkbench`, `NekkoraNavigator`, `NekkoraInspector`, `NekkoraPropertiesGroup`, `NekkoraWorkArea`, `NekkoraStatusBar`, `NekkoraPanelHeader` |
| Janela | `Nekkora.install()`, `NekkoraFrame` |

## Exemplos

```java
// Tabela
var model = new NekkoraTableModel<Item>(List.of(
        Column.text("Nome", Item::nome).width(240),
        Column.status("Status", Item::status).width(140),
        Column.mono("ID", Item::id)));
model.setRows(itens);
NekkoraTable table = new NekkoraTable(model);
table.setEmptyState(IconName.SEARCH, "Nada encontrado", "Ajuste os filtros.");
table.setRowMenu(row -> new NekkoraPopupMenu().item("Abrir", IconName.EXTERNAL_LINK, null, () -> abrir(row)));
table.onRowActivated(row -> abrir(row));
panel.add(table.scrollable());

// Confirmação destrutiva
if (NekkoraConfirmationDialog.confirmDanger(this, "Excluir projeto?", "Não pode ser desfeito.", "Excluir", "financeiro")) { ... }

// Layout de estúdio
NekkoraWorkbench wb = new NekkoraWorkbench();
wb.setSidebar(sidebar); wb.setToolbar(toolbar);
wb.setNavigator(navigator); wb.setWorkspace(workArea); wb.setInspector(inspector);
wb.setStatusBar(statusBar);
```
