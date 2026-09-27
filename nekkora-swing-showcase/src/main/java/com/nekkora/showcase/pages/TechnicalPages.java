package com.nekkora.showcase.pages;

import com.nekkora.showcase.kit.Fixed;
import com.nekkora.showcase.kit.Page;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.container.NekkoraSurface;
import com.nekkora.ui.file.NekkoraFilePathField;
import com.nekkora.ui.file.NekkoraRecentFileItem;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.technical.NekkoraCodeBlock;
import com.nekkora.ui.technical.NekkoraCopyableValue;
import com.nekkora.ui.technical.NekkoraIdentifierLabel;
import com.nekkora.ui.technical.NekkoraJsonViewer;
import com.nekkora.ui.technical.NekkoraLogViewer;
import com.nekkora.ui.technical.NekkoraMonospaceTextArea;
import com.nekkora.tokens.Spacing;
import java.awt.BorderLayout;
import javax.swing.JComponent;

/** Informação técnica e arquivos. */
public final class TechnicalPages {
    private TechnicalPages() {}

    public static JComponent technical() {
        Page p = new Page("Informação técnica", "Código, logs, JSON, IDs e valores copiáveis — sempre em JetBrains Mono.");
        NekkoraCodeBlock code = new NekkoraCodeBlock("""
                SELECT id, nome, status
                  FROM projetos
                 WHERE status = 'ATIVO'
                 ORDER BY atualizado_em DESC;""", "sql");
        p.section("Bloco de código", null, new Fixed(code, 560, 150));

        NekkoraLogViewer log = new NekkoraLogViewer();
        log.append(NekkoraLogViewer.Level.INFO, "Iniciando sessão de trabalho");
        log.append(NekkoraLogViewer.Level.DEBUG, "Carregando configuração de C:\\Nekkora\\config.json");
        log.append(NekkoraLogViewer.Level.SUCCESS, "Etapa 1/3 concluída em 1,2 s");
        log.append(NekkoraLogViewer.Level.WARN, "Etapa 2/3 demorou mais que o esperado (8,4 s)");
        log.append(NekkoraLogViewer.Level.ERROR, "Etapa 3/3 falhou: tempo limite excedido");
        NekkoraButton more = NekkoraButton.secondary("Adicionar linha").withIcon(IconName.PLUS);
        int[] n = {0};
        more.addActionListener(e -> log.append(NekkoraLogViewer.Level.INFO, "Evento de exemplo #" + (++n[0])));
        NekkoraSurface logBox = NekkoraSurface.outlined(new BorderLayout());
        logBox.add(log);
        p.section("Logs", "Cores por nível, rolagem automática (pausa se você rolar para cima), limite de linhas.",
                Page.column(new Fixed(logBox, 0, 170), more));

        NekkoraJsonViewer json = new NekkoraJsonViewer();
        json.setJson("{\"id\":\"prj_7f3a91c2\",\"ativo\":true,\"tentativas\":3,\"timeout\":12.5,\"tags\":[\"financeiro\",\"mensal\"],\"dono\":null}");
        NekkoraSurface jsonBox = NekkoraSurface.outlined(new BorderLayout());
        jsonBox.add(json);
        NekkoraMonospaceTextArea mono = new NekkoraMonospaceTextArea("chave=valor\ntimeout=30\nmodo=estrito", 4);
        p.section("JSON e texto monoespaçado", null, Page.row(new Fixed(jsonBox, 360, 230), new Fixed(mono, 300, 0)));

        p.section("Identificadores e valores", "Clique no identificador (ou Enter) para copiar o valor completo.", Page.row(
                Page.specimen("identificador", new NekkoraIdentifierLabel("5f0c2a9e-81d3-4b7a-9c61-2e4d8f7a1b3c")),
                Page.specimen("copiável", new NekkoraCopyableValue("https://api.exemplo.com/v1/hooks/7f3a"))));
        return p;
    }

    public static JComponent files() {
        Page p = new Page("Arquivos", "Somente apresentação: a aplicação decide o que ler e gravar.");
        NekkoraFilePathField open = new NekkoraFilePathField(NekkoraFilePathField.Mode.OPEN_FILE, "Selecione um arquivo .json");
        open.setExtensions("json");
        NekkoraFilePathField folder = new NekkoraFilePathField(NekkoraFilePathField.Mode.FOLDER, "Pasta de saída");
        NekkoraFilePathField save = new NekkoraFilePathField(NekkoraFilePathField.Mode.SAVE_FILE, "Salvar relatório como…");
        save.setExtensions("csv");
        p.section("Campos de caminho", null, new Fixed(Page.column(
                new NekkoraFormField("Arquivo de parâmetros", open),
                new NekkoraFormField("Pasta de saída", folder),
                new NekkoraFormField("Exportar relatório", save)), 520, 0));
        NekkoraStack recents = NekkoraStack.vertical(Spacing.SPACE_1);
        recents.add(new NekkoraRecentFileItem("conciliacao.nkp", "C:\\Users\\ana\\Documentos\\Nekkora\\financeiro\\conciliacao.nkp", "há 5 min"));
        recents.add(new NekkoraRecentFileItem("leitura-emails.nkp", "D:\\Projetos\\operacoes\\leitura-emails.nkp", "ontem"));
        recents.add(new NekkoraRecentFileItem("parametros.json", "C:\\Nekkora\\config\\ambientes\\producao\\parametros.json", "3 dias", IconName.BRACES));
        p.section("Recentes", "Caminhos longos são truncados no meio; o completo fica no tooltip.", new Fixed(recents, 520, 0));
        return p;
    }
}
