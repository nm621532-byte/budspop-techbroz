package com.techbroz.budspop;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** Hosts the animation: redraws until the scene finishes, then stops. */
final class BudsPopView extends View {
    private final int theme;
    private final String device;
    private final long startedAt = System.currentTimeMillis();

    BudsPopView(Context context, int theme, String device) {
        super(context);
        this.theme = theme;
        this.device = device;
        setBackgroundColor(0x00000000);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float t = System.currentTimeMillis() - startedAt;
        BudsScene.draw(canvas, getWidth(), getHeight(), t, theme, device);
        if (t < BudsScene.DURATION_MS) postInvalidateOnAnimation();
    }
}
