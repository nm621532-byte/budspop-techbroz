package com.techbroz.budspop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Shows the "Buds Connected!" pop-up as a bottom sheet: a window pinned to the bottom edge
 * (above the navigation bar), a rounded sheet that slides up with a small overshoot, holds,
 * then slides back down. If the overlay permission is missing it falls back to a heads-up
 * notification plus a toast. Every trigger (real connect, Test pop-up, My Animation) goes
 * through show(), so they all use this same path.
 */
final class OverlayPresenter {
    static final String CHANNEL_ALERTS = "buds_pop_alerts";
    private static final int NOTIFY_ID_POP = 2001;
    private static final long MEDIA_MS = 4000;
    /** Slide-up time (ease-out with a small overshoot). */
    static final long IN_MS = 420;
    /** Slide-down time (accelerating). */
    static final long OUT_MS = 260;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** The window root, the sheet inside it, and what the sheet shows. */
    private static FrameLayout current;
    private static View currentSheet;
    private static View currentContent;
    private static boolean outing;
    private static long showId;

    private OverlayPresenter() {}

    static void show(Context ctx, String device, int theme, boolean sound) {
        UI.post(() -> {
            if (sound) chime(ctx);
            if (Settings.canDrawOverlays(ctx)) {
                showOverlay(ctx, device, theme, sound);
            } else {
                showHeadsUp(ctx, device);
            }
        });
    }

    private static void showOverlay(Context ctx, String device, int theme, boolean sound) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            showHeadsUp(ctx, device);
            return;
        }
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int sideMargin = dp(ctx, 12);
        int sheetW = screenW - 2 * sideMargin;
        // the scene is 2:1; the sheet adds room for the handle and breathing space, and is
        // never shorter than about a third of the screen
        int sceneH = Math.round(sheetW * 0.5f);
        int sheetH = Math.max(Math.round(screenH * 0.36f), sceneH + dp(ctx, 52));
        int shadowPad = dp(ctx, 20);            // room above the sheet for its shadow
        int bottomGap = Math.max(navBarHeight(ctx), dp(ctx, 8)) + dp(ctx, 12);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, sheetH + shadowPad, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        lp.y = bottomGap;

        View content;
        long holdMs = BudsScene.DURATION_MS;
        if (theme == BudsScene.THEME_MY_ANIMATION && !Prefs.mediaUri(ctx).isEmpty()) {
            MediaPopView media = new MediaPopView(ctx, Prefs.mediaUri(ctx), Prefs.mediaMime(ctx),
                    Prefs.mediaSound(ctx), device,
                    () -> UI.post(() -> {
                        // the file could not play: show the built-in animation instead
                        removeCurrent(wm);
                        showOverlay(ctx, device, BudsScene.THEME_SAIYAN_RUSH, sound);
                    }));
            if (media.usable()) {
                content = media;
                holdMs = MEDIA_MS;
            } else {
                content = new BudsPopView(ctx, BudsScene.THEME_SAIYAN_RUSH, device);
            }
        } else {
            int drawTheme = theme == BudsScene.THEME_MY_ANIMATION ? BudsScene.THEME_SAIYAN_RUSH : theme;
            content = new BudsPopView(ctx, drawTheme, device);
        }

        // the sheet: rounded top corners, soft shadow, a small grab handle
        FrameLayout sheet = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        float r = dp(ctx, 28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setColor(0xFF121A2E);
        sheet.setBackground(bg);
        sheet.setElevation(dp(ctx, 14));
        sheet.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        sheet.setClipToOutline(true);
        sheet.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        View grab = new View(ctx);
        GradientDrawable grabBg = new GradientDrawable();
        grabBg.setColor(0x66FFFFFF);
        grabBg.setCornerRadius(dp(ctx, 3));
        grab.setBackground(grabBg);
        FrameLayout.LayoutParams grabLp = new FrameLayout.LayoutParams(dp(ctx, 40), dp(ctx, 4),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        grabLp.topMargin = dp(ctx, 10);
        sheet.addView(grab, grabLp);

        FrameLayout root = new FrameLayout(ctx);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(sheetW, sheetH,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(sheet, sheetLp);

        // start hidden below the sheet's own bottom edge, then slide up
        sheet.setPivotX(sheetW / 2f);
        sheet.setPivotY(sheetH);
        sheet.setTranslationY(sheetH + dp(ctx, 4));
        sheet.setAlpha(0.6f);
        sheet.setScaleX(0.96f);
        sheet.setScaleY(0.96f);

        removeCurrent(wm); // a pop-up still on screen gives way to the new one
        long id = ++showId;
        try {
            wm.addView(root, lp);
        } catch (RuntimeException e) {
            showHeadsUp(ctx, device);
            return;
        }
        current = root;
        currentSheet = sheet;
        currentContent = content;
        outing = false;

        sheet.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        final FrameLayout myRoot = root;
        sheet.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(IN_MS)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .withEndAction(() -> {
                    if (currentSheet == sheet) sheet.setLayerType(View.LAYER_TYPE_NONE, null);
                })
                .start();

        sheet.setOnClickListener(v -> slideOut(wm, id));
        UI.postDelayed(() -> {
            if (showId == id) slideOut(wm, id);
        }, IN_MS + holdMs);
        if (sound) vibrate(ctx, theme);
    }

    /** Slides the sheet back down and removes the window at the end. */
    private static void slideOut(WindowManager wm, long id) {
        if (showId != id || outing || currentSheet == null) return;
        outing = true;
        final View sheet = currentSheet;
        final FrameLayout myRoot = current;
        sheet.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        sheet.animate()
                .translationY(sheet.getHeight() + 8f)
                .alpha(0f)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(OUT_MS)
                .setInterpolator(new AccelerateInterpolator(1.2f))
                .withEndAction(() -> {
                    if (current == myRoot) removeCurrent(wm);
                })
                .start();
    }

    private static void removeCurrent(WindowManager wm) {
        FrameLayout root = current;
        View sheet = currentSheet;
        Object content = currentContent;
        current = null;
        currentSheet = null;
        currentContent = null;
        outing = false;
        if (root == null) return;
        if (sheet != null) sheet.animate().cancel();
        if (content instanceof MediaPopView) ((MediaPopView) content).release();
        try {
            wm.removeView(root);
        } catch (RuntimeException ignored) { }
    }

    private static void showHeadsUp(Context ctx, String device) {
        UI.post(() -> Toast.makeText(ctx,
                "Buds connected. Allow BudsPop \"Show over other apps\" for the full pop-up.",
                Toast.LENGTH_LONG).show());
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ALERTS, "Buds alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Pop-up when your earbuds connect");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(ctx, CHANNEL_ALERTS)
                : new Notification.Builder(ctx);
        b.setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle("Buds Connected! 🎧")
                .setContentText(device == null || device.isEmpty() ? "Earbuds are connected" : device + " is connected")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b.setTimeoutAfter(BudsScene.DURATION_MS);
        nm.notify(NOTIFY_ID_POP, b.build());
    }

    private static void chime(Context ctx) {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 220);
            UI.postDelayed(tg::release, 400);
        } catch (RuntimeException ignored) { }
    }

    /** Fight themes get a rising power-up pattern; the others a single short buzz. */
    private static void vibrate(Context ctx, int theme) {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (theme >= BudsScene.THEME_SAIYAN_RUSH) {
                long[] timings = {0, 70, 50, 90, 40, 130, 60, 300};
                int[] amplitudes = {0, 60, 0, 120, 0, 180, 0, 255};
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                v.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else {
            v.vibrate(35);
        }
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /** Height of the system navigation bar, or 0 when the phone has none (gesture bar). */
    private static int navBarHeight(Context ctx) {
        Resources res = ctx.getResources();
        int id = res.getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? res.getDimensionPixelSize(id) : 0;
    }
}
