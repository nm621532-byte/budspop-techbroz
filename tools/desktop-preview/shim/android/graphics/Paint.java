package android.graphics;

/** Desktop stand-in for the parts of android.graphics.Paint that BudsScene and BudsFight use. */
public class Paint {
  public enum Style { FILL, STROKE }
  public enum Align { LEFT, CENTER, RIGHT }
  public enum Cap { BUTT, ROUND, SQUARE }
  public enum Join { MITER, ROUND, BEVEL }
  public int color = 0xFF000000;
  public Style style = Style.FILL;
  public float strokeWidth = 1f;
  public float textSize = 12f;
  public boolean bold = false;
  public Align align = Align.LEFT;
  public Cap cap = Cap.BUTT;
  public Join join = Join.MITER;
  public Shader shader = null;
  public Paint() {}
  public void setAntiAlias(boolean b) {}
  public void setStyle(Style s) { style = s; }
  public void setColor(int c) { color = c; shader = null; }
  public void setStrokeWidth(float w) { strokeWidth = w; }
  public void setStrokeCap(Cap c) { cap = c; }
  public void setStrokeJoin(Join j) { join = j; }
  public void setShader(Shader s) { shader = s; }
  public void setTextSize(float s) { textSize = s; }
  public void setFakeBoldText(boolean b) { bold = b; }
  public void setTextAlign(Align a) { align = a; }
  java.awt.Paint awtPaint() { return shader != null ? shader.toAwt() : new java.awt.Color(color, true); }
}
