package com.techbroz.budspop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
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
import android.view.WindowManager;

/**
 * Shows the full-width "Buds Connected!" pop-up as an overlay window. If the overlay
 * permission is missing, it falls back to a heads-up notification with the same words.
 */
final class OverlayPresenter {
    static final String CHANNEL_ALERTS = "buds_pop_alerts";
    private static final int NOTIFY_ID_POP = 2001;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static View current;

    private OverlayPresenter() {}

    static void show(Context ctx, String device, int theme, boolean sound) {
        UI.post(() -> {
            if (sound) chime(ctx);
            if (Settings.canDrawOverlays(ctx)) {
                showOverlay(ctx, device, theme);
            } else {
                showHeadsUp(ctx, device);
            }
        });
    }

    private static void showOverlay(Context ctx, String device, int theme) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) { showHeadsUp(ctx, device); return; }
        if (current != null) removeCurrent(wm);

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = Math.round(width * 0.5f); // the scene is 2:1
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        lp.y = Math.round(dm.heightPixels * 0.08f);

        View view = new BudsPopView(ctx, theme, device);
        view.setOnClickListener(v -> removeCurrent(wm));
        try {
            wm.addView(view, lp);
        } catch (RuntimeException e) {
            showHeadsUp(ctx, device);
            return;
        }
        current = view;
        UI.postDelayed(() -> removeCurrent(wm), BudsScene.DURATION_MS);
        vibrate(ctx);
    }

    private static void removeCurrent(WindowManager wm) {
        if (current == null) return;
        try { wm.removeView(current); } catch (RuntimeException ignored) { }
        current = null;
    }

    private static void showHeadsUp(Context ctx, String device) {
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

    private static void vibrate(Context ctx) {
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(35);
        }
    }
}
