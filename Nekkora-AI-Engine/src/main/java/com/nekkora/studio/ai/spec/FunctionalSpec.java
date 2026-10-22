package com.nekkora.studio.ai.spec;

import com.nekkora.studio.ai.Hashes;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.AutomationId;
import com.nekkora.studio.domain.StepId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * STUDIO-014 — especificação funcional do processo, COMPLEMENTO estruturado do PDD
 * ({@code nekkora-studio/functional-spec/1.0.0}). O PDD Visual não tem campos para o efeito de um clique, a pós-condição
 * de uma gravação ou a conclusão observável do processo; essas DECISÕES ficam aqui, versionadas, revisáveis, com o ator e
 * ligadas a cada etapa por {@code StepId} + resumo do alvo (mudou a etapa → a decisão deixa de valer e a pergunta volta).
 *
 * <p>Só guarda decisões explícitas (resposta a um pedido de ajuda ou proposta aceita). O que é derivado (vocabulário de
 * rótulos, tipo do alvo, última observação) é recalculado a cada análise por {@link SpecResolver}. Não guarda valores,
 * textos livres nem URLs: os valores são códigos de um vocabulário fechado.
 *
 * <p>NEKKORA-E2E-002 (1.1.0, lê a 1.0.0): {@code DATA_FLOW} — como o processo recebe itens e entrega o que leu
 * ({@code SINGLE_JSON}: uma execução, resultados em JSON; {@code LIST_XLSX}: uma transação por linha de uma lista de entrada
 * e uma planilha de saída) — e {@code OUTPUT_COLUMN} por etapa Observar: {@code COLUMN:<cabeçalho>} (rótulo de interface do
 * vocabulário controlado, nunca dado), {@code FALLBACK:<StepId>} (fonte alternativa de outra coluna, quando o alvo dela
 * falta na página) ou {@code NONE}. A 1.0.0 continua sendo gravada quando nenhum campo novo é usado (revisões estáveis).
 *
 * <p>NEKKORA-SPEC-OPTIONAL-COLUMNS-001 (1.2.0, lê 1.0.0 e 1.1.0): {@code OUTPUT_REQUIREMENT} por etapa Observar que dá
 * uma coluna: {@code REQUIRED} ou {@code OPTIONAL:<motivo>} (vocabulário fechado, {@link #OPTIONAL_REASONS}). Sem decisão,
 * a coluna é obrigatória — o legado não muda. A 1.2.0 só é gravada quando o campo é usado, e o campo só é aceito nela.
 */
public record FunctionalSpec(AutomationId automationId, List<Decision> decisions) {

    public static final String SCHEMA = "nekkora-studio/functional-spec";
    public static final String VERSION = "1.2.0";
    static final String VERSION_1_1 = "1.1.0";
    static final String VERSION_1_0 = "1.0.0";

    public enum Field { EFFECT, POSTCONDITION, CONCLUSION, START_URL, DATA_FLOW, OUTPUT_COLUMN, OUTPUT_REQUIREMENT }

    /** Quem decidiu. Nunca "confirmado pelo cliente": é o vocabulário de ator do Studio. */
    public enum Actor { DEVELOPER, TEST_AGENT }

    /** Como a decisão nasceu: resposta direta ou proposta (inclusive da IA) aceita por um ator. */
    public enum Basis { ANSWER, PROPOSAL_ACCEPTED }

    public static final Set<String> EFFECTS = Set.of("NONE", "EXTERNAL");
    public static final Set<String> POSTCONDITIONS = Set.of("TARGET_HIDDEN", "FIRST_FIELD_REPLACED", "NEXT_TARGET_VISIBLE");
    public static final String NO_CONCLUSION = "NONE_ACCEPTED";
    public static final Set<String> DATA_FLOWS = Set.of("SINGLE_JSON", "LIST_XLSX");
    public static final String COLUMN = "COLUMN:";
    public static final String FALLBACK = "FALLBACK:";
    public static final String NOT_IN_OUTPUT = "NONE";
    /** A coluna precisa de valor: sem valor depois de todas as fontes, o item é recusado (o padrão, sem decisão). */
    public static final String REQUIRED = "REQUIRED";
    public static final String OPTIONAL = "OPTIONAL:";
    /**
     * Por que a coluna pode ficar vazia (a justificativa revisada, sem texto livre): o dado não existe em alguns itens
     * (ex.: algo que ainda não aconteceu) ou não se aplica a alguns itens.
     */
    public static final Set<String> OPTIONAL_REASONS = Set.of("ABSENT_FOR_SOME_ITEMS", "NOT_APPLICABLE_FOR_SOME_ITEMS");

    /** {@code true} só para {@code OPTIONAL:<motivo>} do vocabulário. */
    public static boolean optional(String requirement) {
        return requirement != null && requirement.startsWith(OPTIONAL) && OPTIONAL_REASONS.contains(requirement.substring(OPTIONAL.length()));
    }

    /** Cabeçalho de coluna aceitável: rótulo de interface do vocabulário controlado, curto, sem cara de fórmula. */
    public static boolean headerOk(String h) {
        return h != null && h.equals(h.strip()) && !h.isEmpty() && h.length() <= 60 && "=+-@".indexOf(h.charAt(0)) < 0
                && com.nekkora.studio.domain.privacy.CapturePrivacy.interfaceText(h) != null;
    }

    public record Decision(Field field, StepId step, String value, Actor actor, Basis basis, String fingerprint, Instant at) {
        public Decision {
            Objects.requireNonNull(field);
            Objects.requireNonNull(value);
            Objects.requireNonNull(actor);
            Objects.requireNonNull(basis);
            Objects.requireNonNull(at);
            switch (field) {
                case EFFECT -> require(step != null && fingerprint != null && EFFECTS.contains(value));
                case POSTCONDITION -> require(step != null && fingerprint != null && POSTCONDITIONS.contains(value));
                case CONCLUSION -> require(step == null && (NO_CONCLUSION.equals(value) || isUuid(value)));
                // endereço inicial: o MESMO que a pessoa abriria no navegador, sem consulta nem fragmento (podem carregar dados)
                case START_URL -> require(step == null && value.matches("https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[^?#\\s]*)?") && value.length() <= 300);
                case DATA_FLOW -> require(step == null && DATA_FLOWS.contains(value));
                case OUTPUT_COLUMN -> require(step != null && fingerprint != null && (NOT_IN_OUTPUT.equals(value)
                        || value.startsWith(COLUMN) && headerOk(value.substring(COLUMN.length()))
                        || value.startsWith(FALLBACK) && isUuid(value.substring(FALLBACK.length()))));
                case OUTPUT_REQUIREMENT -> require(step != null && fingerprint != null && (REQUIRED.equals(value) || optional(value)));
            }
        }
    }

    public FunctionalSpec {
        Objects.requireNonNull(automationId);
        decisions = List.copyOf(decisions);
    }

    public static FunctionalSpec empty(AutomationId id) {
        return new FunctionalSpec(id, List.of());
    }

    public Optional<Decision> decision(Field f, StepId step) {
        return decisions.stream().filter(d -> d.field() == f && Objects.equals(d.step(), step)).findFirst();
    }

    /** Nova versão com a decisão (substitui a do mesmo campo e etapa). */
    public FunctionalSpec with(Decision d) {
        List<Decision> out = new ArrayList<>();
        for (Decision x : decisions) if (!(x.field() == d.field() && Objects.equals(x.step(), d.step()))) out.add(x);
        out.add(d);
        return new FunctionalSpec(automationId, out);
    }

    /** Revisão de conteúdo ({@code sha256:…} do JSON canônico). */
    public String revision() {
        return Hashes.sha256(toJson());
    }

    public String toJson() {
        List<Object> list = new ArrayList<>();
        for (Decision d : decisions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("field", d.field().name());
            m.put("stepId", d.step() == null ? Json.NULL : d.step().value().toString());
            m.put("value", d.value());
            m.put("actor", d.actor().name());
            m.put("basis", d.basis().name());
            m.put("fingerprint", d.fingerprint() == null ? Json.NULL : d.fingerprint());
            m.put("at", d.at().toString());
            list.add(m);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", SCHEMA);
        boolean requirement = decisions.stream().anyMatch(d -> d.field() == Field.OUTPUT_REQUIREMENT);
        boolean modern = decisions.stream().anyMatch(d -> d.field() == Field.DATA_FLOW || d.field() == Field.OUTPUT_COLUMN);
        doc.put("version", requirement ? VERSION : modern ? VERSION_1_1 : VERSION_1_0);
        doc.put("automationId", automationId.value().toString());
        doc.put("decisions", list);
        return Json.write(doc);
    }

    /** Leitura estrita: qualquer campo fora do contrato → {@link IllegalArgumentException} (sem conteúdo na mensagem). */
    @SuppressWarnings("unchecked")
    public static FunctionalSpec parse(String json) {
        try {
            Map<String, Object> m = Json.parseObject(json);
            if (!Set.of("schema", "version", "automationId", "decisions").equals(m.keySet())) throw new IllegalArgumentException("SPEC_FIELDS");
            Object version = m.get("version");
            if (!SCHEMA.equals(m.get("schema")) || !(VERSION.equals(version) || VERSION_1_1.equals(version) || VERSION_1_0.equals(version))) {
                throw new IllegalArgumentException("SPEC_VERSION");
            }
            AutomationId id = new AutomationId(UUID.fromString((String) m.get("automationId")));
            List<Decision> out = new ArrayList<>();
            for (Object o : (List<Object>) m.get("decisions")) {
                Map<String, Object> d = (Map<String, Object>) o;
                if (!Set.of("field", "stepId", "value", "actor", "basis", "fingerprint", "at").equals(d.keySet())) throw new IllegalArgumentException("SPEC_FIELDS");
                Object step = d.get("stepId");
                Object fp = d.get("fingerprint");
                Field field = Field.valueOf((String) d.get("field"));
                // campo da 1.2.0 num documento de versão anterior: o documento não é dessa versão (nada é presumido)
                if (field == Field.OUTPUT_REQUIREMENT && !VERSION.equals(version)) throw new IllegalArgumentException("SPEC_VERSION");
                out.add(new Decision(field, step == Json.NULL ? null : new StepId(UUID.fromString((String) step)),
                        (String) d.get("value"), Actor.valueOf((String) d.get("actor")), Basis.valueOf((String) d.get("basis")),
                        fp == Json.NULL ? null : (String) fp, Instant.parse((String) d.get("at"))));
            }
            return new FunctionalSpec(id, out);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException iae && iae.getMessage() != null && iae.getMessage().startsWith("SPEC_")) throw iae;
            throw new IllegalArgumentException("SPEC_INVALID");
        }
    }

    private static void require(boolean ok) {
        if (!ok) throw new IllegalArgumentException("SPEC_DECISION_INVALID");
    }

    private static boolean isUuid(String s) {
        return s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
