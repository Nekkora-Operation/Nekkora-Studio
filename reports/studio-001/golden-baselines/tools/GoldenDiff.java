import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GoldenDiff {
    public static void main(String[] a) throws Exception {
        BufferedImage e = ImageIO.read(new File(a[0])), c = ImageIO.read(new File(a[1]));
        int w = e.getWidth(), h = e.getHeight();
        BufferedImage heat = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int band = 20; long[] rows = new long[h / band + 1];
        long changed = 0, any = 0; int maxd = 0;
        int x0 = w, y0 = h, x1 = -1, y1 = -1, ax0 = w, ay0 = h, ax1 = -1, ay1 = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = e.getRGB(x, y), q = c.getRGB(x, y);
            int d = Math.max(Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)), Math.max(Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)), Math.abs((p & 255) - (q & 255))));
            int g = (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255)) / 9;
            if (d > 0) { any++; ax0 = Math.min(ax0, x); ay0 = Math.min(ay0, y); ax1 = Math.max(ax1, x); ay1 = Math.max(ay1, y); }
            maxd = Math.max(maxd, d);
            if (d > 40) { changed++; rows[y / band]++; heat.setRGB(x, y, 0xFF2060); x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
            else heat.setRGB(x, y, d > 0 ? 0x806000 | (g << 16 >> 16) : (g << 16) | (g << 8) | g);
        }
        System.out.printf("%s: >40=%d (%.3f%%), any=%d (%.2f%%), max=%d, bbox=[%d,%d..%d,%d] anyBbox=[%d,%d..%d,%d]%n", new File(a[0]).getName(), changed, 100.0 * changed / (w * h), any, 100.0 * any / (w * h), maxd, x0, y0, x1, y1, ax0, ay0, ax1, ay1);
        StringBuilder sb = new StringBuilder("  faixas y (20px) com pixels >40: ");
        for (int i = 0; i < rows.length; i++) if (rows[i] > 0) sb.append(i * band).append('-').append(i * band + band - 1).append(':').append(rows[i]).append(' ');
        System.out.println(sb);
        if (a.length > 2) ImageIO.write(heat, "png", new File(a[2]));
    }
}
