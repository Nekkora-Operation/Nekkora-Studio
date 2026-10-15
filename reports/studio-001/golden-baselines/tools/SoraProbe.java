import com.nekkora.tokens.NekkoraThemes;
import com.nekkora.ui.theme.ThemeManager;
import com.nekkora.ui.typography.NekkoraHeading;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** Mede, para títulos em Sora, a tinta desenhada contra a métrica de layout e a largura preferida (com a folga). */
public class SoraProbe {
    public static void main(String[] a) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ThemeManager.install(NekkoraThemes.SAKURA_DARK);
            String[] texts = {"Nova etapa", "Nova automação", "Editar etapa", "Renomear sessão", "Propriedades", "Automações",
                    "Sessões lógicas", "Nekkora — composição de referência", "Wwwmmm", "rrrfff", "Configurações avançadas"};
            NekkoraHeading.Level[] levels = {NekkoraHeading.Level.DISPLAY_L, NekkoraHeading.Level.DISPLAY_M, NekkoraHeading.Level.H1, NekkoraHeading.Level.H2};
            double worstRatio = 0; int clippedWithSlack = 0, clippedWithoutSlack = 0, n = 0;
            System.out.println("nivel;texto;metrica;tinta;preferida;tinta/metrica;cortaria_sem_folga;cortaria_com_folga");
            for (NekkoraHeading.Level lv : levels) for (String t : texts) {
                NekkoraHeading h = new NekkoraHeading(t, lv);
                Dimension pref = h.getPreferredSize();
                int left = h.getInsets().left, right = h.getInsets().right;
                int metric = h.getFontMetrics(h.getFont()).stringWidth(t);
                int w = pref.width + 200;
                h.setSize(w, pref.height);
                BufferedImage img = new BufferedImage(w, pref.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.BLACK); g.fillRect(0, 0, w, pref.height);
                h.printAll(g); g.dispose();
                int ink = -1;
                for (int x = w - 1; x >= 0 && ink < 0; x--) for (int y = 0; y < pref.height; y++) {
                    int p = img.getRGB(x, y);
                    if (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255) > 60) { ink = x + 1; break; }
                }
                int inkW = ink - left;
                boolean cutNo = ink > left + metric + right, cutYes = ink > pref.width;
                double r = inkW / (double) metric;
                worstRatio = Math.max(worstRatio, r); n++;
                if (cutNo) clippedWithoutSlack++;
                if (cutYes) clippedWithSlack++;
                System.out.printf("%s;%s;%d;%d;%d;%.3f;%s;%s%n", lv, t, metric, inkW, pref.width, r, cutNo ? "SIM" : "não", cutYes ? "SIM" : "não");
            }
            System.out.printf("RESUMO: %d títulos; maior tinta/métrica = %.3f; cortados sem folga = %d; cortados com folga de 6%% = %d%n", n, worstRatio, clippedWithoutSlack, clippedWithSlack);
        });
        System.exit(0);
    }
}
