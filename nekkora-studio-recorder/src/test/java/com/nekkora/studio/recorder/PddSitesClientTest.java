package com.nekkora.studio.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.BridgeEvent;
import com.nekkora.studio.bridge.BrowserLink;
import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.bridge.json.Json;
import com.nekkora.studio.domain.StepId;
import com.nekkora.studio.domain.action.ActionType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NEKKORA-STUDIO-013 — clientes do Studio: consentimento PDD por origem, armação v3 com origem e leitura da descrição v3. */
class PddSitesClientTest {

    static final class Link implements BrowserLink {
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        volatile String lastType;
        volatile Map<String, Object> last;
        volatile String reply = "{}";

        @Override
        public LinkStatus status() {
            return new LinkStatus(LinkStatus.State.CONNECTED, "teste", new LinkStatus.ExtensionInfo("0.6.0", "DEV", "nekkora-privacy/2"));
        }

        @Override
        public void addListener(Listener l) {
            listeners.add(l);
        }

        @Override
        public void removeListener(Listener l) {
            listeners.remove(l);
        }

        @Override
        public CompletableFuture<Map<String, Object>> send(String type, Map<String, Object> payload, Duration timeout) {
            lastType = type;
            last = payload;
            return CompletableFuture.completedFuture(Json.parseObject(reply));
        }

        void emit(String json) {
            listeners.forEach(l -> l.event(new BridgeEvent("studio.pdd.captured", Json.parseObject(json))));
        }
    }

    @Test
    @DisplayName("versões: v3/consentimento só a partir da extensão 0.6.0; desconhecida = não")
    void versoes() {
        assertTrue(PddCaptureClient.supportsV3("0.6.0"));
        assertTrue(PddCaptureClient.supportsV3("1.0.0"));
        assertFalse(PddCaptureClient.supportsV3("0.5.9"));
        assertFalse(PddCaptureClient.supportsV3(null));
        assertFalse(PddCaptureClient.supportsV3("dev"));
    }

    @Test
    @DisplayName("revisão da usuária: nível do consentimento — FULL só pedido; nível desconhecido ou ausente vale MASKED (o lado seguro)")
    void nivel() throws Exception {
        Link link = new Link();
        PddConsentClient c = new PddConsentClient(link, Duration.ofSeconds(1));
        link.reply = "{\"origin\":\"https://app.exemplo.test:8443\",\"consent\":\"SESSION\",\"level\":\"FULL\",\"actor\":\"DEVELOPER\",\"via\":\"STUDIO\",\"grantedAt\":\"x\"}";
        PddConsentClient.Consent g = c.grant("t-SiteTab00002", "https://app.exemplo.test:8443", "SESSION", "DEVELOPER", "FULL").get();
        assertEquals("FULL", link.last.get("level"));
        assertTrue(g.full());
        assertThrows(IllegalArgumentException.class, () -> c.grant("t-SiteTab00002", "https://app.exemplo.test", "SESSION", "DEVELOPER", "TOTAL"));
        link.reply = "{\"consents\":[{\"origin\":\"https://a.exemplo.test\",\"consent\":\"SESSION\",\"level\":\"FULL\",\"actor\":\"DEVELOPER\",\"via\":\"STUDIO\","
                + "\"grantedAt\":\"x\",\"chromeAccess\":true},{\"origin\":\"https://b.exemplo.test\",\"consent\":\"SESSION\",\"actor\":\"DEVELOPER\","
                + "\"via\":\"STUDIO\",\"grantedAt\":\"x\",\"chromeAccess\":true},{\"origin\":\"https://c.exemplo.test\",\"consent\":\"SESSION\",\"level\":\"TUDO\","
                + "\"actor\":\"DEVELOPER\",\"via\":\"STUDIO\",\"grantedAt\":\"x\",\"chromeAccess\":true}]}";
        List<PddConsentClient.Consent> list = c.list().get();
        assertEquals(List.of("FULL", "MASKED", "MASKED"), list.stream().map(PddConsentClient.Consent::level).toList(), "extensão anterior ou nível estranho: mascarado");
        link.reply = "{\"tabRef\":\"t-SiteTab00002\",\"origin\":\"https://app.exemplo.test:8443\",\"access\":\"AUTHORIZED\",\"consent\":\"SESSION\","
                + "\"level\":\"FULL\",\"visualCapture\":\"AUTHORIZED_SITE\",\"reason\":null}";
        assertTrue(c.status("t-SiteTab00002").get().full());
    }

    @Test
    @DisplayName("consentimento: só origem esquema://host[:porta], escopo e ator fechados; lista descarta origem com caminho")
    void consentimento() throws Exception {
        Link link = new Link();
        PddConsentClient c = new PddConsentClient(link, Duration.ofSeconds(1));
        link.reply = "{\"origin\":\"https://app.exemplo.test:8443\",\"consent\":\"SESSION\",\"actor\":\"DEVELOPER\",\"via\":\"STUDIO\",\"grantedAt\":\"x\"}";
        PddConsentClient.Consent g = c.grant("t-SiteTab00002", "https://app.exemplo.test:8443", "SESSION", "DEVELOPER").get();
        assertEquals("studio.pdd.consent.grant", link.lastType);
        assertEquals(Map.of("tabRef", "t-SiteTab00002", "origin", "https://app.exemplo.test:8443", "scope", "SESSION", "actor", "DEVELOPER", "level", "MASKED"),
                link.last, "sem nível escolhido: print mascarado");
        assertEquals("SESSION", g.scope());
        for (String bad : new String[] {"https://app.exemplo.test/pacientes", "https://app.exemplo.test?x=1", "https://u:s@app.exemplo.test", "https://*/*", "ftp://x"}) {
            assertThrows(IllegalArgumentException.class, () -> c.grant("t-SiteTab00002", bad, "SESSION", "DEVELOPER"), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> c.grant("t-SiteTab00002", "https://app.exemplo.test", "FOREVER", "DEVELOPER"));
        assertThrows(IllegalArgumentException.class, () -> c.grant("t-SiteTab00002", "https://app.exemplo.test", "SESSION", "PAGE"));
        link.reply = "{\"consents\":[{\"origin\":\"https://a.exemplo.test\",\"consent\":\"REMEMBERED\",\"actor\":\"TEST_AGENT\",\"via\":\"EXTENSION\","
                + "\"grantedAt\":\"x\",\"chromeAccess\":false},{\"origin\":\"https://b.exemplo.test/x\",\"consent\":\"SESSION\",\"actor\":\"DEVELOPER\","
                + "\"via\":\"STUDIO\",\"grantedAt\":\"x\",\"chromeAccess\":true}]}";
        List<PddConsentClient.Consent> list = c.list().get();
        assertEquals(1, list.size(), "origem com caminho descartada");
        assertFalse(list.get(0).chromeAccess());
        link.reply = "{\"tabRef\":\"t-SiteTab00002\",\"origin\":\"https://app.exemplo.test/pacientes\",\"access\":\"AUTHORIZED\",\"consent\":\"NONE\","
                + "\"visualCapture\":null,\"reason\":\"PDD_CONSENT_REQUIRED\"}";
        PddConsentClient.Status st = c.status("t-SiteTab00002").get();
        assertNull(st.origin(), "origem fora da forma é descartada");
        assertEquals(TabAccess.AUTHORIZED, st.access());
    }

    @Test
    @DisplayName("captura v3: arma com política e origem; o resultado traz só a descrição (px CSS, rótulos), nunca imagem; v3 com png é recusada")
    void capturaV3() throws Exception {
        Link link = new Link();
        StepId step = new StepId(UUID.randomUUID());
        try (PddCaptureClient c = new PddCaptureClient(link, Duration.ofSeconds(1))) {
            link.reply = "{\"captureId\":\"" + c.captureId() + "\",\"armed\":true,\"duplicate\":false,\"shortcut\":\"Alt+Shift+K\",\"policy\":\"nekkora-visual/3\"}";
            c.arm("t-SiteTab00002", List.of(new PddCaptureClient.StepTarget(step, ActionType.CLICK, null)), Duration.ofSeconds(60), PddCaptureClient.POLICY_V3,
                    "https://app.exemplo.test:8443").get();
            assertEquals("https://app.exemplo.test:8443", link.last.get("origin"));
            assertEquals("nekkora-visual/3", link.last.get("policy"));
            link.emit("{\"captureId\":\"" + c.captureId() + "\",\"status\":\"CAPTURED\",\"policy\":\"nekkora-visual/3\",\"observedAt\":\"2026-10-19T12:00:00Z\","
                    + "\"viewport\":{\"width\":400,\"height\":200,\"dpr\":1.25},\"regions\":[{\"kind\":\"TEXT\",\"x\":0,\"y\":0,\"width\":32,\"height\":8,\"ink\":\"DARK\"},"
                    + "{\"kind\":\"LABEL\",\"x\":4,\"y\":20,\"width\":60,\"height\":18,\"text\":\"Senha\",\"size\":13,\"weight\":\"NORMAL\",\"ink\":\"DARK\",\"align\":\"START\"}],"
                    + "\"annotations\":[{\"stepRef\":\"" + step.value() + "\",\"status\":\"OMITTED\",\"x\":0,\"y\":0,\"width\":0,\"height\":0,\"reason\":\"AMBIGUOUS\"}],"
                    + "\"limitations\":[]}");
            PddCaptureClient.Result r = c.result().get();
            assertTrue(r.captured());
            assertNull(r.png());
            assertEquals(400, r.width());
            assertEquals(1.25, r.devicePixelRatio());
            assertEquals("Senha", r.regions().get(1).text());
            assertEquals("DARK", r.regions().get(0).ink());
        }
        try (PddCaptureClient c = new PddCaptureClient(link, Duration.ofSeconds(1))) {
            link.reply = "{\"captureId\":\"" + c.captureId() + "\",\"armed\":true,\"duplicate\":false,\"shortcut\":\"\",\"policy\":\"nekkora-visual/3\"}";
            c.arm("t-SiteTab00002", List.of(new PddCaptureClient.StepTarget(step, ActionType.CLICK, null)), Duration.ofSeconds(60), PddCaptureClient.POLICY_V3,
                    null).get();
            link.emit("{\"captureId\":\"" + c.captureId() + "\",\"status\":\"CAPTURED\",\"policy\":\"nekkora-visual/3\",\"observedAt\":\"2026-10-19T12:00:00Z\","
                    + "\"viewport\":{\"width\":400,\"height\":200,\"dpr\":1},\"png\":\"QUFB\",\"regions\":[],\"annotations\":[],\"limitations\":[]}");
            PddCaptureClient.Result r = c.result().get();
            assertFalse(r.captured(), "v3 com imagem anexada não é aceita");
            assertEquals("SANITIZATION_CHECK_FAILED", r.failureCode());
        }
    }
}
