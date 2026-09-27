package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.kit.Page;
import com.nekkora.tokens.ControlSize;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Interaction;
import com.nekkora.ui.core.NekkoraUI;
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
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/** Campos de texto, formulários e seleção. */
public final class InputPages {
    private InputPages() {}

    public static JComponent textFields() {
        Page p = new Page("Campos", "Campos de uma linha, senha, busca e área de texto. O NekkoraFormField junta rótulo, "
                + "indicador de obrigatório/opcional, ajuda e erro — e associa o rótulo ao controle.");
        NekkoraTextField def = new NekkoraTextField("Nome do projeto");
        NekkoraTextField focus = new NekkoraTextField("Nome do projeto");
        NekkoraUI.forceInteraction(focus, Interaction.FOCUSED);
        NekkoraTextField filled = new NekkoraTextField();
        filled.setText("Conciliação mensal");
        NekkoraTextField error = new NekkoraTextField();
        error.setText("conciliação#01");
        error.setError(true);
        NekkoraTextField disabled = new NekkoraTextField("Indisponível");
        disabled.setEnabled(false);
        NekkoraTextField readOnly = new NekkoraTextField();
        readOnly.setText("PRJ-2024-0192");
        readOnly.setReadOnly(true);
        p.section("Estados", null, Page.row(
                Page.specimen("padrão", w(def)), Page.specimen("foco", w(focus)), Page.specimen("preenchido", w(filled)),
                Page.specimen("erro", w(error)), Page.specimen("desabilitado", w(disabled)),
                Page.specimen("somente leitura", w(readOnly))));

        NekkoraFormField name = new NekkoraFormField("Nome", new NekkoraTextField("Ex.: Conciliação mensal"))
                .required().helper("Aparece na lista de projetos.");
        NekkoraTextField emailField = new NekkoraTextField();
        emailField.setText("contato@empresa");
        NekkoraFormField email = new NekkoraFormField("E-mail de notificação", emailField).optional();
        email.setError("Informe um e-mail válido, como nome@empresa.com.");
        NekkoraFormField pass = new NekkoraFormField("Senha", new NekkoraPasswordField("Mínimo de 8 caracteres")).required();
        NekkoraFormField notes = new NekkoraFormField("Descrição", new NekkoraTextArea("Descreva o objetivo…", 3).scrollable())
                .optional().helper("Até 500 caracteres.");
        p.section("Formulário", null, new Fixed(Page.column(name, email, pass, notes), 440, 0));

        NekkoraSearchField search = new NekkoraSearchField("Buscar projetos…");
        NekkoraTextField small = new NekkoraTextField("Pequeno");
        small.setControlSize(ControlSize.SMALL);
        NekkoraTextField large = new NekkoraTextField("Grande");
        large.setControlSize(ControlSize.LARGE);
        NekkoraTextField withIcon = new NekkoraTextField("https://");
        withIcon.setLeadingIcon(IconName.LINK);
        withIcon.setClearable(true);
        p.section("Busca, ícones e tamanhos", "Busca: Esc limpa, Enter busca na hora, digitação com debounce.",
                Page.row(Page.specimen("busca", w(search)), Page.specimen("ícone + limpar", w(withIcon)),
                        Page.specimen("SMALL", w(small)), Page.specimen("LARGE", w(large))));

        NekkoraIntegerField qty = new NekkoraIntegerField("0");
        qty.setRange(1, 10);
        qty.setValue(3);
        NekkoraDecimalField money = new NekkoraDecimalField("0,00", Locale.forLanguageTag("pt-BR"));
        money.setValue(new java.math.BigDecimal("1250.5"));
        NekkoraDateField date = new NekkoraDateField();
        date.setValue(java.time.LocalDate.of(2026, 9, 27));
        NekkoraTimeField time = new NekkoraTimeField();
        time.setValue(java.time.LocalTime.of(14, 30));
        p.section("Campos especializados", "Inteiro (1–10, ↑/↓ incrementam), decimal (vírgula em pt-BR), data e hora com máscara.",
                Page.row(Page.specimen("inteiro", w(qty, 120)), Page.specimen("decimal", w(money, 140)),
                        Page.specimen("data", w(date, 160)), Page.specimen("hora", w(time, 110))));
        return p;
    }

    public static JComponent selection() {
        Page p = new Page("Seleção", "Combo, caixas de seleção, opções exclusivas e interruptor. "
                + "Todos com foco visível e operáveis pelo teclado.");
        NekkoraComboBox<String> env = new NekkoraComboBox<>(List.of("Produção", "Homologação", "Desenvolvimento"));
        env.setIconProvider(s -> IconName.SERVER);
        NekkoraComboBox<String> placeholder = new NekkoraComboBox<>(List.of("Diário", "Semanal", "Mensal"));
        placeholder.setSelectedIndex(-1);
        placeholder.setPlaceholder("Selecione a frequência");
        NekkoraComboBox<String> err = new NekkoraComboBox<>(List.of("—"));
        err.setError(true);
        NekkoraComboBox<String> dis = new NekkoraComboBox<>(List.of("Bloqueado"));
        dis.setEnabled(false);
        p.section("Combo", null, Page.row(Page.specimen("com ícones", w(env)), Page.specimen("placeholder", w(placeholder)),
                Page.specimen("erro", w(err)), Page.specimen("desabilitado", w(dis))));

        NekkoraCheckBox c1 = new NekkoraCheckBox("Notificar por e-mail", true);
        NekkoraCheckBox c2 = new NekkoraCheckBox("Repetir em caso de falha");
        NekkoraCheckBox c3 = new NekkoraCheckBox("Selecionar todos");
        c3.setIndeterminate(true);
        NekkoraCheckBox c4 = new NekkoraCheckBox("Foco");
        NekkoraUI.forceInteraction(c4, Interaction.FOCUSED);
        NekkoraCheckBox c5 = new NekkoraCheckBox("Desabilitado", true);
        c5.setEnabled(false);
        p.section("Caixas de seleção", "Inclui o estado indeterminado (seleção parcial).", Page.tightRow(c1, c2, c3, c4, c5));

        NekkoraRadioButton r1 = new NekkoraRadioButton("Manual", true);
        NekkoraRadioButton r2 = new NekkoraRadioButton("Agendado");
        NekkoraRadioButton r3 = new NekkoraRadioButton("Por evento");
        NekkoraRadioButton.group(r1, r2, r3);
        NekkoraRadioButton r4 = new NekkoraRadioButton("Desabilitado");
        r4.setEnabled(false);
        p.section("Opções exclusivas", "Setas movem entre as opções do grupo.", Page.tightRow(r1, r2, r3, r4));

        NekkoraSwitch s1 = new NekkoraSwitch("Ativo", true);
        NekkoraSwitch s2 = new NekkoraSwitch("Modo detalhado", false);
        NekkoraSwitch s3 = new NekkoraSwitch("Foco", false);
        NekkoraUI.forceInteraction(s3, Interaction.FOCUSED);
        NekkoraSwitch s4 = new NekkoraSwitch("Desabilitado", true);
        s4.setEnabled(false);
        p.section("Interruptor", "Efeito imediato. Para escolhas que dependem de \"Salvar\", use caixa de seleção.",
                Page.tightRow(s1, s2, s3, s4));
        return p;
    }

    private static JComponent w(JComponent c) {
        return new Fixed(c, 220, 0);
    }

    private static JComponent w(JComponent c, int width) {
        return new Fixed(c, width, 0);
    }
}
