package com.techbroz.budspop;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * BudsPop's original pop-up artwork: a chibi character who slips in a pair of earbuds,
 * sparkles, and a "Buds Connected!" line that types itself out. Everything is drawn with
 * Canvas primitives in a fixed 1000 x 500 design space, so one routine serves the overlay,
 * the Test button, and the preview renderer.
 */
public final class BudsScene {

    public static final int THEME_SAKURA = 0;
    public static final int THEME_NEON = 1;
    public static final int THEME_SUNNY = 2;
    public static final int THEME_SAIYAN_RUSH = 3;   // v1.1 default: original spiky-hair fighter, power-up, blast
    public static final int THEME_CLASH = 4;         // v1.1: two original fighters, beam clash
    public static final int THEME_SUPER_CHARGE = 5;  // v1.1: energy ball charges, then bursts
    public static final int THEME_MY_ANIMATION = 6;  // v1.1: the user's own GIF / picture / video
    public static final String[] THEME_NAMES = {"Sakura", "Neon Night", "Sunny",
            "Saiyan Rush", "Clash", "Super Charge", "My Animation (own GIF/video)"};

    /** Length of the whole animation; the overlay closes after roughly this (the auto-dismiss is 3 s). */
    public static final long DURATION_MS = 3000;

    private static final int DESIGN_W = 1000;
    private static final int DESIGN_H = 500;

    private BudsScene() {}

    private static int a(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float t = x - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }

    private static float easeOut(float x) {
        float t = 1f - clamp01(x);
        return 1f - t * t * t;
    }

    /**
     * Draws one frame.
     * @param w      target width in pixels
     * @param h      target height in pixels
     * @param t      elapsed milliseconds since the pop-up started
     * @param theme  THEME_* constant
     * @param device name of the device that connected (may be empty)
     */
    public static void draw(Canvas c, float w, float h, float t, int theme, String device) {
        float s = Math.min(w / DESIGN_W, h / DESIGN_H);
        float ox = (w - DESIGN_W * s) / 2f;
        float oy = (h - DESIGN_H * s) / 2f;
        c.save();
        c.translate(ox, oy);
        c.scale(s, s);

        if (theme >= THEME_SAIYAN_RUSH) {
            // fight-anime themes live in BudsFight; "My Animation" falls back to Saiyan Rush here
            BudsFight.draw(c, t, theme, device);
            c.restore();
            return;
        }

        int bgTop, bgBottom, accent, ink, hair;
        switch (theme) {
            case THEME_NEON:
                bgTop = 0xFF1B1038; bgBottom = 0xFF0B2A3F; accent = 0xFF3EF0FF;
                ink = 0xFFFFFFFF; hair = 0xFF7B5CFF;
                break;
            case THEME_SUNNY:
                bgTop = 0xFFFFF3C4; bgBottom = 0xFFFFD27A; accent = 0xFFFF7A00;
                ink = 0xFF3A2400; hair = 0xFF6B3E12;
                break;
            default: // Sakura
                bgTop = 0xFFFFE6F0; bgBottom = 0xFFFFC7DD; accent = 0xFFFF5C8D;
                ink = 0xFF3B1F33; hair = 0xFF4A2A55;
                break;
        }

        // Card background (two flat bands make a soft two-tone sky)
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bgTop);
        c.drawRoundRect(new RectF(20, 20, 980, 480), 56, 56, p);
        p.setColor(bgBottom);
        c.drawRoundRect(new RectF(20, 260, 980, 480), 56, 56, p);
        p.setColor(a(90, 0xFFFFFF));
        c.drawCircle(120, 110, 90, p);
        c.drawCircle(880, 400, 120, p);

        // Sparkles burst out once the buds click in
        float burst = clamp01((t - 700f) / 500f);
        for (int i = 0; i < 9; i++) {
            float ang = (float) (i * 0.698 + 0.3);
            float dist = 170f + 60f * easeOut(burst);
            float cx = 330f + (float) Math.cos(ang) * dist;
            float cy = 230f + (float) Math.sin(ang) * dist * 0.9f;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.012 + i * 1.7);
            float size = (10f + 12f * tw) * burst;
            if (size > 0.5f) drawSparkle(c, cx, cy, size, (i % 3 == 0) ? 0xFFFFFFFF : accent);
        }

        // Character: drops in, then bobs gently
        float drop = (1f - easeOutBack(clamp01(t / 450f))) * -160f;
        float bob = t > 1100 ? (float) Math.sin((t - 1100) * 0.008) * 7f : 0f;
        float cx = 330f;
        float cy = 250f + drop + bob;

        // Hair back
        p.setColor(hair);
        c.drawCircle(cx, cy - 18, 132, p);
        // Face
        p.setColor(0xFFFFE2CC);
        c.drawCircle(cx, cy + 12, 112, p);
        // Bangs
        Path bangs = new Path();
        bangs.moveTo(cx - 118, cy - 6);
        bangs.quadTo(cx - 90, cy - 118, cx - 6, cy - 62);
        bangs.quadTo(cx + 40, cy - 110, cx + 118, cy - 6);
        bangs.quadTo(cx + 60, cy - 60, cx, cy - 40);
        bangs.quadTo(cx - 60, cy - 60, cx - 118, cy - 6);
        bangs.close();
        p.setColor(hair);
        c.drawPath(bangs, p);

        // Blush
        p.setColor(a(150, 0xFF7A9E));
        c.drawCircle(cx - 74, cy + 62, 18, p);
        c.drawCircle(cx + 74, cy + 62, 18, p);

        // Eyes: blink every ~3 s
        boolean blink = (t % 3000) < 130;
        p.setColor(ink);
        if (blink) {
            Paint line = new Paint();
            line.setAntiAlias(true);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(6);
            line.setColor(ink);
            c.drawLine(cx - 62, cy + 22, cx - 30, cy + 22, line);
            c.drawLine(cx + 30, cy + 22, cx + 62, cy + 22, line);
        } else {
            c.drawOval(new RectF(cx - 62, cy - 2, cx - 30, cy + 40), p);
            c.drawOval(new RectF(cx + 30, cy - 2, cx + 62, cy + 40), p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx - 52, cy + 8, 8, p);
            c.drawCircle(cx + 40, cy + 8, 8, p);
        }

        // Mouth: a small open smile that grows in
        float smile = clamp01((t - 850f) / 300f);
        p.setColor(0xFFC0395E);
        c.drawOval(new RectF(cx - 16, cy + 74, cx + 16, cy + 74 + 22 * smile), p);

        // Earbuds fly in from each side and snap onto the ears
        float lIn = easeOut(clamp01((t - 200f) / 450f));
        float rIn = easeOut(clamp01((t - 380f) / 450f));
        drawBud(c, cx - 120 - 160 * (1f - lIn), cy + 52, accent, lIn);
        drawBud(c, cx + 120 + 160 * (1f - rIn), cy + 52, accent, rIn);

        // Check badge pops in on top-right of the head
        float pop = easeOutBack(clamp01((t - 1000f) / 350f));
        if (pop > 0.01f) {
            c.save();
            c.translate(cx + 112, cy - 112);
            c.scale(pop, pop);
            p.setColor(0xFF2ECC71);
            c.drawCircle(0, 0, 42, p);
            Paint tick = new Paint();
            tick.setAntiAlias(true);
            tick.setStyle(Paint.Style.STROKE);
            tick.setStrokeWidth(10);
            tick.setColor(0xFFFFFFFF);
            c.drawLine(-20, 2, -6, 16, tick);
            c.drawLine(-6, 16, 22, -14, tick);
            c.restore();
        }

        // Text block on the right: "Buds" then "Connected!" typed out letter by letter
        float textX = 520f;
        String line1 = "Buds";
        String line2 = "Connected!";
        int shown1 = (int) Math.min(line1.length(), Math.max(0, (t - 600f) / 60f));
        int shown2 = (int) Math.min(line2.length(), Math.max(0, (t - 900f) / 55f));
        Paint big = new Paint();
        big.setAntiAlias(true);
        big.setStyle(Paint.Style.FILL);
        big.setFakeBoldText(true);
        big.setTextAlign(Paint.Align.LEFT);
        big.setTextSize(104);
        big.setColor(ink);
        if (shown1 > 0) c.drawText(line1.substring(0, shown1), textX, 210, big);
        big.setTextSize(60);
        big.setColor(accent);
        if (shown2 > 0) c.drawText(line2.substring(0, shown2), textX, 300, big);

        // Device name, faded in last
        float devAlpha = clamp01((t - 1500f) / 400f);
        if (device != null && !device.isEmpty() && devAlpha > 0f) {
            Paint small = new Paint();
            small.setAntiAlias(true);
            small.setStyle(Paint.Style.FILL);
            small.setTextAlign(Paint.Align.LEFT);
            small.setTextSize(36);
            small.setColor(a((int) (devAlpha * 255), ink));
            String shortName = device.length() > 22 ? device.substring(0, 21) + "…" : device;
            c.drawText(shortName, textX, 370, small);
        }

        c.restore();
    }

    private static void drawBud(Canvas c, float x, float y, int accent, float in) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawOval(new RectF(x - 22, y - 26, x + 22, y + 26), p);
        p.setColor(a((int) (255 * in), accent));
        c.drawOval(new RectF(x - 12, y - 14, x + 12, y + 2), p);
    }

    private static void drawSparkle(Canvas c, float x, float y, float r, int color) {
        Path path = new Path();
        path.moveTo(x, y - r);
        path.quadTo(x + r * 0.18f, y - r * 0.18f, x + r, y);
        path.quadTo(x + r * 0.18f, y + r * 0.18f, x, y + r);
        path.quadTo(x - r * 0.18f, y + r * 0.18f, x - r, y);
        path.quadTo(x - r * 0.18f, y - r * 0.18f, x, y - r);
        path.close();
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(path, p);
    }
}
