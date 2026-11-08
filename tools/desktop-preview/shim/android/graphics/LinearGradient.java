package android.graphics;

public class LinearGradient extends Shader {
  private final float x0, y0, x1, y1; private final int[] colors; private final float[] stops;
  public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] stops, TileMode tm) {
    this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.colors = colors; this.stops = stops;
  }
  java.awt.Paint toAwt() {
    java.awt.Color[] cs = new java.awt.Color[colors.length];
    for (int i = 0; i < colors.length; i++) cs[i] = new java.awt.Color(colors[i], true);
    float ex = x1, ey = y1;
    if (ex == x0 && ey == y0) ey = y0 + 0.001f; // Java refuses a zero-length gradient
    return new java.awt.LinearGradientPaint(new java.awt.geom.Point2D.Float(x0, y0), new java.awt.geom.Point2D.Float(ex, ey), stops, cs);
  }
}
