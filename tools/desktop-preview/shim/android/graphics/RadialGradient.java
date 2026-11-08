package android.graphics;

public class RadialGradient extends Shader {
  private final float x, y, r; private final int[] colors; private final float[] stops;
  public RadialGradient(float x, float y, float r, int[] colors, float[] stops, TileMode tm) {
    this.x = x; this.y = y; this.r = Math.max(r, 0.5f); this.colors = colors; this.stops = stops;
  }
  java.awt.Paint toAwt() {
    java.awt.Color[] cs = new java.awt.Color[colors.length];
    for (int i = 0; i < colors.length; i++) cs[i] = new java.awt.Color(colors[i], true);
    return new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(x, y), r, stops, cs);
  }
}
