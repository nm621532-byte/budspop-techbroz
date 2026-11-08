package com.techbroz.budspop;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.Random;

/**
 * BudsPop v1.1 fight-anime pop-ups: "Saiyan Rush", "Clash" and "Super Charge".
 *
 * Original artwork only. The fighters are our own characters (a spiky-haired fighter in a
 * black-and-cyan vest, and a rival pair in navy and crimson armour). They are drawn with
 * Canvas primitives in BudsScene's 1000 x 500 design space, so the overlay, the Test button
 * and the preview renderer draw the same frames. Every random value is precomputed with a
 * fixed seed, so a given time t always draws the same picture.
 */
final class BudsFight {

    private BudsFight() {}

    private static final float DEG = (float) (Math.PI / 180.0);
    private static final int GOLD = 0xFFFFD84A, GOLD_HOT = 0xFFFF8A1F, GOLD_CORE = 0xFFFFF6C2;
    private static final int CYAN = 0xFF7FE9FF, BLUE = 0xFF1E63FF, WHITE = 0xFFFFFFFF, INK = 0xFF0B0B12;

    private static final int NF = 18, NL = 40, NC = 7, NS = 40, ND = 26;
    private static final float[] fAng = new float[NF], fJit = new float[NF], fPh = new float[NF];
    private static final float[] lAng = new float[NL], lLen = new float[NL], lOff = new float[NL];
    private static final float[][] crack = new float[NC][10];
    private static final float[] sAng = new float[NS], sR = new float[NS], sPh = new float[NS];
    private static final float[] dAng = new float[ND], dDist = new float[ND], dSize = new float[ND];

    static {
        Random r = new Random(20261108L);
        for (int i = 0; i < NF; i++) {
            fAng[i] = (float) (i * 2 * Math.PI / NF + (r.nextFloat() - 0.5) * 0.25);
            fJit[i] = 0.8f + 0.4f * r.nextFloat();
            fPh[i] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < NL; i++) {
            lAng[i] = (float) (r.nextFloat() * 2 * Math.PI);
            lLen[i] = 90f + r.nextFloat() * 170f;
            lOff[i] = r.nextFloat();
        }
        for (int k = 0; k < NC; k++) {
            // ground cracks fan out from the feet, flattened for the perspective of the floor
            float ang = (float) (Math.PI * (0.08 + 0.84 * k / (NC - 1.0)) + (r.nextFloat() - 0.5) * 0.12);
            float len = 120f + r.nextFloat() * 110f;
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang) * 0.28f;
            for (int p = 0; p < 5; p++) {
                float f = (p + 1) / 5f;
                float jx = (r.nextFloat() - 0.5f) * 14f, jy = (r.nextFloat() - 0.5f) * 6f;
                crack[k][2 * p] = dx * len * f + jx;
                crack[k][2 * p + 1] = dy * len * f + jy;
            }
        }
        for (int i = 0; i < NS; i++) {
            sAng[i] = (float) (r.nextFloat() * 2 * Math.PI);
            sR[i] = 0.55f + 0.45f * r.nextFloat();
            sPh[i] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < ND; i++) {
            dAng[i] = (float) (r.nextFloat() * 2 * Math.PI);
            dDist[i] = 0.6f + 0.6f * r.nextFloat();
            dSize[i] = 5f + 7f * r.nextFloat();
        }
    }

    // ---- entry point -------------------------------------------------------------------

    static void draw(Canvas c, float t, int theme, String device) {
        if (theme == BudsScene.THEME_CLASH) {
            clash(c, t);
        } else if (theme == BudsScene.THEME_SUPER_CHARGE) {
            superCharge(c, t);
        } else {
            saiyanRush(c, t);
        }
        pill(c, t, device);
    }

    // ---- Saiyan Rush -------------------------------------------------------------------

    private static final float RUSH_X = 300f, FEET_Y = 412f, FIGHT_SC = 0.85f;
    private static final float TARGET_X = 640f, TARGET_Y = 262f;

    private static void saiyanRush(Canvas c, float t) {
        background(c, new int[] {0xFF0B1030, 0xFF150C22, 0xFF050308});
        c.drawCircle(500, FEET_Y, 330, radial(500, FEET_Y, 330, new int[] {al(0.40f, GOLD), a(0, GOLD)}, new float[] {0f, 1f}));
        ground(c, 0xFF2A1A12, 0xFF0B0608);

        speedLines(c, RUSH_X, 280, t, 260, 1450, GOLD, 0xFFFFFFFF);
        cracks(c, RUSH_X, FEET_Y, clamp01((t - 420f) / 650f), GOLD_HOT);

        float power = clamp01((t - 100f) / 900f);
        float hands = ease(clamp01((t - 150f) / 650f));
        float frontX = lerp(48f, 62f, hands), frontY = lerp(-118f, -150f, hands);
        float backX = lerp(-8f, 40f, hands), backY = lerp(-108f, -144f, hands);

        // Camera shake after the impact; the background above stays put.
        float shake = 16f * (1f - clamp01((t - 1450f) / 550f)) * (t >= 1450f ? 1f : 0f);
        float sx = shake * (float) Math.sin(t * 0.09), sy = shake * (float) Math.cos(t * 0.11);
        c.save();
        c.translate(sx, sy);

        fighter(c, RUSH_X, FEET_Y, FIGHT_SC, false, Look.RUSH, power, t,
                frontX, frontY, backX, backY, power);

        float hx = RUSH_X + frontX * FIGHT_SC, hy = FEET_Y + frontY * FIGHT_SC;
        // Gathering sparks that fly in toward the hands
        float gather = clamp01((t - 300f) / 900f);
        for (int i = 0; i < NS; i++) {
            float life = clamp01((t - 300f) / 200f) * (1f - clamp01((t - 1150f) / 200f));
            if (life <= 0f) continue;
            float rr = sR[i] * 190f * (1f - gather);
            float px = hx + (float) Math.cos(sAng[i]) * (rr + 6f), py = hy + (float) Math.sin(sAng[i]) * (rr + 6f);
            c.drawCircle(px, py, 2.5f + 2.5f * (float) Math.sin(sPh[i] + t * 0.02), fill(al(life, mix(CYAN, WHITE, 0.5f))));
        }

        // Energy ball: grows in the hands, then flies to the earbuds
        if (t < 1450f) {
            float grow = ease(clamp01((t - 700f) / 500f));
            float bx = hx, by = hy;
            float rad = 6f + 56f * grow;
            if (t >= 1200f) {
                float f = easeIn(clamp01((t - 1200f) / 250f));
                bx = lerp(hx, TARGET_X, f);
                by = lerp(hy, TARGET_Y, f);
                rad = 56f - 14f * f;
                c.drawLine(hx, hy, bx, by, stroke(al(0.9f, CYAN), 8f * (1f - 0.5f * f)));
                c.drawLine(hx, hy, bx, by, stroke(al(0.9f, WHITE), 3f));
            }
            if (rad > 0.5f) {
                float pulse = 1f + 0.04f * (float) Math.sin(t * 0.05);
                orb(c, bx, by, rad * pulse);
            }
        }

        // Impact: shockwave ring from the earbuds
        float imp = clamp01((t - 1450f) / 550f);
        if (t >= 1450f) {
            c.drawCircle(TARGET_X, TARGET_Y, 26f + 300f * ease(imp),
                    strokeRing(al(1f - imp, CYAN), 18f * (1f - imp) + 2f));
            c.drawCircle(TARGET_X, TARGET_Y, 12f + 200f * ease(clamp01((t - 1520f) / 480f)),
                    strokeRing(al(0.8f * (1f - imp), GOLD), 8f * (1f - imp) + 1f));
        }
        earbudPair(c, TARGET_X, TARGET_Y, back(clamp01((t - 1560f) / 380f)), t, 1900f);

        c.restore();

        flash(c, t, 1450f);
        title(c, t, 1640f, "BUDS CONNECTED!", 120f, GOLD_CORE, GOLD, 0xFFB35200);
    }

    // ---- Clash -------------------------------------------------------------------------

    private static final float CLASH_X = 500f, CLASH_Y = 262f, CLASH_SC = 0.8f;

    private static void clash(Canvas c, float t) {
        background(c, new int[] {0xFF0E0C1C, 0xFF1A0E18, 0xFF07060E});
        c.drawCircle(500, CLASH_Y, 300, radial(500, CLASH_Y, 300,
                new int[] {al(0.22f, 0xFFFFFFFF), a(0, 0xFFFFFFFF)}, new float[] {0f, 1f}));
        ground(c, 0xFF1E1A2E, 0xFF07060C);

        float dash = ease(clamp01(t / 560f));
        float lx = lerp(-90f, 300f, dash);
        float rx = lerp(1090f, 700f, dash);

        // Dash streaks behind each fighter while they run in
        float streak = clamp01(1f - t / 620f);
        if (streak > 0f) {
            for (int i = 0; i < 6; i++) {
                float y = 210f + i * 26f;
                float len = 120f + 60f * (i % 3);
                float xl = lx - 150f - 40f * i, xr = rx + 150f + 40f * i;
                c.drawLine(xl - len, y, xl, y, stroke(al(0.7f * streak, CYAN), 4f));
                c.drawLine(xr + len, y + 8f, xr, y + 8f, stroke(al(0.7f * streak, 0xFFFF9A6B), 4f));
            }
        }

        float attack = ease(clamp01((t - 380f) / 420f));
        float frontX = lerp(40f, 88f, attack), frontY = lerp(-120f, -168f, attack);
        float backX = lerp(-8f, 36f, attack), backY = lerp(-108f, -132f, attack);
        float power = clamp01((t - 200f) / 700f);

        // Beams from each front hand meet at the centre, then clash
        float beam = ease(clamp01((t - 760f) / 420f));
        float lhx = lx + frontX * CLASH_SC, lhy = FEET_Y + frontY * CLASH_SC;
        float rhx = rx - frontX * CLASH_SC, rhy = FEET_Y + frontY * CLASH_SC;
        float ex = lerp(lhx, CLASH_X, beam), ey = lerp(lhy, CLASH_Y, beam);
        float fx = lerp(rhx, CLASH_X, beam), fy = lerp(rhy, CLASH_Y, beam);
        float clashT = clamp01((t - 1180f) / 480f);

        float shake = 14f * (1f - clamp01((t - 1180f) / 500f)) * (t >= 1180f ? 1f : 0f);
        c.save();
        c.translate(shake * (float) Math.sin(t * 0.1), shake * (float) Math.cos(t * 0.13));

        if (t > 760f && t < 1500f) {
            beamLine(c, lhx, lhy, ex, ey, Look.BLUE.aura1, 0xFF9FF2FF);
            beamLine(c, rhx, rhy, fx, fy, Look.RED.aura1, 0xFFFFD2A8);
        }

        fighter(c, lx, FEET_Y, CLASH_SC, false, Look.BLUE, power, t, frontX, frontY, backX, backY, power);
        fighter(c, rx, FEET_Y, CLASH_SC, true, Look.RED, power, t, frontX, frontY, backX, backY, power);

        if (t >= 1180f) {
            float p = clashT;
            c.drawCircle(CLASH_X, CLASH_Y, 30f + 260f * ease(p), strokeRing(al(1f - p, WHITE), 16f * (1f - p) + 2f));
            c.drawCircle(CLASH_X, CLASH_Y, 20f + 380f * ease(clamp01((t - 1260f) / 560f)),
                    strokeRing(al(0.7f * (1f - p), 0xFF9FF2FF), 7f * (1f - p) + 1f));
            // debris thrown out from the clash point
            for (int i = 0; i < ND; i++) {
                float q = clamp01((t - 1180f) / 820f);
                float d = dDist[i] * 260f * ease(q);
                float dx = CLASH_X + (float) Math.cos(dAng[i]) * d;
                float dy = CLASH_Y + (float) Math.sin(dAng[i]) * d * 0.7f;
                c.drawRect(dx, dy, dx + dSize[i], dy + dSize[i], fill(al(1f - q, i % 2 == 0 ? 0xFFFFE08A : 0xFF9FF2FF)));
            }
        }
        earbudPair(c, CLASH_X, CLASH_Y, back(clamp01((t - 1330f) / 380f)), t, 1560f);

        c.restore();

        flash(c, t, 1180f);
        title(c, t, 1380f, "BUDS CONNECTED!", 120f, 0xFFFFFFFF, 0xFF7FE9FF, 0xFF0A2A4A);
    }

    // ---- Super Charge ------------------------------------------------------------------

    private static final float SC_X = 500f, SC_Y = 262f;

    private static void superCharge(Canvas c, float t) {
        background(c, new int[] {0xFF0A0B1E, 0xFF06081A, 0xFF030410});
        c.drawCircle(SC_X, SC_Y, 340, radial(SC_X, SC_Y, 340,
                new int[] {al(0.32f, BLUE), a(0, BLUE)}, new float[] {0f, 1f}));

        float charge = ease(clamp01((t - 120f) / 1000f));
        float wobble = clamp01((t - 700f) / 300f) * (1f - clamp01((t - 1000f) / 60f));
        float jx = wobble * 7f * (float) Math.sin(t * 0.09), jy = wobble * 5f * (float) Math.cos(t * 0.12);
        float cx = SC_X + jx, cy = SC_Y + jy;

        // Rings of light pulse out while the ball charges
        for (int k = 0; k < 3; k++) {
            float ph = ((t + k * 330f) % 990f) / 990f;
            float rr = 60f + 220f * ph;
            float alpha = (1f - ph) * 0.45f * clamp01((t - 150f) / 300f);
            if (t < 1000f && alpha > 0f) c.drawCircle(cx, cy, rr, strokeRing(al(alpha, CYAN), 3f));
        }

        // Sparks streaming in
        for (int i = 0; i < NS; i++) {
            float p = 1f - clamp01((t - 150f) / 1000f);
            float life = clamp01((t - 150f) / 200f) * (1f - clamp01((t - 1000f) / 150f));
            if (life <= 0f) continue;
            float rr = sR[i] * 300f * p;
            float px = cx + (float) Math.cos(sAng[i]) * rr, py = cy + (float) Math.sin(sAng[i]) * rr;
            c.drawCircle(px, py, 2f + 3f * sR[i], fill(al(life, mix(WHITE, CYAN, sR[i]))));
        }

        if (t < 1000f) {
            float rad = 6f + 118f * charge;
            orb(c, cx, cy, rad * (1f + 0.035f * (float) Math.sin(t * 0.06)));
        } else {
            // the burst
            float b = clamp01((t - 1000f) / 480f);
            float flashA = 0.9f * (1f - clamp01((t - 1000f) / 260f));
            for (int i = 0; i < 12; i++) {
                float ang = (float) (i * 2 * Math.PI / 12 + 0.12);
                float len = 60f + 430f * ease(b);
                float x1 = cx + (float) Math.cos(ang) * 40f, y1 = cy + (float) Math.sin(ang) * 40f;
                float x2 = cx + (float) Math.cos(ang) * len, y2 = cy + (float) Math.sin(ang) * len;
                c.drawLine(x1, y1, x2, y2, stroke(al((1f - b) * 0.9f, i % 2 == 0 ? CYAN : WHITE), 16f * (1f - b) + 2f));
            }
            c.drawCircle(cx, cy, 90f * (1f - b) + 6f, fill(al(1f - b, WHITE)));
            c.drawCircle(cx, cy, 30f + 500f * ease(b), strokeRing(al(0.9f * (1f - b), CYAN), 20f * (1f - b) + 2f));
            c.drawCircle(cx, cy, 20f + 380f * ease(clamp01((t - 1120f) / 500f)),
                    strokeRing(al(0.6f * (1f - clamp01((t - 1120f) / 500f)), GOLD), 8f));
            if (flashA > 0f) c.drawRect(0, 0, 1000, 500, fill(al(flashA, WHITE)));
        }
        earbudPair(c, cx, cy, back(clamp01((t - 1180f) / 380f)), t, 1500f);

        title(c, t, 1300f, "BUDS CONNECTED!", 120f, WHITE, CYAN, 0xFF0A2A66);
    }

    // ---- shared pieces ----------------------------------------------------------------

    /** The shape of one fighter. Local space: origin at the feet, +y down, about 290 tall at scale 1. */
    private static final class Look {
        final int hairDark, hairLit, armor, trim, skin, aura0, aura1, glow;
        Look(int hairDark, int hairLit, int armor, int trim, int skin, int aura0, int aura1, int glow) {
            this.hairDark = hairDark; this.hairLit = hairLit; this.armor = armor; this.trim = trim;
            this.skin = skin; this.aura0 = aura0; this.aura1 = aura1; this.glow = glow;
        }
        static final Look RUSH = new Look(0xFF15151C, GOLD, 0xFF1B1F2E, 0xFF38E8FF, 0xFFF2C7A5,
                GOLD_HOT, 0xFFFFE97A, GOLD);
        static final Look BLUE = new Look(0xFFDDE6F2, 0xFFFFFFFF, 0xFF10243A, 0xFF3EF0FF, 0xFFEFC6A0,
                0xFF2F8CFF, 0xFF9FF2FF, 0xFF3EF0FF);
        static final Look RED = new Look(0xFF1A1014, 0xFFFF5A3C, 0xFF3A0E12, 0xFFFFB347, 0xFFE9B890,
                0xFFFF3B2E, 0xFFFFC36B, 0xFFFF5A3C);
    }

    private static void fighter(Canvas c, float x, float feetY, float sc, boolean flip, Look L,
                                float power, float t,
                                float frontX, float frontY, float backX, float backY, float aura) {
        c.save();
        c.translate(x, feetY);
        c.scale(flip ? -sc : sc, sc);

        if (aura > 0.01f) aura(c, L, aura, t);

        int hair = mix(L.hairDark, L.hairLit, power);

        // back arm, behind the torso
        limb(c, -32f, -182f, backX, backY, L.armor, L.skin);

        // legs and boots
        c.drawLine(-14f, -100f, -22f, -12f, stroke(L.armor, 32f));
        c.drawLine(14f, -100f, 24f, -12f, stroke(L.armor, 32f));
        c.drawOval(new RectF(-46f, -16f, -4f, 6f), fill(INK));
        c.drawOval(new RectF(4f, -16f, 46f, 6f), fill(INK));

        // torso and vest
        Path torso = new Path();
        torso.moveTo(-42f, -198f);
        torso.lineTo(42f, -198f);
        torso.lineTo(34f, -96f);
        torso.lineTo(-34f, -96f);
        torso.close();
        c.drawPath(torso, fill(L.armor));
        Path vee = new Path();
        vee.moveTo(-26f, -190f);
        vee.lineTo(0f, -132f);
        vee.lineTo(26f, -190f);
        c.drawPath(vee, stroke(L.trim, 6f));
        c.drawRect(-36f, -110f, 36f, -98f, fill(L.trim));

        // shoulders
        c.drawCircle(-40f, -186f, 19f, fill(L.armor));
        c.drawCircle(40f, -186f, 19f, fill(L.armor));

        // front arm, in front of the torso
        limb(c, 32f, -182f, frontX, frontY, L.armor, L.skin);

        // neck, head, spiky hair, face
        c.drawRect(-12f, -210f, 12f, -194f, fill(L.skin));
        c.drawCircle(0f, -234f, 44f, fill(mix(L.hairDark, hair, 0.5f)));
        crown(c, hair);
        c.drawCircle(0f, -236f, 39f, fill(L.skin));
        float glowEyes = clamp01(power);
        Path brows = new Path();
        brows.moveTo(-24f, -252f);
        brows.lineTo(-7f, -246f);
        brows.moveTo(7f, -246f);
        brows.lineTo(24f, -252f);
        c.drawPath(brows, stroke(hair == L.hairLit ? mix(INK, L.hairLit, 0.2f) : INK, 4f));
        c.drawCircle(-15f, -238f, 4.5f + 1.5f * glowEyes, fill(INK));
        c.drawCircle(15f, -238f, 4.5f + 1.5f * glowEyes, fill(INK));
        if (glowEyes > 0.5f) {
            c.drawCircle(-15f, -238f, 2f, fill(CYAN));
            c.drawCircle(15f, -238f, 2f, fill(CYAN));
        }
        Path mouth = new Path();
        mouth.moveTo(-9f, -220f);
        mouth.quadTo(0f, -214f, 9f, -220f);
        c.drawPath(mouth, stroke(0xFFB0424E, 3.5f));

        // front hand and forearm already drawn; back arm drawn before the torso
        c.restore();
    }

    /** One arm: shoulder to elbow to hand, the elbow bent downward. */
    private static void limb(Canvas c, float sx, float sy, float hx, float hy, int sleeve, int skin) {
        float ex = (sx + hx) / 2f - 4f, ey = Math.max(sy, hy) + 26f;
        if (ey < sy + 10f) ey = sy + 10f;
        c.drawLine(sx, sy, ex, ey, stroke(sleeve, 18f));
        c.drawLine(ex, ey, hx, hy, stroke(sleeve, 15f));
        c.drawCircle(hx, hy, 10.5f, fill(skin));
    }

    /** Spiky hair crown above the head (centre 0,-236 in local space). */
    private static void crown(Canvas c, int color) {
        float[] angs = {196f, 218f, 242f, 266f, 290f, 314f, 340f};
        float[] lens = {28f, 40f, 52f, 60f, 50f, 38f, 26f};
        Path p = new Path();
        p.moveTo(pol(0f, -236f, 30f, 186f)[0], pol(0f, -236f, 30f, 186f)[1]);
        for (int k = 0; k < angs.length; k++) {
            float[] tip = pol(0f, -236f, 40f + lens[k], angs[k]);
            p.lineTo(tip[0], tip[1]);
            float va = k < angs.length - 1 ? (angs[k] + angs[k + 1]) / 2f : 352f;
            float[] v = pol(0f, -236f, 34f, va);
            p.lineTo(v[0], v[1]);
        }
        p.close();
        c.drawPath(p, fill(color));
    }

    private static float[] pol(float cx, float cy, float r, float deg) {
        return new float[] {cx + r * (float) Math.cos(deg * DEG), cy + r * (float) Math.sin(deg * DEG)};
    }

    /** Power-up aura: flame tongues around the body, flickering, in two layers. */
    private static void aura(Canvas c, Look L, float s, float t) {
        float gr = 175f * s;
        c.drawCircle(0f, -140f, gr, radial(0f, -140f, gr,
                new int[] {al(0.42f * s, L.glow), al(0.16f * s, L.glow), a(0, L.glow)},
                new float[] {0f, 0.55f, 1f}));
        for (int i = 0; i < NF; i++) {
            float ang = fAng[i] + (float) Math.sin(t * 0.004 + fPh[i]) * 0.08f;
            float flick = 0.55f + 0.45f * (float) Math.sin(t * 0.03 + fPh[i] * 3f);
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
            float bx = dx * 66f, by = -140f + dy * 118f;
            float nx = -dy, ny = dx;
            float len = (62f + 40f * flick * fJit[i]) * s;
            float curl = 9f * s * (float) Math.sin(t * 0.02 + fPh[i]);
            flame(c, bx, by, dx, dy, nx, ny, len, 15f * s, curl, al(0.85f * s, L.aura0));
            flame(c, bx, by, dx, dy, nx, ny, len * 0.6f, 8f * s, curl * 0.6f, al(0.95f * s, L.aura1));
        }
    }

    private static void flame(Canvas c, float bx, float by, float dx, float dy, float nx, float ny,
                              float len, float w, float curl, int color) {
        float tx = bx + dx * len + nx * curl, ty = by + dy * len + ny * curl;
        Path p = new Path();
        p.moveTo(bx + nx * w, by + ny * w);
        p.quadTo(bx + dx * len * 0.55f + nx * w * 0.6f, by + dy * len * 0.55f + ny * w * 0.6f, tx, ty);
        p.quadTo(bx + dx * len * 0.55f - nx * w * 0.6f, by + dy * len * 0.55f - ny * w * 0.6f, bx - nx * w, by - ny * w);
        p.close();
        c.drawPath(p, fill(color));
    }

    private static void background(Canvas c, int[] cols) {
        c.drawRoundRect(new RectF(10, 10, 990, 490), 40, 40,
                linear(0, 0, 0, 500, cols, new float[] {0f, 0.6f, 1f}));
    }

    private static void ground(Canvas c, int top, int bottom) {
        c.drawRect(10, FEET_Y, 990, 490, linear(0, FEET_Y, 0, 490, new int[] {top, bottom}, new float[] {0f, 1f}));
        c.drawRect(10, FEET_Y - 1.5f, 990, FEET_Y + 1.5f, fill(al(0.55f, 0xFFFFFFFF)));
    }

    private static void speedLines(Canvas c, float cx, float cy, float t, float fadeIn, float fadeOut, int col1, int col2) {
        float a = clamp01((t - fadeIn) / 200f) * (1f - clamp01((t - (fadeOut - 300f)) / 300f));
        if (a <= 0f) return;
        float grow = ease(clamp01((t - fadeIn) / 700f));
        for (int i = 0; i < NL; i++) {
            float r0 = 150f + 140f * lOff[i] * (1.2f - grow);
            float len = lLen[i] * grow;
            float x1 = cx + (float) Math.cos(lAng[i]) * r0, y1 = cy + (float) Math.sin(lAng[i]) * r0 * 0.7f;
            float x2 = cx + (float) Math.cos(lAng[i]) * (r0 + len), y2 = cy + (float) Math.sin(lAng[i]) * (r0 + len) * 0.7f;
            c.drawLine(x1, y1, x2, y2, stroke(al(0.7f * a, i % 3 == 0 ? col2 : col1), 2f + 3f * lOff[i]));
        }
    }

    private static void cracks(Canvas c, float gx, float gy, float p, int color) {
        if (p <= 0f) return;
        for (int k = 0; k < NC; k++) {
            Path path = new Path();
            path.moveTo(gx, gy);
            float[] pts = crack[k];
            float upto = p * 5f;
            for (int i = 0; i < 5; i++) {
                float f = clamp01(upto - i);
                if (f <= 0f) break;
                float px = gx + pts[2 * i] * f, py = gy + pts[2 * i + 1] * f;
                path.lineTo(px, py);
            }
            c.drawPath(path, stroke(al(0.85f, color), 4f));
        }
    }

    private static void beamLine(Canvas c, float x1, float y1, float x2, float y2, int core, int tip) {
        Paint outer = stroke(0xFFFFFFFF, 26f);
        outer.setShader(new LinearGradient(x1, y1, x2, y2,
                new int[] {a(0, core), al(0.85f, core), al(0.95f, tip)}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawLine(x1, y1, x2, y2, outer);
        c.drawLine(x1, y1, x2, y2, stroke(al(0.9f, WHITE), 8f));
        c.drawCircle(x2, y2, 22f, radial(x2, y2, 22f, new int[] {al(0.9f, WHITE), a(0, tip)}, new float[] {0f, 1f}));
    }

    /** Charged energy ball: white core, cyan body, blue halo. */
    private static void orb(Canvas c, float x, float y, float r) {
        if (r < 1f) return;
        c.drawCircle(x, y, r * 1.9f, radial(x, y, r * 1.9f,
                new int[] {al(0.38f, CYAN), a(0, BLUE)}, new float[] {0f, 1f}));
        c.drawCircle(x, y, r, radial(x, y, r,
                new int[] {WHITE, al(0.95f, CYAN), al(0.75f, BLUE), a(0, BLUE)},
                new float[] {0f, 0.35f, 0.7f, 1f}));
    }

    /** Two earbuds at (x-78, y) and (x+78, y) that pop in, then a crackling link between them. */
    private static void earbudPair(Canvas c, float x, float y, float pop, float t, float linkAt) {
        if (pop <= 0.01f) return;
        c.drawCircle(x, y, 90f * pop, radial(x, y, 90f * pop + 1f,
                new int[] {al(0.35f * pop, CYAN), a(0, CYAN)}, new float[] {0f, 1f}));
        bud(c, x - 78f, y, pop, t);
        bud(c, x + 78f, y, pop, t + 300f);
        float link = clamp01((t - linkAt) / 220f);
        if (link > 0f) {
            Path zig = new Path();
            float lx = x - 78f + 16f * pop, rx = x + 78f - 16f * pop;
            zig.moveTo(lx, y);
            for (int i = 1; i < 8; i++) {
                float px = lerp(lx, rx, i / 8f);
                float py = y + (i % 2 == 0 ? -9f : 9f) * (float) Math.sin(t * 0.07 + i) * link;
                zig.lineTo(px, py);
            }
            zig.lineTo(rx, y);
            c.drawPath(zig, stroke(al(link, CYAN), 6f));
            c.drawPath(zig, stroke(al(link, WHITE), 2.5f));
        }
    }

    private static void bud(Canvas c, float x, float y, float s, float t) {
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        c.drawOval(new RectF(-21f, -27f, 21f, 27f), fill(WHITE));
        c.drawOval(new RectF(-21f, -27f, 21f, 27f), stroke(INK, 3f));
        c.drawOval(new RectF(-11f, -15f, 11f, 3f), fill(al(0.6f + 0.4f * (float) Math.sin(t * 0.02), CYAN)));
        c.drawRect(-6f, 22f, 6f, 36f, fill(WHITE));
        c.drawRect(-6f, 22f, 6f, 36f, stroke(INK, 2f));
        c.restore();
    }

    private static void flash(Canvas c, float t, float at) {
        float f = 0.9f * (1f - clamp01((t - at) / 280f));
        if (t < at || f <= 0f) return;
        c.drawRoundRect(new RectF(10, 10, 990, 490), 40, 40, fill(al(f, WHITE)));
    }

    /** Title that slams in with a zoom, a gold halo and a dark outline. */
    private static void title(Canvas c, float t, float t0, String text, float baseY,
                              int topColor, int bottomColor, int outline) {
        float p = clamp01((t - t0) / 380f);
        if (t < t0) return;
        float sc = 1.9f - 0.9f * back(p);
        float alpha = clamp01((t - t0) / 120f);
        c.save();
        c.translate(500f, baseY);
        c.scale(sc, sc);
        Paint halo = stroke(al(0.55f * alpha, bottomColor), 30f);
        halo.setFakeBoldText(true);
        halo.setTextSize(76f);
        halo.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, 0f, 0f, halo);
        Paint edge = stroke(al(alpha, outline), 13f);
        edge.setFakeBoldText(true);
        edge.setTextSize(76f);
        edge.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, 0f, 0f, edge);
        Paint fillTxt = fill(WHITE);
        fillTxt.setShader(new LinearGradient(0f, -62f, 0f, 6f,
                new int[] {topColor, bottomColor}, new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        fillTxt.setFakeBoldText(true);
        fillTxt.setTextSize(76f);
        fillTxt.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, 0f, 0f, fillTxt);
        c.restore();
    }

    /** Device name pill at the bottom, once the title has landed. */
    private static void pill(Canvas c, float t, String device) {
        float p = clamp01((t - 2050f) / 300f);
        if (p <= 0f) return;
        String name = device == null || device.trim().isEmpty() ? "Earbuds" : device.trim();
        if (name.length() > 26) name = name.substring(0, 25) + "...";
        c.drawRoundRect(new RectF(290, 438, 710, 484), 23, 23, fill(al(0.6f * p, INK)));
        Paint tp = fill(al(p, WHITE));
        tp.setFakeBoldText(true);
        tp.setTextSize(24f);
        tp.setTextAlign(Paint.Align.CENTER);
        c.drawText(name, 500f, 470f, tp);
    }

    // ---- small helpers -----------------------------------------------------------------

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }
    private static float ease(float x) { float t = 1f - clamp01(x); return 1f - t * t * t; }
    private static float easeIn(float x) { float v = clamp01(x); return v * v * v; }
    private static float back(float x) {
        float v = clamp01(x), c1 = 1.70158f, c3 = c1 + 1f, t = v - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }

    private static int a(int alpha, int rgb) {
        int al = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int al(float alpha01, int rgb) {
        return a(Math.round(255f * clamp01(alpha01)), rgb);
    }

    private static int mix(int c1, int c2, float f) {
        float v = clamp01(f);
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int aa = Math.round(a1 + (a2 - a1) * v), rr = Math.round(r1 + (r2 - r1) * v);
        int gg = Math.round(g1 + (g2 - g1) * v), bb = Math.round(b1 + (b2 - b1) * v);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static Paint fill(int color) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    private static Paint stroke(int color, float width) {
        Paint p = fill(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    private static Paint strokeRing(int color, float width) {
        return stroke(color, Math.max(width, 0.5f));
    }

    private static Paint radial(float x, float y, float r, int[] cols, float[] stops) {
        Paint p = fill(0xFFFFFFFF);
        p.setShader(new RadialGradient(x, y, Math.max(r, 0.5f), cols, stops, Shader.TileMode.CLAMP));
        return p;
    }

    private static Paint linear(float x0, float y0, float x1, float y1, int[] cols, float[] stops) {
        Paint p = fill(0xFFFFFFFF);
        p.setShader(new LinearGradient(x0, y0, x1, y1, cols, stops, Shader.TileMode.CLAMP));
        return p;
    }
}
