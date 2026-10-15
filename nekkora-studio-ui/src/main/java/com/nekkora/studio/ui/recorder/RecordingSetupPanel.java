package com.nekkora.studio.ui.recorder;

import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.tokens.ControlSize;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraScrollPane;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraComboBox;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.tokens.TypeStyle;
import com.nekkora.ui.typography.NekkoraParagraph;
import com.nekkora.ui.typography.NekkoraText;
import com.nekkora.ui.typography.TextTone;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;

/**
 * Conteúdo do diálogo "Iniciar gravação": destino lógico (automação e sessão), conexão com o navegador, abas do
 * Chrome (somente título mascarado e host — nunca URL ou id interno) e modo. Abas sem autorização aparecem, mas não
 * podem ser escolhidas: a autorização é dada pelo usuário na própria extensão.
 */
public final class RecordingSetupPanel extends JPanel {

    /** Escolha do usuário. */
    public record Choice(SessionId session, BrowserTab tab, RecordingMode mode) {
    }

    private final NekkoraComboBox<LogicalSession> session = new NekkoraComboBox<>();
    private final NekkoraInlineAlert connection = new NekkoraInlineAlert(Severity.INFO, "", "");
    private final NekkoraStack tabList = NekkoraStack.vertical(Spacing.SPACE_3);
    private final NekkoraParagraph tabHint = note("", TextTone.SECONDARY);
    private final NekkoraButton refresh = NekkoraButton.ghost("Atualizar abas").withIcon(IconName.REFRESH).withSize(ControlSize.SMALL);
    private final NekkoraRadioButton captureOnly = new NekkoraRadioButton(RecordingMode.CAPTURE_ONLY.label());
    private final NekkoraRadioButton captureAndInteract = new NekkoraRadioButton(RecordingMode.CAPTURE_AND_INTERACT.label(), true);
    private final List<NekkoraRadioButton> tabButtons = new ArrayList<>();
    private final List<BrowserTab> tabs = new ArrayList<>();
    private ButtonGroup tabGroup = new ButtonGroup();
    private Runnable onChange = () -> { };

    public RecordingSetupPanel(Automation automation, SessionId preselected, Runnable onRefresh) {
        super(new BorderLayout());
        setOpaque(false);
        NekkoraStack col = NekkoraStack.vertical(Spacing.SPACE_5);

        col.add(new NekkoraText("Automação: " + automation.name()));
        session.setLabelProvider(s -> s == null ? "" : s.name());
        for (LogicalSession s : automation.sessions()) {
            session.addItem(s);
            if (s.id().equals(preselected)) {
                session.setSelectedItem(s);
            }
        }
        session.getAccessibleContext().setAccessibleName("Sessão lógica de destino");
        col.add(new NekkoraFormField("Sessão de destino", session).helper("A gravação pertence a esta sessão; dá para trocar depois no Recorder."));

        connection.setVisible(false);
        col.add(connection);

        NekkoraStack tabHead = NekkoraStack.horizontal(Spacing.SPACE_3).align(NekkoraStack.Align.CENTER);
        tabHead.add(new NekkoraText("Aba do Chrome"));
        tabHead.addGlue();
        refresh.onClick(e -> onRefresh.run());
        tabHead.add(refresh);
        col.add(tabHead);
        NekkoraScrollPane scroll = NekkoraScrollPane.verticalOnly(tabList);
        scroll.setPreferredSize(new Dimension(10, com.nekkora.ui.theme.Scale.px(150)));
        col.add(scroll);
        col.add(tabHint);

        NekkoraRadioButton.group(captureOnly, captureAndInteract);
        captureOnly.setToolTipText(RecordingMode.CAPTURE_ONLY.description());
        captureAndInteract.setToolTipText(RecordingMode.CAPTURE_AND_INTERACT.description());
        NekkoraStack modes = NekkoraStack.horizontal(Spacing.SPACE_5);
        modes.add(new NekkoraText("Modo:"));
        modes.add(captureOnly);
        modes.add(captureAndInteract);
        modes.addGlue();
        col.add(modes);
        col.add(note(RecorderText.MODE_NOTICE, TextTone.MUTED));
        col.add(note(RecorderText.SETUP_NOTICE, TextTone.SECONDARY));
        add(col, BorderLayout.CENTER);
        showLoading();
    }

    private static NekkoraParagraph note(String text, TextTone tone) {
        NekkoraParagraph p = new NekkoraParagraph(text, tone);
        p.setTypeStyle(TypeStyle.CAPTION);
        return p;
    }

    public void onSelectionChange(Runnable r) {
        this.onChange = r == null ? () -> { } : r;
    }

    /** Estado da conexão com o navegador. */
    public void showLink(LinkStatus status) {
        if (status != null && status.connected()) {
            connection.setVisible(false);
        } else {
            connection.setSeverity(Severity.WARNING);
            connection.setTitle(RecorderText.WAITING_LINK);
            connection.setMessage(RecorderText.connectHint(status));
            connection.setVisible(true);
        }
        revalidate();
    }

    public void showLoading() {
        clearTabs();
        tabList.add(NekkoraText.muted("Consultando as abas do Chrome…"));
        tabHint.setText("");
        revalidate();
        repaint();
    }

    public void showError(String message) {
        clearTabs();
        tabList.add(NekkoraText.muted(message));
        revalidate();
        repaint();
    }

    /** Abas devolvidas pela extensão; a primeira aba autorizada e ativa vem selecionada. */
    public void showTabs(List<BrowserTab> list) {
        clearTabs();
        if (list.isEmpty()) {
            tabList.add(NekkoraText.muted("Nenhuma aba aberta no Chrome."));
        }
        NekkoraRadioButton preferred = null;
        // Autorizadas primeiro, depois as que pedem autorização; páginas internas do navegador por último.
        List<BrowserTab> sorted = new ArrayList<>(list);
        sorted.sort(java.util.Comparator.comparingInt(t -> t.access().allowed() ? 0 : t.host() != null ? 1 : 2));
        for (BrowserTab t : sorted) {
            String status = t.recording() ? "já em gravação" : t.access().label();
            String text = t.label().equals(t.access().label()) ? status
                    : t.label() + (t.host() != null && !t.host().equals(t.label()) ? " · " + t.host() : "") + "  —  " + status;
            NekkoraRadioButton b = new NekkoraRadioButton(text);
            b.setEnabled(t.access().allowed() && !t.recording());
            b.getAccessibleContext().setAccessibleDescription(t.access().allowed() ? "Página autorizada"
                    : "Página não autorizada: conceda acesso ao site na Nekkora Extension");
            b.addActionListener(e -> onChange.run());
            tabGroup.add(b);
            tabButtons.add(b);
            tabs.add(t);
            tabList.add(b);
            if (b.isEnabled() && (preferred == null || t.active())) {
                preferred = preferred == null || t.active() ? b : preferred;
            }
        }
        if (preferred != null) {
            preferred.setSelected(true);
        }
        boolean someDenied = list.stream().anyMatch(t -> t.access() == com.nekkora.studio.recorder.TabAccess.PERMISSION_REQUIRED);
        tabHint.setText(someDenied ? "Sites não autorizados não podem ser gravados. Abra a Nekkora Extension no Chrome e conceda acesso ao site; depois atualize a lista."
                : "");
        revalidate();
        repaint();
        onChange.run();
    }

    private void clearTabs() {
        tabList.removeAll();
        tabButtons.clear();
        tabs.clear();
        tabGroup = new ButtonGroup();
    }

    /** Escolha completa (sessão, aba autorizada e modo), se houver. */
    public Optional<Choice> choice() {
        LogicalSession s = session.getSelectedValue();
        for (int i = 0; i < tabButtons.size(); i++) {
            if (tabButtons.get(i).isSelected() && tabButtons.get(i).isEnabled() && s != null) {
                return Optional.of(new Choice(s.id(), tabs.get(i), captureOnly.isSelected() ? RecordingMode.CAPTURE_ONLY : RecordingMode.CAPTURE_AND_INTERACT));
            }
        }
        return Optional.empty();
    }

    // ---- acesso para testes e evidências
    public List<NekkoraRadioButton> tabButtons() {
        return List.copyOf(tabButtons);
    }

    public NekkoraButton refreshButton() {
        return refresh;
    }

    public NekkoraRadioButton modeButton(RecordingMode m) {
        return m == RecordingMode.CAPTURE_ONLY ? captureOnly : captureAndInteract;
    }

    public NekkoraInlineAlert connectionAlert() {
        return connection;
    }

    public NekkoraParagraph tabHint() {
        return tabHint;
    }
}
