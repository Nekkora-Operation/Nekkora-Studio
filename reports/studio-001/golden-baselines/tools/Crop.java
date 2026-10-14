import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
public class Crop {
    // args: out x y w h scale label1 img1 label2 img2 ...
    public static void main(String[] a) throws Exception {
        int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), w = Integer.parseInt(a[3]), h = Integer.parseInt(a[4]), s = Integer.parseInt(a[5]);
        int n = (a.length - 6) / 2, lh = 22;
        BufferedImage out = new BufferedImage(w * s, n * (h * s + lh), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int i = 0; i < n; i++) {
            BufferedImage im = ImageIO.read(new File(a[7 + 2 * i]));
            int oy = i * (h * s + lh);
            g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14)); g.drawString(a[6 + 2 * i], 4, oy + 16);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(im.getSubimage(x, y, w, h), 0, oy + lh, w * s, h * s, null);
        }
        ImageIO.write(out, "png", new File(a[0]));
    }
}
