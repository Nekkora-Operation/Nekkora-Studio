package com.nekkora.studio.ai.quality;

import com.nekkora.studio.ai.Hashes;
import com.nekkora.studio.ai.capability.CapabilityCheck;
import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.plan.DevelopmentPlan;
import com.nekkora.studio.ai.plan.DevelopmentPlanner;
import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.CodegenConfig;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.ProcessProjection;
import com.nekkora.studio.validation.ValidationReport;
import com.nekkora.studio.validation.ValidationStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * STUDIO-014 — AutomationQualityGate: agrega as camadas V0–V6 SEM rede, sem IA e sem executar nada no sistema-alvo. Cada
 * camada diz o que verificou; o que não rodou é {@code NOT_CHECKED} com o motivo, nunca aprovação implícita:
 * <ul>
 *   <li>V0 — validação estrutural existente (o significado de {@code NO_STATIC_BLOCKERS} não muda);</li>
 *   <li>V1 — gate {@code PDD_READY};</li>
 *   <li>V2 — capacidades do framework e do gerador;</li>
 *   <li>V3 — plano determinístico (só com o PDD pronto);</li>
 *   <li>V4/V5 — resultados REAIS das ferramentas Python, quando o código existe e o ambiente está configurado; código de
 *       outra revisão vira "desatualizado" com a correção segura "gerar de novo";</li>
 *   <li>V6 — execução real: só o TESTE AUTORIZADO acionado pela pessoa (NEKKORA-E2E-002) conta, e só se for da revisão
 *       atual do código, terminar {@code COMPLETED}, gravar a saída e a planilha reaberta conferir; senão {@code NOT_CHECKED}
 *       (sem teste) ou ATENÇÃO (o teste desta revisão não terminou bem). Nada é presumido.</li>
 * </ul>
 */
public final class AutomationQualityGate {
    private AutomationQualityGate() {}

    /** Entradas da avaliação (tudo do estado atual; {@code checks} vazio = V4/V5 não rodaram nesta avaliação). */
    public record Inputs(Automation automation, ValidationReport staticReport, Optional<DocumentDraft> draft, FunctionalSpec spec, FrameworkCapabilityCatalog catalog,
                         Path outputDir, Optional<CodegenConfig> declaredTabular, Optional<PythonChecks.Environment> python, boolean pythonPending,
                         List<PythonChecks.Check> checks, String prompt, Instant now, List<String> publicOrigins, Optional<SupervisedRunRecord> supervisedRun,
                         String environmentProblem, String frameworkProblem) {
        public Inputs {
            environmentProblem = environmentProblem == null ? "" : environmentProblem;
            frameworkProblem = frameworkProblem == null ? "" : frameworkProblem;
        }

        /** Sem diagnóstico de catálogo (PACKAGING-001: vazio = o catálogo confere com a versão do código gerado). */
        public Inputs(Automation automation, ValidationReport staticReport, Optional<DocumentDraft> draft, FunctionalSpec spec, FrameworkCapabilityCatalog catalog,
                      Path outputDir, Optional<CodegenConfig> declaredTabular, Optional<PythonChecks.Environment> python, boolean pythonPending,
                      List<PythonChecks.Check> checks, String prompt, Instant now, List<String> publicOrigins, Optional<SupervisedRunRecord> supervisedRun,
                      String environmentProblem) {
            this(automation, staticReport, draft, spec, catalog, outputDir, declaredTabular, python, pythonPending, checks, prompt, now, publicOrigins, supervisedRun,
                    environmentProblem, "");
        }

        /** Sem diagnóstico do ambiente (PACKAGING-001: vazio = ambiente pronto ou não avaliado). */
        public Inputs(Automation automation, ValidationReport staticReport, Optional<DocumentDraft> draft, FunctionalSpec spec, FrameworkCapabilityCatalog catalog,
                      Path outputDir, Optional<CodegenConfig> declaredTabular, Optional<PythonChecks.Environment> python, boolean pythonPending,
                      List<PythonChecks.Check> checks, String prompt, Instant now, List<String> publicOrigins, Optional<SupervisedRunRecord> supervisedRun) {
            this(automation, staticReport, draft, spec, catalog, outputDir, declaredTabular, python, pythonPending, checks, prompt, now, publicOrigins, supervisedRun, "");
        }

        public Inputs(Automation automation, ValidationReport staticReport, Optional<DocumentDraft> draft, FunctionalSpec spec, FrameworkCapabilityCatalog catalog,
                      Path outputDir, Optional<CodegenConfig> declaredTabular, Optional<PythonChecks.Environment> python, boolean pythonPending,
                      List<PythonChecks.Check> checks, String prompt, Instant now, List<String> publicOrigins) {
            this(automation, staticReport, draft, spec, catalog, outputDir, declaredTabular, python, pythonPending, checks, prompt, now, publicOrigins, Optional.empty());
        }

        public Inputs(Automation automation, ValidationReport staticReport, Optional<DocumentDraft> draft, FunctionalSpec spec, FrameworkCapabilityCatalog catalog,
                      Path outputDir, Optional<CodegenConfig> declaredTabular, Optional<PythonChecks.Environment> python, boolean pythonPending,
                      List<PythonChecks.Check> checks, String prompt, Instant now) {
            this(automation, staticReport, draft, spec, catalog, outputDir, declaredTabular, python, pythonPending, checks, prompt, now, List.of());
        }
    }

    /** Estado do código gerado na pasta de saída em relação à revisão atual. */
    public record CodeState(boolean exists, boolean current, String manifestHash, String reason) {}

    public record Assessment(QualityReport report, PddGate.Result gate, DevelopmentPlan plan, CodeState code) {}

    public static Assessment assess(Inputs in) {
        Automation a = in.automation();
        ProjectContext ctx = ProjectContext.of(a, in.draft()).withAuthorizedPublicOrigins(in.publicOrigins());
        ProcessProjection projection = ProcessProjection.of(a);
        Map<Layer, QualityReport.LayerResult> layers = new EnumMap<>(Layer.class);
        List<QualityIssue> issues = new ArrayList<>();
        // V0
        ValidationReport v0 = in.staticReport();
        ValidationReport.Summary s0 = v0.summary();
        layers.put(Layer.V0_MODEL, new QualityReport.LayerResult(Layer.V0_MODEL, switch (v0.status()) {
            case NO_STATIC_BLOCKERS -> QualityReport.State.PASS;
            case NOT_FULLY_ASSESSABLE -> QualityReport.State.ATTENTION;
            case INCOMPLETE -> QualityReport.State.BLOCKED;
        }, (v0.status() == ValidationStatus.NO_STATIC_BLOCKERS ? "sem bloqueios estáticos" : v0.status().label().toLowerCase(java.util.Locale.ROOT))
                + " · " + s0.blockers() + " bloqueio(s), " + s0.warnings() + " aviso(s) · regras " + v0.rulesVersion()));
        // V1
        PddGate.Result gate = PddGate.evaluate(ctx, in.draft(), in.spec(), projection, in.now());
        issues.addAll(gate.issues());
        layers.put(Layer.V1_PDD, new QualityReport.LayerResult(Layer.V1_PDD, stateOf(gate.issues(), true),
                gate.ready() ? "PDD_READY · " + gate.record().coveredSteps() + " de " + gate.record().totalSteps() + " etapas cobertas"
                        : "PDD_BLOCKED · " + gate.record().blockers().size() + " pendência(s)"));
        // V2
        boolean catalogMissing = !in.frameworkProblem().isEmpty();
        if (catalogMissing) {
            // PACKAGING-001: a versão declarada pelo código gerado não tem catálogo — o padrão NÃO confere esse código
            QualityIssue missing = QualityIssue.of("FRAMEWORK_CATALOG_MISSING", Layer.V2_CAPABILITY, QualityIssue.Kind.BLOCKER, null, "Versão do framework",
                    "Incompatível: " + in.frameworkProblem() + ". As capacidades do código não podem ser conferidas e nada é dado por validado.",
                    "Desenvolva de novo pelo Studio (gera com uma versão homologada) ou use um Studio que tenha o catálogo dessa versão.");
            issues.add(missing);
            layers.put(Layer.V2_CAPABILITY, new QualityReport.LayerResult(Layer.V2_CAPABILITY, QualityReport.State.BLOCKED,
                    "incompatível: " + in.frameworkProblem()));
        } else {
            CapabilityCheck.Result caps = CapabilityCheck.check(a, ctx, in.catalog(), in.declaredTabular());
            issues.addAll(caps.issues());
            layers.put(Layer.V2_CAPABILITY, new QualityReport.LayerResult(Layer.V2_CAPABILITY, stateOf(caps.issues(), true),
                    caps.used().size() + " capacidade(s) do nekkora_rpa " + in.catalog().version()
                            + (caps.issues().isEmpty() ? "" : " · " + QualityIssue.blocking(caps.issues()).size() + " ausência(s) ou bloqueio(s)")));
        }
        // V3
        DevelopmentPlan plan = null;
        if (!gate.ready()) {
            layers.put(Layer.V3_PLAN, new QualityReport.LayerResult(Layer.V3_PLAN, QualityReport.State.NOT_CHECKED, "depende do PDD pronto (PDD_READY)"));
        } else {
            plan = DevelopmentPlanner.plan(a, ctx, gate, in.catalog(), in.declaredTabular(), in.draft());
            List<QualityIssue> own = plan.issues().stream().filter(i -> i.layer() == Layer.V3_PLAN).toList();
            issues.addAll(own);
            layers.put(Layer.V3_PLAN, new QualityReport.LayerResult(Layer.V3_PLAN, stateOf(plan.issues(), true), plan.model().label() + " · "
                    + plan.variables().size() + " variável(is) · " + (plan.pages().isEmpty() ? "Pages por sessão (lote)" : plan.pages().size() + " Page(s)")));
        }
        // V4 / V5
        CodeState code = codeState(in.outputDir(), a, gate);
        if (!code.exists()) {
            layers.put(Layer.V4_CODE, new QualityReport.LayerResult(Layer.V4_CODE, QualityReport.State.NOT_CHECKED, "código ainda não gerado"));
            layers.put(Layer.V5_TESTS, new QualityReport.LayerResult(Layer.V5_TESTS, QualityReport.State.NOT_CHECKED, "código ainda não gerado"));
        } else if (!code.current()) {
            QualityIssue stale = plan != null && plan.ready()
                    ? new QualityIssue("CODE_OUTDATED", Layer.V4_CODE, QualityIssue.Kind.AUTO_FIX_SAFE, null, null, "Código Python",
                            "O código na pasta foi gerado de outra revisão (" + code.reason() + ").", "Gerar de novo pelo gerador oficial (edições à mão viram .novo/.diff).",
                            null, new QualityIssue.Fix("REGENERATE", "Gerar o código de novo", "revisão anterior", "revisão atual"), null)
                    : QualityIssue.of("CODE_OUTDATED", Layer.V4_CODE, QualityIssue.Kind.WARNING, null, "Código Python",
                            "O código na pasta foi gerado de outra revisão (" + code.reason() + ").", "Resolva as pendências e use Desenvolver automação.");
            issues.add(stale);
            layers.put(Layer.V4_CODE, new QualityReport.LayerResult(Layer.V4_CODE, QualityReport.State.ATTENTION, "código desatualizado: não verificado"));
            layers.put(Layer.V5_TESTS, new QualityReport.LayerResult(Layer.V5_TESTS, QualityReport.State.NOT_CHECKED, "código desatualizado"));
        } else if (catalogMissing) {
            layers.put(Layer.V4_CODE, new QualityReport.LayerResult(Layer.V4_CODE, QualityReport.State.NOT_CHECKED, "não validado: versão do framework sem catálogo"));
            layers.put(Layer.V5_TESTS, new QualityReport.LayerResult(Layer.V5_TESTS, QualityReport.State.NOT_CHECKED, "não validado: versão do framework sem catálogo"));
        } else if (in.pythonPending()) {
            layers.put(Layer.V4_CODE, new QualityReport.LayerResult(Layer.V4_CODE, QualityReport.State.RUNNING, "procurando o Python de verificação…"));
            layers.put(Layer.V5_TESTS, new QualityReport.LayerResult(Layer.V5_TESTS, QualityReport.State.RUNNING, "aguardando a verificação do código"));
        } else if (in.python().isEmpty() && !in.environmentProblem().isEmpty()) {
            // PACKAGING-001: o Dependency Manager disse o MOTIVO (pacote ausente, versão errada, falta preparar...)
            issues.add(QualityIssue.of("ENVIRONMENT_NOT_READY", Layer.V4_CODE, QualityIssue.Kind.INFO, null, "Ambiente Python da automação",
                    in.environmentProblem() + ": as verificações do código não rodaram.", "Desenvolver automação → Pendências → Preparar ambiente."));
            layers.put(Layer.V4_CODE, new QualityReport.LayerResult(Layer.V4_CODE, QualityReport.State.NOT_CHECKED, "ambiente da automação: " + in.environmentProblem()));
            layers.put(Layer.V5_TESTS, new QualityReport.LayerResult(Layer.V5_TESTS, QualityReport.State.NOT_CHECKED, "ambiente da automação não está pronto"));
        } else if (in.python().isEmpty()) {
            issues.add(QualityIssue.of("PYTHON_ENV_NOT_CONFIGURED", Layer.V4_CODE, QualityIssue.Kind.INFO, null, "Ambiente de verificação",
                    "Não há interpretador Python com nekkora_rpa, ruff e mypy configurado: as verificações do código não rodaram.",
                    "Opções avançadas → Python para verificação (ou crie a .venv na pasta gerada)."));
            layers.put(Layer.V4_CODE, new QualityReport.LayerResult(Layer.V4_CODE, QualityReport.State.NOT_CHECKED, "ambiente Python de verificação não configurado"));
            layers.put(Layer.V5_TESTS, new QualityReport.LayerResult(Layer.V5_TESTS, QualityReport.State.NOT_CHECKED, "ambiente Python de verificação não configurado"));
        } else if (in.checks().isEmpty()) {
            layers.put(Layer.V4_CODE, new QualityReport.LayerResult(Layer.V4_CODE, QualityReport.State.RUNNING, "verificando (compilação, importação, ruff, mypy)…"));
            layers.put(Layer.V5_TESTS, new QualityReport.LayerResult(Layer.V5_TESTS, QualityReport.State.RUNNING, "testes de contrato…"));
        } else {
            layers.put(Layer.V4_CODE, checksLayer(Layer.V4_CODE, in.checks(), in.python().get(), issues));
            layers.put(Layer.V5_TESTS, checksLayer(Layer.V5_TESTS, in.checks(), in.python().get(), issues));
        }
        // VQ — qualidade da geração (CODEGEN-QUALITY-001): manifesto + AST; D13 continua soberano
        if (catalogMissing) {
            layers.put(Layer.VQ_GENERATION, new QualityReport.LayerResult(Layer.VQ_GENERATION, QualityReport.State.NOT_CHECKED,
                    "não validado: versão do framework sem catálogo"));
        } else if (!code.exists() || !code.current()) {
            layers.put(Layer.VQ_GENERATION, new QualityReport.LayerResult(Layer.VQ_GENERATION, QualityReport.State.NOT_CHECKED,
                    !code.exists() ? "código ainda não gerado" : "código desatualizado"));
        } else if (in.declaredTabular().isPresent()) {
            layers.put(Layer.VQ_GENERATION, new QualityReport.LayerResult(Layer.VQ_GENERATION, QualityReport.State.NOT_CHECKED,
                    "lote tabular declarado (gerador 1.0.0): fora desta camada"));
        } else {
            GeneratedCodeQuality.Result q = GeneratedCodeQuality.assess(in.outputDir(), in.checks(), in.python().isPresent());
            issues.addAll(q.issues());
            layers.put(Layer.VQ_GENERATION, q.layer());
        }
        // V6 — só o teste autorizado da REVISÃO ATUAL do código comprova a execução
        layers.put(Layer.V6_EXECUTION, catalogMissing ? new QualityReport.LayerResult(Layer.V6_EXECUTION, QualityReport.State.NOT_CHECKED,
                "não validado: versão do framework sem catálogo")
                : in.environmentProblem().isEmpty() || in.supervisedRun().isEmpty() ? execution(in.supervisedRun(), code, a)
                // PACKAGING-001: o ambiente atual não confere com o exigido — o teste anterior não vale como prova desta configuração
                : new QualityReport.LayerResult(Layer.V6_EXECUTION, QualityReport.State.NOT_CHECKED, "o ambiente Python atual não confere ("
                        + in.environmentProblem() + "): prepare o ambiente e execute o teste autorizado de novo"));
        QualityReport r = new QualityReport(a.id().value().toString(), a.revision(), projection.revision(), gate.record().documentRevision(),
                gate.record().specRevision(), code.manifestHash(), in.now(), layers, issues, code.current() && !catalogMissing ? in.checks() : List.of(), in.prompt(),
                catalogMissing ? "sem catálogo da versão declarada" : "nekkora-rpa " + in.catalog().version() + " " + in.catalog().fingerprint());
        return new Assessment(r, gate, plan, code);
    }

    static QualityReport.LayerResult execution(Optional<SupervisedRunRecord> run, CodeState code, Automation a) {
        if (run.isEmpty()) {
            return new QualityReport.LayerResult(Layer.V6_EXECUTION, QualityReport.State.NOT_CHECKED, "nenhum teste autorizado desta automação");
        }
        SupervisedRunRecord t = run.get();
        if (!code.current() || !t.codeRevision().equals(code.manifestHash()) || t.automationRevision() != a.revision()) {
            return new QualityReport.LayerResult(Layer.V6_EXECUTION, QualityReport.State.NOT_CHECKED,
                    "o último teste autorizado é de outra revisão do código — execute de novo");
        }
        if (t.proven()) {
            return new QualityReport.LayerResult(Layer.V6_EXECUTION, QualityReport.State.PASS, "teste autorizado executado e conferido: " + t.outputRows()
                    + " linha(s) na planilha reaberta (" + (t.spreadsheetSha256().length() > 19 ? t.spreadsheetSha256().substring(0, 19) + "…" : t.spreadsheetSha256())
                    + ")");
        }
        return new QualityReport.LayerResult(Layer.V6_EXECUTION, QualityReport.State.ATTENTION, "o teste autorizado desta revisão terminou "
                + t.status() + (t.outputStatus().isEmpty() ? "" : " · saída " + t.outputStatus() + (t.outputDetail().isEmpty() ? "" : " (" + t.outputDetail() + ")"))
                + (t.verifyCode().isEmpty() ? "" : " · conferência " + t.verifyCode()));
    }

    static QualityReport.LayerResult checksLayer(Layer layer, List<PythonChecks.Check> checks, PythonChecks.Environment env, List<QualityIssue> issues) {
        List<PythonChecks.Check> mine = checks.stream().filter(c -> c.layer() == layer).toList();
        if (mine.isEmpty()) return new QualityReport.LayerResult(layer, QualityReport.State.NOT_CHECKED, "nenhuma verificação desta camada rodou");
        int failed = 0;
        int notRun = 0;
        for (PythonChecks.Check c : mine) {
            switch (c.status()) {
                case PASS -> {
                }
                case FAIL -> {
                    failed++;
                    issues.add(QualityIssue.of("PY_" + c.id(), layer, QualityIssue.Kind.BLOCKER, null, label(c.id()),
                            c.problems() + " problema(s): " + c.codes(), "O código vem do gerador oficial: a correção vai para o gerador ou para o modelo, não para o arquivo."));
                }
                case ERROR, NOT_RUN -> {
                    notRun++;
                    issues.add(QualityIssue.of("PY_" + c.id() + "_NOT_RUN", layer, QualityIssue.Kind.WARNING, null, label(c.id()),
                            "A verificação não terminou (" + c.reason() + ").", "Valide novamente; se repetir, confira o ambiente Python de verificação."));
                }
            }
        }
        String tools = layer == Layer.V4_CODE ? " · Python " + env.pythonVersion() + ", ruff " + env.ruff() + ", mypy " + env.mypy() + ", nekkora_rpa " + env.framework()
                : " · nekkora_rpa " + env.framework();
        QualityReport.State st = failed > 0 ? QualityReport.State.BLOCKED : notRun > 0 ? QualityReport.State.ATTENTION : QualityReport.State.PASS;
        String head = layer == Layer.V5_TESTS ? mine.stream().mapToInt(c -> c.codes().getOrDefault("TOTAL", 0)).sum() + " teste(s) de contrato, " : "";
        return new QualityReport.LayerResult(layer, st, head + (mine.size() - failed - notRun) + " de " + mine.size() + " verificação(ões) sem problema" + tools);
    }

    static String label(String id) {
        return switch (id) {
            case "PY_COMPILE" -> "Compilação";
            case "PY_IMPORT" -> "Importação";
            case "RUFF_CHECK" -> "ruff check";
            case "RUFF_FORMAT" -> "ruff format";
            case "MYPY_STRICT" -> "mypy estrito";
            case "CONTRACT_TESTS" -> "Testes de contrato";
            default -> id;
        };
    }

    /** Estado da camada pelos apontamentos dela (só os que impedem contam). */
    static QualityReport.State stateOf(List<QualityIssue> issues, boolean ran) {
        if (!ran) return QualityReport.State.NOT_CHECKED;
        if (issues.stream().anyMatch(i -> i.kind() == QualityIssue.Kind.SECURITY_BLOCKED)) return QualityReport.State.SECURITY_BLOCKED;
        if (issues.stream().anyMatch(i -> i.kind() == QualityIssue.Kind.BLOCKER)) return QualityReport.State.BLOCKED;
        if (issues.stream().anyMatch(i -> i.kind() == QualityIssue.Kind.HELP_REQUIRED)) return QualityReport.State.HELP_REQUIRED;
        if (issues.stream().anyMatch(i -> i.kind() == QualityIssue.Kind.PROPOSE_REVIEW || i.kind() == QualityIssue.Kind.AUTO_FIX_SAFE
                || i.kind() == QualityIssue.Kind.WARNING)) {
            return QualityReport.State.ATTENTION;
        }
        return QualityReport.State.PASS;
    }

    /** O código na pasta corresponde ao modelo e ao PDD atuais? (manifesto do gerador oficial) */
    @SuppressWarnings("unchecked")
    public static CodeState codeState(Path outputDir, Automation a, PddGate.Result gate) {
        if (outputDir == null) return new CodeState(false, false, "", "projeto não salvo");
        Path m = outputDir.resolve("manifest.json");
        if (!Files.isRegularFile(m)) return new CodeState(false, false, "", "sem manifesto");
        try {
            String text = Files.readString(m, StandardCharsets.UTF_8);
            Map<String, Object> man = Json.parseObject(text);
            String hash = Hashes.sha256(text.replace("\r\n", "\n"));
            if (!a.id().value().toString().equals(man.get("automationId"))) return new CodeState(true, false, hash, "outra automação");
            long rev = man.get("automationRevision") instanceof Number n ? n.longValue() : -1;
            if (rev != a.revision()) return new CodeState(true, false, hash, "modelo mudou");
            if (man.get("pdd") instanceof Map<?, ?> p) {
                Map<String, Object> pdd = (Map<String, Object>) p;
                if (!gate.record().documentRevision().equals(pdd.get("documentRevision")) || !gate.record().specRevision().equals(pdd.get("specRevision"))
                        || !gate.record().processRevision().equals(pdd.get("processRevision"))) {
                    return new CodeState(true, false, hash, "PDD ou especificação mudaram");
                }
            }
            return new CodeState(true, true, hash, "");
        } catch (IOException | RuntimeException e) {
            return new CodeState(true, false, "", "manifesto ilegível");
        }
    }
}
