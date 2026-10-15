package com.nekkora.ui;
import com.nekkora.tokens.NekkoraTheme;
import com.nekkora.tokens.NekkoraThemes;
import java.io.File;
import javax.imageio.ImageIO;
public class RenderGolden {
    public static void main(String[] a) throws Exception {
        for (NekkoraTheme t : NekkoraThemes.all()) {
            TestSupport.theme(t);
            ImageIO.write(TestSupport.render(TestSupport.onEdt(GoldenScreenshotTest::composition), 720, 520), "png", new File(a[0], t.id() + ".png"));
        }
        System.exit(0);
    }
}
