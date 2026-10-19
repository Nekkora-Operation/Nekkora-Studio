package com.nekkora.studio.ui.pdd;

import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraSplitPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.data.NekkoraList;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.navigation.NekkoraToolbar;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * STUDIO-010 G6 — preparação do PDD visual: lista de seções e blocos à esquerda, edição do item selecionado ao centro e
 * PRÉVIA do PDF à direita (o próprio PDF rasterizado — o que se vê é o que se exporta). Nada aqui altera a automação: as
 * ações vão ao apresentador, que só mexe no RASCUNHO do documento (com desfazer/refazer próprio).
 *
 * <p>STUDIO-012 PDD-01: "Imagem do bloco" — a imagem sanitizada com recorte e marcadores ({@link PddImageEditor});
 * "Capturar novamente" traz uma captura CANDIDATA que só substitui a anterior em "Aplicar" ("Cancelar" a descarta);
 * "Selecionar área" ajusta o recorte; "Posicionar marcador" cria um marcador MANUAL para instrução sem marcador
 * automático.
 */
public class PddPanel extends JPanel {

    /** O que o apresentador faz com as escolhas da tela. */
    public interface Actions {
        void select(String key);

        void undo();

        void redo();

        void refresh();

        void export();

        void saveTitle(String key, String text);

        void acceptTitle(String key);

        void saveInstruction(String blockId, int ordinal, String text);

        void acceptInstruction(String blockId, int ordinal);

        void split(String blockId, int at);

        void joinNext(String blockId);

        void capture(String blockId, String tabRef);

        void cancelCapture();

        void removeCapture(String blockId);

        /** Relê as abas do Chrome (a lista ao abrir a escolha de aba fica sempre atual). */
        default void refreshTabs() {}

        /**
         * STUDIO-013: autoriza a captura do PDD na ORIGEM mostrada da aba (só esta origem exata). {@code remember}: lembrar o
         * site; sem marcar, vale até o navegador fechar.
         */
        default void authorizeSite(String tabRef, String origin, boolean remember) {}

        /** STUDIO-013: revoga a captura do PDD na origem (a armação ou captura em curso nela é cancelada pela extensão). */
        default void revokeSite(String origin) {}

        /** Aplica a captura candidata ao bloco (substitui a anterior). */
        void applyCandidate();

        /** Descarta a captura candidata (a anterior continua). */
        void cancelCandidate();

        /** Recorte em px da imagem (candidata, se houver; senão a aplicada). */
        void setCrop(String blockId, int x, int y, int width, int height);

        /** Volta ao recorte automático. */
        void autoCrop(String blockId);

        /** Marcador manual da instrução {@code ordinal}: retângulo normalizado sobre a imagem inteira. */
        void placeMarker(String blockId, int ordinal, double x, double y, double width, double height);

        void removeMarker(String blockId, int ordinal);
    }

    /** Imagem do bloco no editor: a aplicada ou a candidata, recorte ({@code null} = automático), marcadores e a política. */
    public record ImageView(BufferedImage image, java.awt.Rectangle crop, List<PddImageEditor.Mark> marks, boolean candidate, String policy) {
        public ImageView {
            marks = List.copyOf(marks);
        }
    }

    /** Instrução do bloco quanto ao marcador: automático, manual ou indisponível ({@code markable} = aceita marcador manual). */
    public record MarkerSlot(int ordinal, String state, boolean markable, boolean manual) {}

    /** Item da lista: seção ({@code secao-<n>}) ou bloco ({@code blk-…}). */
    public record Item(String key, String label, String secondary, boolean section) {}

    /** Instrução no editor: número, texto, situação ("Derivada", "Revisar", "Revisada", "Fora da política"). */
    public record InstructionRow(int ordinal, String text, String state, boolean needsAttention) {}

    /**
     * Aba do Chrome para a captura. {@code state}: {@code READY} (pode capturar), {@code CONSENT_REQUIRED} (o Chrome permite
     * gravar, falta autorizar o PDD nesta origem), {@code CHROME_ACCESS_REQUIRED}, {@code RECORDING} ou {@code UPDATE_EXTENSION}.
     */
    public record Tab(String tabRef, String label, String origin, String state) {
        public Tab(String tabRef, String label) {
            this(tabRef, label, null, "READY");
        }

        public boolean ready() {
            return "READY".equals(state);
        }
    }

    /** Site autorizado para o PDD (origem exata) e como aparece na lista. */
    public record Site(String origin, String label) {}

    /** Tudo o que a tela mostra. */
    public record Model(List<Item> items, String selected, String title, String titleState, boolean titleNeedsAttention, boolean block,
                        List<InstructionRow> instructions, List<String> pending, boolean hasImage, boolean canJoinNext, List<Tab> tabs,
                        boolean capturing, String bannerTitle, String banner, Severity bannerSeverity, String status, Status statusKind,
                        List<BufferedImage> preview, boolean canUndo, boolean canRedo, boolean canExport, ImageView image, List<MarkerSlot> markers,
                        String captureHint, List<Site> sites) {
        public Model {
            captureHint = captureHint == null ? "" : captureHint;
            sites = sites == null ? List.of() : List.copyOf(sites);
            items = List.copyOf(items);
            instructions = List.copyOf(instructions);
            pending = List.copyOf(pending);
            tabs = List.copyOf(tabs);
            preview = List.copyOf(preview);
            markers = markers == null ? List.of() : List.copyOf(markers);
        }

        /** A forma da STUDIO-010 (sem imagem no editor). */
        public Model(List<Item> items, String selected, String title, String titleState, boolean titleNeedsAttention, boolean block,
                List<InstructionRow> instructions, List<String> pending, boolean hasImage, boolean canJoinNext, List<Tab> tabs, boolean capturing,
                String bannerTitle, String banner, Severity bannerSeverity, String status, Status statusKind, List<BufferedImage> preview, boolean canUndo,
                boolean canRedo, boolean canExport) {
            this(items, selected, title, titleState, titleNeedsAttention, block, instructions, pending, hasImage, canJoinNext, tabs, capturing, bannerTitle,
                    banner, bannerSeverity, status, statusKind, preview, canUndo, canRedo, canExport, null, List.of(), "", List.of());
        }

        /** A forma da STUDIO-012 PDD-01 (sem dica de captura nem sites autorizados). */
        public Model(List<Item> items, String selected, String title, String titleState, boolean titleNeedsAttention, boolean block,
                List<InstructionRow> instructions, List<String> pending, boolean hasImage, boolean canJoinNext, List<Tab> tabs, boolean capturing,
                String bannerTitle, String banner, Severity bannerSeverity, String status, Status statusKind, List<BufferedImage> preview, boolean canUndo,
                boolean canRedo, boolean canExport, ImageView image, List<MarkerSlot> markers) {
            this(items, selected, title, titleState, titleNeedsAttention, block, instructions, pending, hasImage, canJoinNext, tabs, capturing, bannerTitle,
                    banner, bannerSeverity, status, statusKind, preview, canUndo, canRedo, canExport, image, markers, "", List.of());
        }

        /** Há uma captura candidata do bloco selecionado aguardando Aplicar/Cancelar. */
        public boolean reviewing() {
            return image != null && image.candidate();
        }
    }

    private final Actions actions;
    private final NekkoraButton undo = NekkoraButton.ghost("Desfazer").withIcon(IconName.ARROW_LEFT).withSize(ControlSize.SMALL);
    private final NekkoraButton redo = NekkoraButton.ghost("Refazer").withIcon(IconName.ARROW_RIGHT).withSize(ControlSize.SMALL);
    private final NekkoraButton refresh = NekkoraButton.ghost("Atualizar com a automação").withSize(ControlSize.SMALL);
    private final NekkoraButton export = NekkoraButton.primary("Exportar PDF…").withSize(ControlSize.SMALL);
    private final NekkoraStatusBadge status = new NekkoraStatusBadge(Status.PENDING, "");
    private final NekkoraInlineAlert banner = new NekkoraInlineAlert(Severity.INFO, "", "");
    private final NekkoraList<Item> list = new NekkoraList<>();
    private final NekkoraText heading = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraTextField titleField = new NekkoraTextField("");
    private final NekkoraText titleState = new NekkoraText("", TextTone.MUTED);
    private final NekkoraButton saveTitle = NekkoraButton.secondary("Salvar título").withSize(ControlSize.SMALL);
    private final NekkoraButton acceptTitle = NekkoraButton.ghost("Aceitar como está").withSize(ControlSize.SMALL);
    private final NekkoraStack instructionsBox = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraStack pendingBox = NekkoraStack.vertical(Spacing.SPACE_1);
    private final NekkoraComboBox<Integer> splitAt = new NekkoraComboBox<>();
    private final NekkoraButton split = NekkoraButton.ghost("Dividir antes da instrução").withSize(ControlSize.SMALL);
    private final NekkoraButton joinNext = NekkoraButton.ghost("Juntar com o próximo bloco").withSize(ControlSize.SMALL);
    /** Largura máxima das escolhas (aba, site): rótulos longos não empurram os botões para fora da coluna (homologação STUDIO-013). */
    static final int CHOICE_MAX_WIDTH = 340;
    private final NekkoraComboBox<Tab> tabs = capped();
    private final NekkoraButton capture = NekkoraButton.secondary("Capturar tela…").withIcon(IconName.PLAY).withSize(ControlSize.SMALL);
    private final NekkoraButton cancelCapture = NekkoraButton.ghost("Cancelar captura").withSize(ControlSize.SMALL);
    private final NekkoraButton removeCapture = NekkoraButton.ghost("Remover imagem").withSize(ControlSize.SMALL);
    /** Por que "Capturar tela…" está indisponível (sem conexão, nenhuma aba autorizada, aba gravando, PDD não autorizado). */
    private final NekkoraText captureHint = new NekkoraText("", TextTone.WARNING);
    // STUDIO-013: autorização da captura do PDD por site (origem exata), separada do acesso do Chrome
    private final NekkoraStack consentBox = NekkoraStack.vertical(Spacing.SPACE_1);
    private final NekkoraParagraph consentText = new NekkoraParagraph("");
    private final NekkoraCheckBox remember = new NekkoraCheckBox("Lembrar este site (sem marcar: só até fechar o navegador)");
    private final NekkoraButton authorize = NekkoraButton.secondary("Autorizar captura PDD neste site").withSize(ControlSize.SMALL);
    private final NekkoraComboBox<Site> sites = capped();
    private final NekkoraButton revokeSite = NekkoraButton.ghost("Revogar captura PDD").withSize(ControlSize.SMALL);
    private final NekkoraStack sitesRow = NekkoraStack.vertical(Spacing.SPACE_1);
    private final NekkoraStack blockTools = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraStack imageBox = NekkoraStack.vertical(Spacing.SPACE_2);
    private final NekkoraText imageHeading = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraText imageNote = new NekkoraText("", TextTone.MUTED);
    private final PddImageEditor editor = new PddImageEditor();
    private final NekkoraButton selectArea = NekkoraButton.secondary("Selecionar área").withSize(ControlSize.SMALL);
    private final NekkoraButton autoCrop = NekkoraButton.ghost("Recorte automático").withSize(ControlSize.SMALL);
    private final NekkoraComboBox<MarkerSlot> markerSlot = new NekkoraComboBox<>();
    private final NekkoraButton placeMarker = NekkoraButton.secondary("Posicionar marcador").withSize(ControlSize.SMALL);
    private final NekkoraButton removeMarker = NekkoraButton.ghost("Remover marcador manual").withSize(ControlSize.SMALL);
    private final NekkoraButton apply = NekkoraButton.primary("Aplicar").withSize(ControlSize.SMALL);
    private final NekkoraButton cancelCandidate = NekkoraButton.ghost("Cancelar").withSize(ControlSize.SMALL);
    private final NekkoraStack reviewRow = NekkoraStack.horizontal(Spacing.SPACE_2);
    private final Preview preview = new Preview();
    private final List<NekkoraTextField> instructionFields = new ArrayList<>();
    private Model model;
    private boolean updating;

    public PddPanel(Actions actions) {
        super(new BorderLayout(0, Spacing.SPACE_2.px()));
        this.actions = Objects.requireNonNull(actions);
        setOpaque(false);
        NekkoraToolbar bar = new NekkoraToolbar();
        bar.add(undo);
        bar.add(redo);
        bar.addSeparator();
        bar.add(refresh);
        bar.addGlue();
        bar.add(status);
        bar.addGap(Spacing.SPACE_2);
        bar.add(export);
        NekkoraStack north = NekkoraStack.vertical(Spacing.SPACE_2);
        north.add(bar);
        north.add(banner);
        add(north, BorderLayout.NORTH);

        list.withLabel(Item::label).withSecondary(Item::secondary, true);
        list.getAccessibleContext().setAccessibleName("Seções e blocos do documento");
        list.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && list.getSelectedValue() != null) actions.select(list.getSelectedValue().key());
        });

        NekkoraStack editor = NekkoraStack.vertical(Spacing.SPACE_3);
        editor.add(heading);
        titleField.getAccessibleContext().setAccessibleName("Título");
        editor.add(titleField);
        NekkoraStack titleRow = NekkoraStack.horizontal(Spacing.SPACE_2);
        titleRow.add(saveTitle);
        titleRow.add(acceptTitle);
        titleRow.add(titleState);
        editor.add(titleRow);
        editor.add(instructionsBox);
        NekkoraStack splitRow = NekkoraStack.horizontal(Spacing.SPACE_2);
        splitAt.getAccessibleContext().setAccessibleName("Instrução onde o bloco é dividido");
        splitRow.add(split);
        splitRow.add(splitAt);
        splitRow.add(joinNext);
        blockTools.add(splitRow);
        NekkoraStack captureRow = NekkoraStack.vertical(Spacing.SPACE_1);
        NekkoraStack captureButtons = NekkoraStack.horizontal(Spacing.SPACE_2);
        tabs.setLabelProvider(t -> t == null ? "" : t.label());
        tabs.getAccessibleContext().setAccessibleName("Aba do Chrome (laboratório) para a captura");
        captureRow.add(tabs);
        captureButtons.add(capture);
        captureButtons.add(cancelCapture);
        captureButtons.add(removeCapture);
        captureRow.add(captureButtons);
        blockTools.add(captureRow);
        captureHint.getAccessibleContext().setAccessibleName("Por que a captura está indisponível");
        blockTools.add(captureHint);
        consentText.getAccessibleContext().setAccessibleName("Autorização da captura do PDD neste site");
        consentBox.add(consentText);
        NekkoraStack consentRow = NekkoraStack.horizontal(Spacing.SPACE_2);
        consentRow.add(authorize);
        consentRow.add(remember);
        consentBox.add(consentRow);
        blockTools.add(consentBox);
        sites.setLabelProvider(s -> s == null ? "" : s.label());
        sites.getAccessibleContext().setAccessibleName("Sites autorizados para o PDD");
        sitesRow.add(new NekkoraText("Sites autorizados para o PDD:", TextTone.MUTED));
        sitesRow.add(sites);
        sitesRow.add(revokeSite);
        blockTools.add(sitesRow);
        tabs.addActionListener(e -> tabs.setToolTipText(tabs.getSelectedValue() == null ? null : tabs.getSelectedValue().label()));
        sites.addActionListener(e -> sites.setToolTipText(sites.getSelectedValue() == null ? null : sites.getSelectedValue().label()));
        // STUDIO-012 PDD-01: imagem do bloco (recorte, marcadores, revisão da candidata)
        imageBox.add(imageHeading);
        imageBox.add(imageNote);
        imageBox.add(this.editor);
        NekkoraStack cropRow = NekkoraStack.horizontal(Spacing.SPACE_2);
        cropRow.add(selectArea);
        cropRow.add(autoCrop);
        imageBox.add(cropRow);
        NekkoraStack markerRow = NekkoraStack.horizontal(Spacing.SPACE_2);
        markerSlot.setLabelProvider(m -> m == null ? "" : "Instrução " + m.ordinal() + " · " + m.state());
        markerSlot.getAccessibleContext().setAccessibleName("Instrução do marcador");
        markerRow.add(markerSlot);
        markerRow.add(placeMarker);
        markerRow.add(removeMarker);
        imageBox.add(markerRow);
        reviewRow.add(apply);
        reviewRow.add(cancelCandidate);
        imageBox.add(reviewRow);
        blockTools.add(imageBox);
        editor.add(blockTools);
        editor.add(new NekkoraText("Pendências", TextTone.SECONDARY));
        editor.add(pendingBox);
        NekkoraScrollPane editorScroll = NekkoraScrollPane.verticalOnly(editor);
        // barra vertical fixa: a altura da prévia (e do texto quebrado) depende da largura; com a barra aparecendo e sumindo
        // conforme a largura, o layout entrava em laço (STUDIO-012 PDD-01, homologação na janela real)
        editorScroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        NekkoraScrollPane previewScroll = NekkoraScrollPane.verticalOnly(preview);
        previewScroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        previewScroll.getAccessibleContext().setAccessibleName("Prévia do PDF");
        NekkoraSplitPane right = NekkoraSplitPane.horizontal(editorScroll, previewScroll).withProportion(0.48);
        NekkoraSplitPane main = NekkoraSplitPane.horizontal(NekkoraScrollPane.verticalOnly(list), right).withProportion(0.24);
        add(main, BorderLayout.CENTER);

        undo.addActionListener(e -> actions.undo());
        redo.addActionListener(e -> actions.redo());
        refresh.addActionListener(e -> actions.refresh());
        export.addActionListener(e -> actions.export());
        saveTitle.addActionListener(e -> actions.saveTitle(model.selected(), titleField.getText()));
        acceptTitle.addActionListener(e -> actions.acceptTitle(model.selected()));
        split.addActionListener(e -> {
            Integer at = splitAt.getSelectedValue();
            if (at != null) actions.split(model.selected(), at);
        });
        joinNext.addActionListener(e -> actions.joinNext(model.selected()));
        capture.addActionListener(e -> {
            Tab t = tabs.getSelectedValue();
            if (t != null) actions.capture(model.selected(), t.tabRef());
        });
        cancelCapture.addActionListener(e -> actions.cancelCapture());
        tabs.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                actions.refreshTabs();
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });
        tabs.addActionListener(e -> {
            if (!updating && model != null) consent(model);
        });
        authorize.addActionListener(e -> {
            Tab t = tabs.getSelectedValue();
            if (t != null && t.origin() != null) actions.authorizeSite(t.tabRef(), t.origin(), remember.isSelected());
        });
        revokeSite.addActionListener(e -> {
            Site s = sites.getSelectedValue();
            if (s != null) actions.revokeSite(s.origin());
        });
        removeCapture.addActionListener(e -> actions.removeCapture(model.selected()));
        markerSlot.addActionListener(e -> {
            MarkerSlot m = markerSlot.getSelectedValue();
            boolean editable = model != null && model.image() != null && !model.image().candidate();
            placeMarker.setEnabled(editable && m != null && m.markable());
            removeMarker.setEnabled(editable && m != null && m.manual());
        });
        apply.addActionListener(e -> actions.applyCandidate());
        cancelCandidate.addActionListener(e -> actions.cancelCandidate());
        autoCrop.addActionListener(e -> actions.autoCrop(model.selected()));
        removeMarker.addActionListener(e -> {
            MarkerSlot m = markerSlot.getSelectedValue();
            if (m != null) actions.removeMarker(model.selected(), m.ordinal());
        });
        selectArea.addActionListener(e -> {
            String blk = model.selected();
            this.editor.setMode(PddImageEditor.Mode.CROP, r -> actions.setCrop(blk, r.x, r.y, r.width, r.height), () -> { });
            this.editor.requestFocusInWindow();
        });
        placeMarker.addActionListener(e -> {
            MarkerSlot m = markerSlot.getSelectedValue();
            ImageView v = model.image();
            if (m == null || v == null) return;
            String blk = model.selected();
            double w = v.image().getWidth();
            double h = v.image().getHeight();
            this.editor.setMode(PddImageEditor.Mode.MARKER, r -> actions.placeMarker(blk, m.ordinal(), r.x / w, r.y / h, r.width / w, r.height / h), () -> { });
            this.editor.requestFocusInWindow();
        });
        selectArea.getAccessibleContext().setAccessibleDescription("Arraste sobre a imagem (ou use as setas e Enter) para escolher a área que vai ao PDF.");
        placeMarker.getAccessibleContext().setAccessibleDescription("Marca a área da instrução escolhida; o marcador fica como manual e não comprova o elemento.");
        apply.getAccessibleContext().setAccessibleDescription("Substitui a imagem do bloco pela nova captura revisada.");
        cancelCandidate.getAccessibleContext().setAccessibleDescription("Descarta a nova captura; a imagem anterior continua.");
        export.getAccessibleContext().setAccessibleDescription("Gera um PDF local com versão nova; nunca substitui um PDF anterior.");
        capture.getAccessibleContext().setAccessibleDescription("Arma a captura sanitizada do laboratório; a imagem é tirada ao pressionar o atalho no Chrome.");
    }

    /** Mostra o modelo (na EDT). */
    public void show(Model m) {
        this.model = Objects.requireNonNull(m);
        updating = true;
        try {
            list.setItems(m.items());
            for (int i = 0; i < m.items().size(); i++) {
                if (m.items().get(i).key().equals(m.selected())) list.setSelectedIndex(i);
            }
        } finally {
            updating = false;
        }
        undo.setEnabled(m.canUndo());
        redo.setEnabled(m.canRedo());
        export.setEnabled(m.canExport());
        status.setStatus(m.statusKind(), m.status());
        banner.setVisible(!m.banner().isEmpty());
        banner.setSeverity(m.bannerSeverity());
        banner.setTitle(m.bannerTitle());
        banner.setMessage(m.banner());
        heading.setText(m.block() ? "Bloco" : "Seção");
        titleField.setText(m.title());
        titleState.setText(m.titleState());
        acceptTitle.setVisible(m.titleNeedsAttention());
        instructionsBox.removeAll();
        instructionFields.clear();
        for (InstructionRow r : m.instructions()) {
            NekkoraStack row = NekkoraStack.horizontal(Spacing.SPACE_2);
            row.add(new NekkoraText(r.ordinal() + ".", TextTone.SECONDARY));
            NekkoraTextField f = new NekkoraTextField(r.text());
            f.getAccessibleContext().setAccessibleName("Instrução " + r.ordinal());
            f.setColumns(28);
            instructionFields.add(f);
            row.add(f);
            NekkoraButton save = NekkoraButton.ghost("Salvar").withSize(ControlSize.SMALL);
            save.addActionListener(e -> actions.saveInstruction(m.selected(), r.ordinal(), f.getText()));
            row.add(save);
            if (r.needsAttention()) {
                NekkoraButton accept = NekkoraButton.ghost("Aceitar").withSize(ControlSize.SMALL);
                accept.addActionListener(e -> actions.acceptInstruction(m.selected(), r.ordinal()));
                row.add(accept);
            }
            row.add(new NekkoraText(r.state(), r.needsAttention() ? TextTone.SECONDARY : TextTone.MUTED));
            instructionsBox.add(row);
        }
        blockTools.setVisible(m.block());
        splitAt.removeAllItems();
        for (int i = 2; i <= m.instructions().size(); i++) splitAt.addItem(i);
        split.setEnabled(m.block() && m.instructions().size() > 1 && !m.capturing());
        joinNext.setEnabled(m.block() && m.canJoinNext() && !m.capturing());
        List<Tab> current = new ArrayList<>();
        for (int i = 0; i < tabs.getItemCount(); i++) current.add(tabs.getItemAt(i));
        if (!current.equals(m.tabs())) {
            tabs.removeAllItems();
            m.tabs().forEach(tabs::addItem);
        }
        capture.setText(m.hasImage() ? "Capturar novamente…" : "Capturar tela…");
        cancelCapture.setVisible(m.capturing());
        captureHint.setText(m.captureHint());
        captureHint.setVisible(m.block() && !m.captureHint().isEmpty());
        consent(m);
        List<Site> currentSites = new ArrayList<>();
        for (int i = 0; i < sites.getItemCount(); i++) currentSites.add(sites.getItemAt(i));
        if (!currentSites.equals(m.sites())) {
            sites.removeAllItems();
            m.sites().forEach(sites::addItem);
        }
        sitesRow.setVisible(m.block() && !m.sites().isEmpty());
        revokeSite.setEnabled(!m.sites().isEmpty());
        removeCapture.setEnabled(m.block() && m.hasImage() && !m.capturing() && !m.reviewing());
        ImageView iv = m.block() ? m.image() : null;
        imageBox.setVisible(iv != null);
        if (iv != null) {
            imageHeading.setText(iv.candidate() ? "Nova captura — revise e aplique" : "Imagem do bloco");
            imageNote.setText(("nekkora-visual/4".equals(iv.policy()) ? "Print real da tela: campos, textos de dados, tabelas e frames cobertos por faixas."
                    : "nekkora-visual/3".equals(iv.policy())
                    ? "Representação sintética desenhada pelo Studio: nenhum pixel da página; só rótulos de interface como texto, o resto em barras."
                    : "nekkora-visual/2".equals(iv.policy()) ? "Representação documental: estrutura redesenhada, só rótulos de interface reais."
                    : "Captura da política anterior: fundo oculto em cinza.") + (iv.crop() == null ? " Recorte automático." : " Recorte escolhido."));
            if (editor.mode() != PddImageEditor.Mode.VIEW) editor.setMode(PddImageEditor.Mode.VIEW, null, null);
            editor.setContent(iv.image(), iv.crop(), iv.marks());
            List<MarkerSlot> slots = new ArrayList<>();
            for (int i = 0; i < markerSlot.getItemCount(); i++) slots.add(markerSlot.getItemAt(i));
            if (!slots.equals(m.markers())) {
                MarkerSlot before = markerSlot.getSelectedValue();
                markerSlot.removeAllItems();
                m.markers().forEach(markerSlot::addItem);
                if (before != null) {
                    for (int i = 0; i < markerSlot.getItemCount(); i++) if (markerSlot.getItemAt(i).ordinal() == before.ordinal()) markerSlot.setSelectedIndex(i);
                }
            }
            MarkerSlot sel = markerSlot.getSelectedValue();
            placeMarker.setEnabled(!iv.candidate() && sel != null && sel.markable());
            removeMarker.setEnabled(!iv.candidate() && sel != null && sel.manual());
            reviewRow.setVisible(iv.candidate());
        } else {
            editor.setContent(null, null, List.of());
        }
        pendingBox.removeAll();
        if (m.pending().isEmpty()) pendingBox.add(new NekkoraText("Nenhuma pendência neste item.", TextTone.MUTED));
        for (String p : m.pending()) pendingBox.add(new NekkoraParagraph("• " + p));
        preview.setPages(m.preview());
        revalidate();
        repaint();
    }

    /** Escolha com largura máxima (o rótulo inteiro fica na dica). */
    private static <T> NekkoraComboBox<T> capped() {
        return new NekkoraComboBox<T>() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, CHOICE_MAX_WIDTH), d.height);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
    }

    /** STUDIO-013: o que a aba escolhida permite — capturar, autorizar o PDD neste site ou o motivo do bloqueio. */
    private void consent(Model m) {
        Tab t = tabs.getSelectedValue();
        boolean busy = m.capturing() || m.reviewing();
        capture.setEnabled(m.block() && t != null && t.ready() && !busy);
        String state = t == null ? "" : t.state();
        String origin = t == null || t.origin() == null ? "" : t.origin();
        String text = switch (state) {
            case "CONSENT_REQUIRED" -> "Esta página permite gravação, mas a captura de PDD ainda não está autorizada. Site: " + origin + ". A autorização vale só"
                    + " para esta origem exata; no PDD, campos, textos de dados, tabelas e frames saem cobertos por faixas.";
            case "CHROME_ACCESS_REQUIRED" -> "Sem acesso do Chrome a este site: conceda pelo ícone da Nekkora Extension (Acesso ao site → Neste site). Depois, autorize"
                    + " a captura do PDD.";
            case "RECORDING" -> "Esta aba está sendo gravada: finalize a gravação para capturar.";
            case "UPDATE_EXTENSION" -> "A captura de PDD em sites exige a Nekkora Extension 0.6.0 ou mais nova; hoje só o laboratório local captura.";
            case "READY" -> origin.isEmpty() || origin.startsWith("http://127.0.0.1") || origin.startsWith("http://localhost") ? ""
                    : "Captura do PDD autorizada em " + origin + ".";
            default -> "";
        };
        consentText.setText(text);
        boolean ask = "CONSENT_REQUIRED".equals(state) && !origin.isEmpty();
        authorize.setVisible(ask);
        remember.setVisible(ask);
        authorize.setEnabled(ask && !busy);
        consentBox.setVisible(m.block() && !text.isEmpty());
    }

    public Model model() {
        return model;
    }

    // acesso para o roteiro de homologação e testes de interface
    public NekkoraButton exportButton() {
        return export;
    }

    public NekkoraButton captureButton() {
        return capture;
    }

    public NekkoraText captureHintText() {
        return captureHint;
    }

    public NekkoraButton authorizeButton() {
        return authorize;
    }

    public NekkoraCheckBox rememberBox() {
        return remember;
    }

    public NekkoraParagraph consentText() {
        return consentText;
    }

    public NekkoraComboBox<Site> sitesCombo() {
        return sites;
    }

    public NekkoraButton revokeSiteButton() {
        return revokeSite;
    }

    public NekkoraButton undoButton() {
        return undo;
    }

    public NekkoraButton redoButton() {
        return redo;
    }

    public NekkoraButton saveTitleButton() {
        return saveTitle;
    }

    public NekkoraButton acceptTitleButton() {
        return acceptTitle;
    }

    public NekkoraButton splitButton() {
        return split;
    }

    public NekkoraButton joinNextButton() {
        return joinNext;
    }

    public NekkoraButton refreshButton() {
        return refresh;
    }

    public NekkoraTextField titleField() {
        return titleField;
    }

    public NekkoraList<Item> itemsList() {
        return list;
    }

    public NekkoraComboBox<Tab> tabsInput() {
        return tabs;
    }

    public NekkoraComboBox<Integer> splitInput() {
        return splitAt;
    }

    public PddImageEditor imageEditor() {
        return editor;
    }

    public NekkoraButton selectAreaButton() {
        return selectArea;
    }

    public NekkoraButton autoCropButton() {
        return autoCrop;
    }

    public NekkoraButton placeMarkerButton() {
        return placeMarker;
    }

    public NekkoraButton removeMarkerButton() {
        return removeMarker;
    }

    public NekkoraButton applyButton() {
        return apply;
    }

    public NekkoraButton cancelCandidateButton() {
        return cancelCandidate;
    }

    public NekkoraComboBox<MarkerSlot> markerInput() {
        return markerSlot;
    }

    public List<NekkoraTextField> instructionFields() {
        return List.copyOf(instructionFields);
    }

    /** Prévia: as páginas do PDF rasterizadas, uma embaixo da outra, ajustadas à largura. */
    static final class Preview extends JComponent {
        private List<BufferedImage> pages = List.of();

        void setPages(List<BufferedImage> p) {
            pages = List.copyOf(p);
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            int w = Math.max(200, getWidth());
            int h = 8;
            for (BufferedImage p : pages) h += (int) (p.getHeight() * (w - 16) / (double) p.getWidth()) + 12;
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int y = 8;
            int w = getWidth() - 16;
            for (BufferedImage p : pages) {
                int h = (int) (p.getHeight() * w / (double) p.getWidth());
                g2.drawImage(p, 8, y, w, h, null);
                y += h + 12;
            }
            g2.dispose();
        }
    }
}
