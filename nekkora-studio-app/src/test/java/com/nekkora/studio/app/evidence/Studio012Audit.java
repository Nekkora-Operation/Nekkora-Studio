package com.nekkora.studio.app.evidence;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.variable.VariableDefinition;
import com.nekkora.studio.persistence.ProjectFiles;
import com.nekkora.studio.persistence.ProjectRepository;
import com.nekkora.studio.ui.ElementPresentation;
import com.nekkora.studio.validation.AutomationValidator;
import com.nekkora.studio.validation.Finding;
import com.nekkora.studio.validation.RuleSet;
import com.nekkora.studio.validation.ValidationReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * NEKKORA-STUDIO-012 — auditoria SEM janela de uma CÓPIA de projeto real: abre com o Studio atual, valida e imprime SÓ
 * metadados (ação, estratégia preferida, escopo, códigos de obstáculo/limitação/apontamento, origem e sensibilidade das
 * variáveis, se a Finalidade tem forma de valor). Nenhum nome, descrição, valor, URL ou texto da página é impresso. O
 * arquivo é lido e o hash conferido antes e depois: a auditoria não escreve nele.
 *
 * <p>Uso: {@code Studio012Audit <copia.nekkora>}
 */
public final class Studio012Audit {

    private Studio012Audit() {
    }

    private static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))).substring(0, 16);
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        String before = sha(file);
        Automation a = new ProjectFiles(new ProjectRepository(), "studio-012").load(file, Clock.systemUTC()).automation();
        ValidationReport report = new AutomationValidator(RuleSet.V1_1).validate(a);
        System.out.println("modelo=" + a.modelVersion() + " sessoes=" + a.sessions().size() + " etapas=" + a.stepCount() + " variaveis=" + a.variables().size());
        int s = 0;
        for (LogicalSession session : a.sessions()) {
            s++;
            int n = 0;
            for (AutomationStep st : session.steps()) {
                n++;
                ElementReference e = st.element().orElse(null);
                String strategy = e == null ? "-" : e.preferredLocator().map(c -> c.strategy().name()).orElse("NENHUM");
                String scope = e == null ? "-" : ElementPresentation.scope(e.accessPath());
                String obstacles = e == null ? "" : String.join(",", e.obstacles().stream().map(ObservedObstacle::type).toList());
                String limits = st.provenance().recordingObservation().map(r -> String.join(",", r.limitations())).orElse("");
                String mode = st.provenance().recordingObservation().map(r -> r.mode().name() + "/" + r.observation().name()).orElse("-");
                Map<String, Integer> findings = new TreeMap<>();
                for (Finding f : report.findings()) {
                    if (st.id().equals(f.step())) findings.merge(f.code().name(), 1, Integer::sum);
                }
                System.out.printf("S%d.E%02d %s %s preferido=%s escopo=[%s] opcao=%s obst=[%s] limit=[%s] apont=%s%n", s, n, st.actionType(), mode, strategy, scope,
                        st.parameters().option().isPresent() ? "configurada" : "-", obstacles, limits, findings);
            }
        }
        for (VariableDefinition v : a.variables()) {
            String p = v.purpose() == null ? "" : v.purpose().strip();
            boolean single = !p.isEmpty() && p.chars().noneMatch(Character::isWhitespace);
            System.out.printf("variavel origem=%s sensibilidade=%s finalidade=%s%n", v.origin().kind(), v.sensitivity(),
                    p.isEmpty() ? "vazia" : single ? "PALAVRA UNICA (forma de valor; corrigir na cópia local)" : "descricao");
        }
        System.out.println("hash antes=" + before + " depois=" + sha(file) + " inalterado=" + before.equals(sha(file)));
    }
}
