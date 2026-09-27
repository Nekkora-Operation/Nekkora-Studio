package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.kit.Page;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.container.NekkoraSurface;
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
import com.nekkora.ui.data.NekkoraTableModel.Column;
import com.nekkora.ui.data.NekkoraTag;
import com.nekkora.ui.data.NekkoraTree;
import com.nekkora.ui.data.NekkoraTreeNode;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.navigation.NekkoraPopupMenu;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** Exibição de dados. */
public final class DataPages {
    private DataPages() {}

    /** Linha fictícia para exemplos (a lib não conhece o domínio). */
    public record Row(String name, Status status, String id, int items, String updated) {}

    public static List<Row> sampleRows() {
        return List.of(
                new Row("Conciliação bancária", Status.SUCCESS, "prj_7f3a91c2", 1280, "há 2 min"),
                new Row("Leitura de e-mails", Status.RUNNING, "prj_1c9b44d0", 342, "agora"),
                new Row("Emissão de notas", Status.FAILED, "prj_a02e7c19", 57, "há 14 min"),
                new Row("Cadastro de fornecedores", Status.PENDING, "prj_55d1e8aa", 0, "há 1 h"),
                new Row("Relatório semanal", Status.PAUSED, "prj_93be0f71", 12, "ontem"),
                new Row("Migração de planilhas", Status.WARNING, "prj_0dd2c6b5", 768, "há 3 h"),
                new Row("Integração ERP", Status.DRAFT, "prj_c44f1a2e", 0, "há 2 dias"),
                new Row("Arquivo morto", Status.DISABLED, "prj_e1a7730f", 9, "há 1 mês"));
    }

    public static NekkoraTableModel<Row> sampleModel() {
        NekkoraTableModel<Row> model = new NekkoraTableModel<>(List.of(
                Column.<Row>text("Nome", Row::name).width(240),
                Column.<Row>status("Status", Row::status).width(140),
                Column.<Row>mono("ID", Row::id).width(140),
                Column.<Row>number("Itens", Row::items).width(80),
                Column.<Row>secondary("Atualizado", Row::updated).width(110)));
        model.setRows(sampleRows());
        return model;
    }

    public static NekkoraTreeNode sampleTree() {
        NekkoraTreeNode root = new NekkoraTreeNode("Workspace", IconName.WORKSPACE);
        NekkoraTreeNode fin = new NekkoraTreeNode("Financeiro", IconName.FOLDER).setCount(3);
        fin.add(new NekkoraTreeNode("Conciliação bancária", IconName.WORKFLOW).setStatus(Status.SUCCESS),
                new NekkoraTreeNode("Emissão de notas", IconName.WORKFLOW).setStatus(Status.FAILED),
                new NekkoraTreeNode("parametros.json", IconName.BRACES).setSecondary("2 KB"));
        NekkoraTreeNode ops = new NekkoraTreeNode("Operações", IconName.FOLDER).setCount(2);
        ops.add(new NekkoraTreeNode("Leitura de e-mails", IconName.WORKFLOW).setStatus(Status.RUNNING),
                new NekkoraTreeNode("Relatório semanal", IconName.WORKFLOW).setStatus(Status.PAUSED));
        NekkoraTreeNode drafts = new NekkoraTreeNode("Rascunhos", IconName.FOLDER);
        drafts.add(new NekkoraTreeNode("Integração ERP", IconName.WORKFLOW).setIconTone(Tone.ACCENT).setSecondary("rascunho"));
        root.add(fin, ops, drafts);
        return root;
    }

    public static JComponent data() {
        Page p = new Page("Dados", "Tabela, árvore, lista, pares chave-valor, badges, tags e marcadores. "
                + "Status sempre com ícone + texto — nunca só cor.");
        NekkoraTable table = new NekkoraTable(sampleModel());
        table.setRowMenu(row -> new NekkoraPopupMenu()
                .item("Abrir", IconName.EXTERNAL_LINK, null, () -> { })
                .item("Duplicar", IconName.COPY, null, () -> { })
                .separator()
                .danger("Excluir", IconName.TRASH, () -> { }));
        table.setHoverRow(2);
        table.setRowSelectionInterval(0, 0);
        NekkoraSurface tbox = NekkoraSurface.outlined(new BorderLayout());
        tbox.add(table.scrollable());
        NekkoraTable empty = new NekkoraTable(new NekkoraTableModel<Row>(List.of(Column.<Row>text("Nome", Row::name), Column.<Row>status("Status", Row::status))));
        empty.setEmptyState(IconName.SEARCH, "Nenhum projeto encontrado", "Ajuste os filtros ou crie um novo projeto.");
        NekkoraSurface ebox = NekkoraSurface.outlined(new BorderLayout());
        ebox.add(empty.scrollable());
        p.section("Tabela", "Cabeçalho ordenável, seleção, hover, status, menu por linha (clique direito / Shift+F10), "
                + "Enter ativa a linha. Estado vazio embutido.", Page.column(new Fixed(tbox, 0, 330), new Fixed(ebox, 520, 220)));

        NekkoraTree tree = new NekkoraTree(sampleTree());
        tree.expandAll();
        tree.setSelectionRow(2);
        tree.setHoverRow(5);
        NekkoraSurface trbox = NekkoraSurface.outlined(new BorderLayout());
        trbox.add(tree.scrollable());
        NekkoraList<Row> list = new NekkoraList<>(new ArrayList<>(sampleRows().subList(0, 5)))
                .withLabel(Row::name).withSecondary(r -> r.id() + " · " + r.updated(), true).withIcon(r -> IconName.WORKFLOW);
        list.setSelectedIndex(1);
        NekkoraSurface lbox = NekkoraSurface.outlined(new BorderLayout());
        lbox.add(new com.nekkora.ui.container.NekkoraScrollPane(list));
        p.section("Árvore e lista", "Árvore com ícones, status e contadores (setas expandem/recolhem); lista de duas linhas.",
                Page.row(new Fixed(trbox, 340, 300), new Fixed(lbox, 340, 300)));

        NekkoraKeyValueView kv = new NekkoraKeyValueView()
                .add("Nome", "Conciliação bancária")
                .add("Status", new NekkoraStatusBadge(Status.SUCCESS))
                .addMono("Pasta", "C:\\Nekkora\\projetos\\financeiro")
                .addCopyable("ID", "prj_7f3a91c2")
                .add("Última execução", "27/09/2026 14:32");
        p.section("Chave e valor", null, new Fixed(kv, 460, 0));

        NekkoraStack statuses = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        for (Status s : Status.values()) statuses.add(new NekkoraStatusBadge(s));
        statuses.addGlue();
        NekkoraStack soft = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        NekkoraStack solid = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        NekkoraStack outline = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        for (Tone t : Tone.values()) {
            String name = t.name().charAt(0) + t.name().substring(1).toLowerCase();
            soft.add(new NekkoraBadge(name, t));
            solid.add(new NekkoraBadge(name, t, NekkoraBadge.Variant.SOLID));
            outline.add(new NekkoraBadge(name, t, NekkoraBadge.Variant.OUTLINE).withDot());
        }
        soft.add(NekkoraBadge.count(7));
        soft.add(NekkoraBadge.count(128));
        soft.addGlue();
        solid.addGlue();
        outline.addGlue();
        p.section("Status", "O sistema de status é genérico: cada aplicação mapeia seus estados para estes.", statuses);
        p.section("Badges", "SOFT (padrão), SOLID (texto branco AA) e OUTLINE; contadores.", Page.column(soft, solid, outline));

        NekkoraStack tags = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        tags.add(new NekkoraTag("financeiro"));
        tags.add(new NekkoraTag("prioridade alta", Tone.WARNING).withIcon(IconName.STAR));
        NekkoraTag removable = new NekkoraTag("filtro: ativos", Tone.ACCENT);
        removable.removable(() -> removable.setVisible(false));
        tags.add(removable);
        tags.addGlue();
        p.section("Tags", "Removíveis com o \"x\" ou Delete quando focadas.", tags);

        NekkoraStack markers = NekkoraStack.horizontal(Spacing.SPACE_5).align(NekkoraStack.Align.CENTER);
        markers.add(new NekkoraIconMarker(IconName.WORKFLOW, Tone.ACCENT));
        markers.add(new NekkoraIconMarker(IconName.SERVER, Tone.INFO));
        markers.add(new NekkoraIconMarker(IconName.CHECK_CIRCLE, Tone.SUCCESS, false, 32));
        markers.add(new NekkoraIconMarker(IconName.ALERT, Tone.WARNING, false, 32));
        markers.add(new NekkoraAvatar("Nicolly Carvalho", 32));
        markers.add(new NekkoraAvatar("Equipe Operações", 32));
        markers.add(new NekkoraAvatar("Ana", 24));
        markers.addGlue();
        p.section("Marcadores e avatares", null, markers);
        return p;
    }
}
