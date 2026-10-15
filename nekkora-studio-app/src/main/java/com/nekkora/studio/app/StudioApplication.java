package com.nekkora.studio.app;

import com.nekkora.studio.app.recorder.SwingRecorderPrompts;
import com.nekkora.studio.app.recorder.SwingRecorderWindowHost;
import com.nekkora.studio.app.ui.SwingStudioPrompts;
import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.Nekkora;
import com.nekkora.ui.NekkoraFrame;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Ponto de entrada do Nekkora Studio. Abre direto no ambiente local de trabalho — sem login, sem licença,
 * sem dados fictícios. Uso: {@code java -jar nekkora-studio.jar [--theme=sakura-dark|midnight-dark|porcelain-light|sage-light]}.
 */
public final class StudioApplication {
    public static final String TITLE = "Nekkora Studio";

    private StudioApplication() {}

    public static final String VERSION = "1.0.0-SNAPSHOT";

    public static void main(String[] args) {
        Optional<NekkoraTheme> theme = themeArg(args);
        // Bridge local publicado FORA da EDT, antes da janela (operações de arquivo e socket).
        BridgeServer bridge = BridgeServer.start(BridgeServer.Options.defaults(VERSION));
        Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "nekkora-bridge-close"));
        SwingUtilities.invokeLater(() -> launch(theme, bridge, () -> {
            bridge.close(); // apaga a descoberta e invalida a credencial antes de sair
            System.exit(0);
        }));
    }

    /**
     * Instala o design system, monta o Studio e mostra a janela (na EDT).
     *
     * @param afterExit executado depois que a janela é fechada (a aplicação real encerra a JVM)
     */
    public static Window launch(Optional<NekkoraTheme> theme, Runnable afterExit) {
        return launch(theme, BrowserLink.offline("DISABLED"), afterExit);
    }

    /** Monta o Studio com a ligação com o navegador ({@link BridgeServer} na aplicação real). */
    public static Window launch(Optional<NekkoraTheme> theme, BrowserLink link, Runnable afterExit) {
        if (theme.isPresent()) Nekkora.install(theme.get());
        else Nekkora.install();
        NekkoraFrame frame = new NekkoraFrame(TITLE);
        AtomicReference<Studio> ref = new AtomicReference<>();
        Studio studio = Studio.create(Clock.systemDefaultZone(), new SwingStudioPrompts(frame::getRootPane), () -> {
            ref.get().dispose();
            frame.dispose();
            afterExit.run();
        }, link, new SwingRecorderPrompts(frame::getRootPane),
                panel -> new SwingRecorderWindowHost(() -> frame, panel, () -> ref.get().recorderPresenter().closeRequest().run()));
        ref.set(studio);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                studio.controller().requestExit();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                studio.view().navigator().createButton().requestFocusInWindow();
            }
        });
        frame.getContentPane().add(studio.view().component(), BorderLayout.CENTER);
        frame.sizeToScreen();
        frame.setVisible(true);
        return new Window(frame, studio);
    }

    /** Janela aberta e a montagem correspondente. */
    public record Window(NekkoraFrame frame, Studio studio) {}

    static Optional<NekkoraTheme> themeArg(String[] args) {
        for (String a : args) {
            if (!a.startsWith("--theme=")) continue;
            String id = a.substring("--theme=".length());
            return NekkoraThemes.all().stream().filter(t -> t.id().equals(id)).findFirst();
        }
        return Optional.empty();
    }
}
