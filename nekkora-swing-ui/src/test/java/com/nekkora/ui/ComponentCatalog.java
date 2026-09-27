package com.nekkora.ui;

import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Elevation;
import com.nekkora.ui.brand.NekkoraLogo;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.container.NekkoraCollapsiblePanel;
import com.nekkora.ui.container.NekkoraDivider;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraSection;
import com.nekkora.ui.container.NekkoraSplitPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.container.NekkoraSurface;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraAvatar;
import com.nekkora.ui.data.NekkoraBadge;
import com.nekkora.ui.data.NekkoraIconMarker;
import com.nekkora.ui.data.NekkoraKeyValueView;
import com.nekkora.ui.data.NekkoraList;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.data.NekkoraTable;
import com.nekkora.ui.data.NekkoraTableModel;
import com.nekkora.ui.data.NekkoraTag;
import com.nekkora.ui.data.NekkoraTree;
import com.nekkora.ui.data.NekkoraTreeNode;
import com.nekkora.ui.dialog.NekkoraDialogPanel;
import com.nekkora.ui.feedback.NekkoraBanner;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.feedback.NekkoraProgressBar;
import com.nekkora.ui.feedback.NekkoraSkeleton;
import com.nekkora.ui.feedback.NekkoraSpinner;
import com.nekkora.ui.feedback.NekkoraToast;
import com.nekkora.ui.file.NekkoraFilePathField;
import com.nekkora.ui.file.NekkoraRecentFileItem;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraDateField;
import com.nekkora.ui.input.NekkoraDecimalField;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraIntegerField;
import com.nekkora.ui.input.NekkoraPasswordField;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.ui.input.NekkoraSearchField;
import com.nekkora.ui.input.NekkoraSwitch;
import com.nekkora.ui.input.NekkoraTextArea;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.input.NekkoraTimeField;
import com.nekkora.ui.navigation.NekkoraBreadcrumb;
import com.nekkora.ui.navigation.NekkoraPopupMenu;
import com.nekkora.ui.navigation.NekkoraSidebar;
import com.nekkora.ui.navigation.NekkoraSidebarItem;
import com.nekkora.ui.navigation.NekkoraTabs;
import com.nekkora.ui.navigation.NekkoraToolbar;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
import com.nekkora.ui.search.NekkoraGlobalSearch;
import com.nekkora.ui.search.NekkoraSearchResult;
import com.nekkora.ui.search.SearchItem;
import com.nekkora.ui.state.NekkoraEmptyState;
import com.nekkora.ui.state.NekkoraErrorState;
import com.nekkora.ui.state.NekkoraLoadingState;
import com.nekkora.ui.state.NekkoraNoResultsState;
import com.nekkora.ui.state.NekkoraOfflineState;
import com.nekkora.ui.state.NekkoraPermissionState;
import com.nekkora.ui.studio.NekkoraInspector;
import com.nekkora.ui.studio.NekkoraNavigator;
import com.nekkora.ui.studio.NekkoraStatusBar;
import com.nekkora.ui.studio.NekkoraWorkArea;
import com.nekkora.ui.studio.NekkoraWorkbench;
import com.nekkora.ui.technical.NekkoraCodeBlock;
import com.nekkora.ui.technical.NekkoraCopyableValue;
import com.nekkora.ui.technical.NekkoraIdentifierLabel;
import com.nekkora.ui.technical.NekkoraJsonViewer;
import com.nekkora.ui.technical.NekkoraLogViewer;
import com.nekkora.ui.technical.NekkoraMonospaceTextArea;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraCodeText;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraLabel;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.workflow.NekkoraStepper;
import com.nekkora.ui.workflow.NekkoraTimeline;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JComponent;

/** Todos os componentes da lib com uma configuração representativa (Gate V1 + extras). */
final class ComponentCatalog {
    private ComponentCatalog() {}

    record Row(String name, Status status, int n) {}

    static NekkoraTableModel<Row> tableModel() {
        NekkoraTableModel<Row> m = new NekkoraTableModel<>(List.of(
                NekkoraTableModel.Column.<Row>text("Nome", Row::name),
                NekkoraTableModel.Column.<Row>status("Status", Row::status).width(130),
                NekkoraTableModel.Column.<Row>number("Qtd", Row::n)));
        m.setRows(List.of(new Row("Alpha", Status.SUCCESS, 3), new Row("Beta", Status.FAILED, 7), new Row("Gama", Status.RUNNING, 1)));
        return m;
    }

    static NekkoraTreeNode tree() {
        NekkoraTreeNode root = new NekkoraTreeNode("Raiz", IconName.WORKSPACE);
        NekkoraTreeNode a = new NekkoraTreeNode("Pasta", IconName.FOLDER).setCount(2);
        a.add(new NekkoraTreeNode("Item 1", IconName.FILE).setStatus(Status.SUCCESS), new NekkoraTreeNode("Item 2", IconName.FILE).setSecondary("rascunho"));
        root.add(a);
        return root;
    }

    static Map<String, Supplier<JComponent>> all() {
        Map<String, Supplier<JComponent>> m = new LinkedHashMap<>();
        // Tipografia
        m.put("Heading", () -> new NekkoraHeading("Título", NekkoraHeading.Level.H1));
        m.put("Text", () -> new NekkoraText("Texto"));
        m.put("Label", () -> new NekkoraLabel("Rótulo"));
        m.put("Caption", () -> new NekkoraCaption("Legenda"));
        m.put("CodeText", () -> new NekkoraCodeText("abc-123"));
        m.put("Paragraph", () -> new NekkoraParagraph("Parágrafo longo com quebra de linha automática para testar a altura."));
        // Marca e ícones
        m.put("Logo", () -> new NekkoraLogo(NekkoraLogo.Variant.FULL, 32).withTagline());
        m.put("LogoApp", () -> new NekkoraLogo(NekkoraLogo.Variant.APP_ICON, 48));
        m.put("IconMarker", () -> new NekkoraIconMarker(IconName.SERVER, Tone.INFO));
        m.put("Avatar", () -> new NekkoraAvatar("Ana Souza"));
        // Botões
        m.put("Button", () -> NekkoraButton.primary("Salvar").withIcon(IconName.SAVE));
        m.put("ButtonLoading", () -> {
            NekkoraButton b = NekkoraButton.secondary("Enviando");
            b.setLoading(true);
            return b;
        });
        m.put("IconButton", () -> new NekkoraIconButton(IconName.REFRESH, "Atualizar"));
        m.put("LinkButton", () -> NekkoraButton.link("Saiba mais"));
        // Campos
        m.put("TextField", () -> new NekkoraTextField("Nome"));
        m.put("PasswordField", () -> new NekkoraPasswordField("Senha"));
        m.put("SearchField", NekkoraSearchField::new);
        m.put("TextArea", () -> new NekkoraTextArea("Descrição", 3).scrollable());
        m.put("IntegerField", NekkoraIntegerField::new);
        m.put("DecimalField", NekkoraDecimalField::new);
        m.put("DateField", NekkoraDateField::new);
        m.put("TimeField", NekkoraTimeField::new);
        m.put("FormField", () -> {
            NekkoraFormField f = new NekkoraFormField("E-mail", new NekkoraTextField()).required().helper("Ajuda");
            f.setError("Inválido");
            return f;
        });
        m.put("ComboBox", () -> new NekkoraComboBox<>(List.of("A", "B")));
        m.put("CheckBox", () -> new NekkoraCheckBox("Opção", true));
        m.put("Radio", () -> new NekkoraRadioButton("Opção", true));
        m.put("Switch", () -> new NekkoraSwitch("Ativo", true));
        m.put("FilePathField", () -> new NekkoraFilePathField(NekkoraFilePathField.Mode.FOLDER, "Pasta"));
        // Contêineres
        m.put("Panel", NekkoraPanel::new);
        m.put("Surface", NekkoraSurface::new);
        m.put("Card", () -> {
            NekkoraCard c = new NekkoraCard("Card", "Subtítulo");
            c.setElevation(Elevation.LEVEL_1);
            c.setContent(new NekkoraText("Conteúdo"));
            return c;
        });
        m.put("Section", () -> new NekkoraSection("Seção", "Descrição").setContent(new NekkoraText("x")));
        m.put("Stack", () -> NekkoraStack.vertical(com.nekkora.tokens.Spacing.SPACE_4).addAll(new NekkoraText("a"), new NekkoraText("b")));
        m.put("ScrollPane", () -> new NekkoraScrollPane(new NekkoraText("rolável")));
        m.put("SplitPane", () -> NekkoraSplitPane.horizontal(new NekkoraText("a"), new NekkoraText("b")));
        m.put("Divider", () -> NekkoraDivider.labeled("ou"));
        m.put("CollapsiblePanel", () -> new NekkoraCollapsiblePanel("Grupo", new NekkoraText("x")));
        // Navegação
        m.put("Sidebar", () -> {
            NekkoraSidebar s = new NekkoraSidebar();
            s.addGroup("Grupo").addItem(new NekkoraSidebarItem("a", "Início", IconName.HOME));
            s.select("a");
            return s;
        });
        m.put("Toolbar", () -> {
            NekkoraToolbar t = new NekkoraToolbar();
            t.add(new NekkoraToolbarButton(IconName.PLAY, "Executar", true));
            t.addSeparator();
            t.add(new NekkoraToolbarButton(IconName.STOP, "Parar"));
            return t;
        });
        m.put("Tabs", () -> {
            NekkoraTabs t = new NekkoraTabs();
            t.addTab("Um", IconName.HOME, new NekkoraText("1"));
            t.addTab("Dois", IconName.FILE, new NekkoraText("2"));
            return t;
        });
        m.put("Breadcrumb", () -> new NekkoraBreadcrumb().item("A", () -> { }).item("B"));
        m.put("PopupMenu", () -> new NekkoraPopupMenu().item("Abrir", IconName.EXTERNAL_LINK, null, () -> { }).danger("Excluir", IconName.TRASH, () -> { }));
        // Dados
        m.put("Table", () -> new NekkoraTable(tableModel()).scrollable());
        m.put("TableEmpty", () -> {
            NekkoraTableModel<Row> model = tableModel();
            model.setRows(List.of());
            return new NekkoraTable(model).scrollable();
        });
        m.put("Tree", () -> {
            NekkoraTree t = new NekkoraTree(tree());
            t.expandAll();
            t.setSelectionRow(1);
            return t.scrollable();
        });
        m.put("List", () -> new NekkoraList<>(List.of("a", "b")).withSecondary(s -> s.toUpperCase(), true).withIcon(s -> IconName.FILE));
        m.put("KeyValue", () -> new NekkoraKeyValueView().add("Chave", "Valor").addMono("ID", "x1").addCopyable("URL", "https://x"));
        m.put("Badge", () -> new NekkoraBadge("Novo", Tone.ACCENT, NekkoraBadge.Variant.SOLID).withIcon(IconName.STAR));
        m.put("Tag", () -> new NekkoraTag("filtro").removable(() -> { }));
        m.put("StatusBadge", () -> new NekkoraStatusBadge(Status.RUNNING));
        // Feedback
        m.put("InlineAlert", () -> new NekkoraInlineAlert(Severity.WARNING, "Atenção", "Mensagem").addAction("Ação", () -> { }).dismissible(null));
        m.put("Banner", () -> new NekkoraBanner(Severity.INFO, "Info", null));
        m.put("Toast", () -> new NekkoraToast(Severity.SUCCESS, "Salvo", "Tudo certo"));
        m.put("ProgressBar", () -> {
            NekkoraProgressBar p = new NekkoraProgressBar();
            p.setValue(50);
            return p;
        });
        m.put("ProgressIndeterminate", NekkoraProgressBar::indeterminate);
        m.put("Spinner", NekkoraSpinner::new);
        m.put("Skeleton", () -> new NekkoraSkeleton(NekkoraSkeleton.Shape.TEXT, 3));
        // Diálogos (anatomia, sem janela)
        m.put("DialogPanel", () -> {
            NekkoraDialogPanel d = new NekkoraDialogPanel("Título");
            d.setDescription("Descrição");
            d.setMarker(IconName.ALERT, Tone.ERROR);
            d.addFooterButton(NekkoraButton.secondary("Cancelar"));
            d.addFooterButton(NekkoraButton.danger("Excluir"));
            return d;
        });
        // Estados
        m.put("EmptyState", () -> new NekkoraEmptyState(IconName.FOLDER, "Vazio", "Descrição").addAction(NekkoraButton.primary("Criar")));
        m.put("ErrorState", () -> new NekkoraErrorState(() -> { }));
        m.put("LoadingState", NekkoraLoadingState::new);
        m.put("NoResultsState", () -> new NekkoraNoResultsState("x", () -> { }));
        m.put("PermissionState", NekkoraPermissionState::new);
        m.put("OfflineState", () -> new NekkoraOfflineState(() -> { }));
        // Técnico
        m.put("CodeBlock", () -> new NekkoraCodeBlock("print(1)", "py"));
        m.put("LogViewer", () -> {
            NekkoraLogViewer l = new NekkoraLogViewer();
            l.append(NekkoraLogViewer.Level.ERROR, "falhou");
            return l;
        });
        m.put("JsonViewer", () -> {
            NekkoraJsonViewer j = new NekkoraJsonViewer();
            j.setJson("{\"a\":[1,2,{\"b\":true}],\"c\":null}");
            return j;
        });
        m.put("MonospaceTextArea", () -> new NekkoraMonospaceTextArea("a=1", 3));
        m.put("CopyableValue", () -> new NekkoraCopyableValue("valor"));
        m.put("IdentifierLabel", () -> new NekkoraIdentifierLabel("5f0c2a9e-81d3-4b7a-9c61-2e4d8f7a1b3c"));
        m.put("RecentFileItem", () -> new NekkoraRecentFileItem("a.json", "C:\\pasta\\muito\\longa\\a.json", "ontem"));
        // Fluxo e busca
        m.put("Stepper", () -> {
            NekkoraStepper s = new NekkoraStepper("A", "B", "C");
            s.setCurrent(1);
            return s;
        });
        m.put("StepperVertical", () -> new NekkoraStepper("A", "B").withOrientation(NekkoraStepper.Orientation.VERTICAL));
        m.put("Timeline", () -> new NekkoraTimeline().addItem("10:00", "Evento", "Detalhe", Status.SUCCESS).addItem("09:00", "Outro", null, Status.FAILED));
        m.put("GlobalSearch", () -> new NekkoraGlobalSearch("Buscar"));
        m.put("SearchResult", () -> new NekkoraSearchResult(new SearchItem("Conciliação", "Pasta / x", "Projeto", IconName.WORKFLOW, null), "conc"));
        // Padrões de estúdio
        m.put("Navigator", () -> {
            NekkoraNavigator n = new NekkoraNavigator("Projetos");
            n.enableSearch("Filtrar", q -> { });
            n.setContent(new NekkoraTree(tree()));
            return n;
        });
        m.put("Inspector", () -> {
            NekkoraInspector i = new NekkoraInspector("Propriedades");
            i.setSubject(IconName.FILE, Tone.ACCENT, "Item", "Tipo");
            i.addGroup("Geral").addRow("Nome", new NekkoraTextField()).addRow("Ativo", new NekkoraSwitch());
            return i;
        });
        m.put("WorkArea", () -> {
            NekkoraWorkArea w = new NekkoraWorkArea();
            w.openTab("a", "Documento", IconName.FILE, new NekkoraText("x"));
            return w;
        });
        m.put("StatusBar", () -> {
            NekkoraStatusBar s = new NekkoraStatusBar();
            s.addLeft(NekkoraStatusBar.status(Status.SUCCESS, "Ok"));
            s.addRight(NekkoraStatusBar.item(IconName.CLOCK, "10:00"));
            return s;
        });
        m.put("Workbench", () -> {
            NekkoraWorkbench w = new NekkoraWorkbench();
            w.setNavigator(new NekkoraText("nav"));
            w.setWorkspace(new NekkoraText("work"));
            w.setInspector(new NekkoraText("insp"));
            w.setStatusBar(new NekkoraStatusBar());
            return w;
        });
        m.put("IconButtonSmall", () -> new NekkoraIconButton(IconName.CLOSE, "Fechar").withSize(ControlSize.SMALL));
        return m;
    }
}
