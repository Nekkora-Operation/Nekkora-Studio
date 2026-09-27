package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.kit.Page;
import com.nekkora.tokens.ColorToken;
import com.nekkora.tokens.Elevation;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.brand.NekkoraLogo;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.button.NekkoraIconButton;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.container.NekkoraCollapsiblePanel;
import com.nekkora.ui.container.NekkoraDivider;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraSection;
import com.nekkora.ui.container.NekkoraSplitPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.container.NekkoraSurface;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.core.NekkoraUI;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.feedback.NekkoraToast;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraSearchField;
import com.nekkora.ui.navigation.NekkoraBreadcrumb;
import com.nekkora.ui.navigation.NekkoraContextMenu;
import com.nekkora.ui.navigation.NekkoraPopupMenu;
import com.nekkora.ui.navigation.NekkoraSidebar;
import com.nekkora.ui.navigation.NekkoraSidebarGroup;
import com.nekkora.ui.navigation.NekkoraSidebarItem;
import com.nekkora.ui.navigation.NekkoraTabs;
import com.nekkora.ui.navigation.NekkoraToolbar;
import com.nekkora.ui.navigation.NekkoraToolbarButton;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;

/** Contêineres e navegação. */
public final class ContainerNavPages {
    private ContainerNavPages() {}

    public static JComponent containers() {
        Page p = new Page("Contêineres", "Painéis, superfícies, cards, seções, divisórias, rolagem e divisões "
                + "redimensionáveis. Fundo, borda e raio por papel — nunca setBackground/setBorder.");
        NekkoraCard basic = new NekkoraCard("Execuções de hoje", "Atualizado há 2 min");
        basic.setHeaderActions(new NekkoraIconButton(IconName.REFRESH, "Atualizar"), new NekkoraIconButton(IconName.MORE_HORIZONTAL, "Mais opções"));
        basic.setContent(new NekkoraText("156 execuções · 98,1% de sucesso"));
        basic.setFooter(Page.tightRow(NekkoraButton.link("Ver todas").withTrailingIcon(IconName.ARROW_RIGHT)));
        NekkoraCard elevated = new NekkoraCard("Card elevado", "elevation.1");
        elevated.setElevation(Elevation.LEVEL_1);
        elevated.setContent(new NekkoraParagraph("Para destacar um bloco sobre o fundo."));
        NekkoraCard clickable = new NekkoraCard("Card clicável", "hover + Enter/Espaço");
        clickable.setContent(new NekkoraStatusBadge(Status.RUNNING));
        clickable.setOnClick(() -> NekkoraToast.show(clickable, Severity.INFO, "Card acionado", null));
        NekkoraUI.forceInteraction(clickable, Interaction.HOVER);
        p.section("Cards", null, Page.row(new Fixed(basic, 300, 0), new Fixed(elevated, 260, 0), new Fixed(clickable, 260, 0)));

        NekkoraPanel canvas = new NekkoraPanel(new BorderLayout(), Background.CANVAS).setPadding(Spacing.SPACE_6).setOutline(ColorToken.BORDER_SUBTLE);
        canvas.add(new NekkoraText("background.canvas"));
        NekkoraSurface surface = NekkoraSurface.outlined(new BorderLayout());
        surface.setPadding(Spacing.SPACE_6);
        surface.add(new NekkoraText("background.surface"));
        NekkoraPanel sunken = new NekkoraPanel(new BorderLayout(), Background.SUNKEN).setPadding(Spacing.SPACE_6);
        sunken.add(new NekkoraText("background.sunken"));
        p.section("Painéis e superfícies", null, Page.row(new Fixed(canvas, 200, 0), new Fixed(surface, 200, 0), new Fixed(sunken, 200, 0)));

        NekkoraSection section = new NekkoraSection("Integrações", "Conecte serviços externos ao projeto.");
        section.setActions(NekkoraButton.secondary("Documentação"), NekkoraButton.primary("Nova integração").withIcon(IconName.PLUS));
        section.setContent(new NekkoraText("Conteúdo da seção…"));
        p.section("Seção", null, section);

        NekkoraStack dividers = NekkoraStack.vertical(Spacing.SPACE_3);
        dividers.add(new NekkoraText("Acima"));
        dividers.add(NekkoraDivider.horizontal());
        dividers.add(new NekkoraText("Abaixo"));
        dividers.add(NekkoraDivider.labeled("ou"));
        dividers.add(new NekkoraText("Alternativa"));
        p.section("Divisórias", null, new Fixed(dividers, 360, 0));

        NekkoraCollapsiblePanel open = new NekkoraCollapsiblePanel("Opções avançadas", new NekkoraParagraph("Conteúdo visível. Enter/Espaço no cabeçalho recolhe."));
        NekkoraCollapsiblePanel closed = new NekkoraCollapsiblePanel("Histórico", new NekkoraParagraph("…"));
        closed.setExpanded(false);
        p.section("Painel recolhível", null, new Fixed(Page.column(open, closed), 420, 0));

        NekkoraPanel left = new NekkoraPanel(new BorderLayout(), Background.CANVAS).setPadding(Spacing.SPACE_5);
        left.add(new NekkoraText("Navegador"), BorderLayout.NORTH);
        NekkoraPanel right = new NekkoraPanel(new BorderLayout(), Background.SURFACE).setPadding(Spacing.SPACE_5);
        right.add(new NekkoraText("Área de trabalho — arraste a divisória"), BorderLayout.NORTH);
        NekkoraSplitPane split = NekkoraSplitPane.horizontal(left, right).withProportion(0.3);
        NekkoraSurface splitBox = NekkoraSurface.outlined(new BorderLayout());
        splitBox.add(split);
        p.section("Divisão redimensionável", null, new Fixed(splitBox, 640, 160));

        NekkoraStack longList = NekkoraStack.vertical(Spacing.SPACE_3);
        longList.setPadding(Spacing.SPACE_5);
        for (int i = 1; i <= 20; i++) longList.add(new NekkoraText("Linha " + i));
        NekkoraSurface scrollBox = NekkoraSurface.outlined(new BorderLayout());
        scrollBox.add(new NekkoraScrollPane(longList));
        p.section("Rolagem", "Barras finas e arredondadas, rolagem suave.", new Fixed(scrollBox, 320, 160));
        return p;
    }

    public static JComponent navigation() {
        Page p = new Page("Navegação", "Sidebar, toolbar, abas, trilha e menus. Tudo navegável pelo teclado.");

        NekkoraSidebar sidebar = new NekkoraSidebar();
        sidebar.setHeader(new NekkoraLogo(NekkoraLogo.Variant.COMPACT, 22));
        NekkoraSidebarGroup work = sidebar.addGroup("Trabalho");
        work.addItem(new NekkoraSidebarItem("home", "Início", IconName.HOME));
        NekkoraSidebarItem projects = work.addItem(new NekkoraSidebarItem("projects", "Projetos", IconName.FOLDER));
        work.addItem(new NekkoraSidebarItem("runs", "Execuções", IconName.EXECUTIONS)).setCount(4);
        NekkoraSidebarGroup admin = sidebar.addGroup("Administração");
        admin.addItem(new NekkoraSidebarItem("settings", "Configurações", IconName.SETTINGS));
        sidebar.select(projects);
        NekkoraSidebar collapsed = new NekkoraSidebar();
        NekkoraSidebarGroup cg = collapsed.addGroup(null);
        cg.addItem(new NekkoraSidebarItem("home", "Início", IconName.HOME));
        NekkoraSidebarItem cp = cg.addItem(new NekkoraSidebarItem("projects", "Projetos", IconName.FOLDER));
        cg.addItem(new NekkoraSidebarItem("runs", "Execuções", IconName.EXECUTIONS)).setCount(4);
        cg.addItem(new NekkoraSidebarItem("settings", "Configurações", IconName.SETTINGS));
        collapsed.setHeader(new NekkoraLogo(NekkoraLogo.Variant.SYMBOL, 20));
        collapsed.select(cp);
        collapsed.setCollapsed(true);
        p.section("Sidebar", "Expandida e recolhida (só ícones, rótulos no tooltip). ↑/↓ movem, Enter seleciona.",
                Page.row(new Fixed(sidebar, 232, 300), new Fixed(collapsed, 64, 300)));

        NekkoraToolbar tb = new NekkoraToolbar();
        tb.add(new NekkoraToolbarButton(IconName.PLUS, "Novo", true));
        tb.add(new NekkoraToolbarButton(IconName.SAVE, "Salvar").withShortcut(NekkoraKeys.shortcut(KeyEvent.VK_S)));
        tb.addSeparator();
        tb.add(new NekkoraToolbarButton(IconName.PLAY, "Executar", true));
        tb.add(new NekkoraToolbarButton(IconName.STOP, "Parar"));
        tb.addSeparator();
        NekkoraToolbarButton grid = new NekkoraToolbarButton(IconName.GRID, "Mostrar grade");
        grid.setToggle(true);
        grid.setSelected(true);
        tb.add(grid);
        tb.addGlue();
        tb.add(new Fixed(new NekkoraSearchField("Buscar…"), 220, 0));
        NekkoraSurface tbBox = NekkoraSurface.outlined(new BorderLayout());
        tbBox.add(tb);
        p.section("Toolbar", "Ações discretas; tooltips mostram o atalho.", tbBox);

        NekkoraTabs tabs = new NekkoraTabs();
        tabs.addTab("Visão geral", IconName.HOME, pad(new NekkoraText("Conteúdo da visão geral")));
        tabs.addTab("Execuções", IconName.EXECUTIONS, pad(new NekkoraText("Conteúdo de execuções")));
        tabs.addTab("Configurações", IconName.SETTINGS, pad(new NekkoraText("Conteúdo de configurações")));
        NekkoraTabs docs = new NekkoraTabs(NekkoraTabs.Style.CARD);
        docs.addTab("fluxo-principal", IconName.WORKFLOW, pad(new NekkoraText("Documento aberto")));
        docs.addTab("mapeamento.json", IconName.BRACES, pad(new NekkoraText("Outro documento")));
        docs.setClosable(true, i -> true);
        p.section("Abas", "Sublinhadas (seções) e \"card\" fecháveis (documentos). Ctrl+Tab alterna, Ctrl+W fecha.",
                Page.column(new Fixed(tabs, 560, 120), new Fixed(docs, 560, 120)));

        NekkoraBreadcrumb bc = new NekkoraBreadcrumb().item("Projetos", () -> { }).item("Financeiro", () -> { }).item("Conciliação mensal");
        p.section("Trilha (breadcrumb)", null, bc);

        NekkoraButton menuButton = NekkoraButton.secondary("Ações").withTrailingIcon(IconName.CHEVRON_DOWN);
        NekkoraPopupMenu menu = new NekkoraPopupMenu()
                .header("Item")
                .item("Renomear", IconName.EDIT, NekkoraKeys.shortcut(KeyEvent.VK_R), () -> { })
                .item("Duplicar", IconName.COPY, NekkoraKeys.shortcut(KeyEvent.VK_D), () -> { })
                .item("Exportar", IconName.DOWNLOAD, null, () -> { })
                .separator()
                .danger("Excluir", IconName.TRASH, () -> { });
        menuButton.addActionListener(e -> menu.show(menuButton, 0, menuButton.getHeight()));
        NekkoraPanel area = new NekkoraPanel(new BorderLayout(), Background.SUNKEN).setPadding(Spacing.SPACE_8);
        area.setFocusable(true);
        area.add(new NekkoraText("Clique com o botão direito aqui (ou Shift+F10)"));
        NekkoraContextMenu.install(area, () -> menu);
        p.section("Menus", "Menu suspenso e menu de contexto com ícones, atalhos e item destrutivo.",
                Page.row(menuButton, new Fixed(area, 360, 0)));
        return p;
    }

    private static JComponent pad(JComponent c) {
        NekkoraPanel panel = new NekkoraPanel(new BorderLayout()).setPadding(Spacing.SPACE_6);
        panel.add(c, BorderLayout.NORTH);
        return panel;
    }
}
