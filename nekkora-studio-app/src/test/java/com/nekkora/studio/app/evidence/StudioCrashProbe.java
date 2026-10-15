package com.nekkora.studio.app.evidence;

import com.nekkora.studio.bridge.BridgeServer;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.RecorderController;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.recorder.RecordingTarget;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Processo "Studio" para o cenário de encerramento ABRUPTO (homologação S002-018): publica o Bridge real, inicia uma
 * gravação técnica real pela extensão e fica parado — o roteiro de homologação mata este processo sem aviso.
 * Uso: {@code StudioCrashProbe <tituloDaAba> <leaseMs>}. Imprime {@code READY} quando a gravação está confirmada.
 */
public final class StudioCrashProbe {
    private StudioCrashProbe() {
    }

    public static void main(String[] args) throws Exception {
        String title = args[0];
        long lease = Long.parseLong(args[1]);
        BridgeServer bridge = BridgeServer.start(BridgeServer.Options.defaults("crash-probe"));
        RecorderController rec = new RecorderController(bridge, Clock.systemUTC(),
                new RecorderController.Options(Duration.ofMillis(lease), Duration.ofMillis(Math.max(1000, lease / 3)), Duration.ofSeconds(20), Duration.ofSeconds(60)));
        long deadline = System.currentTimeMillis() + 60_000;
        while (!bridge.status().connected()) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("extensão não conectou: " + bridge.status());
            Thread.sleep(100);
        }
        List<BrowserTab> tabs = rec.listTabs().get();
        BrowserTab tab = tabs.stream().filter(t -> title.equals(t.title())).findFirst().orElseThrow(() -> new IllegalStateException("aba não encontrada: " + tabs));
        Automation a = Automation.create("Sonda de queda", "", Clock.systemUTC());
        LogicalSession s = a.addSession("Sessão", "");
        rec.start(new RecordingTarget(a.id(), s.id()), tab, RecordingMode.CAPTURE_ONLY);
        while (rec.snapshot().state() != RecorderState.RECORDING) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("gravação não confirmada: " + rec.snapshot());
            Thread.sleep(50);
        }
        System.out.println("READY");
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE); // será morto sem aviso
    }
}
