package com.techbroz.budspop;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Everything the user can change, kept on the phone only. */
final class Prefs {
    private static final String FILE = "budspop";
    static final String KEY_THEME = "theme";
    static final String KEY_DEVICE = "device_address";
    static final String KEY_DEVICE_NAME = "device_name";
    static final String KEY_SOUND = "sound";
    static final String KEY_CASE = "show_case";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_USER_STOPPED = "user_stopped";
    static final String KEY_MEDIA_URI = "media_uri";
    static final String KEY_MEDIA_MIME = "media_mime";
    static final String KEY_MEDIA_NAME = "media_name";
    static final String KEY_MEDIA_SOUND = "media_sound";
    static final String KEY_V11 = "v11_defaults";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** v1.1 makes the fight-anime theme the default; this runs the switch once for existing installs. */
    static int theme(Context c) {
        SharedPreferences s = sp(c);
        if (!s.getBoolean(KEY_V11, false)) {
            s.edit().putInt(KEY_THEME, BudsScene.THEME_SAIYAN_RUSH).putBoolean(KEY_V11, true).apply();
        }
        return s.getInt(KEY_THEME, BudsScene.THEME_SAIYAN_RUSH);
    }

    static void setTheme(Context c, int t) { sp(c).edit().putInt(KEY_THEME, t).apply(); }

    /** Empty string means "any audio device". */
    static String deviceAddress(Context c) { return sp(c).getString(KEY_DEVICE, ""); }
    static String deviceName(Context c) { return sp(c).getString(KEY_DEVICE_NAME, ""); }
    static void setDevice(Context c, String address, String name) {
        sp(c).edit().putString(KEY_DEVICE, address).putString(KEY_DEVICE_NAME, name).apply();
    }

    static boolean sound(Context c) { return sp(c).getBoolean(KEY_SOUND, true); }
    static void setSound(Context c, boolean on) { sp(c).edit().putBoolean(KEY_SOUND, on).apply(); }

    static boolean enabled(Context c) { return sp(c).getBoolean(KEY_ENABLED, false); }
    static void setEnabled(Context c, boolean on) { sp(c).edit().putBoolean(KEY_ENABLED, on).apply(); }

    /** True once the user pressed Stop, so the app does not start the service again on its own. */
    static boolean userStopped(Context c) { return sp(c).getBoolean(KEY_USER_STOPPED, false); }
    static void setUserStopped(Context c, boolean on) { sp(c).edit().putBoolean(KEY_USER_STOPPED, on).apply(); }

    static boolean showCase(Context c) { return sp(c).getBoolean(KEY_CASE, true); }
    static void setShowCase(Context c, boolean on) { sp(c).edit().putBoolean(KEY_CASE, on).apply(); }

    static String mediaUri(Context c) { return sp(c).getString(KEY_MEDIA_URI, ""); }
    static String mediaMime(Context c) { return sp(c).getString(KEY_MEDIA_MIME, ""); }
    static String mediaName(Context c) { return sp(c).getString(KEY_MEDIA_NAME, ""); }
    static boolean mediaSound(Context c) { return sp(c).getBoolean(KEY_MEDIA_SOUND, false); }
    static void setMediaSound(Context c, boolean on) { sp(c).edit().putBoolean(KEY_MEDIA_SOUND, on).apply(); }

    static void setMedia(Context c, String uri, String mime, String name) {
        String m = mime == null || mime.isEmpty() ? guessMime(name) : mime;
        sp(c).edit().putString(KEY_MEDIA_URI, uri).putString(KEY_MEDIA_MIME, m).putString(KEY_MEDIA_NAME, name).apply();
    }

    private static String guessMime(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        if (n.endsWith(".mp4") || n.endsWith(".3gp") || n.endsWith(".webm") || n.endsWith(".mkv") || n.endsWith(".mov")) {
            return "video/mp4";
        }
        if (n.endsWith(".gif")) return "image/gif";
        return "image/*";
    }
}
