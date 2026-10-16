package com.nekkora.studio.app.project;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configurações locais do Studio (STUDIO-006): por enquanto, só a recuperação automática. Ela é DESLIGADA até o
 * usuário consentir (§23: nada de arquivos de recuperação criados em segredo). Sem diretório de dados (testes), fica em
 * memória.
 */
public final class StudioSettings {
    /** Intervalos oferecidos para a recuperação automática. */
    public static final List<Integer> INTERVALS = List.of(60, 120, 300);
    public static final int DEFAULT_INTERVAL = 120;
    private static final Logger LOG = Logger.getLogger(StudioSettings.class.getName());

    /** Consentimento informado para a recuperação automática. */
    public enum Consent { NOT_ASKED, GRANTED, DECLINED }

    private final Path file;
    private Consent consent = Consent.NOT_ASKED;
    private int intervalSeconds = DEFAULT_INTERVAL;

    private StudioSettings(Path file) {
        this.file = file;
    }

    public static StudioSettings load(Path dataDir) {
        StudioSettings s = new StudioSettings(dataDir.resolve("settings.json"));
        try {
            if (Files.isRegularFile(s.file) && Files.size(s.file) < 64 * 1024) {
                Map<String, Object> m = Json.object(Json.parseObject(Files.readString(s.file, StandardCharsets.UTF_8)), "recovery");
                String c = Json.string(m, "consent");
                if (c != null) s.consent = Consent.valueOf(c);
                int i = (int) Json.number(m, "intervalSeconds", DEFAULT_INTERVAL);
                s.intervalSeconds = INTERVALS.contains(i) ? i : DEFAULT_INTERVAL;
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("Configurações ilegíveis (recuperação continua desligada): " + e.getClass().getSimpleName());
            s.consent = Consent.NOT_ASKED;
        }
        return s;
    }

    public static StudioSettings inMemory() {
        return new StudioSettings(null);
    }

    public Consent recoveryConsent() {
        return consent;
    }

    public boolean recoveryEnabled() {
        return consent == Consent.GRANTED;
    }

    public int recoveryIntervalSeconds() {
        return intervalSeconds;
    }

    public void setRecovery(boolean enabled, int interval) {
        consent = enabled ? Consent.GRANTED : Consent.DECLINED;
        intervalSeconds = INTERVALS.contains(interval) ? interval : DEFAULT_INTERVAL;
        persist();
    }

    private void persist() {
        if (file == null) return;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("consent", consent.name());
        r.put("intervalSeconds", (long) intervalSeconds);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("recovery", r);
        try {
            StudioDataDir.writeAtomically(file, Json.write(doc).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warning("Configurações não gravadas: " + e.getClass().getSimpleName());
        }
    }
}
