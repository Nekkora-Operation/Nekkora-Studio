package com.nekkora.studio.ui.develop;

import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.Background;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.data.NekkoraList;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.navigation.NekkoraTabs;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JComponent;

/**
 * STUDIO-014 — "Desenvolver automação": um clique inicia a análise (sem formulário). A janela explica em poucas linhas o
 * que será feito e o que NÃO será, mostra as fases, o que será usado do framework (com onde consultar), de onde vieram
 * as etapas, as variáveis derivadas com procedência, as pendências específicas (com resposta, ir para a etapa, abrir o
 * PDD) e o resultado (arquivos e verificações realmente executadas). Caminhos e configuração técnica ficam em "Opções
 * avançadas". Textos sem valores, URLs completas ou segredos.
 */
public final class DevelopmentPanel extends NekkoraPanel {

    public static final String TITLE = "Desenvolver automação";
    public static final String EXPLANATION = "Vou usar o PDD e a gravação já validados para interpretar as etapas, escolher as Libs existentes do Nekkora "
            + "Framework, organizar as classes e gerar um projeto Python. Depois verificarei a qualidade do código. Não executarei ações em sistemas reais "
            + "sem sua autorização.";

    /** Pedidos do usuário. */
    public interface Actions {
        void develop();

        void cancel();

        void saveProject();

        void resolve(QualityIssue issue, AssistanceRequest.Option option);

        /** Resposta curta digitada (ex.: endereço inicial mascarado pela privacidade da gravação). */
        void answerText(QualityIssue issue, String text);

        void apply(QualityIssue issue);

        void goTo(QualityIssue issue);

        void openPdd(QualityIssue issue);

        void showDocs(String capabilityId);

        void openCode();

        void showReport();

        void validate();

        void analyzeWithAi();

        void chooseOutput();

        void choosePython();

        void showConfig();

        void legacyGenerator();

        void close();
    }

    /** Estado de uma fase. */
    public enum PhaseState {
        PENDING("○"), RUNNING("…"), DONE("✓"), BLOCKED("×"), SKIPPED("—");

        private final String symbol;

        PhaseState(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    public record Phase(String label, PhaseState state, String detail) {}

    /** Um componente do framework usado (com onde consultar). */
    public record Use(String id, String name, String group, String purpose, String where) {}

    /** Pergunta da desenvolvedora → resposta do Studio (derivada do caso real). */
    public record Answer(String question, String answer) {}

    public record Variable(String name, String type, String origin, String provenance) {}

    /** Modelo completo da janela. */
    public record Model(String subject, boolean projectSaved, boolean running, String statusTitle, String statusText, com.nekkora.ui.core.Severity severity,
                        List<Phase> phases, List<Use> uses, List<Answer> answers, List<Variable> variables, List<QualityIssue> pending, List<String> results,
                        String outputDir, String python, boolean canOpenCode, boolean aiAvailable) {
        public Model {
            phases = List.copyOf(phases);
            uses = List.copyOf(uses);
            answers = List.copyOf(answers);
            variables = List.copyOf(variables);
            pending = List.copyOf(pending);
            results = List.copyOf(results);
        }

        public static Model empty(String subject, boolean saved) {
            return new Model(subject, saved, false, "", "", com.nekkora.ui.core.Severity.INFO, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    "", "", false, false);
        }
    }

    private final Actions actions;
    private final NekkoraInlineAlert status = new NekkoraInlineAlert(com.nekkora.ui.core.Severity.INFO, "", "");
    private final NekkoraText phases = new NekkoraText("");
    private final NekkoraTabs tabs = new NekkoraTabs();
    private final NekkoraList<Answer> answers = new NekkoraList<>();
    private final NekkoraList<Use> uses = new NekkoraList<>();
    private final NekkoraList<Variable> variables = new NekkoraList<>();
    private final NekkoraList<QualityIssue> pending = new NekkoraList<>();
    private final NekkoraParagraph pendingDetail = new NekkoraParagraph("");
    private final NekkoraStack pendingButtons = NekkoraStack.horizontal(Spacing.SPACE_3);
    private final NekkoraList<String> results = new NekkoraList<>();
    private final NekkoraText output = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraText python = new NekkoraText("", TextTone.SECONDARY);
    private final NekkoraButton develop = NekkoraButton.primary("Desenvolver").withIcon(IconName.PLAY);
    private final NekkoraButton cancel = NekkoraButton.secondary("Cancelar");
    private final NekkoraButton save = NekkoraButton.secondary("Salvar projeto").withIcon(IconName.SAVE);
    private final NekkoraButton openCode = NekkoraButton.secondary("Abrir código").withIcon(IconName.FOLDER_OPEN);
    private final NekkoraButton report = NekkoraButton.secondary("Ver relatório");
    private final NekkoraButton validate = NekkoraButton.secondary("Validar novamente").withIcon(IconName.CHECK_CIRCLE);
    private final NekkoraButton help = NekkoraButton.secondary("Solicitar ajuda");
    private final NekkoraButton docs = NekkoraButton.secondary("Ver documentação").withSize(ControlSize.SMALL);
    private Model model = Model.empty("", false);

    public DevelopmentPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = Objects.requireNonNull(actions);
        setSurface(Background.SURFACE);
        getAccessibleContext().setAccessibleName(TITLE);

        NekkoraStack head = NekkoraStack.vertical(Spacing.SPACE_3);
        head.add(new NekkoraText(TITLE, NekkoraText.Size.LARGE, TextTone.PRIMARY));
        NekkoraParagraph explanation = new NekkoraParagraph(EXPLANATION, TextTone.SECONDARY);
        explanation.getAccessibleContext().setAccessibleName("O que será feito");
        head.add(explanation);
        head.add(status);
        phases.getAccessibleContext().setAccessibleName("Fases");
        head.add(phases);
        add(head, BorderLayout.NORTH);

        // aba Plano: perguntas e respostas, o que vou utilizar, variáveis
        answers.withLabel(Answer::question).withSecondary(Answer::answer, true);
        answers.getAccessibleContext().setAccessibleName("De onde vem cada coisa");
        uses.withLabel(u -> u.name() + " · " + u.group()).withSecondary(u -> u.purpose() + " — Onde consultar: " + u.where(), true);
        uses.getAccessibleContext().setAccessibleName("O que vou utilizar neste projeto");
        uses.onItemActivated(u -> actions.showDocs(u.id()));
        uses.addListSelectionListener(e -> docs.setEnabled(uses.getSelectedValue() != null));
        docs.onClick(e -> Optional.ofNullable(uses.getSelectedValue()).ifPresent(u -> actions.showDocs(u.id())));
        docs.setEnabled(false);
        variables.withLabel(v -> v.name() + " · " + v.type() + " · " + v.origin()).withSecondary(Variable::provenance, false);
        variables.getAccessibleContext().setAccessibleName("Variáveis derivadas");
        NekkoraStack plan = NekkoraStack.vertical(Spacing.SPACE_3);
        plan.add(new NekkoraText("Respostas do Studio para este projeto"));
        plan.add(fixed(answers, 210));
        NekkoraStack usesHead = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        usesHead.add(new NekkoraText("O que vou utilizar neste projeto"));
        usesHead.addGlue();
        usesHead.add(docs);
        plan.add(usesHead);
        plan.add(fixed(uses, 190));
        plan.add(new NekkoraText("Variáveis e credenciais (só nomes e tipos; nenhum valor)"));
        plan.add(fixed(variables, 120));
        tabs.addTab("Plano", IconName.LIST, scroll(plan));

        // aba Pendências
        pending.withLabel(i -> i.kind().label() + " · " + i.title()).withSecondary(QualityIssue::detail, true);
        pending.getAccessibleContext().setAccessibleName("Pendências");
        pending.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshPending();
        });
        pendingDetail.getAccessibleContext().setAccessibleName("Pergunta e orientação");
        NekkoraPanel pend = new NekkoraPanel(new BorderLayout());
        pend.add(new NekkoraScrollPane(pending), BorderLayout.CENTER);
        NekkoraStack pendFoot = NekkoraStack.vertical(Spacing.SPACE_3);
        pendFoot.add(pendingDetail);
        pendFoot.add(pendingButtons);
        pend.add(pendFoot, BorderLayout.SOUTH);
        tabs.addTab("Pendências", IconName.ALERT, pend);

        // aba Resultado
        results.withLabel(s -> s);
        results.getAccessibleContext().setAccessibleName("Resultado do desenvolvimento");
        tabs.addTab("Resultado", IconName.FILE, new NekkoraScrollPane(results));

        // aba Opções avançadas (nada disso é necessário no fluxo padrão)
        NekkoraStack adv = NekkoraStack.vertical(Spacing.SPACE_3);
        adv.add(new NekkoraParagraph("Não é preciso mexer aqui: a pasta de saída, a configuração técnica e o Python de verificação são derivados "
                + "automaticamente. Use só se quiser inspecionar ou mudar.", TextTone.SECONDARY));
        adv.add(output);
        NekkoraButton chooseOut = NekkoraButton.secondary("Mudar a pasta de saída…");
        chooseOut.onClick(e -> actions.chooseOutput());
        adv.add(chooseOut);
        adv.add(python);
        NekkoraButton choosePy = NekkoraButton.secondary("Python para verificação…");
        choosePy.onClick(e -> actions.choosePython());
        adv.add(choosePy);
        NekkoraButton cfg = NekkoraButton.secondary("Ver a configuração derivada");
        cfg.onClick(e -> actions.showConfig());
        adv.add(cfg);
        NekkoraButton legacy = NekkoraButton.ghost("Gerador avançado (configuração manual)…");
        legacy.onClick(e -> actions.legacyGenerator());
        adv.add(legacy);
        tabs.addTab("Opções avançadas", IconName.SETTINGS, scroll(adv));
        add(tabs, BorderLayout.CENTER);

        NekkoraStack foot = NekkoraStack.horizontal(Spacing.SPACE_3);
        develop.setName("develop.start");
        develop.onClick(e -> actions.develop());
        cancel.setName("develop.cancel");
        cancel.onClick(e -> actions.cancel());
        save.onClick(e -> actions.saveProject());
        openCode.onClick(e -> actions.openCode());
        report.onClick(e -> actions.showReport());
        validate.onClick(e -> actions.validate());
        help.onClick(e -> actions.analyzeWithAi());
        help.getAccessibleContext().setAccessibleDescription("Mostra a próxima pergunta pendente; com IA autorizada, oferece analisar as pendências.");
        foot.addAll(develop, cancel, save, openCode, report, validate, help);
        foot.addGlue();
        NekkoraButton close = NekkoraButton.ghost("Fechar");
        close.onClick(e -> actions.close());
        foot.add(close);
        NekkoraPanel south = new NekkoraPanel(new BorderLayout());
        south.setPadding(Spacing.SPACE_3, Spacing.SPACE_1);
        south.add(foot, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        show(model);
    }

    private static JComponent fixed(NekkoraList<?> list, int height) {
        NekkoraScrollPane sp = new NekkoraScrollPane(list);
        sp.setPreferredSize(new Dimension(200, com.nekkora.ui.theme.Scale.px(height)));
        sp.setMinimumSize(new Dimension(100, com.nekkora.ui.theme.Scale.px(Math.min(height, 90))));
        return sp;
    }

    private static JComponent scroll(JComponent c) {
        NekkoraPanel p = new NekkoraPanel(new BorderLayout());
        p.add(c, BorderLayout.NORTH);
        return NekkoraScrollPane.verticalOnly(p);
    }

    /** Mostra o estado (na EDT). */
    public void show(Model m) {
        QualityIssue keep = pending.getSelectedValue();
        this.model = Objects.requireNonNull(m);
        status.setSeverity(m.severity());
        status.setTitle(m.statusTitle().isEmpty() ? (m.projectSaved() ? "Pronto para começar" : "Salve o projeto antes") : m.statusTitle());
        status.setMessage(m.statusText().isEmpty() ? (m.projectSaved() ? "Use Desenvolver: o Studio analisa a automação ativa e o PDD. Nada é executado."
                : "O código vai para uma pasta ao lado do arquivo do projeto: salve o projeto (Ctrl+S) e depois use Desenvolver.") : m.statusText());
        List<String> ph = new ArrayList<>();
        for (Phase p : m.phases()) ph.add(p.state().symbol() + " " + p.label());
        phases.setText(String.join("   ", ph));
        phases.getAccessibleContext().setAccessibleDescription(String.join("; ", m.phases().stream().map(p -> p.label() + ": " + p.state().name()
                + (p.detail().isEmpty() ? "" : " (" + p.detail() + ")")).toList()));
        phases.setVisible(!m.phases().isEmpty());
        answers.setItems(m.answers());
        uses.setItems(m.uses());
        variables.setItems(m.variables());
        pending.setItems(m.pending());
        tabs.setTitleAt(1, m.pending().isEmpty() ? "Pendências" : "Pendências (" + m.pending().size() + ")");
        if (keep != null) {
            for (int i = 0; i < m.pending().size(); i++) if (m.pending().get(i).id().equals(keep.id())) pending.setSelectedIndex(i);
        }
        if (pending.getSelectedIndex() < 0 && !m.pending().isEmpty()) pending.setSelectedIndex(0);
        results.setItems(m.results());
        output.setText("Pasta de saída: " + (m.outputDir().isEmpty() ? "(salve o projeto)" : m.outputDir()));
        python.setText("Python para verificação: " + (m.python().isEmpty() ? "não configurado (as verificações do código ficam \"não verificado\")" : m.python()));
        develop.setEnabled(!m.running() && m.projectSaved());
        develop.setText(m.results().isEmpty() ? "Desenvolver" : "Desenvolver de novo");
        cancel.setVisible(m.running());
        save.setVisible(!m.projectSaved());
        openCode.setEnabled(!m.running() && m.canOpenCode());
        report.setEnabled(!m.running());
        validate.setEnabled(!m.running() && m.projectSaved());
        help.setEnabled(!m.running());
        help.setText(m.aiAvailable() ? "Solicitar ajuda (IA opcional)" : "Solicitar ajuda");
        refreshPending();
        revalidate();
        repaint();
    }

    private void refreshPending() {
        pendingButtons.removeAll();
        QualityIssue i = pending.getSelectedValue();
        if (i == null) {
            pendingDetail.setText(model.pending().isEmpty() ? "Nenhuma pendência." : "");
        } else {
            StringBuilder b = new StringBuilder();
            i.assistance().ifPresent(h -> b.append(h.question()).append("\n").append(h.why()).append(h.destinationLabel().isEmpty() ? ""
                    : "\nA resposta vai para: " + h.destinationLabel()).append("\n"));
            if (b.length() == 0) b.append(i.detail()).append("\n");
            if (!i.guidance().isEmpty()) b.append(i.guidance());
            pendingDetail.setText(b.toString().strip());
            i.assistance().ifPresent(h -> {
                for (AssistanceRequest.Option o : h.options()) {
                    if (o.isText()) {
                        com.nekkora.ui.input.NekkoraTextField field = new com.nekkora.ui.input.NekkoraTextField("", 36);
                        field.setName("develop.answerText");
                        field.getAccessibleContext().setAccessibleName(o.label());
                        field.setToolTipText(o.basis());
                        NekkoraButton ok = NekkoraButton.primary("Confirmar " + o.label().toLowerCase(java.util.Locale.ROOT)).withSize(ControlSize.SMALL);
                        ok.onClick(e -> actions.answerText(i, field.getText()));
                        field.addActionListener(e -> actions.answerText(i, field.getText()));
                        pendingButtons.add(field);
                        pendingButtons.add(ok);
                        continue;
                    }
                    NekkoraButton ob = o.isAnswer() ? NekkoraButton.primary(o.label()) : NekkoraButton.secondary(o.label());
                    ob.withSize(ControlSize.SMALL);
                    ob.setToolTipText(o.basis());
                    ob.getAccessibleContext().setAccessibleDescription(o.basis());
                    ob.onClick(e -> actions.resolve(i, o));
                    pendingButtons.add(ob);
                }
            });
            i.correction().ifPresent(f -> {
                NekkoraButton ab = NekkoraButton.primary(i.kind() == QualityIssue.Kind.AUTO_FIX_SAFE ? f.label() : "Revisar e aplicar: " + f.label())
                        .withSize(ControlSize.SMALL);
                ab.onClick(e -> actions.apply(i));
                pendingButtons.add(ab);
            });
            if (i.step() != null) {
                NekkoraButton go = NekkoraButton.secondary("Ir para etapa").withIcon(IconName.ARROW_RIGHT).withSize(ControlSize.SMALL);
                go.onClick(e -> actions.goTo(i));
                pendingButtons.add(go);
            }
            if (i.layer() == com.nekkora.studio.ai.quality.Layer.V1_PDD) {
                NekkoraButton pdd = NekkoraButton.secondary("Abrir PDD").withIcon(IconName.FILE).withSize(ControlSize.SMALL);
                pdd.onClick(e -> actions.openPdd(i));
                pendingButtons.add(pdd);
            }
            if (!i.capability().isEmpty()) {
                NekkoraButton d = NekkoraButton.secondary("Ver função/documentação").withSize(ControlSize.SMALL);
                d.onClick(e -> actions.showDocs(i.capability()));
                pendingButtons.add(d);
            }
        }
        pendingButtons.revalidate();
        pendingButtons.repaint();
    }

    /** Mostra a aba de pendências com a primeira pergunta selecionada ("Solicitar ajuda"). */
    public void focusPending() {
        tabs.setSelectedIndex(1);
        for (int i = 0; i < model.pending().size(); i++) {
            if (model.pending().get(i).kind() == QualityIssue.Kind.HELP_REQUIRED) {
                pending.setSelectedIndex(i);
                break;
            }
        }
        pending.requestFocusInWindow();
    }

    public void focusResults() {
        tabs.setSelectedIndex(2);
    }

    // ------------------------------------------------------------------ acesso para testes/homologação
    public Model model() {
        return model;
    }

    public NekkoraTabs tabs() {
        return tabs;
    }

    public NekkoraList<QualityIssue> pendingList() {
        return pending;
    }

    public NekkoraStack pendingButtons() {
        return pendingButtons;
    }

    public NekkoraButton developButton() {
        return develop;
    }

    public NekkoraButton openCodeButton() {
        return openCode;
    }

    public NekkoraInlineAlert statusAlert() {
        return status;
    }

    public NekkoraText phasesText() {
        return phases;
    }

    public NekkoraList<Use> usesList() {
        return uses;
    }
}
