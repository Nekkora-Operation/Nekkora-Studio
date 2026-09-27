package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.kit.Page;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.dialog.NekkoraConfirmationDialog;
import com.nekkora.ui.dialog.NekkoraDialogPanel;
import com.nekkora.ui.dialog.NekkoraModalForm;
import com.nekkora.ui.dialog.NekkoraPopover;
import com.nekkora.ui.feedback.NekkoraToast;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.state.NekkoraEmptyState;
import com.nekkora.ui.state.NekkoraErrorState;
import com.nekkora.ui.state.NekkoraLoadingState;
import com.nekkora.ui.state.NekkoraNoResultsState;
import com.nekkora.ui.state.NekkoraOfflineState;
import com.nekkora.ui.state.NekkoraPermissionState;
import com.nekkora.ui.typography.NekkoraParagraph;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Diálogos e estados. */
public final class DialogStatePages {
    private DialogStatePages() {}

    public static JComponent dialogs() {
        Page p = new Page("Diálogos", "Diálogo, confirmação, confirmação destrutiva, formulário modal e popover. "
                + "Esc cancela, Enter confirma (exceto ações destrutivas), o foco volta à origem ao fechar.");
        NekkoraButton confirm = NekkoraButton.secondary("Confirmação");
        confirm.addActionListener(e -> {
            boolean ok = NekkoraConfirmationDialog.confirm(confirm, "Publicar projeto?",
                    "A versão atual ficará disponível para execução.", "Publicar");
            NekkoraToast.show(confirm, ok ? Severity.SUCCESS : Severity.INFO, ok ? "Publicado" : "Cancelado", null);
        });
        NekkoraButton danger = NekkoraButton.danger("Destrutiva").withIcon(IconName.TRASH);
        danger.addActionListener(e -> NekkoraConfirmationDialog.confirmDanger(danger, "Excluir projeto?",
                "Esta ação não pode ser desfeita. Todas as versões e o histórico serão removidos.", "Excluir projeto",
                "conciliacao"));
        NekkoraButton form = NekkoraButton.primary("Formulário modal").withIcon(IconName.PLUS);
        form.addActionListener(e -> {
            NekkoraTextField nameField = new NekkoraTextField("Ex.: Conciliação mensal");
            NekkoraFormField name = new NekkoraFormField("Nome", nameField).required();
            NekkoraFormField type = new NekkoraFormField("Modelo",
                    new NekkoraComboBox<>(List.of("Em branco", "Planilha → sistema", "E-mail → pasta")));
            NekkoraModalForm f = new NekkoraModalForm(form, "Novo projeto", "Criar projeto", m -> {
                boolean valid = !nameField.getText().isBlank();
                name.setError(valid ? null : "Informe um nome.");
                return valid;
            });
            f.setDescription("Você poderá alterar tudo depois.");
            f.addField(name).addField(type).addField(new NekkoraFormField("", new NekkoraCheckBox("Abrir após criar", true)));
            f.showDialog();
        });
        NekkoraButton pop = NekkoraButton.ghost("Popover").withTrailingIcon(IconName.CHEVRON_DOWN);
        pop.addActionListener(e -> NekkoraPopover.show(pop, new Fixed(Page.column(
                new NekkoraParagraph("Filtros rápidos"), new NekkoraCheckBox("Somente ativos", true),
                new NekkoraCheckBox("Com falhas recentes")), 220, 0)));
        p.section("Abrir", null, Page.tightRow(confirm, danger, form, pop));

        NekkoraDialogPanel preview = new NekkoraDialogPanel("Renomear projeto");
        preview.setDescription("O nome aparece na navegação e nos relatórios.");
        preview.setContent(new NekkoraFormField("Nome", new NekkoraTextField("Conciliação bancária")));
        preview.addFooterButton(NekkoraButton.secondary("Cancelar"));
        preview.addFooterButton(NekkoraButton.primary("Renomear"));
        NekkoraDialogPanel dangerPreview = new NekkoraDialogPanel("Excluir 3 itens?");
        dangerPreview.setMarker(IconName.ALERT, Tone.ERROR);
        dangerPreview.setDescription("Os itens serão movidos para a lixeira por 30 dias.");
        dangerPreview.addFooterButton(NekkoraButton.secondary("Cancelar"));
        dangerPreview.addFooterButton(NekkoraButton.danger("Excluir"));
        p.section("Anatomia", "Cabeçalho (título, descrição, fechar), conteúdo e ações à direita — primária por último.",
                Page.row(new Fixed(preview, 460, 0), new Fixed(dangerPreview, 420, 0)));
        return p;
    }

    public static JComponent states() {
        Page p = new Page("Estados", "Vazio, erro, carregando, sem resultados, sem permissão e offline. "
                + "Explicam o que aconteceu e oferecem o próximo passo.");
        JPanel grid = new JPanel(new GridLayout(0, 3, 16, 16));
        grid.setOpaque(false);
        grid.add(card(new NekkoraEmptyState(IconName.FOLDER, "Nenhum projeto ainda", "Crie o primeiro projeto para começar.")
                .addAction(NekkoraButton.primary("Novo projeto").withIcon(IconName.PLUS))));
        grid.add(card(new NekkoraErrorState(() -> { })));
        grid.add(card(new NekkoraLoadingState("Carregando projetos…")));
        grid.add(card(new NekkoraNoResultsState("conciliaçao", () -> { })));
        grid.add(card(new NekkoraPermissionState()));
        grid.add(card(new NekkoraOfflineState(() -> { })));
        p.section("Galeria", null, grid);
        return p;
    }

    private static JComponent card(JComponent state) {
        NekkoraCard c = new NekkoraCard();
        c.setContent(state);
        return new Fixed(c, 0, 320);
    }
}
