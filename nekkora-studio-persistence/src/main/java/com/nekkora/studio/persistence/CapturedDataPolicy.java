package com.nekkora.studio.persistence;

import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.domain.AutomationStep;
import com.nekkora.studio.domain.LogicalSession;
import com.nekkora.studio.domain.StepContext;
import com.nekkora.studio.domain.element.AccessStep;
import com.nekkora.studio.domain.element.ContextAnchor;
import com.nekkora.studio.domain.element.ElementReference;
import com.nekkora.studio.domain.element.LocatorCandidate;
import com.nekkora.studio.domain.element.ObservedObstacle;
import com.nekkora.studio.domain.evidence.ObservedEvidence;
import com.nekkora.studio.domain.privacy.CapturePrivacy;
import com.nekkora.studio.domain.privacy.DisplaySafety;
import java.util.Objects;

/**
 * {@code nekkora-privacy/2} nos dados CAPTURADOS de um projeto (STUDIO-006, §11/§14/§27/§31).
 *
 * <p>Os campos que vieram da página (nome acessível, id/name HTML, nome do elemento, localizadores, rótulos da
 * trajetória e do contexto, título e endereço da tela, endereços das evidências) precisam ser PONTO FIXO da política: a
 * política aplicada a eles não muda nada. É o que acontece com tudo que entrou pelo Recorder (4ª barreira). Um arquivo
 * que traga algo fora disso — produzido por uma política anterior, alterado à mão ou corrompido — é RECUSADO inteiro; nada
 * é "sanitizado" em silêncio para parecer compatível, e nenhum texto omitido é recuperado.
 *
 * <p>Os textos gerados pelo próprio Studio (notas de estabilidade, obstáculos, resultado observado) passam pela checagem
 * de exibição segura. Os textos digitados pelo usuário (nomes, descrições, comentários, valores) seguem as validações do
 * domínio — a política da captura não se aplica a eles (§31).
 *
 * <p>Usada ANTES de gravar e DEPOIS de ler: o mesmo critério nas duas direções.
 */
public final class CapturedDataPolicy {

    private CapturedDataPolicy() {}

    /** @throws ProjectException {@link ProjectException.Error#PRIVACY_VIOLATION} com a posição do primeiro campo fora da política */
    public static void check(Automation a) throws ProjectException {
        int s = 0;
        for (LogicalSession session : a.sessions()) {
            int i = 0;
            for (AutomationStep st : session.steps()) {
                String w = "sessions[" + s + "].steps[" + i + "]";
                if (st.capturedElement().isPresent()) element(st.capturedElement().get(), w + ".original.capturedElement");
                if (st.context().isPresent()) context(st.context().get(), w + ".original.context");
                studioText(st.observedOutcome(), w + ".original.observedOutcome");
                int e = 0;
                for (ObservedEvidence ev : st.evidence()) evidence(ev, w + ".evidence[" + e++ + "]");
                i++;
            }
            s++;
        }
        int u = 0;
        for (ObservedEvidence ev : a.unattributedEvidence()) evidence(ev, "unattributedEvidence[" + u++ + "]");
    }

    private static void element(ElementReference e, String w) throws ProjectException {
        interfaceText(e.accessibleName(), w + ".accessibleName");
        require(e.tag().isEmpty() || "unknown".equals(e.tag()) || e.tag().equals(CapturePrivacy.tagName(e.tag())), w + ".tag");
        require(e.semanticKind().matches("[A-Z_]{1,40}"), w + ".semanticKind");
        for (String id : new String[] {e.htmlId().orElse(null), e.htmlName().orElse(null)}) {
            require(id == null || (!e.sensitive() && id.equals(CapturePrivacy.identifier(id))), w + ".htmlId/htmlName");
        }
        require(e.privacyPolicy().map(DisplaySafety.KNOWN_PRODUCER_POLICY::equals).orElse(true), w + ".privacyPolicy");
        for (int i = 0; i < e.locators().size(); i++) {
            LocatorCandidate c = e.locators().get(i);
            require(CapturePrivacy.expressionSafe(c.description()) && CapturePrivacy.expressionSafe(c.expression()), w + ".locators[" + i + "]");
        }
        for (int i = 0; i < e.accessPath().size(); i++) {
            AccessStep st = e.accessPath().get(i);
            interfaceText(st.label(), w + ".accessPath[" + i + "].label");
            require(CapturePrivacy.expressionSafe(st.locator()), w + ".accessPath[" + i + "].locator");
        }
        for (int i = 0; i < e.context().size(); i++) {
            ContextAnchor c = e.context().get(i);
            require(c.relation().matches("[A-Z_]{1,40}") && c.kind().matches("[A-Z_]{1,40}"), w + ".context[" + i + "]");
            interfaceText(c.name(), w + ".context[" + i + "].name");
            require(CapturePrivacy.expressionSafe(c.locator()), w + ".context[" + i + "].locator");
        }
        for (int i = 0; i < e.stabilityNotes().size(); i++) studioText(e.stabilityNotes().get(i), w + ".stabilityNotes[" + i + "]");
        for (int i = 0; i < e.obstacles().size(); i++) {
            ObservedObstacle o = e.obstacles().get(i);
            require(o.type().matches("[A-Z_]{1,40}"), w + ".obstacles[" + i + "].type");
            studioText(o.description(), w + ".obstacles[" + i + "].description");
        }
    }

    private static void context(StepContext c, String w) throws ProjectException {
        studioText(c.application(), w + ".application");
        require(c.screenTitle().isEmpty() || c.screenTitle().equals(CapturePrivacy.title(c.screenTitle())), w + ".screenTitle");
        require(c.location().isEmpty() || c.location().equals(CapturePrivacy.urlTemplate(c.location())), w + ".location");
    }

    private static void evidence(ObservedEvidence e, String w) throws ProjectException {
        // o domínio já garante: mensagem sem texto, título nulo, região e campo do vocabulário controlado
        require(e.message().text() == null && e.document().title() == null, w + ".message");
        url(e.document().url(), w + ".document.url");
        if (e.navigation() != null) url(e.navigation().url(), w + ".navigation.url");
    }

    private static void url(String u, String w) throws ProjectException {
        require(u == null || u.equals(CapturePrivacy.urlTemplate(u)), w);
    }

    private static void interfaceText(String v, String w) throws ProjectException {
        require(v.isEmpty() || v.equals(CapturePrivacy.interfaceText(v)), w);
    }

    /** Texto produzido pelo Studio (não pela página): só o que é seguro exibir, sem quebras de linha. */
    private static void studioText(String v, String w) throws ProjectException {
        require(v.isEmpty() || (v.length() <= 500 && v.chars().noneMatch(Character::isISOControl) && DisplaySafety.isSafeToDisplay(v)), w);
    }

    private static void require(boolean ok, String where) throws ProjectException {
        if (!ok) throw new ProjectException(ProjectException.Error.PRIVACY_VIOLATION, Objects.requireNonNull(where));
    }
}
