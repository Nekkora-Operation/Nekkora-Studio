package com.nekkora.showcase;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.pages.ButtonPages;
import com.nekkora.showcase.pages.ContainerNavPages;
import com.nekkora.showcase.pages.DataPages;
import com.nekkora.showcase.pages.DialogStatePages;
import com.nekkora.showcase.pages.FeedbackPages;
import com.nekkora.showcase.pages.FoundationPages;
import com.nekkora.showcase.pages.IconBrandPages;
import com.nekkora.showcase.pages.InputPages;
import com.nekkora.showcase.pages.TechnicalPages;
import com.nekkora.showcase.pages.WorkflowSearchPages;
import com.nekkora.showcase.studio.StudioDemo;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.Nekkora;
import com.nekkora.ui.NekkoraFrame;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.brand.NekkoraLogo;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.NekkoraUI;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraSwitch;
import com.nekkora.ui.navigation.NekkoraSidebar;
import com.nekkora.ui.navigation.NekkoraSidebarGroup;
import com.nekkora.ui.navigation.NekkoraSidebarItem;
import com.nekkora.ui.navigation.NekkoraToolbar;
import com.nekkora.ui.theme.ThemeManager;
import com.nekkora.ui.typography.NekkoraCaption;
import com.nekkora.ui.typography.NekkoraHeading;
import com.nekkora.ui.typography.NekkoraLabel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * Nekkora Design System Showcase — todas as categorias, estados e os 4 temas ao vivo.
 *
 * <p>Argumentos: {@code --theme=<id>}, {@code --page=<id>}, {@code --studio} (abre a tela
 * fictícia de Studio), {@code --screenshots=<pasta>} (exporta PNGs de todas as páginas em todos
 * os temas e encerra).
 */
public final class ShowcaseApp {

    /** Página do catálogo. */
    public record PageDef(String id, String group, String title, IconName icon, Supplier<JComponent> factory) {}

    public static final List<PageDef> PAGES = List.of(
            new PageDef("cores", "Fundamentos", "Cores", IconName.SUN, FoundationPages::colors),
            new PageDef("tipografia", "Fundamentos", "Tipografia", IconName.EDIT, FoundationPages::typography),
            new PageDef("espaco", "Fundamentos", "Espaço e forma", IconName.GRID, FoundationPages::spaceAndShape),
            new PageDef("icones", "Fundamentos", "Ícones", IconName.STAR, IconBrandPages::icons),
            new PageDef("marca", "Fundamentos", "Marca", IconName.SAKURA, IconBrandPages::brand),
            new PageDef("botoes", "Componentes", "Botões", IconName.PLAY_CIRCLE, ButtonPages::buttons),
            new PageDef("campos", "Componentes", "Campos", IconName.EDIT, InputPages::textFields),
            new PageDef("selecao", "Componentes", "Seleção", IconName.CHECK_CIRCLE, InputPages::selection),
            new PageDef("conteineres", "Componentes", "Contêineres", IconName.LAYERS, ContainerNavPages::containers),
            new PageDef("navegacao", "Componentes", "Navegação", IconName.MENU, ContainerNavPages::navigation),
            new PageDef("dados", "Componentes", "Dados", IconName.LIST, DataPages::data),
            new PageDef("feedback", "Componentes", "Feedback", IconName.BELL, FeedbackPages::feedback),
            new PageDef("dialogos", "Componentes", "Diálogos", IconName.MESSAGE, DialogStatePages::dialogs),
            new PageDef("estados", "Componentes", "Estados", IconName.BOX, DialogStatePages::states),
            new PageDef("tecnico", "Padrões", "Informação técnica", IconName.CODE, TechnicalPages::technical),
            new PageDef("arquivos", "Padrões", "Arquivos", IconName.FOLDER_OPEN, TechnicalPages::files),
            new PageDef("fluxo", "Padrões", "Fluxo e histórico", IconName.WORKFLOW, WorkflowSearchPages::workflow),
            new PageDef("busca", "Padrões", "Busca", IconName.SEARCH, WorkflowSearchPages::search),
            new PageDef("studio", "Padrões", "Tela Studio", IconName.STUDIO, ShowcaseApp::studioPage));

    private final NekkoraFrame frame = new NekkoraFrame("Nekkora Design System Showcase");
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JComponent> built = new LinkedHashMap<>();
    private final NekkoraHeading pageTitle = new NekkoraHeading("", NekkoraHeading.Level.H2);
    private final NekkoraSidebar sidebar = new NekkoraSidebar();

    public static void main(String[] args) {
        String theme = arg(args, "--theme");
        String page = arg(args, "--page");
        String shots = arg(args, "--screenshots");
        boolean studio = List.of(args).contains("--studio");
        SwingUtilities.invokeLater(() -> {
            if (theme != null) Nekkora.install(NekkoraThemes.byId(theme).orElse(NekkoraThemes.defaultTheme()));
            else if (shots != null) Nekkora.install(NekkoraThemes.defaultTheme());
            else Nekkora.install();
            if (studio) {
                StudioDemo.open();
                return;
            }
            ShowcaseApp app = new ShowcaseApp();
            app.show(page == null ? "botoes" : page);
            if (shots != null) {
                SwingUtilities.invokeLater(() -> app.screenshots(new File(shots)));
            }
        });
    }

    private ShowcaseApp() {
        NekkoraPanel root = new NekkoraPanel(new BorderLayout(), Background.CANVAS);
        root.add(buildSidebar(), BorderLayout.WEST);
        NekkoraPanel main = new NekkoraPanel(new BorderLayout(), Background.CANVAS);
        main.add(buildTopBar(), BorderLayout.NORTH);
        content.setOpaque(false);
        main.add(content, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        frame.getContentPane().add(root, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(NekkoraFrame.EXIT_ON_CLOSE);
        NekkoraKeys.bind(root, NekkoraKeys.shortcut(KeyEvent.VK_T, KeyEvent.SHIFT_DOWN_MASK), "showcase.theme", ShowcaseApp::nextTheme);
        frame.sizeToScreen();
        frame.setVisible(true);
    }

    private JComponent buildSidebar() {
        sidebar.setHeader(new NekkoraLogo(NekkoraLogo.Variant.COMPACT, 22));
        Map<String, NekkoraSidebarGroup> groups = new LinkedHashMap<>();
        for (PageDef p : PAGES) {
            groups.computeIfAbsent(p.group(), sidebar::addGroup).addItem(new NekkoraSidebarItem(p.id(), p.title(), p.icon()));
        }
        sidebar.onSelect(item -> show(item.getId()));
        NekkoraCaption version = new NekkoraCaption("Nekkora Swing UI · V1");
        version.setHorizontalAlignment(NekkoraCaption.CENTER);
        sidebar.setFooter(version);
        return sidebar;
    }

    private JComponent buildTopBar() {
        NekkoraToolbar tb = new NekkoraToolbar();
        tb.setSurface(Background.CANVAS);
        tb.addGap(Spacing.SPACE_4);
        tb.add(pageTitle);
        tb.addGlue();
        tb.add(new NekkoraLabel("Tema"));
        tb.addGap(Spacing.SPACE_3);
        NekkoraComboBox<NekkoraTheme> themes = new NekkoraComboBox<>(NekkoraThemes.all());
        themes.setLabelProvider(NekkoraTheme::displayName);
        themes.setIconProvider(t -> t.isDark() ? IconName.MOON : IconName.SUN);
        themes.setSelectedItem(ThemeManager.current());
        themes.getAccessibleContext().setAccessibleName("Tema");
        themes.addActionListener(e -> {
            if (themes.getSelectedValue() != null && themes.getSelectedValue() != ThemeManager.current()) {
                ThemeManager.setTheme(themes.getSelectedValue());
            }
        });
        ThemeManager.addThemeListener((o, n) -> {
            if (themes.getSelectedValue() != n) themes.setSelectedItem(n);
        });
        tb.add(new Fixed(themes, 190, 0));
        tb.addGap(Spacing.SPACE_6);
        NekkoraSwitch motion = new NekkoraSwitch("Movimento reduzido", NekkoraUI.isReducedMotion());
        motion.addItemListener(e -> NekkoraUI.setReducedMotion(motion.isSelected()));
        tb.add(motion);
        tb.addGap(Spacing.SPACE_6);
        NekkoraButton studio = NekkoraButton.primary("Abrir tela Studio").withIcon(IconName.STUDIO);
        studio.addActionListener(e -> StudioDemo.open());
        tb.add(studio);
        tb.addGap(Spacing.SPACE_3);
        return tb;
    }

    private static JComponent studioPage() {
        NekkoraStack s = NekkoraStack.vertical(Spacing.SPACE_6);
        s.setPadding(Spacing.SPACE_8, Spacing.SPACE_9);
        NekkoraStack head = NekkoraStack.vertical(Spacing.SPACE_2);
        head.add(new NekkoraHeading("Tela Studio (fictícia)", NekkoraHeading.Level.DISPLAY_M));
        head.add(new com.nekkora.ui.typography.NekkoraParagraph("Sidebar + Navegador (árvore) + Abas + Área de trabalho + "
                + "Inspector + Toolbar + Status + Diálogo — montada só com componentes da lib. Nenhuma cor, fonte, borda "
                + "ou espaçamento decidido pela tela. Ctrl+B / Ctrl+Alt+B recolhem os painéis; F5 executa; Ctrl+K busca."));
        NekkoraButton open = NekkoraButton.primary("Abrir em janela própria").withIcon(IconName.EXTERNAL_LINK);
        open.addActionListener(e -> StudioDemo.open());
        head.add(com.nekkora.showcase.kit.Page.tightRow(open));
        s.add(head);
        com.nekkora.ui.container.NekkoraSurface box = com.nekkora.ui.container.NekkoraSurface.outlined(new BorderLayout());
        box.add(StudioDemo.build());
        s.add(new Fixed(box, 0, 760));
        return s;
    }

    private void show(String id) {
        PageDef def = PAGES.stream().filter(p -> p.id().equals(id)).findFirst().orElse(PAGES.get(0));
        if (!built.containsKey(def.id())) {
            JComponent page = def.factory().get();
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(page, BorderLayout.NORTH);
            NekkoraScrollPane scroll = new NekkoraScrollPane(holder);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            built.put(def.id(), page);
            content.add(scroll, def.id());
        }
        cards.show(content, def.id());
        pageTitle.setText(def.group() + " · " + def.title());
        if (sidebar.getSelectedItem() == null || !sidebar.getSelectedItem().getId().equals(def.id())) sidebar.select(def.id());
    }

    private static void nextTheme() {
        List<NekkoraTheme> all = NekkoraThemes.all();
        ThemeManager.setTheme(all.get((all.indexOf(ThemeManager.current()) + 1) % all.size()));
    }

    // ------------------------------------------------------------------ Screenshots

    private void screenshots(File dir) {
        dir.mkdirs();
        try {
            for (NekkoraTheme theme : NekkoraThemes.all()) {
                ThemeManager.setTheme(theme);
                for (PageDef p : PAGES) {
                    show(p.id());
                    JComponent page = built.get(p.id());
                    frame.getRootPane().validate();
                    save(frame.getRootPane(), frame.getRootPane().getWidth(), frame.getRootPane().getHeight(),
                            new File(dir, theme.id() + "-" + p.id() + "-janela.png"));
                    int w = page.getParent().getWidth();
                    Dimension pref = page.getPreferredSize();
                    int h = Math.min(pref.height, 4000);
                    Dimension old = page.getSize();
                    page.setSize(w, h);
                    layoutTree(page);
                    save(page, w, h, new File(dir, theme.id() + "-" + p.id() + ".png"));
                    page.setSize(old);
                    page.getParent().revalidate();
                }
            }
            System.out.println("Screenshots em " + dir.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

    private static void layoutTree(Component c) {
        if (c instanceof Container ct) {
            ct.doLayout();
            for (Component ch : ct.getComponents()) layoutTree(ch);
        }
    }

    private static void save(JComponent c, int w, int h, File out) throws java.io.IOException {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(ThemeManager.color(com.nekkora.tokens.ColorToken.BACKGROUND_CANVAS));
        g.fillRect(0, 0, w, h);
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    private static String arg(String[] args, String name) {
        for (String a : args) if (a.startsWith(name + "=")) return a.substring(name.length() + 1);
        return null;
    }
}
