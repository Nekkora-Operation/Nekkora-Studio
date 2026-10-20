package com.nekkora.studio.ai.provider;

import com.nekkora.studio.ai.ConsultaCase;
import com.nekkora.studio.ai.capability.FrameworkCapabilityCatalog;
import com.nekkora.studio.ai.context.ProjectContext;
import com.nekkora.studio.ai.gate.PddGate;
import com.nekkora.studio.ai.prompt.PromptRepository;
import com.nekkora.studio.ai.spec.FunctionalSpec;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.pdd.DocumentDraft;
import com.nekkora.studio.pdd.ProcessProjection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * STUDIO-014 · Caso D — UMA chamada REAL ao provedor da Anthropic (não é teste da suíte: roda só à mão, com a chave no
 * ambiente {@code NEKKORA_ANTHROPIC_API_KEY}). Projeto SINTÉTICO (ConsultaCase com botão fora do vocabulário), a MESMA
 * carga minimizada e o MESMO verificador do Studio. Grava só metadados seguros: provedor, modelo, tokens, tempo, tamanho da
 * carga, propostas aceitas (tipo, etapa conhecida, valor do vocabulário) e códigos das recusadas — nunca a resposta crua,
 * a justificativa, o prompt ou a chave.
 *
 * <p>Uso: {@code java ... RealAnthropicCheck <saída.json>}
 */
public final class RealAnthropicCheck {
    private RealAnthropicCheck() {}

    public static void main(String[] args) throws Exception {
        AnthropicProvider provider = new AnthropicProvider();
        if (!provider.available()) {
            System.out.println("sem chave no ambiente: nada enviado");
            System.exit(3);
        }
        ConsultaCase c = ConsultaCase.unknownButton();
        DocumentDraft d = c.reviewedDraft();
        ProjectContext ctx = ProjectContext.of(c.a, Optional.of(d));
        PddGate.Result gate = PddGate.evaluate(ctx, Optional.of(d), FunctionalSpec.empty(c.a.id()), ProcessProjection.of(c.a), ConsultaCase.CLOCK.instant());
        PromptRepository.Prompt prompt = PromptRepository.bundled().current();
        AiAssist.Payload payload = AiAssist.payload(ctx, gate.issues(), FrameworkCapabilityCatalog.bundled(), prompt);
        System.out.println("prévia — vai: " + payload.sent());
        System.out.println("prévia — não vai: " + payload.notSent());
        System.out.println("carga: " + payload.json().length() + " caracteres; perguntas abertas: " + payload.questions());
        long t = System.nanoTime();
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("schema", "nekkora-studio/ai-real-call-evidence");
        ev.put("version", "1.0.0");
        ev.put("provider", provider.id());
        ev.put("prompt", prompt.label());
        ev.put("payloadChars", payload.json().length());
        ev.put("openQuestions", payload.questions());
        ev.put("payloadExcludes", payload.notSent());
        try {
            PlanVerifier.Result r = AiAssist.run(provider, true, prompt, payload, ctx, () -> false);
            ev.put("outcome", "RESPONSE_VERIFIED");
            ev.put("inputTokens", r.inputTokens());
            ev.put("outputTokens", r.outputTokens());
            List<Object> acc = r.accepted().stream().map(p -> (Object) Map.of("kind", p.kind().name(),
                    "step", c.query.equals(p.step()) ? "botão sem efeito conhecido" : ctx.step(p.step()).map(s -> s.action().name()).orElse("?"),
                    "value", p.value())).toList();
            ev.put("accepted", acc);
            ev.put("rejected", r.rejected().stream().map(x -> (Object) x.code()).toList());
        } catch (AIProvider.AIException e) {
            ev.put("outcome", "PROVIDER_ERROR");
            ev.put("code", e.code().name());
            ev.put("status", e.status());
            ev.put("providerError", e.detail());
        }
        ev.put("millis", (System.nanoTime() - t) / 1_000_000);
        String json = Json.write(ev);
        Files.writeString(Path.of(args[0]), json + "\n", StandardCharsets.UTF_8);
        System.out.println(json);
    }
}
