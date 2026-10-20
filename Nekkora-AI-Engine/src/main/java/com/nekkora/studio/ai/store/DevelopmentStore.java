package com.nekkora.studio.ai.store;

import com.nekkora.studio.ai.gate.GateRecord;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.AutomationId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * STUDIO-014 — artefatos do desenvolvimento por automação, no diretório de dados do Studio (o {@code .nekkora} não
 * muda): {@code <dados>/desenvolvimento/<automationId>/} com a especificação funcional ({@code especificacao.json}), o
 * último registro do gate ({@code gate.json}), a configuração técnica DERIVADA ({@code codegen.json}, só para auditoria
 * e Opções avançadas), o último relatório de qualidade ({@code qualidade.json}) e as opções avançadas
 * ({@code opcoes.json}: pasta de saída e Python de verificação). Escrita atômica; nenhum valor, segredo ou pixel.
 */
public final class DevelopmentStore {
    private final Path root;

    /** Opções avançadas (vazias = padrão). */
    public record Options(Optional<Path> outputDir, Optional<Path> python) {
        public static Options defaults() {
            return new Options(Optional.empty(), Optional.empty());
        }
    }

    public DevelopmentStore(Path dataDir) {
        this.root = dataDir.resolve("desenvolvimento");
    }

    public Path folder(AutomationId id) {
        return root.resolve(id.value().toString());
    }

    public FunctionalSpec spec(AutomationId id) throws IOException {
        Path f = folder(id).resolve("especificacao.json");
        if (!Files.exists(f)) return FunctionalSpec.empty(id);
        FunctionalSpec s = FunctionalSpec.parse(Files.readString(f, StandardCharsets.UTF_8));
        if (!s.automationId().equals(id)) throw new IOException("especificação de outra automação");
        return s;
    }

    public void saveSpec(FunctionalSpec s) throws IOException {
        write(folder(s.automationId()).resolve("especificacao.json"), s.toJson());
    }

    public Optional<GateRecord> gate(AutomationId id) {
        return read(folder(id).resolve("gate.json")).flatMap(t -> {
            try {
                return Optional.of(GateRecord.parse(t));
            } catch (RuntimeException e) {
                return Optional.empty(); // registro ilegível: como se não houvesse (nunca "aprovado")
            }
        });
    }

    public void saveGate(GateRecord r) throws IOException {
        write(folder(java.util.UUID.fromString(r.automationId())).resolve("gate.json"), r.toJson());
    }

    public void saveConfig(AutomationId id, String json) throws IOException {
        write(folder(id).resolve("codegen.json"), json);
    }

    public Optional<String> config(AutomationId id) {
        return read(folder(id).resolve("codegen.json"));
    }

    public void saveQuality(AutomationId id, String json) throws IOException {
        write(folder(id).resolve("qualidade.json"), json);
    }

    public Optional<String> quality(AutomationId id) {
        return read(folder(id).resolve("qualidade.json"));
    }

    public Options options(AutomationId id) {
        return read(folder(id).resolve("opcoes.json")).map(t -> {
            try {
                Map<String, Object> m = Json.parseObject(t);
                Optional<Path> out = m.get("outputDir") instanceof String s && !s.isBlank() ? Optional.of(Path.of(s)) : Optional.empty();
                Optional<Path> py = m.get("python") instanceof String s && !s.isBlank() ? Optional.of(Path.of(s)) : Optional.empty();
                return new Options(out, py);
            } catch (RuntimeException e) {
                return Options.defaults();
            }
        }).orElse(Options.defaults());
    }

    public void saveOptions(AutomationId id, Options o) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", "nekkora-studio/development-options");
        m.put("version", "1.0.0");
        m.put("outputDir", o.outputDir().map(Path::toString).orElse(""));
        m.put("python", o.python().map(Path::toString).orElse(""));
        write(folder(id).resolve("opcoes.json"), Json.write(m));
    }

    private Path folder(java.util.UUID id) {
        return root.resolve(id.toString());
    }

    private static Optional<String> read(Path f) {
        try {
            return Files.exists(f) ? Optional.of(Files.readString(f, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static void write(Path target, String text) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".dev-", ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
