package com.techbroz.budspop;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything the user can change, kept on the phone only. */
final class Prefs {
    private static final String FILE = "budspop";
    static final String KEY_THEME = "theme";
    static final String KEY_DEVICE = "device_address";
    static final String KEY_DEVICE_NAME = "device_name";
    static final String KEY_SOUND = "sound";
    static final String KEY_CASE = "show_case";
    static final String KEY_ENABLED = "enabled";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int theme(Context c) { return sp(c).getInt(KEY_THEME, BudsScene.THEME_SAKURA); }
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

    static boolean showCase(Context c) { return sp(c).getBoolean(KEY_CASE, true); }
    static void setShowCase(Context c, boolean on) { sp(c).edit().putBoolean(KEY_CASE, on).apply(); }
}
