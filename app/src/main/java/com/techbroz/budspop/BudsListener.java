package com.techbroz.budspop;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.List;

/**
 * Exists so the app may read the phone's media sessions (the user switches this on once in
 * Settings > Notification access). Play/pause/next/previous are sent to whichever player is active.
 */
public class BudsListener extends NotificationListenerService {

    static final String CMD_PLAY = "play";
    static final String CMD_PREV = "prev";
    static final String CMD_NEXT = "next";

    /** @return true if a player answered the command. */
    static boolean sendMedia(Context ctx, String cmd) {
        try {
            MediaSessionManager msm = ctx.getSystemService(MediaSessionManager.class);
            List<MediaController> sessions = msm.getActiveSessions(new ComponentName(ctx, BudsListener.class));
            if (sessions == null || sessions.isEmpty()) return false;
            MediaController mc = sessions.get(0);
            MediaController.TransportControls tc = mc.getTransportControls();
            switch (cmd) {
                case CMD_PREV: tc.skipToPrevious(); break;
                case CMD_NEXT: tc.skipToNext(); break;
                default:
                    PlaybackState ps = mc.getPlaybackState();
                    boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                    if (playing) tc.pause(); else tc.play();
                    break;
            }
            return true;
        } catch (SecurityException e) {
            return false; // access not granted yet
        }
    }

    static boolean hasAccess(Context ctx) {
        String flat = android.provider.Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(new ComponentName(ctx, BudsListener.class).flattenToString());
    }

    @Override
    public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) { }
}
