package com.nekkora.studio.semantics;

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

/**
 * STUDIO-008, revisão do PR #11 (P1): a API JAVA do contrato semântico cumpre a mesma minimização do JSON. Percorre TODO
 * caminho público alcançável a partir de {@link SemanticContract} — componentes de records, elementos de coleções,
 * {@link Optional} e métodos públicos sem argumentos — e confere que nenhum leva ao valor fixo, à finalidade em texto
 * livre, a literais, a apelidos de credencial, a nomes de etapas/sessões/automação ou a objetos do domínio que os
 * carreguem (a definição completa da variável, a origem com o valor, o rascunho, a própria automação).
 *
 * <p>Escrito ANTES da correção: no código revisado ({@code 6d24d51}) o caminho
 * {@code variables().get(0).definition().origin().constant()} devolvia a constante e {@code definition().purpose()} a
 * finalidade, e este teste falhava.
 */
class SemanticContractApiTest {
    /** Tudo o que a automação de exemplo tem e o contrato promete excluir. */
    private static final List<String> EXCLUDED = List.of("https://lab.exemplo.test/portal", "Endereço do portal de laboratório", "texto literal fictício",
            "sistema.senha.ficticia", "Cadastro de Pedidos", "Exemplo fictício", "Dados do titular", "Cadastro", "Pesquisa", "Abrir portal", "Preencher CPF",
            "Ler protocolo", "Cliente.Nome.Digitado");

    /** Tipos do domínio que um contrato pode expor: só identidades e vocabulários fechados (enums). */
    private static final Set<String> DOMAIN_ALLOWED = Set.of("AutomationId", "SessionId", "StepId", "VariableId");

    /** Resultado da varredura: textos alcançados (com o caminho) e tipos alcançados. */
    private record Reach(List<String> strings, Set<String> types) {}

    private static Reach walk(Object root) {
        Reach r = new Reach(new ArrayList<>(), new TreeSet<>());
        walk(root, "contract", r, new IdentityHashMap<>(), 0);
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
        if (!c.isRecord() && !c.getPackageName().equals(SemanticContract.class.getPackageName())) return;
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

    @Test
    @DisplayName("P1: nenhum caminho público do contrato alcança valor fixo, finalidade, literal, apelido de credencial ou nomes de etapas")
    void apiSemValoresExcluidos() {
        Automation a = SemanticContractTest.exemplo();
        assertTrue(a.variables().get(0).origin().constant() != null && !a.variables().get(0).purpose().isEmpty(), "o exemplo tem constante e finalidade");
        Reach reach = walk(SemanticContract.of(a));
        List<String> leaks = new ArrayList<>();
        for (String s : reach.strings()) {
            String value = s.substring(s.indexOf(" = ") + 3);
            for (String x : EXCLUDED) if (value.contains(x)) leaks.add(s);
        }
        assertEquals(List.of(), leaks, "caminhos públicos que alcançam conteúdo excluído");
    }

    @Test
    @DisplayName("P1: o contrato não guarda referências ao domínio além de identidades (nem transitivas): definição, origem, rascunho, automação")
    void semReferenciasAoDominio() {
        Reach reach = walk(SemanticContract.of(SemanticContractTest.exemplo()));
        List<String> domain = reach.types().stream().filter(t -> t.startsWith("com.nekkora.studio.domain."))
                .filter(t -> !DOMAIN_ALLOWED.contains(t.substring(t.lastIndexOf('.') + 1))).toList();
        assertEquals(List.of(), domain, "tipos do domínio alcançáveis pelo contrato");
        List<String> outside = reach.types().stream().filter(t -> !t.startsWith("com.nekkora.studio.") && !t.startsWith("java.util.")).toList();
        assertEquals(List.of(), outside, "tipos fora do contrato e de coleções");
    }

    @Test
    @DisplayName("P1: a origem da variável diz só o tipo, se há constante declarada e a produtora — nunca o valor")
    void origemSemValor() throws Exception {
        SemanticContract c = SemanticContract.of(SemanticContractTest.exemplo());
        Object variable = c.variables().get(0);
        List<String> components = new ArrayList<>();
        for (var rc : variable.getClass().getRecordComponents()) components.add(rc.getName());
        assertTrue(!components.contains("definition"), "a variável do contrato não carrega a definição do domínio: " + components);
        Object origin = variable.getClass().getMethod("origin").invoke(variable);
        List<String> originComponents = new ArrayList<>();
        for (var rc : origin.getClass().getRecordComponents()) originComponents.add(rc.getName());
        assertEquals(List.of("kind", "constantDeclared", "producer"), originComponents);
        assertEquals(Boolean.TRUE, origin.getClass().getMethod("constantDeclared").invoke(origin));
    }
}
