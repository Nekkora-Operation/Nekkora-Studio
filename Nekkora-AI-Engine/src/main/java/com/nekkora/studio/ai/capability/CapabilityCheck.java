package com.nekkora.studio.ai.capability;

import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.LocatorStrategy;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * STUDIO-014 — V2: o que o modelo pede existe no {@code nekkora_rpa} desta versão e no gerador oficial? Sempre offline.
 * Lista as capacidades exatas que o projeto usaria e as ausências, por etapa:
 * <ul>
 *   <li>ação sem Lib → {@code UNSUPPORTED_CAPABILITY} (pede ajuda: rever a etapa ou estender o framework pelo guia);</li>
 *   <li>alvo em iframe → {@code TARGET_IN_FRAME_NOT_SUPPORTED} (o runtime 0.2.0 só resolve o documento principal);</li>
 *   <li>alvo sem decisão do debug → bloqueio (marcador manual do PDD NÃO conta);</li>
 *   <li>localizador posicional com alternativa validada → revisar e aplicar (e confirmar de novo no debug);</li>
 *   <li>origem fora de laboratório local → bloqueio de segurança (o framework não executa fora de lab/benchmark).</li>
 * </ul>
 */
public final class CapabilityCheck {
    private CapabilityCheck() {}

    public record Result(List<String> used, List<QualityIssue> issues, String environment) {
        public Result {
            used = List.copyOf(used);
            issues = List.copyOf(issues);
        }
    }

    static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]");

    /** Capacidades estruturais de todo projeto de execução única. */
    static final List<String> STRUCTURE = List.of("core.rpa_process", "core.engine", "core.single_input", "core.transaction_item", "core.process_result",
            "config.load_config", "pages.base_page", "data.json_result_sink");

    public static Result check(Automation a, ProjectContext ctx, FrameworkCapabilityCatalog cat) {
        return check(a, ctx, cat, java.util.Optional.empty());
    }

    /**
     * Com a configuração tabular DECLARADA no projeto (a E2E-001), o ambiente e as origens são os declarados por ela (a
     * autorização registrada), e uma etapa Observar sem alvo com padrão de texto declarado é suportada.
     */
    public static Result check(Automation a, ProjectContext ctx, FrameworkCapabilityCatalog cat, java.util.Optional<com.nekkora.studio.codegen.CodegenConfig> declared) {
        List<QualityIssue> issues = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>(STRUCTURE);
        boolean credentials = false;
        boolean inputs = false;
        for (ProjectContext.Step s : ctx.steps()) {
            AutomationStep st = a.step(s.stepId()).orElse(null);
            if (st == null) continue;
            List<FrameworkCapabilityCatalog.Capability> caps = cat.forAction(s.action());
            if (caps.isEmpty()) {
                issues.add(unsupported(s, cat, "UNSUPPORTED_CAPABILITY", "A ação “" + s.action().label() + "” não tem Lib no nekkora_rpa "
                        + cat.version() + " nem tradução no gerador oficial."));
                continue;
            }
            caps.forEach(c -> used.add(c.id()));
            if (s.value() == ProjectContext.ValueKind.CREDENTIAL || (s.action() == ActionType.FILL && s.sensitive())) credentials = true;
            if (s.value() == ProjectContext.ValueKind.VARIABLE) inputs = true;
            if (s.action() == ActionType.NAVIGATE || (!s.hasTarget() && (s.action() == ActionType.WAIT_FOR || s.action() == ActionType.OBSERVE))) {
                boolean textObservation = declared.map(c -> c.textObservations().containsKey(s.stepId().value().toString())).orElse(false);
                if (textObservation) used.add("actions.read_text_matching");
                // NEKKORA-E2E-002: Observar sem alvo lê o ENDEREÇO da página (nekkora_rpa 0.3.0)
                boolean address = s.action() == ActionType.OBSERVE && !s.hasTarget() && cat.find("actions.current_address").isPresent();
                if (address) used.add("actions.current_address");
                if (!s.hasTarget() && s.action() != ActionType.NAVIGATE && !textObservation && !address) {
                    issues.add(QualityIssue.of(s.action() == ActionType.OBSERVE ? "OBSERVE_WITHOUT_TARGET" : "WAIT_WITHOUT_TARGET", Layer.V2_CAPABILITY,
                            QualityIssue.Kind.BLOCKER, s.stepId(), place(s), "Etapa sem alvo: o código não tem o que ler ou esperar.",
                            "Associe um alvo à etapa (Depurar no navegador → corrigir por seleção) ou remova a etapa.").withBlock(s.blockId()));
                }
                continue;
            }
            if (s.inFrame()) {
                issues.add(unsupported(s, cat, "TARGET_IN_FRAME_NOT_SUPPORTED", "O alvo está dentro de um iframe; o nekkora_rpa " + cat.version()
                        + " só resolve alvos no documento principal (gerar seria procurar no documento errado)."));
                continue;
            }
            if (!s.confirmed()) {
                issues.add(QualityIssue.of("TARGET_NOT_CONFIRMED", Layer.V2_CAPABILITY, QualityIssue.Kind.BLOCKER, s.stepId(), place(s),
                        "O alvo desta etapa não foi confirmado no debug guiado (um marcador manual do PDD não é confirmação).",
                        "Use Depurar no navegador (Ctrl+Shift+D) e confirme ou corrija o alvo.").withBlock(s.blockId()));
                continue;
            }
            if (!s.locatorUsable()) issues.add(locator(st, s));
        }
        if (credentials) used.add("security.env_secrets");
        if (inputs || ctx.steps().stream().anyMatch(x -> x.action() == ActionType.FILL && x.value() == ProjectContext.ValueKind.NONE && !x.sensitive())) {
            used.add("errors.business_rule_rejected");
        }
        for (String id : used) {
            cat.require(id).ifPresent(u -> issues.add(QualityIssue.of(u.code(), Layer.V2_CAPABILITY, QualityIssue.Kind.BLOCKER, null, "Framework",
                    "Capacidade “" + id + "” ausente no catálogo do nekkora_rpa " + cat.version() + ".", "Veja " + u.extending().file() + ".")));
        }
        if (declared.isPresent()) {
            used.remove("core.single_input");
            used.addAll(List.of("tabular.input", "tabular.schema", "tabular.column"));
            if (declared.get().input().source() == com.nekkora.studio.codegen.CodegenConfig.Source.DOWNLOAD) {
                used.addAll(List.of("tabular.deferred_input", "actions.download"));
            }
        }
        String env = declared.isPresent() ? declared.get().environment() : environment(ctx, issues);
        return new Result(List.copyOf(used), issues, env);
    }

    /** Ambiente do framework para as origens observadas: só laboratório local é gerado sem autorização específica. */
    static String environment(ProjectContext ctx, List<QualityIssue> issues) {
        if (ctx.origins().isEmpty()) {
            issues.add(QualityIssue.of("ORIGIN_UNKNOWN", Layer.V2_CAPABILITY, QualityIssue.Kind.BLOCKER, null, "Processo",
                    "Nenhuma etapa registrou o endereço da página: não há endereço inicial nem origem permitida.",
                    "Grave o processo pelo Recorder (o endereço observado entra no modelo) ou acrescente uma etapa Navegar."));
            return "";
        }
        boolean allLoopback = true;
        boolean allAuthorized = true;
        for (String o : ctx.origins()) {
            String host = URI.create(o).getHost().toLowerCase(Locale.ROOT);
            if (!LOOPBACK.contains(host)) allLoopback = false;
            if (LOOPBACK.contains(host) || !o.startsWith("https://") || !ctx.authorizedPublicOrigins().contains(o)) allAuthorized = false;
        }
        if (allLoopback) return "lab";
        // site público de TREINAMENTO autorizado explicitamente (lista fechada de origens https exatas): ambiente benchmark do
        // framework — nunca produção, nunca misturado com laboratório
        if (allAuthorized) return "benchmark";
        List<String> candidates = ctx.origins().stream().filter(o -> o.startsWith("https://") && !LOOPBACK.contains(URI.create(o).getHost())).toList();
        AssistanceRequest help = candidates.size() == ctx.origins().size()
                ? new AssistanceRequest("Este é um site público de TREINAMENTO que você autoriza para execução?",
                        "O nekkora_rpa 0.2.0 só executa em laboratório ou num site público de treinamento autorizado por lista fechada de origens (ambiente "
                                + "benchmark). Não há ambiente de produção: um sistema real de cliente continua bloqueado.",
                        List.of(AssistanceRequest.Option.action("AUTHORIZE_PUBLIC_SITE", "Autorizar como site público de treinamento…",
                                "registra a autorização (origem exata, ator e data) nas opções do projeto; pede confirmação")),
                        null, "Opções do projeto · origens públicas autorizadas")
                : null;
        issues.add(new QualityIssue("ORIGIN_NOT_AUTHORIZED_FOR_EXECUTION", Layer.V2_CAPABILITY, QualityIssue.Kind.SECURITY_BLOCKED, null, null, "Processo",
                "O processo usa um site fora do laboratório local (" + String.join(", ", ctx.origins()) + "). O nekkora_rpa 0.2.0 só executa em laboratório "
                        + "(loopback) ou num site público de treinamento autorizado (benchmark); não há ambiente de produção nesta versão.",
                help == null ? "Origem sem https ou misturada com o laboratório: não há autorização possível." : "Autorize só sites públicos feitos para "
                        + "praticar automação; sistemas de clientes continuam bloqueados.", help, null, null));
        return "";
    }

    private static QualityIssue unsupported(ProjectContext.Step s, FrameworkCapabilityCatalog cat, String code, String detail) {
        AssistanceRequest help = new AssistanceRequest("Como seguir com esta etapa?", detail + " O Studio não gera uma aproximação (posição, JavaScript, force).",
                List.of(AssistanceRequest.Option.action("GO_TO_STEP", "Rever a etapa no modelo", "trocar por uma ação suportada ou remover"),
                        AssistanceRequest.Option.action("SEE_EXTENDING", "Ver como estender o framework", cat.extending().file() + " — "
                                + cat.extending().section().replaceFirst("^#+\\s*", "")),
                        AssistanceRequest.Option.action("OPEN_DEBUG", "Depurar no navegador", "conferir o alvo e o contexto da etapa")),
                null, "Modelo (.nekkora) ou evolução do framework");
        return new QualityIssue(code, Layer.V2_CAPABILITY, QualityIssue.Kind.HELP_REQUIRED, s.stepId(), s.blockId(), place(s), detail,
                "Sem esta capacidade, nenhum código é gerado para o processo.", help, null, null);
    }

    private static QualityIssue locator(AutomationStep st, ProjectContext.Step s) {
        ElementReference e = st.element().orElseThrow();
        int index = -1;
        List<LocatorCandidate> l = e.locators();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).validated() && l.get(i).strategy() != LocatorStrategy.POSITIONAL && !l.get(i).equals(e.preferredLocator().orElse(null))) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return QualityIssue.of("LOCATOR_NOT_EXPORTABLE", Layer.V2_CAPABILITY, QualityIssue.Kind.BLOCKER, s.stepId(), place(s),
                    "O localizador preferido é posicional ou não validado, e não há alternativa validada.",
                    "Use Depurar no navegador e corrija o alvo por seleção.").withBlock(s.blockId());
        }
        LocatorCandidate alt = l.get(index);
        return new QualityIssue("LOCATOR_POSITIONAL", Layer.V2_CAPABILITY, QualityIssue.Kind.PROPOSE_REVIEW, s.stepId(), s.blockId(), place(s),
                "O localizador preferido muda com a posição na página.", "Revisar e aplicar troca a preferência; confirme o alvo de novo no debug guiado.",
                null, new QualityIssue.Fix("PREFER_LOCATOR:" + index, "Usar o localizador " + alt.strategy().name().toLowerCase(Locale.ROOT).replace('_', ' '),
                        "posicional", alt.strategy().name()), null);
    }

    static String place(ProjectContext.Step s) {
        return "Sessão " + s.session() + " · Etapa " + String.format("%02d", s.number()) + (s.targetName().isEmpty() ? "" : " — " + s.targetName());
    }
}
