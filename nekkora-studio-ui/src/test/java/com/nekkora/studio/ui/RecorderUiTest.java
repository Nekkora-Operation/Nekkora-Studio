package com.nekkora.studio.ui;

import static com.nekkora.studio.ui.StudioUiTest.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.domain.Automation;
import com.nekkora.studio.recorder.BrowserTab;
import com.nekkora.studio.recorder.RecordingMode;
import com.nekkora.studio.recorder.TabAccess;
import com.nekkora.studio.ui.recorder.RecorderText;
import com.nekkora.studio.ui.recorder.RecordingSetupPanel;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.Nekkora;
import com.nekkora.ui.input.NekkoraRadioButton;
import com.nekkora.ui.theme.ThemeManager;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Diálogo "Iniciar gravação": abas sanitizadas, só autorizadas escolhíveis, orientação de conexão e textos. */
class RecorderUiTest {

    @BeforeAll
    static void install() {
        edt(() -> {
            if (!ThemeManager.isInstalled()) Nekkora.install(NekkoraThemes.SAKURA_DARK);
            return null;
        });
    }

    @Test
    @DisplayName("Abas autorizadas primeiro; não autorizadas e internas desabilitadas; escolha só com aba autorizada")
    void abasEScolha() {
        Automation a = Automation.create("Consulta de clientes", "", Clock.systemUTC());
        a.addSession("Autenticação", "");
        var consulta = a.addSession("Consulta", "");
        RecordingSetupPanel p = edt(() -> new RecordingSetupPanel(a, consulta.id(), () -> { }));
        assertTrue(edt(p::choice).isEmpty(), "sem abas não há escolha");
        edt(() -> {
            p.showTabs(List.of(
                    new BrowserTab("t-INTERNAL01", null, null, TabAccess.BROWSER_INTERNAL, false, false),
                    new BrowserTab("t-DENIED0001", null, "portal.exemplo.com", TabAccess.PERMISSION_REQUIRED, false, false),
                    new BrowserTab("t-LAB0000001", "Sistema de testes", "127.0.0.1", TabAccess.LAB, false, false),
                    new BrowserTab("t-BUSY000001", "Outra", "127.0.0.1", TabAccess.LAB, false, true),
                    new BrowserTab("t-AUTH000001", "Wikipédia", "pt.wikipedia.org", TabAccess.AUTHORIZED, true, false)));
            return null;
        });
        List<NekkoraRadioButton> radios = edt(p::tabButtons);
        List<String> texts = edt(() -> radios.stream().map(NekkoraRadioButton::getText).toList());
        assertTrue(texts.get(0).startsWith("Sistema de testes") || texts.get(0).startsWith("Wikipédia"), texts.toString());
        assertEquals("Página interna do navegador", texts.get(texts.size() - 1), "interna por último e sem texto duplicado");
        assertTrue(texts.stream().anyMatch(t -> t.equals("portal.exemplo.com  —  Não autorizada")), texts.toString());
        assertTrue(texts.stream().anyMatch(t -> t.contains("já em gravação")));
        for (int i = 0; i < radios.size(); i++) {
            String t = texts.get(i);
            boolean enabled = edt(radios.get(i)::isEnabled);
            assertEquals(t.contains("Laboratório local") || t.contains("Autorizada"), enabled, t);
        }
        var choice = edt(p::choice).orElseThrow();
        assertEquals("t-AUTH000001", choice.tab().tabRef(), "a aba ativa autorizada vem selecionada");
        assertEquals(consulta.id(), choice.session(), "sessão pré-escolhida");
        assertEquals(RecordingMode.CAPTURE_AND_INTERACT, choice.mode());
        assertTrue(edt(() -> p.tabHint().getText()).contains("conceda acesso ao site"));
        for (String t : texts) {
            assertFalse(t.contains("http") || t.contains("t-"), "sem URL nem referência interna: " + t);
        }
    }

    @Test
    @DisplayName("Sem navegador conectado: orientação para ligar a conexão na extensão")
    void semConexao() {
        Automation a = Automation.create("A", "", Clock.systemUTC());
        var s = a.addSession("S", "");
        RecordingSetupPanel p = edt(() -> new RecordingSetupPanel(a, s.id(), () -> { }));
        edt(() -> {
            p.showLink(LinkStatus.of(LinkStatus.State.WAITING, null));
            return null;
        });
        assertTrue(edt(() -> p.connectionAlert().isVisible()));
        assertTrue(RecorderText.connectHint(LinkStatus.of(LinkStatus.State.WAITING, null)).contains("Conectar ao Nekkora Studio"));
        assertTrue(RecorderText.connectHint(LinkStatus.of(LinkStatus.State.UNAVAILABLE, "OTHER_INSTANCE")).contains("Outra janela"));
        assertEquals("Navegador: aguardando conexão com a extensão", RecorderText.link(LinkStatus.of(LinkStatus.State.WAITING, null)));
        assertTrue(edt(() -> p.refreshButton().isEnabled()), "atualizar disponível para reconsultar depois de ligar a conexão");
    }
}
