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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STUDIO-014 — artefatos do desenvolvimento por automação, no diretório de dados do Studio (o {@code .nekkora} não
 * muda): {@code <dados>/desenvolvimento/<automationId>/} com a especificação funcional ({@code especificacao.json}), o
 * último registro do gate ({@code gate.json}), a configuração técnica DERIVADA ({@code codegen.json}, só para auditoria
 * e Opções avançadas), o último relatório de qualidade ({@code qualidade.json}) e as opções avançadas
 * ({@code opcoes.json}: pasta de saída e Python de verificação). Escrita atômica; nenhum valor, segredo ou pixel. Sem
 * diretório de dados ({@link #inMemory()}), nada vai para o disco.
 */
public final class DevelopmentStore {
    private final Path root;
    private final Map<String, String> memory;

    /** Opções avançadas (vazias = padrão). */
    public record Options(Optional<Path> outputDir, Optional<Path> python, List<PublicOrigin> publicOrigins) {
        public Options {
            publicOrigins = List.copyOf(publicOrigins);
        }

        public Options(Optional<Path> outputDir, Optional<Path> python) {
            this(outputDir, python, List.of());
        }

        public static Options defaults() {
            return new Options(Optional.empty(), Optional.empty(), List.of());
        }

        public List<String> origins() {
            return publicOrigins.stream().map(PublicOrigin::origin).toList();
        }
    }

    /** Autorização de execução num site público de TREINAMENTO (benchmark): origem https exata, quem autorizou e quando. */
    public record PublicOrigin(String origin, String actor, String at) {
        public PublicOrigin {
            if (!origin.matches("https://[a-z0-9.-]+(:[0-9]{1,5})?")) throw new IllegalArgumentException("ORIGIN_INVALID");
        }
    }

    public DevelopmentStore(Path dataDir) {
        this.root = dataDir.resolve("desenvolvimento");
        this.memory = null;
    }

    private DevelopmentStore() {
        this.root = null;
        this.memory = new ConcurrentHashMap<>();
    }

    /** Sem disco (testes e Studio sem diretório de dados). */
    public static DevelopmentStore inMemory() {
        return new DevelopmentStore();
    }

    public Path folder(AutomationId id) {
        return root == null ? null : root.resolve(id.value().toString());
    }

    public FunctionalSpec spec(AutomationId id) throws IOException {
        Optional<String> t = read(id.value(), "especificacao.json");
        if (t.isEmpty()) return FunctionalSpec.empty(id);
        FunctionalSpec s = FunctionalSpec.parse(t.get());
        if (!s.automationId().equals(id)) throw new IOException("especificação de outra automação");
        return s;
    }

    public void saveSpec(FunctionalSpec s) throws IOException {
        write(s.automationId().value(), "especificacao.json", s.toJson());
    }

    public Optional<GateRecord> gate(AutomationId id) {
        return read(id.value(), "gate.json").flatMap(t -> {
            try {
                return Optional.of(GateRecord.parse(t));
            } catch (RuntimeException e) {
                return Optional.empty(); // registro ilegível: como se não houvesse (nunca "aprovado")
            }
        });
    }

    public void saveGate(GateRecord r) throws IOException {
        write(UUID.fromString(r.automationId()), "gate.json", r.toJson());
    }

    public void saveConfig(AutomationId id, String json) throws IOException {
        saveConfig(id.value(), json);
    }

    public void saveConfig(UUID id, String json) throws IOException {
        write(id, "codegen.json", json);
    }

    public Optional<String> config(AutomationId id) {
        return read(id.value(), "codegen.json");
    }

    public void saveQuality(AutomationId id, String json) throws IOException {
        write(id.value(), "qualidade.json", json);
    }

    public Optional<String> quality(AutomationId id) {
        return read(id.value(), "qualidade.json");
    }

    /** NEKKORA-E2E-002: último teste autorizado ({@code teste-autorizado.json}). */
    public void saveSupervisedRun(AutomationId id, String json) throws IOException {
        write(id.value(), "teste-autorizado.json", json);
    }

    public Optional<String> supervisedRun(AutomationId id) {
        return read(id.value(), "teste-autorizado.json");
    }

    /** NEKKORA-E2E-002: entrada e destino escolhidos para o teste autorizado (caminhos locais da pessoa). */
    public void saveRunPaths(AutomationId id, Path input, Path spreadsheet) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("input", input.toString());
        m.put("spreadsheet", spreadsheet.toString());
        write(id.value(), "teste-autorizado-caminhos.json", Json.write(m));
    }

    public Optional<Path[]> runPaths(AutomationId id) {
        return read(id.value(), "teste-autorizado-caminhos.json").flatMap(t -> {
            try {
                Map<String, Object> m = Json.parseObject(t);
                return Optional.of(new Path[] {Path.of(String.valueOf(m.get("input"))), Path.of(String.valueOf(m.get("spreadsheet")))});
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * NEKKORA-E2E-002: metadados de cada chamada REAL ao provedor de IA feita pela interface ({@code ia-chamadas.jsonl}):
     * provedor, modelo, contrato, Prompt-Mestre, tamanhos, resultado, tokens e duração. Nunca a carga, a resposta ou a chave.
     */
    public void appendAiCall(AutomationId id, String json) throws IOException {
        String before = read(id.value(), "ia-chamadas.jsonl").orElse("");
        write(id.value(), "ia-chamadas.jsonl", before + json + "\n");
    }

    public Optional<String> aiCalls(AutomationId id) {
        return read(id.value(), "ia-chamadas.jsonl");
    }

    /** CODEGEN-QUALITY-001: registro de rastreio da automação ({@code rastreio.json}, {@link TraceRegistry}). */
    public TraceRegistry traceRegistry(AutomationId id) {
        return read(id.value(), "rastreio.json").map(t -> {
            try {
                return TraceRegistry.parse(t);
            } catch (RuntimeException e) {
                return TraceRegistry.empty(id); // ilegível: começa de novo, semeado pelo manifesto da pasta de saída
            }
        }).orElse(TraceRegistry.empty(id));
    }

    public void saveTraceRegistry(TraceRegistry r) throws IOException {
        write(r.automationId().value(), "rastreio.json", r.toJson());
    }

    public Options options(AutomationId id) {
        return read(id.value(), "opcoes.json").map(t -> {
            try {
                Map<String, Object> m = Json.parseObject(t);
                Optional<Path> out = m.get("outputDir") instanceof String s && !s.isBlank() ? Optional.of(Path.of(s)) : Optional.empty();
                Optional<Path> py = m.get("python") instanceof String s && !s.isBlank() ? Optional.of(Path.of(s)) : Optional.empty();
                List<PublicOrigin> pub = new java.util.ArrayList<>();
                if (m.get("publicOrigins") instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> x && x.get("origin") instanceof String or && x.get("actor") instanceof String ac && x.get("at") instanceof String at) {
                            pub.add(new PublicOrigin(or, ac, at));
                        }
                    }
                }
                return new Options(out, py, pub);
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
        List<Object> pub = new java.util.ArrayList<>();
        for (PublicOrigin p : o.publicOrigins()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("origin", p.origin());
            x.put("actor", p.actor());
            x.put("at", p.at());
            pub.add(x);
        }
        m.put("publicOrigins", pub);
        write(id.value(), "opcoes.json", Json.write(m));
    }

    private Optional<String> read(UUID id, String name) {
        if (memory != null) return Optional.ofNullable(memory.get(id + "/" + name));
        Path f = root.resolve(id.toString()).resolve(name);
        try {
            return Files.exists(f) ? Optional.of(Files.readString(f, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void write(UUID id, String name, String text) throws IOException {
        if (memory != null) {
            memory.put(id + "/" + name, text);
            return;
        }
        Path target = root.resolve(id.toString()).resolve(name);
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
