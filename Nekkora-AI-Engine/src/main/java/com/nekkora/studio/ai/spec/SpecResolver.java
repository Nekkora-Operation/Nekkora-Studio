package com.nekkora.studio.ai.spec;

import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.quality.AssistanceRequest;
import com.nekkora.studio.ai.quality.Layer;
import com.nekkora.studio.ai.quality.QualityIssue;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * STUDIO-014 — junta as DECISÕES da especificação com o que pode ser DERIVADO das evidências e devolve o que falta como
 * pedidos de ajuda. Uma decisão só vale enquanto o resumo da etapa ({@link ProjectContext.Step#fingerprint()}) não mudar.
 *
 * <ul>
 *   <li>efeito de um clique (ou Enter): decisão → link (navega) → vocabulário do rótulo → pergunta;</li>
 *   <li>pós-condição de um clique com efeito externo: só por decisão (o sistema-alvo não se presume) → pergunta;</li>
 *   <li>conclusão observável: decisão → última etapa Observar/Aguardar com alvo → pergunta.</li>
 * </ul>
 */
public final class SpecResolver {
    private SpecResolver() {}

    /** De onde veio o valor resolvido. */
    public enum Provenance { DECIDED, DERIVED_LABEL, DERIVED_KIND, DERIVED_LAST_OBSERVATION, MISSING }

    public record Resolved<T>(T value, Provenance provenance, FunctionalSpec.Actor actor) {
        public boolean known() {
            return provenance != Provenance.MISSING;
        }
    }

    public record ResolvedSpec(Map<StepId, Resolved<EffectVocabulary.Effect>> effects, Map<StepId, Resolved<String>> postconditions,
                               Resolved<StepId> conclusion, Resolved<String> startUrl, List<QualityIssue> issues) {}

    public static ResolvedSpec resolve(ProjectContext ctx, FunctionalSpec spec) {
        Map<StepId, Resolved<EffectVocabulary.Effect>> effects = new LinkedHashMap<>();
        Map<StepId, Resolved<String>> post = new LinkedHashMap<>();
        List<QualityIssue> issues = new ArrayList<>();
        List<ProjectContext.Step> steps = ctx.steps();
        for (int i = 0; i < steps.size(); i++) {
            ProjectContext.Step s = steps.get(i);
            if (!needsEffect(s)) continue;
            Resolved<EffectVocabulary.Effect> eff = effect(s, spec);
            effects.put(s.stepId(), eff);
            if (!eff.known()) {
                issues.add(effectQuestion(s));
                continue;
            }
            if (eff.value() == EffectVocabulary.Effect.EXTERNAL) {
                Optional<FunctionalSpec.Decision> d = valid(spec.decision(FunctionalSpec.Field.POSTCONDITION, s.stepId()), s);
                if (d.isPresent()) {
                    post.put(s.stepId(), new Resolved<>(d.get().value(), Provenance.DECIDED, d.get().actor()));
                } else {
                    post.put(s.stepId(), new Resolved<>(null, Provenance.MISSING, null));
                    issues.add(postconditionQuestion(s, next(steps, i)));
                }
            }
        }
        Resolved<StepId> conclusion = conclusion(ctx, spec);
        if (!conclusion.known()) issues.add(conclusionQuestion(ctx));
        Resolved<String> start = startUrl(ctx, spec);
        if (!start.known() && !ctx.origins().isEmpty()) issues.add(startQuestion(ctx));
        return new ResolvedSpec(effects, post, conclusion, start, issues);
    }

    /** Ações que podem mudar algo no sistema-alvo e precisam de classificação. */
    public static boolean needsEffect(ProjectContext.Step s) {
        return s.action() == ActionType.CLICK || s.action() == ActionType.PRESS_KEY;
    }

    private static Optional<FunctionalSpec.Decision> valid(Optional<FunctionalSpec.Decision> d, ProjectContext.Step s) {
        return d.filter(x -> s.fingerprint().equals(x.fingerprint()));
    }

    static Resolved<EffectVocabulary.Effect> effect(ProjectContext.Step s, FunctionalSpec spec) {
        Optional<FunctionalSpec.Decision> d = valid(spec.decision(FunctionalSpec.Field.EFFECT, s.stepId()), s);
        if (d.isPresent()) return new Resolved<>(EffectVocabulary.Effect.valueOf(d.get().value()), Provenance.DECIDED, d.get().actor());
        if (s.action() == ActionType.CLICK && "LINK".equals(s.targetKind())) return new Resolved<>(EffectVocabulary.Effect.NONE, Provenance.DERIVED_KIND, null);
        return EffectVocabulary.fromLabel(s.targetName()).map(e -> new Resolved<>(e, Provenance.DERIVED_LABEL, (FunctionalSpec.Actor) null))
                .orElse(new Resolved<>(null, Provenance.MISSING, null));
    }

    static Resolved<StepId> conclusion(ProjectContext ctx, FunctionalSpec spec) {
        Optional<FunctionalSpec.Decision> d = spec.decision(FunctionalSpec.Field.CONCLUSION, null);
        if (d.isPresent()) {
            if (FunctionalSpec.NO_CONCLUSION.equals(d.get().value())) return new Resolved<>(null, Provenance.DECIDED, d.get().actor());
            StepId id = new StepId(java.util.UUID.fromString(d.get().value()));
            if (ctx.step(id).isPresent()) return new Resolved<>(id, Provenance.DECIDED, d.get().actor());
        }
        for (int i = ctx.steps().size() - 1; i >= 0; i--) {
            ProjectContext.Step s = ctx.steps().get(i);
            // Observar (com alvo ou com padrão de texto declarado) ou Aguardar com alvo: a última delas é a conclusão observável
            if (s.action() == ActionType.OBSERVE || (s.action() == ActionType.WAIT_FOR && s.hasTarget())) {
                return new Resolved<>(s.stepId(), Provenance.DERIVED_LAST_OBSERVATION, null);
            }
        }
        return new Resolved<>(null, Provenance.MISSING, null);
    }

    /** Endereço inicial: o observado inteiro (ou de uma etapa Navegar) → a decisão que casa com o modelo gravado → pergunta. */
    static Resolved<String> startUrl(ProjectContext ctx, FunctionalSpec spec) {
        if (!ctx.startUrl().isEmpty()) return new Resolved<>(ctx.startUrl(), Provenance.DERIVED_LABEL, null);
        Optional<FunctionalSpec.Decision> d = spec.decision(FunctionalSpec.Field.START_URL, null);
        if (d.isPresent() && ProjectContext.matchesTemplate(d.get().value(), ctx.startTemplate())) {
            return new Resolved<>(d.get().value(), Provenance.DECIDED, d.get().actor());
        }
        return new Resolved<>(null, Provenance.MISSING, null);
    }

    private static QualityIssue startQuestion(ProjectContext ctx) {
        AssistanceRequest help = new AssistanceRequest("Qual endereço o processo abre no início?",
                "Por privacidade, a gravação guarda o endereço só como modelo (" + ctx.startTemplate() + "). O Studio não adivinha os trechos mascarados; "
                        + "a resposta é conferida contra esse modelo (mesma origem e mesmos trechos conhecidos, sem consulta).",
                List.of(AssistanceRequest.Option.text("Endereço inicial", "o mesmo que você abre no navegador para começar"),
                        AssistanceRequest.Option.action("GO_TO_STEP", "Ir para a primeira etapa", "conferir onde a gravação começou")),
                FunctionalSpec.Field.START_URL, "Especificação funcional · endereço inicial");
        ProjectContext.Step first = ctx.steps().isEmpty() ? null : ctx.steps().get(0);
        return new QualityIssue("SPEC_START_URL_UNKNOWN", Layer.V1_PDD, QualityIssue.Kind.HELP_REQUIRED, first == null ? null : first.stepId(),
                first == null ? null : first.blockId(), "Processo · início", "O endereço inicial não está guardado por inteiro na gravação.",
                "Informe o endereço inicial; ele fica na especificação funcional, nunca no código como dado.", help, null, null);
    }

    private static ProjectContext.Step next(List<ProjectContext.Step> steps, int i) {
        for (int j = i + 1; j < steps.size(); j++) if (steps.get(j).hasTarget()) return steps.get(j);
        return null;
    }

    static String place(ProjectContext.Step s) {
        return "Sessão " + s.session() + " · Etapa " + String.format("%02d", s.number()) + (s.targetName().isEmpty() ? "" : " — " + s.targetName());
    }

    private static QualityIssue effectQuestion(ProjectContext.Step s) {
        AssistanceRequest help = new AssistanceRequest(
                "O clique em " + (s.targetName().isEmpty() ? "este alvo" : "“" + s.targetName() + "”") + " grava ou envia algo no sistema?",
                "O rótulo não está no vocabulário de ações conhecidas e o Studio não presume o efeito de um clique: um clique que grava não pode ser "
                        + "repetido às cegas e precisa de uma confirmação objetiva.",
                List.of(AssistanceRequest.Option.answer("NONE", "Não: só consulta, navega ou abre algo", "nenhum efeito de negócio; pode ser repetido"),
                        AssistanceRequest.Option.answer("EXTERNAL", "Sim: grava, envia ou apaga algo", "efeito externo; será feito uma vez, com confirmação"),
                        AssistanceRequest.Option.action("GO_TO_STEP", "Ir para a etapa", "rever a gravação antes de responder")),
                FunctionalSpec.Field.EFFECT, "Especificação funcional · efeito da etapa");
        return new QualityIssue("SPEC_EFFECT_UNKNOWN", Layer.V1_PDD, QualityIssue.Kind.HELP_REQUIRED, s.stepId(), s.blockId(), place(s),
                "O efeito deste clique no sistema-alvo não foi declarado.", "Responda à pergunta; a resposta fica na especificação funcional.", help, null, null);
    }

    private static QualityIssue postconditionQuestion(ProjectContext.Step s, ProjectContext.Step next) {
        List<AssistanceRequest.Option> opts = new ArrayList<>();
        opts.add(AssistanceRequest.Option.answer("TARGET_HIDDEN", "O botão some depois do envio", "o processo espera o alvo desaparecer"));
        opts.add(AssistanceRequest.Option.answer("FIRST_FIELD_REPLACED", "O formulário é recriado (campos limpos)", "o processo marca o 1º campo e confere a troca"));
        if (next != null) {
            opts.add(AssistanceRequest.Option.answer("NEXT_TARGET_VISIBLE", "Aparece " + (next.targetName().isEmpty() ? "o alvo da etapa seguinte"
                    : "“" + next.targetName() + "”"), "o alvo da etapa seguinte prova que o envio terminou"));
        }
        opts.add(AssistanceRequest.Option.action("OPEN_PDD", "Abrir o PDD", "documentar a confirmação antes"));
        AssistanceRequest help = new AssistanceRequest("Como saber que o envio de " + (s.targetName().isEmpty() ? "esta etapa" : "“" + s.targetName() + "”")
                + " deu certo?", "Sem uma confirmação objetiva, um envio que falhar no meio vira desfecho desconhecido e não pode ser repetido.", opts,
                FunctionalSpec.Field.POSTCONDITION, "Especificação funcional · confirmação do envio");
        return new QualityIssue("SPEC_POSTCONDITION_MISSING", Layer.V1_PDD, QualityIssue.Kind.HELP_REQUIRED, s.stepId(), s.blockId(), place(s),
                "Clique com efeito externo sem confirmação declarada.", "Escolha a confirmação observável do envio.", help, null, null);
    }

    private static QualityIssue conclusionQuestion(ProjectContext ctx) {
        List<AssistanceRequest.Option> opts = new ArrayList<>();
        opts.add(AssistanceRequest.Option.action("GO_TO_LAST_STEP", "Acrescentar uma etapa Observar no fim", "ler o resultado na tela"));
        opts.add(AssistanceRequest.Option.answer(FunctionalSpec.NO_CONCLUSION, "Não há resultado para ler (aceitar a limitação)",
                "o código termina na última etapa, sem confirmação; a limitação vai para o relatório"));
        AssistanceRequest help = new AssistanceRequest("Como saber que o processo terminou bem?",
                "Nenhuma etapa lê ou espera um resultado na tela; sem isso, \"terminou\" significa só \"não deu erro\".", opts,
                FunctionalSpec.Field.CONCLUSION, "Especificação funcional · conclusão observável");
        return new QualityIssue("SPEC_CONCLUSION_MISSING", Layer.V1_PDD, QualityIssue.Kind.HELP_REQUIRED, null, null, "Processo",
                "O processo não tem conclusão observável.", "Acrescente uma etapa Observar no resultado ou aceite a limitação.", help, null, null);
    }
}
