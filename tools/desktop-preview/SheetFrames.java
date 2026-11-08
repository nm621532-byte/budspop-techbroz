import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import android.graphics.Canvas;
import com.techbroz.budspop.BudsScene;

/**
 * Phone-frame preview of the v1.2 bottom sheet: a home screen, then the pop-up sheet sliding up
 * from the bottom edge (overshoot), holding, and sliding back down (accelerate). Sheet geometry,
 * timings and interpolators mirror OverlayPresenter.java; the art is BudsScene.draw itself.
 * Usage: SheetFrames <outDir> <fps> <seconds>
 */
public class SheetFrames {
  // logical phone 360dp x 780dp drawn at 656 x 1216 px (1.822 px per dp), inside a bezel
  static final float PX_PER_DP = 1.822f;
  static final int CW = 720, CH = 1280;
  static final int SX = 32, SY = 32, SW = 656, SH = 1216;
  static final long IN_MS = 420, OUT_MS = 260, HOLD_MS = 3000;

  static float dp(float v) { return v * PX_PER_DP; }

  // OvershootInterpolator(1.1) and AccelerateInterpolator(1.2), as android.view.animation computes them
  static float overshoot(float x) { float t = x - 1f; float T = 1.1f; return t * t * ((T + 1f) * t + T) + 1f; }
  static float accelerate(float x) { return (float) Math.pow(x, 2f * 1.2f); }

  public static void main(String[] args) throws Exception {
    String out = args[0];
    double fps = Double.parseDouble(args[1]);
    double secs = Double.parseDouble(args[2]);
    new File(out).mkdirs();
    int[] themes = {BudsScene.THEME_SAIYAN_RUSH, BudsScene.THEME_CLASH};
    long[] starts = {600, 5000};
    int frames = (int) Math.round(secs * fps);
    for (int i = 0; i < frames; i++) {
      long t = Math.round(i * 1000.0 / fps);
      BufferedImage img = new BufferedImage(CW, CH, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(new Color(10, 10, 14));
      g.fillRect(0, 0, CW, CH);
      drawPhone(g);

      // screen clip region for the window (above the navigation bar)
      int navBar = Math.round(dp(26)); // 48 px nav bar on a real phone
      int bottomGap = Math.round(dp(Math.max(26, 8) + 12));
      Rectangle2D windowRect = new Rectangle2D.Float(SX, SY, SW, SH - bottomGap);
      int sideMargin = Math.round(dp(12));
      int sheetW = SW - 2 * sideMargin;
      int sceneH = Math.round(sheetW * 0.5f);
      int sheetH = Math.max(Math.round(SH * 0.36f), sceneH + Math.round(dp(52)));
      float slack = dp(4);

      for (int k = 0; k < themes.length; k++) {
        long s = starts[k];
        long inEnd = s + IN_MS, holdEnd = inEnd + HOLD_MS, outEnd = holdEnd + OUT_MS;
        if (t < s || t >= outEnd) continue;
        float offY, alpha, scale;
        long sceneT = t - s;
        if (t < inEnd) {
          float p = (t - s) / (float) IN_MS;
          float e = overshoot(p);
          offY = (1f - e) * (sheetH + slack);
          alpha = 0.6f + 0.4f * e;
          scale = 0.96f + 0.04f * e;
        } else if (t < holdEnd) {
          offY = 0; alpha = 1f; scale = 1f;
        } else {
          float p = (t - holdEnd) / (float) OUT_MS;
          float q = accelerate(p);
          offY = q * (sheetH + dp(8));
          alpha = 1f - q;
          scale = 1f - 0.02f * q;
        }
        float baseX = SX + sideMargin;
        float baseY = SY + SH - bottomGap - sheetH; // sheet top when settled
        float pivotX = sheetW / 2f, pivotY = sheetH;
        Graphics2D sg = (Graphics2D) g.create();
        sg.clip(windowRect);
        sg.translate(baseX + pivotX, baseY + pivotY + offY);
        sg.scale(scale, scale);
        sg.translate(-pivotX, -pivotY);
        Composite comp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha)));
        sg.setComposite(comp);
        // soft shadow above the sheet
        for (int sh = 6; sh >= 1; sh--) {
          sg.setColor(new Color(0, 0, 0, 8));
          sg.fill(new RoundRectangle2D.Float(-sh * 2f, -sh * 2f, sheetW + sh * 4f, sheetH + sh * 4f, dp(28) + sh * 2f, dp(28) + sh * 2f));
        }
        // sheet body with rounded top corners
        sg.setColor(new Color(0x12, 0x1A, 0x2E));
        float r = dp(28);
        sg.fill(new RoundRectangle2D.Float(0, 0, sheetW, sheetH + r, r * 2, r * 2));
        // grab handle
        sg.setColor(new Color(255, 255, 255, 102));
        sg.fill(new RoundRectangle2D.Float(sheetW / 2f - dp(20), dp(10), dp(40), dp(4), dp(4), dp(4)));
        // the scene, exactly as the phone draws it
        Graphics2D content = (Graphics2D) sg.create(0, 0, sheetW, sheetH);
        content.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Canvas c = new Canvas(content);
        BudsScene.draw(c, sheetW, sheetH, (float) sceneT, themes[k], "boAt Airdopes 141");
        content.dispose();
        sg.dispose();
      }
      g.dispose();
      ImageIO.write(img, "png", new File(out, String.format("f%04d.png", i)));
    }
    System.out.println("frames=" + frames);
  }

  static void drawPhone(Graphics2D g) {
    // body and bezel
    g.setColor(new Color(24, 24, 32));
    g.fill(new RoundRectangle2D.Float(18, 18, CW - 36, CH - 36, 64, 64));
    g.setColor(new Color(40, 40, 52));
    g.setStroke(new BasicStroke(3f));
    g.draw(new RoundRectangle2D.Float(19.5f, 19.5f, CW - 39, CH - 39, 62, 62));
    // screen with a wallpaper gradient
    Shape screen = new RoundRectangle2D.Float(SX, SY, SW, SH, 44, 44);
    Graphics2D sg = (Graphics2D) g.create();
    sg.clip(screen);
    sg.setPaint(new GradientPaint(SX, SY, new Color(22, 26, 58), SX + SW * 0.6f, SY + SH, new Color(70, 24, 60)));
    sg.fillRect(SX, SY, SW, SH);
    // status bar
    sg.setColor(Color.WHITE);
    sg.setFont(new Font("SansSerif", Font.BOLD, 26));
    sg.drawString("9:36", SX + 36, SY + 48);
    sg.setFont(new Font("SansSerif", Font.PLAIN, 22));
    sg.drawString("5G  ▮▮▮ 86%", SX + SW - 190, SY + 48);
    // app grid (generic tiles, no logos)
    String[] names = {"Phone", "Messages", "Camera", "Gallery", "Music", "Notes", "Clock", "Files",
                      "Settings", "Maps", "Calendar", "Mail", "Weather", "Radio", "Wallet", "Health"};
    Color[] cols = {new Color(90,160,120), new Color(80,130,220), new Color(120,120,130), new Color(200,120,80),
                    new Color(230,90,120), new Color(230,200,80), new Color(60,60,80), new Color(70,150,200),
                    new Color(110,110,140), new Color(90,190,170), new Color(200,80,80), new Color(100,140,200),
                    new Color(80,170,230), new Color(160,100,210), new Color(90,90,100), new Color(120,200,140)};
    int cols4 = 4, tile = 110, gapX = (SW - 60 - cols4 * tile) / (cols4 - 1);
    for (int i = 0; i < names.length; i++) {
      int cx = SX + 30 + (i % cols4) * (tile + gapX);
      int cy = SY + 150 + (i / cols4) * 190;
      sg.setColor(cols[i]);
      sg.fill(new RoundRectangle2D.Float(cx, cy, tile, tile, 30, 30));
      sg.setColor(new Color(255, 255, 255, 230));
      sg.setFont(new Font("SansSerif", Font.PLAIN, 20));
      int w = sg.getFontMetrics().stringWidth(names[i]);
      sg.drawString(names[i], cx + tile / 2 - w / 2, cy + tile + 34);
    }
    // gesture pill at the bottom (navigation area)
    sg.setColor(new Color(255, 255, 255, 170));
    sg.fill(new RoundRectangle2D.Float(SX + SW / 2 - 90, SY + SH - 22, 180, 8, 8, 8));
    sg.dispose();
  }
}
