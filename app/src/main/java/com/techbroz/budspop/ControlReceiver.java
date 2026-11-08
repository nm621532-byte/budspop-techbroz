package com.techbroz.budspop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/** Handles the buttons on BudsPop's notification. */
public class ControlReceiver extends BroadcastReceiver {
    static final String ACTION_PREV = "com.techbroz.budspop.PREV";
    static final String ACTION_PLAY = "com.techbroz.budspop.PLAY";
    static final String ACTION_NEXT = "com.techbroz.budspop.NEXT";
    static final String ACTION_VOL_UP = "com.techbroz.budspop.VOL_UP";
    static final String ACTION_VOL_DOWN = "com.techbroz.budspop.VOL_DOWN";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (a == null) return;
        AudioManager am = ctx.getSystemService(AudioManager.class);
        switch (a) {
            case ACTION_PREV: BudsListener.sendMedia(ctx, BudsListener.CMD_PREV); break;
            case ACTION_PLAY: BudsListener.sendMedia(ctx, BudsListener.CMD_PLAY); break;
            case ACTION_NEXT: BudsListener.sendMedia(ctx, BudsListener.CMD_NEXT); break;
            case ACTION_VOL_UP: am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); break;
            case ACTION_VOL_DOWN: am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); break;
            default: break;
        }
    }
}
