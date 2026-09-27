package com.nekkora.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.button.NekkoraButton;
import com.nekkora.ui.container.NekkoraCard;
import com.nekkora.ui.container.NekkoraPanel;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.core.Severity;
import com.nekkora.ui.core.Status;
import com.nekkora.ui.core.Tone;
import com.nekkora.ui.data.NekkoraBadge;
import com.nekkora.ui.data.NekkoraStatusBadge;
import com.nekkora.ui.data.NekkoraTable;
import com.nekkora.ui.feedback.NekkoraInlineAlert;
import com.nekkora.ui.icon.IconName;
import com.nekkora.ui.input.NekkoraCheckBox;
import com.nekkora.ui.input.NekkoraFormField;
import com.nekkora.ui.input.NekkoraSwitch;
import com.nekkora.ui.input.NekkoraTextField;
import com.nekkora.ui.typography.NekkoraHeading;
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Screenshots de referência ("golden") por tema. As imagens ficam em
 * {@code src/test/resources/golden/<sistema>/} — a renderização de texto varia entre sistemas
 * operacionais. Na primeira execução em um sistema, a imagem é gravada e o teste é ignorado;
 * nas seguintes, qualquer diferença visual relevante falha o teste.
 *
 * <p>Para aceitar uma mudança visual intencional: {@code mvn test -Dnekkora.golden.update=true}.
 */
class GoldenScreenshotTest {
    private static final int W = 720, H = 520;

    static Stream<NekkoraTheme> themes() {
        return NekkoraThemes.all().stream();
    }

    @ParameterizedTest
    @MethodSource("themes")
    void composicaoPadrao(NekkoraTheme theme) throws IOException {
        TestSupport.theme(theme);
        BufferedImage actual = TestSupport.render(TestSupport.onEdt(GoldenScreenshotTest::composition), W, H);
        File dir = new File(System.getProperty("nekkora.golden.dir", "src/test/resources/golden"), osFolder());
        File golden = new File(dir, theme.id() + ".png");
        if (!golden.exists() || Boolean.getBoolean("nekkora.golden.update")) {
            dir.mkdirs();
            ImageIO.write(actual, "png", golden);
            Assumptions.abort("Golden gravado: " + golden + " (revise e faça commit)");
        }
        BufferedImage expected = ImageIO.read(golden);
        double diff = difference(expected, actual);
        if (diff > 0.004) ImageIO.write(actual, "png", new File(dir, theme.id() + ".actual.png"));
        assertTrue(diff <= 0.004, String.format("%s: %.2f%% dos pixels mudaram (limite 0,4%%). Veja %s.actual.png",
                theme.id(), diff * 100, theme.id()));
    }

    static JComponent composition() {
        NekkoraPanel root = new NekkoraPanel(new BorderLayout(), com.nekkora.ui.container.Background.CANVAS);
        root.setPadding(Spacing.SPACE_7);
        NekkoraStack col = NekkoraStack.vertical(Spacing.SPACE_6);
        col.add(new NekkoraHeading("Nekkora — composição de referência", NekkoraHeading.Level.H2));
        NekkoraStack buttons = NekkoraStack.horizontal(Spacing.SPACE_4).align(NekkoraStack.Align.CENTER);
        buttons.addAll(NekkoraButton.primary("Salvar").withIcon(IconName.SAVE), NekkoraButton.secondary("Cancelar"),
                NekkoraButton.ghost("Opções"), NekkoraButton.danger("Excluir"), new NekkoraBadge("Novo", Tone.ACCENT),
                new NekkoraStatusBadge(Status.SUCCESS), new NekkoraStatusBadge(Status.FAILED));
        buttons.addGlue();
        col.add(buttons);
        NekkoraStack form = NekkoraStack.horizontal(Spacing.SPACE_6).align(NekkoraStack.Align.START);
        NekkoraTextField err = new NekkoraTextField();
        err.setText("valor inválido");
        NekkoraFormField ff = new NekkoraFormField("E-mail", err).required();
        ff.setError("Informe um e-mail válido.");
        form.addAll(new com.nekkora.ui.container.NekkoraPanel(new BorderLayout()) {
            {
                add(new NekkoraFormField("Nome", new NekkoraTextField("Ex.: Projeto")).helper("Obrigatório"), BorderLayout.CENTER);
            }

            @Override
            public java.awt.Dimension getPreferredSize() {
                return new java.awt.Dimension(260, super.getPreferredSize().height);
            }
        }, ff, NekkoraStack.vertical(Spacing.SPACE_3).addAll(new NekkoraCheckBox("Notificar", true), new NekkoraSwitch("Ativo", true)));
        form.addGlue();
        col.add(form);
        col.add(new NekkoraInlineAlert(Severity.WARNING, "Atenção", "Credencial expira em 3 dias."));
        NekkoraCard card = new NekkoraCard("Tabela", null);
        card.setContent(new NekkoraTable(ComponentCatalog.tableModel()).scrollable());
        col.add(card);
        root.add(col, BorderLayout.NORTH);
        return root;
    }

    private static String osFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
    }

    /** Fração de pixels com diferença perceptível (qualquer canal > 40). */
    private static double difference(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return 1.0;
        long changed = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                if (Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)) > 40 || Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) > 40
                        || Math.abs((p & 255) - (q & 255)) > 40) changed++;
            }
        }
        return changed / (double) (a.getWidth() * a.getHeight());
    }
}
