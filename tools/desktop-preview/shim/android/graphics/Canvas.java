package android.graphics;

import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.*;

/** Desktop stand-in for android.graphics.Canvas, backed by Graphics2D. */
public class Canvas {
  public final Graphics2D g;
  private final java.util.ArrayDeque<AffineTransform> stack = new java.util.ArrayDeque<>();
  public Canvas(Graphics2D g) {
    this.g = g;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }
  public void save() { stack.push(g.getTransform()); }
  public void restore() { g.setTransform(stack.pop()); }
  public void translate(float x, float y) { g.translate(x, y); }
  public void scale(float x, float y) { g.scale(x, y); }
  public void drawCircle(float cx, float cy, float r, Paint p) { drawOval(new RectF(cx - r, cy - r, cx + r, cy + r), p); }
  public void drawOval(RectF r, Paint p) { shape(new Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p); }
  public void drawRect(float l, float t, float r, float b, Paint p) { shape(new Rectangle2D.Float(l, t, r - l, b - t), p); }
  public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }
  public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
    shape(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), p);
  }
  public void drawLine(float x1, float y1, float x2, float y2, Paint p) {
    Path path = new Path(); path.moveTo(x1, y1); path.lineTo(x2, y2); drawPath(path, p);
  }
  public void drawPath(Path path, Paint p) { shape(path.awt, p); }
  private void shape(Shape s, Paint p) {
    g.setPaint(p.awtPaint());
    if (p.style == Paint.Style.STROKE) {
      int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : (p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT);
      int join = p.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND : (p.join == Paint.Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER);
      g.setStroke(new BasicStroke(p.strokeWidth, cap, join));
      g.draw(s);
    } else {
      g.fill(s);
    }
  }
  public void drawText(String t, float x, float y, Paint p) {
    Font f = new Font("SansSerif", p.bold ? Font.BOLD : Font.PLAIN, Math.round(p.textSize));
    g.setFont(f);
    int w = g.getFontMetrics(f).stringWidth(t);
    float dx = p.align == Paint.Align.CENTER ? -w / 2f : (p.align == Paint.Align.RIGHT ? -w : 0);
    TextLayout tl = new TextLayout(t, f, g.getFontRenderContext());
    Shape outline = tl.getOutline(AffineTransform.getTranslateInstance(x + dx, y));
    g.setPaint(p.awtPaint());
    if (p.style == Paint.Style.STROKE) {
      int join = p.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND : BasicStroke.JOIN_MITER;
      g.setStroke(new BasicStroke(p.strokeWidth, BasicStroke.CAP_ROUND, join));
      g.draw(outline);
    } else {
      g.fill(outline);
    }
  }
}
