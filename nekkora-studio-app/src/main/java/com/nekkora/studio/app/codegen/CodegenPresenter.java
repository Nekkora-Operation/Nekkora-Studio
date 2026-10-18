package com.nekkora.studio.app.codegen;

import com.nekkora.studio.app.workspace.Notice;
import com.nekkora.studio.app.workspace.ProjectState;
import com.nekkora.studio.app.workspace.WorkspaceService;
import com.nekkora.studio.codegen.CodegenConfig;
import com.nekkora.studio.codegen.OutputWriter;
import com.nekkora.studio.codegen.PythonGenerator;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.RuleSet;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.dialog.NekkoraDialog;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.technical.NekkoraMonospaceTextArea;
import java.awt.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JTextField;

/**
 * STUDIO-011 — "Automação → Gerar código Python…": gera, a partir do MODELO validado da automação ativa, o projeto
 * Python que consome {@code nekkora_rpa}. Pré-voo com bloqueios explícitos (validação estática, decisões do debug
 * guiado, ações fora do recorte, localizador posicional, valor literal); nada é executado. A configuração de geração é
 * um arquivo do projeto ({@code <projeto>.codegen.json}, sem valores) e a saída fica numa pasta própria, protegida
 * contra edição manual (arquivo editado → {@code .novo} + {@code .diff}).
 *
 * <p>O plano é calculado com as MESMAS regras do debug guiado ({@link RuleSet#V1_1}), para que a revisão do plano no
 * manifesto seja a mesma da passagem de debug.
 */
public final class CodegenPresenter {

    private final WorkspaceService service;
    private final Supplier<Window> owner;
    private NekkoraDialog dialog;
    private JTextField config;
    private JTextField out;
    private NekkoraMonospaceTextArea result;

    public CodegenPresenter(WorkspaceService service, Supplier<Window> owner) {
        this.service = service;
        this.owner = owner;
    }

    public void open() {
        Automation a = service.state().activeAutomation().orElse(null);
        if (a == null) {
            service.report(new Notice(Notice.Kind.WARNING, "Geração indisponível", "Abra uma automação para gerar o código Python."));
            return;
        }
        Path project = service.state().project(a.id()).flatMap(ProjectState::path).orElse(null);
        String base = project == null ? null : project.getFileName().toString().replaceFirst("\\.nekkora$", "");
        dialog = new NekkoraDialog(owner.get(), "Gerar código Python");
        dialog.setDescription("Gera o projeto Python (nekkora_rpa) a partir do modelo validado. Nada é executado; o código gerado não contém "
                + "valores de planilha nem credenciais. Correções vão para o modelo, não para o código.");
        dialog.setDialogSize(NekkoraDialog.Size.LARGE);
        config = field("codegen.config", project == null ? "" : project.resolveSibling(base + ".codegen.json").toString());
        out = field("codegen.out", project == null ? "" : project.resolveSibling(base + "-python").toString());
        result = new NekkoraMonospaceTextArea("", 14);
        result.setEditable(false);
        result.setName("codegen.result");
        result.getAccessibleContext().setAccessibleName("Resultado da geração");
        NekkoraStack content = NekkoraStack.vertical(Spacing.SPACE_5).addAll(
                row("Configuração de geração", config, false),
                row("Pasta de saída", out, true),
                new NekkoraScrollPane(result));
        dialog.setContent(content);
        dialog.addSecondary("Fechar");
        dialog.addPrimary("Gerar", () -> {
            result.setText(generate(a, Path.of(config.getText().strip()), Path.of(out.getText().strip())));
            result.setCaretPosition(0);
            return false; // continua aberto para ler o resultado
        }).setName("codegen.generate");
        dialog.setLocationRelativeTo(owner.get());
        dialog.setVisible(true);
    }

    /** Geração (também usada pela homologação sem janela). Devolve o relatório em texto, só com códigos e caminhos. */
    public static String generate(Automation a, Path configFile, Path outDir) {
        StringBuilder b = new StringBuilder();
        CodegenConfig cfg;
        try {
            cfg = CodegenConfig.parse(Files.readString(configFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "BLOQUEADO: configuração ilegível (" + configFile.getFileName() + ")\n";
        } catch (CodegenConfig.Invalid e) {
            return "BLOQUEADO: configuração recusada — " + e.code() + "\n";
        }
        PythonGenerator.Result r = PythonGenerator.generate(a, new AutomationValidator(RuleSet.V1_1).validate(a), cfg);
        b.append("Revisão do plano: ").append(r.planRevision()).append('\n');
        b.append("Configuração: ").append(cfg.revision()).append('\n');
        if (!r.ok()) {
            b.append("BLOQUEADO — nenhum arquivo gerado. Bloqueios do pré-voo:\n");
            for (PythonGenerator.Blocker x : r.blockers()) b.append("  ").append(x).append('\n');
            return b.toString();
        }
        try {
            OutputWriter.Report rep = OutputWriter.write(outDir, r.files());
            b.append(rep.protectedAny() ? "GERADO COM PENDÊNCIA — arquivo(s) editado(s) à mão preservado(s); veja .novo e .diff:\n"
                    : "GERADO em " + outDir + ":\n");
            for (Map.Entry<String, OutputWriter.Status> e : rep.files().entrySet()) {
                b.append("  ").append(e.getValue()).append("  ").append(e.getKey()).append('\n');
            }
        } catch (IOException e) {
            b.append("FALHA ao gravar: ").append(e.getClass().getSimpleName()).append('\n');
        }
        return b.toString();
    }

    private static JTextField field(String name, String text) {
        NekkoraTextField f = new NekkoraTextField("", 60);
        f.setText(text);
        f.setName(name);
        return f;
    }

    private NekkoraStack row(String label, JTextField f, boolean directory) {
        NekkoraButton choose = NekkoraButton.secondary("Escolher…");
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(f.getText().isBlank() ? null : Path.of(f.getText()).getParent().toFile());
            fc.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) f.setText(fc.getSelectedFile().toString());
        });
        return NekkoraStack.horizontal(Spacing.SPACE_4).addAll(new NekkoraFormField(label, f), choose);
    }
}
