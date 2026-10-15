package com.nekkora.studio.app.recorder;

import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.SessionId;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.ui.recorder.RecordingSetupPanel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Diálogos do Recorder (reais em Swing; roteirizados nos testes). Chamados na EDT. */
public interface RecorderPrompts {

    /** Fechar o Recorder com a gravação em andamento. */
    enum CloseDecision { CONTINUE, FINISH, CANCEL }

    /** Fechar o Studio com a gravação em andamento. */
    enum ExitDecision { STAY, FINISH, CANCEL }

    /** Fonte das abas do Chrome para o diálogo de início (assíncrona: nunca bloqueia a EDT). */
    interface SetupSource {
        LinkStatus link();

        CompletableFuture<List<BrowserTab>> tabs();
    }

    Optional<RecordingSetupPanel.Choice> chooseRecording(Automation automation, SessionId preselected, SetupSource source);

    CloseDecision askClose();

    ExitDecision askExit();

    /** Sem diálogos (Studio sem janela): nunca inicia gravação; fechar/sair seguem o caminho seguro. */
    static RecorderPrompts none() {
        return new RecorderPrompts() {
            @Override
            public Optional<RecordingSetupPanel.Choice> chooseRecording(Automation automation, SessionId preselected, SetupSource source) {
                return Optional.empty();
            }

            @Override
            public CloseDecision askClose() {
                return CloseDecision.CONTINUE;
            }

            @Override
            public ExitDecision askExit() {
                return ExitDecision.CANCEL;
            }
        };
    }

    /** Apoio dos diálogos reais: fonte simples a partir de dois fornecedores. */
    static SetupSource source(Supplier<LinkStatus> link, Supplier<CompletableFuture<List<BrowserTab>>> tabs) {
        return new SetupSource() {
            @Override
            public LinkStatus link() {
                return link.get();
            }

            @Override
            public CompletableFuture<List<BrowserTab>> tabs() {
                return tabs.get();
            }
        };
    }
}
