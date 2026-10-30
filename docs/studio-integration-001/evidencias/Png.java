import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * scale <dir-in> <dir-out> <fator>          amplia cada PNG (só para o OCR)
 * redact <in.png> <out.png> x0,y0,x1,y1 ... tarja retângulos (cinza escuro sólido) e grava PNG
 */
public class Png {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("scale")) {
            int k = Integer.parseInt(a[3]);
            new File(a[2]).mkdirs();
            for (File f : new File(a[1]).listFiles((d, n) -> n.endsWith(".png"))) {
                BufferedImage src = ImageIO.read(f);
                BufferedImage dst = new BufferedImage(src.getWidth() * k, src.getHeight() * k, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = dst.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(src, 0, 0, dst.getWidth(), dst.getHeight(), null);
                g.dispose();
                ImageIO.write(dst, "png", new File(a[2], f.getName()));
            }
        } else if (a[0].equals("redact")) {
            BufferedImage img = ImageIO.read(new File(a[1]));
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x3a, 0x3a, 0x40));
            for (int i = 3; i < a.length; i++) {
                String[] p = a[i].split(",");
                int x0 = Integer.parseInt(p[0]), y0 = Integer.parseInt(p[1]), x1 = Integer.parseInt(p[2]), y1 = Integer.parseInt(p[3]);
                g.fillRect(x0, y0, x1 - x0, y1 - y0);
            }
            g.dispose();
            ImageIO.write(img, "png", new File(a[2]));
        }
    }
}
