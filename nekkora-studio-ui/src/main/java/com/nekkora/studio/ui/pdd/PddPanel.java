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
    }

    /** Item da lista: seção ({@code secao-<n>}) ou bloco ({@code blk-…}). */
    public record Item(String key, String label, String secondary, boolean section) {}

    /** Instrução no editor: número, texto, situação ("Derivada", "Revisar", "Revisada", "Fora da política"). */
    public record InstructionRow(int ordinal, String text, String state, boolean needsAttention) {}

    /** Aba do Chrome para a captura. */
    public record Tab(String tabRef, String label) {}

    /** Tudo o que a tela mostra. */
    public record Model(List<Item> items, String selected, String title, String titleState, boolean titleNeedsAttention, boolean block,
                        List<InstructionRow> instructions, List<String> pending, boolean hasImage, boolean canJoinNext, List<Tab> tabs,
                        boolean capturing, String bannerTitle, String banner, Severity bannerSeverity, String status, Status statusKind,
                        List<BufferedImage> preview, boolean canUndo, boolean canRedo, boolean canExport) {
        public Model {
            items = List.copyOf(items);
            instructions = List.copyOf(instructions);
            pending = List.copyOf(pending);
            tabs = List.copyOf(tabs);
            preview = List.copyOf(preview);
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
    private final NekkoraComboBox<Tab> tabs = new NekkoraComboBox<>();
    private final NekkoraButton capture = NekkoraButton.secondary("Capturar tela…").withIcon(IconName.PLAY).withSize(ControlSize.SMALL);
    private final NekkoraButton cancelCapture = NekkoraButton.ghost("Cancelar captura").withSize(ControlSize.SMALL);
    private final NekkoraButton removeCapture = NekkoraButton.ghost("Remover imagem").withSize(ControlSize.SMALL);
    private final NekkoraStack blockTools = NekkoraStack.vertical(Spacing.SPACE_2);
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
        NekkoraStack captureRow = NekkoraStack.horizontal(Spacing.SPACE_2);
        tabs.setLabelProvider(t -> t == null ? "" : t.label());
        tabs.getAccessibleContext().setAccessibleName("Aba do Chrome (laboratório) para a captura");
        captureRow.add(tabs);
        captureRow.add(capture);
        captureRow.add(cancelCapture);
        captureRow.add(removeCapture);
        blockTools.add(captureRow);
        editor.add(blockTools);
        editor.add(new NekkoraText("Pendências", TextTone.SECONDARY));
        editor.add(pendingBox);
        NekkoraScrollPane editorScroll = NekkoraScrollPane.verticalOnly(editor);

        NekkoraScrollPane previewScroll = NekkoraScrollPane.verticalOnly(preview);
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
        removeCapture.addActionListener(e -> actions.removeCapture(model.selected()));
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
        capture.setEnabled(m.block() && !m.tabs().isEmpty() && !m.capturing());
        cancelCapture.setVisible(m.capturing());
        removeCapture.setEnabled(m.block() && m.hasImage() && !m.capturing());
        pendingBox.removeAll();
        if (m.pending().isEmpty()) pendingBox.add(new NekkoraText("Nenhuma pendência neste item.", TextTone.MUTED));
        for (String p : m.pending()) pendingBox.add(new NekkoraParagraph("• " + p));
        preview.setPages(m.preview());
        revalidate();
        repaint();
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
