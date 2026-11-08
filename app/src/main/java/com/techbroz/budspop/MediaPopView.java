package com.techbroz.budspop;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;

/**
 * "My Animation": the user's own GIF, picture or short video, full width, with BUDS CONNECTED!
 * and the device name laid over the bottom. The file is read through the permission the user
 * granted when picking it; nothing is copied or uploaded. Video is muted unless the user turns
 * sound on in Settings.
 */
final class MediaPopView extends FrameLayout {
    private final Runnable onFail;
    private final boolean sound;
    private MediaPlayer player;
    private TextureView texture;
    private int videoW, videoH;
    private boolean released;
    private boolean usable = true;

    MediaPopView(Context ctx, String uriString, String mime, boolean sound, String device, Runnable onFail) {
        super(ctx);
        this.onFail = onFail;
        this.sound = sound;
        setBackgroundColor(Color.BLACK);
        Uri uri = Uri.parse(uriString);
        boolean video = mime != null && mime.startsWith("video/");
        if (video) {
            texture = new TextureView(ctx);
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    startVideo(ctx, uri, st);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                    fitVideo();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture st) { }
            });
            addView(texture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            Drawable d = decode(ctx, uri);
            if (d == null) {
                usable = false; // unreadable, or a GIF/picture on Android older than 9
                return;
            }
            ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(d);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) startIfAnimated(d);
            addView(iv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        addView(caption(ctx, device), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    /** False when the file could not be shown here; the caller then uses the built-in theme. */
    boolean usable() {
        return usable;
    }

    private void startVideo(Context ctx, Uri uri, SurfaceTexture st) {
        if (released) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setDataSource(ctx, uri);
            mp.setSurface(new Surface(st));
            float v = sound ? 1f : 0f;
            mp.setVolume(v, v);
            mp.setLooping(true);
            mp.setOnVideoSizeChangedListener((p, w, h) -> {
                videoW = w;
                videoH = h;
                fitVideo();
            });
            mp.setOnPreparedListener(p -> {
                if (!released) p.start();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                failed();
                return true;
            });
            mp.prepareAsync();
        } catch (IOException | RuntimeException e) {
            failed();
        }
    }

    private void failed() {
        if (!released && onFail != null) post(onFail);
    }

    /** Letterbox the video inside the strip, keeping its shape. */
    private void fitVideo() {
        if (texture == null || videoW <= 0 || videoH <= 0) return;
        int vw = texture.getWidth(), vh = texture.getHeight();
        if (vw <= 0 || vh <= 0) return;
        float s = Math.min((float) vw / videoW, (float) vh / videoH);
        Matrix m = new Matrix();
        m.setScale(videoW * s / vw, videoH * s / vh, vw / 2f, vh / 2f);
        texture.setTransform(m);
    }

    void release() {
        released = true;
        if (player != null) {
            try {
                player.stop();
            } catch (RuntimeException ignored) { }
            player.release();
            player = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private static void startIfAnimated(Drawable d) {
        if (d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
    }

    private static Drawable decode(Context ctx, Uri uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null;
        try {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.getContentResolver(), uri));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static View caption(Context ctx, String device) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(ctx, 18), 0, dp(ctx, 12));
        box.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0xD9000000}));
        TextView title = new TextView(ctx);
        title.setText("BUDS CONNECTED!");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFD84A);
        title.setShadowLayer(8f, 0f, 2f, 0xFF000000);
        box.addView(title);
        TextView dev = new TextView(ctx);
        dev.setText(device == null || device.trim().isEmpty() ? "Earbuds" : device.trim());
        dev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dev.setTextColor(0xFFFFFFFF);
        dev.setShadowLayer(6f, 0f, 1f, 0xFF000000);
        box.addView(dev);
        return box;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
