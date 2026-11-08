import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import android.graphics.Canvas;
import com.techbroz.budspop.BudsScene;

/**
 * Desktop preview renderer for BudsPop: draws each fight theme with the same BudsScene.draw code the
 * phone runs (through a small Graphics2D stand-in for android.graphics), and writes PNG frames.
 * Usage: FrameDump <outDir> <fps> <seconds-per-theme>
 */
public class FrameDump {
  static final int W = 1080, CARD_H = 540, BAR_H = 60;
  public static void main(String[] args) throws Exception {
    String out = args[0];
    double fps = Double.parseDouble(args[1]);
    double secs = Double.parseDouble(args[2]);
    int[] themes = {BudsScene.THEME_SAIYAN_RUSH, BudsScene.THEME_CLASH, BudsScene.THEME_SUPER_CHARGE};
    String[] labels = {"Saiyan Rush  (new default)", "Clash", "Super Charge"};
    new File(out).mkdirs();
    int n = 0;
    for (int k = 0; k < themes.length; k++) {
      int frames = (int) Math.round(secs * fps);
      for (int i = 0; i < frames; i++) {
        double t = i * 1000.0 / fps;
        BufferedImage img = new BufferedImage(W, CARD_H + BAR_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(12, 12, 18));
        g.fillRect(0, 0, W, CARD_H + BAR_H);
        Graphics2D card = (Graphics2D) g.create(0, 0, W, CARD_H);
        Canvas c = new Canvas(card);
        BudsScene.draw(c, W, CARD_H, (float) t, themes[k], "boAt Airdopes 141");
        card.dispose();
        g.setColor(new Color(230, 230, 236));
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        g.drawString(labels[k], 28, CARD_H + 40);
        g.dispose();
        ImageIO.write(img, "png", new File(out, String.format("f%04d.png", n)));
        n++;
      }
    }
    System.out.println("frames=" + n);
  }
}
