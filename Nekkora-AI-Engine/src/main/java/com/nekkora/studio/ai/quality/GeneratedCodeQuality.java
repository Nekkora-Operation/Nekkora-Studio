package com.nekkora.studio.ai.quality;

import com.nekkora.studio.ai.pycheck.PythonChecks;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.codegen.CodeNaming;
import com.nekkora.studio.domain.StepId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — camada VQ ("Qualidade da geração") do Validar automação, sem executar nada:
 * <ol>
 *   <li>o MANIFESTO do gerador (sempre): versão com mapa de rastreio, mapa traceId ↔ StepId bijetivo e completo, nomes
 *       com fonte de evidência (Page/método provisório → {@code NOME_SEM_EVIDENCIA}), localizador posicional;</li>
 *   <li>a AST do Python gerado ({@code qualidade_codigo.py}, quando o ambiente de verificação existe): UUID ou comentário
 *       por etapa no código, nomes opacos, docstrings, {@code step_id} sem mapa, seletor no código, falha silenciada e —
 *       no código em inglês (manifesto 1.4.0, rodada 3) — identificador em português e chave de alvo escrita como texto.</li>
 * </ol>
 * Rodada 3: palavra sem tradução no glossário ({@code naming.untranslated} do manifesto) é AVISO
 * ({@code IDENTIFICADOR_SEM_TRADUCAO}); código em português de antes é oferecido para gerar de novo (nunca regenerado
 * sozinho).
 * Cada apontamento diz arquivo, símbolo, o que é e o que fazer. Os objetivos que o gerador garante viram BLOQUEIO quando
 * faltam (o código foi editado ou é de outro gerador); nomes provisórios são AVISO. PASS aqui NÃO é o aceite de
 * legibilidade: esse é da titular, lendo os arquivos.
 */
public final class GeneratedCodeQuality {
    private GeneratedCodeQuality() {}

    /** Versão do manifesto a partir da qual o código tem mapa de rastreio e nomes do PDD. */
    public static final String SEMANTIC_MANIFEST = "1.3.0";

    public record Result(QualityReport.LayerResult layer, List<QualityIssue> issues) {}

    public static Result assess(Path outputDir, List<PythonChecks.Check> checks, boolean pythonAvailable) {
        List<QualityIssue> issues = new ArrayList<>();
        Map<String, Object> m;
        try {
            m = Json.parseObject(Files.readString(outputDir.resolve("manifest.json"), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return new Result(new QualityReport.LayerResult(Layer.VQ_GENERATION, QualityReport.State.NOT_CHECKED, "manifesto ilegível"), List.of());
        }
        String version = String.valueOf(m.get("version"));
        if (!(m.get("trace") instanceof Map<?, ?> trace)) {
            issues.add(new QualityIssue("CODIGO_DE_GERADOR_ANTERIOR", Layer.VQ_GENERATION, QualityIssue.Kind.AUTO_FIX_SAFE, null, null, "Código Python",
                    "Este código veio do gerador anterior (manifesto " + version + "): step_id com o identificador interno da etapa, nomes por sessão e "
                            + "comentário por etapa.", "Gere de novo pelo Studio: nomes do PDD e step_id legível (o mapa para a etapa fica no manifesto).",
                    null, new QualityIssue.Fix("REGENERATE", "Gerar o código de novo", "gerador " + version, "gerador " + SEMANTIC_MANIFEST), null));
            return new Result(new QualityReport.LayerResult(Layer.VQ_GENERATION, QualityReport.State.ATTENTION, "código do gerador anterior (sem mapa de rastreio)"),
                    issues);
        }
        int mapped = traces(trace, m, issues);
        names(m, issues);
        naming(m, issues);
        locators(m, issues);
        Optional<PythonChecks.Check> ast = checks.stream().filter(c -> c.id().equals("CODE_QUALITY")).findFirst();
        ast.ifPresent(c -> astIssues(c, issues));
        QualityReport.State state = issues.stream().anyMatch(i -> i.kind() == QualityIssue.Kind.BLOCKER) ? QualityReport.State.BLOCKED
                : !issues.isEmpty() ? QualityReport.State.ATTENTION
                : ast.isEmpty() ? QualityReport.State.ATTENTION : QualityReport.State.PASS;
        String astText = ast.isPresent() ? (ast.get().status() == PythonChecks.Status.PASS ? "AST sem achados" : "AST: " + ast.get().problems() + " achado(s)")
                : pythonAvailable ? "AST aguardando a verificação do código" : "AST não executada (ambiente Python de verificação)";
        return new Result(new QualityReport.LayerResult(Layer.VQ_GENERATION, state, mapped + " step_id(s) mapeado(s) para etapas · " + astText
                + " · legibilidade: só a revisão da titular aprova"), issues);
    }

    private static int traces(Map<?, ?> trace, Map<String, Object> m, List<QualityIssue> issues) {
        List<?> mappings = trace.get("mappings") instanceof List<?> l ? l : List.of();
        Set<String> traceIds = new HashSet<>();
        Set<String> steps = new HashSet<>();
        for (Object o : mappings) {
            if (!(o instanceof Map<?, ?> x)) continue;
            if (!traceIds.add(String.valueOf(x.get("traceId"))) | !steps.add(String.valueOf(x.get("studioStepId")))) {
                issues.add(QualityIssue.of("TRACE_ALIAS_CONFLITO", Layer.VQ_GENERATION, QualityIssue.Kind.BLOCKER, step(x.get("studioStepId")), "Rastreio",
                        "Dois step_id para a mesma etapa (ou o mesmo step_id para duas etapas): eventos e auditoria ficariam ambíguos.",
                        "Gere de novo pelo Studio; o registro de rastreio da automação garante um step_id por etapa."));
            }
        }
        if (m.get("steps") instanceof List<?> all) {
            for (Object o : all) {
                if (o instanceof Map<?, ?> s && !steps.contains(String.valueOf(s.get("stepId")))) {
                    issues.add(QualityIssue.of("TRACE_SEM_MAPEAMENTO", Layer.VQ_GENERATION, QualityIssue.Kind.BLOCKER, step(s.get("stepId")), "Rastreio",
                            "Uma etapa gerada não tem step_id no mapa do manifesto.", "Gere de novo pelo Studio."));
                }
            }
        }
        return mappings.size();
    }

    /** Arquivo das telas conforme o layout do projeto (pacote {@code automation/} na rodada 3; antes, {@code paginas.py}). */
    static String pagesFile(Map<String, Object> m) {
        return m.get("layout") instanceof Map<?, ?> ? "automation/pages.py" : "paginas.py";
    }

    /**
     * Rodada 3 (Q11): palavras de nomes que ficaram sem tradução no glossário (AVISO, com a origem) e código ainda em
     * português (oferta de gerar de novo em inglês, versionada — o Studio não regenera nem migra sozinho).
     */
    private static void naming(Map<String, Object> m, List<QualityIssue> issues) {
        if (!"en".equals(m.get("language"))) {
            issues.add(new QualityIssue("CODIGO_EM_PORTUGUES", Layer.VQ_GENERATION, QualityIssue.Kind.AUTO_FIX_SAFE, null, null, "Código Python",
                    "Este código veio do gerador anterior (manifesto " + m.get("version") + "): nomes em português e paginas.py/processo.py na raiz. Ele "
                            + "continua válido e executável como está.",
                    "Para o código em inglês (pacote automation/, chaves tipadas SelectorKey), gere de novo pelo Studio: os arquivos anteriores são "
                            + "guardados numa pasta de versão, e os step_id antigos ficam declarados no manifesto.",
                    null, new QualityIssue.Fix("REGENERATE", "Gerar o código de novo (em inglês)", "manifesto " + m.get("version"), "manifesto 1.4.0"), null));
            return;
        }
        if (!(m.get("naming") instanceof Map<?, ?> n) || !(n.get("untranslated") instanceof List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> x)) continue;
            issues.add(QualityIssue.of("IDENTIFICADOR_SEM_TRADUCAO", Layer.VQ_GENERATION, QualityIssue.Kind.WARNING, null, String.valueOf(x.get("python")),
                    "A palavra \"" + x.get("word") + "\" não está no glossário PT→EN do Studio: ficou como está no nome (" + x.get("kind") + ").",
                    "Use no PDD ou no nome da variável uma palavra do glossário, ou peça a inclusão no glossário; o Studio não inventa tradução."));
        }
    }

    private static void names(Map<String, Object> m, List<QualityIssue> issues) {
        if (!(m.get("architecture") instanceof Map<?, ?> arch) || !(arch.get("pages") instanceof List<?> pages)) return;
        String file = pagesFile(m);
        for (Object o : pages) {
            if (!(o instanceof Map<?, ?> p)) continue;
            String cls = String.valueOf(p.get("class"));
            if ("PROVISIONAL".equals(p.get("nameSource")) || CodeNaming.opaque(cls)) {
                issues.add(QualityIssue.of("NOME_SEM_EVIDENCIA", Layer.VQ_GENERATION, QualityIssue.Kind.WARNING, null, file + " · " + cls,
                        "O nome desta tela é provisório: a seção do PDD onde ela começa não tem um título que diga o que a tela é.",
                        "Abra o PDD, dê um título à seção (ex.: \"Pesquisa do artigo\") e desenvolva de novo."));
            }
            if (p.get("methods") instanceof List<?> methods) {
                for (Object x : methods) {
                    if (x instanceof Map<?, ?> mm && CodeNaming.opaque(String.valueOf(mm.get("name")))) {
                        issues.add(QualityIssue.of("NOME_GENERICO", Layer.VQ_GENERATION, QualityIssue.Kind.WARNING, null, file + " · " + cls + "." + mm.get("name"),
                                "Nome de método sem significado.", "Escreva no PDD um título de passo (ex.: \"Pesquisar o artigo pelo nome\") e desenvolva de novo."));
                    }
                }
            }
        }
    }

    private static void locators(Map<String, Object> m, List<QualityIssue> issues) {
        if (!(m.get("locators") instanceof List<?> list)) return;
        for (Object o : list) {
            if (o instanceof Map<?, ?> l && "POSITIONAL".equals(l.get("kind"))) {
                issues.add(QualityIssue.of("LOCALIZADOR_POSICIONAL", Layer.VQ_GENERATION, QualityIssue.Kind.BLOCKER, null, "selectors.json · " + l.get("key"),
                        "Localizador por posição: muda quando a página muda e pode achar outro elemento.",
                        "Refaça o debug guiado nesta etapa e confirme um alvo com identidade ou âncora (rótulo, linha, região)."));
            }
        }
    }

    private static void astIssues(PythonChecks.Check c, List<QualityIssue> issues) {
        if (c.status() == PythonChecks.Status.ERROR || c.status() == PythonChecks.Status.NOT_RUN) {
            issues.add(QualityIssue.of("CODE_QUALITY_NOT_RUN", Layer.VQ_GENERATION, QualityIssue.Kind.WARNING, null, "Qualidade da geração",
                    "A análise da AST não terminou (" + c.reason() + ").", "Valide de novo."));
            return;
        }
        c.codes().forEach((key, n) -> {
            int sp = key.indexOf(' ');
            String code = sp < 0 ? key : key.substring(0, sp);
            String where = sp < 0 ? "" : key.substring(sp + 1);
            boolean objective = objective(code);
            issues.add(QualityIssue.of(code, Layer.VQ_GENERATION, objective ? QualityIssue.Kind.BLOCKER : QualityIssue.Kind.WARNING, null, where,
                    describe(code) + (n > 1 ? " (" + n + "×)" : ""), "O código vem do gerador oficial: a correção vai para o gerador, o modelo ou o PDD — "
                            + "nunca para o arquivo gerado."));
        });
    }

    /** Achado da AST que é critério objetivo (BLOCKER na camada VQ); os demais são só avisos de legibilidade. */
    public static boolean objective(String code) {
        return switch (code) {
            case "UUID_EXPOSTO_NO_CODIGO", "COMENTARIO_POR_ETAPA", "TRACE_SEM_MAPEAMENTO", "TRACE_ALIAS_CONFLITO", "SELETOR_NO_CODIGO", "SILENCIA_FALHA",
                 "IDENTIFICADOR_EM_PORTUGUES", "CHAVE_DE_ALVO_LITERAL", "DATACLASS_REPR_COM_VALORES" -> true;
            default -> false;
        };
    }

    /**
     * Veredito da verificação CODE_QUALITY igual ao da camada VQ: passa sem achados ou só com avisos de legibilidade; não passa
     * com achado objetivo nem quando a análise não terminou.
     */
    public static boolean passes(PythonChecks.Check c) {
        if (c.status() == PythonChecks.Status.PASS) return true;
        if (c.status() != PythonChecks.Status.FAIL) return false;
        return c.codes().keySet().stream().map(k -> k.indexOf(' ') < 0 ? k : k.substring(0, k.indexOf(' '))).noneMatch(GeneratedCodeQuality::objective);
    }

    static String describe(String code) {
        return switch (code) {
            case "UUID_EXPOSTO_NO_CODIGO" -> "Identificador interno de etapa no código (deve ficar só no manifesto).";
            case "COMENTARIO_POR_ETAPA" -> "Comentário automático por etapa (repete o código).";
            case "NOME_GENERICO" -> "Nome sem significado (ex.: Tela1, etapas_sessao_3, fill).";
            case "VARIAVEL_OPACA" -> "Variável ou parâmetro de uma letra.";
            case "DOCSTRING_AUSENTE" -> "Classe ou método público sem docstring.";
            case "TRACE_SEM_MAPEAMENTO" -> "step_id sem mapeamento para uma etapa do Studio.";
            case "TRACE_ALIAS_CONFLITO" -> "O mesmo step_id para etapas diferentes.";
            case "SELETOR_NO_CODIGO" -> "Seletor escrito na Page (deve ficar no selectors.json).";
            case "SILENCIA_FALHA" -> "Falha transformada em vazio (or \"\" / except: pass).";
            case "IDENTIFICADOR_EM_PORTUGUES" -> "Identificador em português no código gerado em inglês (classe, método, variável ou chave).";
            case "CHAVE_DE_ALVO_LITERAL" -> "Chave de alvo escrita como texto na tela (deve ser SelectorKey.<NOME>.value).";
            case "DATACLASS_REPR_COM_VALORES" -> "Dataclass sem repr=False: o repr automático mostraria os valores lidos ou de entrada.";
            default -> code;
        };
    }

    private static StepId step(Object id) {
        try {
            return id instanceof String s ? new StepId(UUID.fromString(s)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
