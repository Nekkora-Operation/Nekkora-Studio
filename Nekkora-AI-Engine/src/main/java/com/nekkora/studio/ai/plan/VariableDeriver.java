package com.nekkora.studio.ai.plan;

import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;
import com.nekkora.studio.domain.variable.Sensitivity;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.domain.variable.VariableDraft;
import com.nekkora.studio.domain.variable.VariableOrigin;
import com.nekkora.studio.domain.variable.VariableType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * STUDIO-014 — variáveis DERIVADAS das evidências, sem cadastro manual e sem inventar valores:
 * <ul>
 *   <li>preenchimento de campo SENSÍVEL (senha) → credencial por apelido ({@code credencial.senha});</li>
 *   <li>preenchimento logo antes de uma senha, na mesma sessão → credencial ({@code credencial.usuario}): usuário de
 *       acesso nunca vira dado de entrada em claro;</li>
 *   <li>outro preenchimento sem valor → variável de ENTRADA com o nome do rótulo seguro do campo (dados pessoais pelo
 *       vocabulário do rótulo); sem rótulo seguro → nome genérico, para revisar;</li>
 *   <li>Observar com alvo → variável SAÍDA DE ETAPA com a etapa como produtora; NEKKORA-E2E-002: nomeada pelo rótulo da
 *       LINHA que ancora o dado ("Nascimento" → {@code nascimento}), "titulo" para um título; tipo DATA quando o rótulo é de
 *       data conhecido (nascimento, morte, data…);</li>
 *   <li>Observar SEM alvo → {@code endereco_da_pagina} (o endereço lido da aba).</li>
 * </ul>
 * Valor já configurado (variável, credencial ou literal) é REUSADO. Nome igual a uma declaração existente compatível
 * reaproveita a declaração; incompatível ganha sufixo. Nenhuma constante é criada: constante exige regra comprovada.
 */
public final class VariableDeriver {
    private VariableDeriver() {}

    public enum Kind { DECLARE_INPUT_AND_BIND, BIND_EXISTING_VARIABLE, BIND_CREDENTIAL, DECLARE_OUTPUT, REUSE }

    /** De onde veio a proposta. */
    public enum Provenance { EXISTING, DERIVED_LABEL, DERIVED_SENSITIVE, DERIVED_LOGIN_PAIR, DERIVED_GENERIC, DERIVED_OBSERVATION }

    /**
     * Uma variável (ou credencial) do plano.
     *
     * @param name        nome da variável ou apelido da credencial
     * @param origin      {@code INPUT}, {@code STEP_OUTPUT} ou {@code CREDENTIAL}
     * @param basis       fundamento em texto (rótulo seguro do campo e a regra), sem valores
     * @param needsReview o nome é genérico (o campo não tem rótulo seguro): revisar antes de gerar
     */
    public record Planned(StepId step, Kind kind, String name, String type, String origin, Sensitivity sensitivity, Provenance provenance,
                          String basis, boolean needsReview) {
        public Planned {
            Objects.requireNonNull(step);
            Objects.requireNonNull(name);
        }

        /** Muda o modelo (precisa ser aplicada pela operação desfazível do Studio)? */
        public boolean changesModel() {
            return kind != Kind.REUSE;
        }
    }

    static final Set<String> USER_LABELS = Set.of("usuario", "login", "user", "username", "email", "matricula", "conta", "acesso", "cpf");
    /** Rótulos de linha que nomeiam DATAS (o dado é lido como data por extenso e normalizado só se completo). */
    static final Set<String> DATE_LABELS = Set.of("nascimento", "morte", "falecimento", "obito", "data", "vencimento", "emissao", "validade", "admissao",
            "demissao", "fundacao");

    static final Set<String> PERSONAL = Set.of("cpf", "rg", "nome", "email", "telefone", "celular", "endereco", "nascimento", "cartao", "conta", "paciente",
            "cliente", "cnpj", "cep");

    public static List<Planned> derive(Automation a, ProjectContext ctx) {
        List<Planned> out = new ArrayList<>();
        Set<String> taken = new HashSet<>(ctx.declaredVariables());
        Set<String> aliases = new HashSet<>();
        List<ProjectContext.Step> steps = ctx.steps();
        for (int i = 0; i < steps.size(); i++) {
            ProjectContext.Step s = steps.get(i);
            if (s.action() == ActionType.FILL) {
                switch (s.value()) {
                    case VARIABLE -> {
                        VariableDefinition v = a.variableNamed(s.variableName()).orElse(null);
                        out.add(new Planned(s.stepId(), Kind.REUSE, s.variableName(), "texto", v == null ? "UNBOUND" : v.origin().kind().name(),
                                v == null ? Sensitivity.PUBLIC : v.sensitivity(), Provenance.EXISTING, "variável já ligada à etapa", false));
                    }
                    case CREDENTIAL -> out.add(new Planned(s.stepId(), Kind.REUSE, credentialAlias(a, s.stepId()), "segredo", "CREDENTIAL", Sensitivity.SECRET,
                            Provenance.EXISTING, "credencial já ligada à etapa", false));
                    case LITERAL -> out.add(new Planned(s.stepId(), Kind.REUSE, "valor fixo", "texto", "LITERAL", Sensitivity.PUBLIC, Provenance.EXISTING,
                            "valor fixo não sensível declarado na etapa", false));
                    case NONE -> out.add(fill(s, i, steps, taken, aliases));
                }
            } else if (s.action() == ActionType.OBSERVE) {
                Optional<VariableDefinition> existing = a.variables().stream()
                        .filter(v -> v.origin().kind() == VariableOrigin.Kind.STEP_OUTPUT && s.stepId().equals(v.origin().producer())).findFirst();
                if (existing.isPresent()) {
                    out.add(new Planned(s.stepId(), Kind.REUSE, existing.get().name(), existing.get().type() == VariableType.DATE ? "data" : "texto", "STEP_OUTPUT",
                            existing.get().sensitivity(), Provenance.EXISTING, "saída já declarada para esta etapa", false));
                } else if (!s.hasTarget()) {
                    out.add(new Planned(s.stepId(), Kind.DECLARE_OUTPUT, unique("endereco_da_pagina", taken), "texto", "STEP_OUTPUT", Sensitivity.INTERNAL,
                            Provenance.DERIVED_OBSERVATION, "endereço da página lido na etapa Observar (sem alvo)", false));
                } else if (!s.anchor().isEmpty()) {
                    boolean date = words(s.anchor()).stream().anyMatch(DATE_LABELS::contains);
                    out.add(new Planned(s.stepId(), Kind.DECLARE_OUTPUT, unique(slug(s.anchor()), taken), date ? "data" : "texto", "STEP_OUTPUT", Sensitivity.INTERNAL,
                            Provenance.DERIVED_LABEL, "dado da linha “" + s.anchor() + "”" + (date ? " (rótulo de data: lido como data por extenso)" : ""), false));
                } else {
                    boolean heading = "HEADING".equals(s.targetKind());
                    String base = heading ? "titulo" : s.targetName().isEmpty() ? "resultado" : slug(s.targetName());
                    String name = unique(heading || base.startsWith("resultado") ? base : "resultado_" + base, taken);
                    out.add(new Planned(s.stepId(), Kind.DECLARE_OUTPUT, name, "texto", "STEP_OUTPUT", Sensitivity.INTERNAL, Provenance.DERIVED_OBSERVATION,
                            "texto lido na etapa Observar" + (s.targetName().isEmpty() ? (heading ? " (título)" : "") : " “" + s.targetName() + "”"),
                            s.targetName().isEmpty() && !heading));
                }
            }
        }
        return out;
    }

    private static Planned fill(ProjectContext.Step s, int i, List<ProjectContext.Step> steps, Set<String> taken, Set<String> aliases) {
        if (s.sensitive()) {
            return new Planned(s.stepId(), Kind.BIND_CREDENTIAL, uniqueAlias("credencial.senha", aliases), "segredo", "CREDENTIAL", Sensitivity.SECRET,
                    Provenance.DERIVED_SENSITIVE, "campo sensível" + label(s) + ": o valor vem do provedor de segredos", false);
        }
        ProjectContext.Step next = i + 1 < steps.size() ? steps.get(i + 1) : null;
        if (next != null && next.action() == ActionType.FILL && next.sensitive() && next.session() == s.session()) {
            boolean known = words(s.targetName()).stream().anyMatch(USER_LABELS::contains);
            return new Planned(s.stepId(), Kind.BIND_CREDENTIAL, uniqueAlias("credencial.usuario", aliases), "segredo", "CREDENTIAL", Sensitivity.SECRET,
                    Provenance.DERIVED_LOGIN_PAIR, "campo" + label(s) + " logo antes da senha: usuário de acesso vem do provedor de segredos", !known);
        }
        boolean generic = s.targetName().isEmpty();
        // E2E-002: campo de BUSCA sem rótulo seguro (searchbox, ou combobox editável confirmado por Enter) tem nome de busca
        boolean search = generic && ("SEARCH_INPUT".equals(s.targetKind()) || "COMBOBOX".equals(s.targetKind()) && next != null
                && next.action() == ActionType.PRESS_KEY && next.session() == s.session());
        String name = unique(search ? "termo_de_busca" : generic ? "entrada_etapa_" + s.session() + "_" + s.number() : slug(s.targetName()), taken);
        Sensitivity sens = words(s.targetName()).stream().anyMatch(PERSONAL::contains) ? Sensitivity.PERSONAL_DATA : Sensitivity.INTERNAL;
        return new Planned(s.stepId(), Kind.DECLARE_INPUT_AND_BIND, name, "texto", "INPUT", sens, generic ? Provenance.DERIVED_GENERIC : Provenance.DERIVED_LABEL,
                generic ? "campo sem rótulo seguro: nome provisório" : "campo" + label(s) + ": informado a cada execução", generic);
    }

    /** Aplica as propostas no MODELO (chamar dentro da operação desfazível do Studio). Devolve quantas etapas mudaram. */
    public static int apply(Automation a, List<Planned> plan) {
        int changed = 0;
        for (Planned p : plan) {
            switch (p.kind()) {
                case DECLARE_INPUT_AND_BIND -> {
                    if (a.variableNamed(p.name()).isEmpty()) {
                        a.declareVariable(VariableDraft.of(p.name(), VariableType.STRING).withOrigin(VariableOrigin.input()).withSensitivity(p.sensitivity())
                                .withPurpose("Entrada da execução (derivada pelo Studio: " + p.basis() + ")"));
                    }
                    if (a.setStepParameters(p.step(), ActionParameters.withValue(new ValueSource.VariableReference(p.name())))) changed++;
                }
                case BIND_CREDENTIAL -> {
                    if (a.setStepParameters(p.step(), ActionParameters.withValue(new ValueSource.CredentialReference(p.name())))) changed++;
                }
                case DECLARE_OUTPUT -> {
                    if (a.variableNamed(p.name()).isEmpty()) {
                        VariableType type = "data".equals(p.type()) ? VariableType.DATE : VariableType.STRING;
                        a.declareVariable(VariableDraft.of(p.name(), type).withOrigin(VariableOrigin.stepOutput(p.step()))
                                .withSensitivity(p.sensitivity()).withPurpose("Resultado lido pelo processo (derivado pelo Studio: " + p.basis() + ")"));
                        changed++;
                    }
                }
                case BIND_EXISTING_VARIABLE, REUSE -> {
                }
            }
        }
        return changed;
    }

    private static String credentialAlias(Automation a, StepId step) {
        return a.step(step).flatMap(st -> st.parameters().value()).map(v -> v instanceof ValueSource.CredentialReference c ? c.alias() : "").orElse("");
    }

    private static String label(ProjectContext.Step s) {
        return s.targetName().isEmpty() ? "" : " “" + s.targetName() + "”";
    }

    static List<String> words(String text) {
        if (text == null) return List.of();
        String n = Normalizer.normalize(com.nekkora.studio.codegen.CodeNaming.foldMarkers(text), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace("e-mail", "email");
        List<String> out = new ArrayList<>();
        for (String w : n.split("[^a-z0-9]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** Nome de variável legível a partir do rótulo seguro ("Código do registro" → {@code codigo_do_registro}). */
    public static String slug(String label) {
        String s = String.join("_", words(label));
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) s = "campo_" + s;
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("_+$", "");
        return s;
    }

    private static String unique(String base, Set<String> taken) {
        String n = base;
        int i = 2;
        while (!taken.add(n)) n = base + "_" + i++;
        return n;
    }

    private static String uniqueAlias(String base, Set<String> taken) {
        return unique(base, taken);
    }
}
