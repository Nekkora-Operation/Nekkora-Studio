package com.nekkora.studio.app.develop;

import com.nekkora.studio.bridge.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * NEKKORA-CODEGEN-QUALITY-001 — texto de "Revisar código gerado": como o projeto está organizado (Pages, métodos, dados
 * lidos, processo), de onde vem cada nome (título do PDD ou ações), o mapa {@code step_id} ↔ etapa do Studio, como cada
 * elemento é localizado, os hashes e o código de {@code paginas.py} e {@code processo.py} exatamente como estão na pasta.
 * Só leitura; nada é reformatado nem reescrito.
 */
final class CodeReviewText {
    private CodeReviewText() {}

    @SuppressWarnings("unchecked")
    static String build(Path dir) throws IOException {
        Path manifestFile = dir.resolve("manifest.json");
        if (!Files.isRegularFile(manifestFile)) return "Ainda não há código gerado nesta pasta: use Desenvolver.";
        Map<String, Object> m = Json.parseObject(Files.readString(manifestFile, StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        b.append("Pasta: ").append(dir).append('\n');
        b.append("Manifesto ").append(m.get("version")).append(" · ").append(m.get("generator")).append(" · requisito ").append(m.get("framework")).append("\n\n");
        b.append("Como ler: as telas estão em paginas.py (uma classe por tela), a ordem do processo em processo.py e a linha de comando em main.py.\n");
        b.append("Os nomes vêm do PDD revisado e das variáveis do modelo. Para mudar um nome: edite o título no PDD (Abrir PDD) ou a variável e use\n");
        b.append("Desenvolver de novo — nunca o arquivo gerado (edição à mão é preservada, mas o novo código vai para .novo/.diff).\n\n");
        if (!(m.get("architecture") instanceof Map<?, ?> arch)) {
            b.append("Este código veio do gerador anterior (sem arquitetura nem mapa de rastreio no manifesto): use Desenvolver de novo.\n\n");
        } else {
            b.append("ARQUITETURA\n");
            for (Object o : (List<Object>) arch.get("pages")) {
                Map<String, Object> p = (Map<String, Object>) o;
                b.append("  ").append(p.get("class")).append(" — ").append(p.get("summary")).append("  [nome: ").append(source(String.valueOf(p.get("nameSource"))))
                        .append("]\n");
                if (p.get("boundary") instanceof String s && !s.isEmpty()) b.append("    (").append(s).append(")\n");
                for (Object x : (List<Object>) p.get("methods")) {
                    Map<String, Object> mm = (Map<String, Object>) x;
                    b.append("    ").append(mm.get("name")).append('(').append(String.join(", ", (List<String>) mm.get("parameters"))).append(") -> ")
                            .append(mm.get("returns")).append("  [nome: ").append(source(String.valueOf(mm.get("nameSource")))).append("]\n");
                    b.append("      ").append(mm.get("summary")).append('\n');
                }
            }
            for (Object o : (List<Object>) arch.get("models")) {
                Map<String, Object> md = (Map<String, Object>) o;
                b.append("  ").append(md.get("name")).append(" (dados lidos por ").append(md.get("readBy")).append("): ")
                        .append(String.join(", ", (List<String>) md.get("fields"))).append("   (? = pode faltar na página)\n");
            }
            b.append("  Processo: ").append(arch.get("process")).append("\n\n");
        }
        if (m.get("trace") instanceof Map<?, ?> t) {
            b.append("STEP_ID NO CÓDIGO → ETAPA DO STUDIO → ALVO → ONDE\n");
            for (Object o : (List<Object>) t.get("mappings")) {
                Map<String, Object> x = (Map<String, Object>) o;
                b.append("  ").append(x.get("traceId")).append(" → ").append(x.get("studioStepId")).append(" (").append(x.get("action")).append(") → ")
                        .append(x.get("targetKey") == null ? "—" : x.get("targetKey")).append(" → ").append(x.get("pythonSymbol")).append('\n');
            }
            b.append('\n');
        }
        if (m.get("locators") instanceof List<?> locators) {
            b.append("COMO CADA ELEMENTO É LOCALIZADO (selectors.json)\n");
            for (Object o : locators) {
                Map<String, Object> l = (Map<String, Object>) o;
                b.append("  ").append(l.get("key")).append(" — ").append(l.get("kind")).append(" — ").append(l.get("description")).append('\n');
            }
            b.append('\n');
        }
        if (m.get("files") instanceof Map<?, ?> files) {
            b.append("ARQUIVOS (SHA-256 registrados no manifesto)\n");
            files.forEach((f, h) -> b.append("  ").append(f).append("  ").append(h).append('\n'));
            b.append('\n');
        }
        for (String f : List.of("paginas.py", "processo.py")) {
            Path p = dir.resolve(f);
            if (!Files.isRegularFile(p)) continue;
            b.append("===================================== ").append(f).append(" =====================================\n");
            b.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
        }
        return b.toString();
    }

    static String source(String s) {
        return switch (s) {
            case "PDD_SECTION_TITLE" -> "título da seção do PDD";
            case "PDD_BLOCK_TITLE" -> "título do passo no PDD";
            case "SESSION_NAME" -> "nome da sessão";
            case "ACTIONS" -> "o que as ações fazem";
            case "PROVISIONAL" -> "PROVISÓRIO (sem evidência)";
            default -> s;
        };
    }
}
