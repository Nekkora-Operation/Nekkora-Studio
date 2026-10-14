package com.nekkora.studio.app.ui;

import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.action.NekkoraAction;
import com.nekkora.ui.action.NekkoraActionRegistry;
import com.nekkora.ui.action.NekkoraKeys;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.theme.ThemeManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.KeyStroke;

/**
 * Ações do Studio (rótulo, ícone, atalho) registradas no {@link NekkoraActionRegistry} do design system.
 * As ações futuras (gravar, executar, salvar) existem apenas <b>desabilitadas</b>: não têm efeito e não
 * produzem nenhum resultado fictício.
 */
public final class StudioActions {
    public static final String NEW_AUTOMATION = "studio.automation.new";
    public static final String NEW_SESSION = "studio.session.new";
    public static final String NEW_STEP = "studio.step.new";
    public static final String RENAME_SESSION = "studio.session.rename";
    public static final String NEXT_THEME = "studio.view.theme";
    public static final String RECORD = "studio.future.record";
    public static final String RUN = "studio.future.run";
    public static final String SAVE = "studio.future.save";

    /** Ações que dependem de missões futuras e permanecem desabilitadas. */
    public static final List<String> FUTURE = List.of(RECORD, RUN, SAVE);

    private final NekkoraActionRegistry registry = new NekkoraActionRegistry();

    public StudioActions(StudioController controller) {
        registry.register(NekkoraAction.of(NEW_AUTOMATION, "Nova automação", controller::newAutomation)
                .icon(IconName.PLUS).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_N)).category("Automação"));
        registry.register(NekkoraAction.of(NEW_SESSION, "Nova sessão", controller::newSession)
                .icon(IconName.LAYERS).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_N, InputEvent.SHIFT_DOWN_MASK)).category("Automação"));
        registry.register(NekkoraAction.of(NEW_STEP, "Nova etapa", () -> controller.newStep(null))
                .icon(IconName.LIST).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_E)).category("Automação"));
        registry.register(NekkoraAction.of(RENAME_SESSION, "Renomear sessão", controller::renameSelectedSession)
                .icon(IconName.EDIT).shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)).category("Automação"));
        registry.register(NekkoraAction.of(NEXT_THEME, "Próximo tema", StudioActions::cycleTheme)
                .icon(IconName.SUN).shortcut(NekkoraKeys.shortcut(KeyEvent.VK_T, InputEvent.SHIFT_DOWN_MASK)).category("Exibir"));
        future(RECORD, "Gravar", IconName.PLAY_CIRCLE, "A gravação de etapas chega em uma próxima versão do Studio.");
        future(RUN, "Executar", IconName.PLAY, "A execução de automações chega em uma próxima versão do Studio.");
        future(SAVE, "Salvar", IconName.SAVE, "O salvamento ainda não está disponível: as automações ficam apenas na memória.");
        for (String id : List.of(NEW_SESSION, NEW_STEP, RENAME_SESSION)) get(id).setEnabled(false);
    }

    private void future(String id, String label, IconName icon, String why) {
        NekkoraAction a = NekkoraAction.of(id, label, () -> { }).icon(icon).category("Próximas versões").description(why);
        a.setEnabled(false);
        registry.register(a);
    }

    public NekkoraActionRegistry registry() {
        return registry;
    }

    public NekkoraAction get(String id) {
        return registry.find(id).orElseThrow();
    }

    /** Alterna para o próximo dos quatro temas, ao vivo. */
    public static void cycleTheme() {
        List<NekkoraTheme> all = NekkoraThemes.all();
        ThemeManager.setTheme(all.get((all.indexOf(ThemeManager.current()) + 1) % all.size()));
    }
}
