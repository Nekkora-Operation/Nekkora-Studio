package com.nekkora.studio.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.nekkora.studio.domain.Automation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepDraft;
import com.nekkora.studio.domain.action.ActionParameters;
import com.nekkora.studio.domain.action.ActionType;
import com.nekkora.studio.domain.action.ValueSource;

/**
 * STUDIO-009 G1 — o plano é seguro também como API Java (lição da revisão 1 do PR #11): nenhum caminho público alcança
 * valores, finalidades, nomes, apelidos de credencial ou chaves de correlação, e nenhum objeto do domínio além de
 * identidades e enums.
 */
class GuidedPlanApiTest {
    private static final List<String> EXCLUDED = List.of(LabCase.LITERAL_URL, "termo-ficticio-xyz", "portal.senha.lab", "Código fictício do registro consultado",
            "Abrir a consulta", "Preencher código do registro", "Consulta de registro (laboratório)", "Caso vertical", "tgt-3f6c1a", "tgt-8d02b7", "rec-lab-009",
            "n-41", "cap-7");

    /** Tipos do domínio que o plano pode expor: identidades (os enums do domínio não são percorridos). */
    private static final Set<String> DOMAIN_ALLOWED = Set.of("AutomationId", "SessionId", "StepId", "VariableId");

    /** Resultado da varredura: textos alcançados (com o caminho) e tipos alcançados. */
    private record Reach(List<String> strings, Set<String> types) {}

    private static Reach walk(Object root) {
        Reach r = new Reach(new ArrayList<>(), new TreeSet<>());
        walk(root, "plan", r, new IdentityHashMap<>(), 0);
        return r;
    }

    private static void walk(Object o, String path, Reach r, Map<Object, Boolean> seen, int depth) {
        if (o == null || depth > 16) return;
        Class<?> c = o.getClass();
        if (o instanceof String s) {
            r.strings().add(path + " = " + s);
            return;
        }
        if (o instanceof Enum<?> || o instanceof Number || o instanceof Boolean || o instanceof Character || o instanceof UUID || o instanceof BigDecimal) {
            return;
        }
        if (seen.put(o, Boolean.TRUE) != null) return;
        r.types().add(c.getName());
        if (o instanceof Optional<?> opt) {
            walk(opt.orElse(null), path + ".get()", r, seen, depth + 1);
            return;
        }
        if (o instanceof Map<?, ?> m) {
            int i = 0;
            for (var e : m.entrySet()) {
                walk(e.getKey(), path + ".key[" + i + "]", r, seen, depth + 1);
                walk(e.getValue(), path + ".value[" + i++ + "]", r, seen, depth + 1);
            }
            return;
        }
        if (o instanceof Collection<?> col) {
            int i = 0;
            for (Object x : col) walk(x, path + "[" + i++ + "]", r, seen, depth + 1);
            return;
        }
        // records (imutáveis) e as classes do próprio contrato: todo método público sem argumentos é um caminho de acesso.
        // Outras classes não são invocadas (poderiam alterar estado): alcançá-las já é registrado em types().
        if (!c.isRecord() && !c.getPackageName().equals(GuidedPlan.class.getPackageName())) return;
        for (Method m : c.getMethods()) {
            if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers()) || m.getReturnType() == void.class
                    || m.getDeclaringClass() == Object.class || m.getName().equals("hashCode")) {
                continue;
            }
            Object v;
            try {
                if (!m.trySetAccessible()) continue;
                v = m.invoke(o);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            walk(v, path + "." + m.getName() + "()", r, seen, depth + 1);
        }
    }


    private static Automation exemplo() {
        Automation a = LabCase.consulta();
        LogicalSession s = a.sessions().get(0);
        a.appendStep(s.id(), StepDraft.builder().name("Credencial").actionType(ActionType.FILL)
                .parameters(ActionParameters.withValue(new ValueSource.CredentialReference("portal.senha.lab"))).build());
        a.appendStep(s.id(), StepDraft.builder().name("Literal").actionType(ActionType.FILL)
                .parameters(ActionParameters.withValue(new ValueSource.LiteralValue("termo-ficticio-xyz"))).build());
        return a;
    }

    @Test
    @DisplayName("nenhum caminho público do plano alcança literal, finalidade, nomes, apelido de credencial ou chave de correlação")
    void apiSemValoresExcluidos() {
        Reach reach = walk(GuidedPlanTest.plan(exemplo()));
        assertTrue(reach.strings().stream().anyMatch(x -> x.endsWith(" = registro.codigo")) && reach.strings().stream().anyMatch(x -> x.endsWith(" = #registro-codigo")),
                "a varredura alcança o que o plano publica (nome da variável, localizador)");
        List<String> leaks = new ArrayList<>();
        for (String s : reach.strings()) {
            String value = s.substring(s.indexOf(" = ") + 3);
            for (String x : EXCLUDED) if (value.contains(x)) leaks.add(s);
        }
        assertEquals(List.of(), leaks, "caminhos públicos que alcançam conteúdo excluído");
    }

    @Test
    @DisplayName("o plano não guarda referências ao domínio além de identidades (nem transitivas)")
    void semReferenciasAoDominio() {
        Reach reach = walk(GuidedPlanTest.plan(exemplo()));
        List<String> domain = reach.types().stream().filter(t -> t.startsWith("com.nekkora.studio.domain."))
                .filter(t -> !DOMAIN_ALLOWED.contains(t.substring(t.lastIndexOf('.') + 1))).toList();
        assertEquals(List.of(), domain, "tipos do domínio alcançáveis pelo plano");
        List<String> outside = reach.types().stream().filter(t -> !t.startsWith("com.nekkora.studio.") && !t.startsWith("java.util.")).toList();
        assertEquals(List.of(), outside, "tipos fora do plano e de coleções");
        assertTrue(reach.types().contains(GuidedPlan.Step.class.getName()));
    }
}
