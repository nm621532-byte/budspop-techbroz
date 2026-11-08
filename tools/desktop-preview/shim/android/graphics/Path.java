package android.graphics;

public class Path {
  final java.awt.geom.Path2D.Float awt = new java.awt.geom.Path2D.Float();
  public void moveTo(float x, float y) { awt.moveTo(x, y); }
  public void lineTo(float x, float y) { awt.lineTo(x, y); }
  public void quadTo(float cx, float cy, float x, float y) { awt.quadTo(cx, cy, x, y); }
  public void close() { awt.closePath(); }
}
