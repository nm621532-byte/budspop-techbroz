package com.techbroz.budspop;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.database.Cursor;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One screen: setup steps (permissions), start/stop, settings, and a Test pop-up button.
 */
public class MainActivity extends Activity {
    private static final int REQ_PERMS = 42;
    private static final int REQ_PICK_MEDIA = 77;

    private LinearLayout root;
    private TextView statusLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureRunning();
        render();
    }

    /** Starts the service when the app is opened, unless the user pressed Stop. */
    private void ensureRunning() {
        if (Prefs.userStopped(this) || BudsService.running || needsBtPerm()) return;
        try {
            Intent i = new Intent(this, BudsService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (RuntimeException ignored) {
            // the status section says what is missing
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void render() {
        root.removeAllViews();
        addText("BudsPop", 28, true, Color.BLACK);
        addText("Earbuds connect hote hi anime pop-up, and battery + controls in your notification bar.", 14, false, 0xFF555555);

        addHeader("1. Setup (one time)");
        addStep("Bluetooth access", needsBtPerm() ? "Needed" : "Done", needsBtPerm(), () -> requestBt());
        addStep("Notifications", needsNotifPerm() ? "Needed" : "Done", needsNotifPerm(), () -> requestNotif());
        addStep("Show over other apps (pop-up)", Settings.canDrawOverlays(this) ? "Done" : "Optional, needed for the pop-up",
                !Settings.canDrawOverlays(this), () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        addStep("Keep running in background", isIgnoringBattery() ? "Done" : "Recommended",
                !isIgnoringBattery(), () -> {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                });
        boolean listener = BudsListener.hasAccess(this);
        addStep("Media controls access (play/pause/next)", listener ? "Done" : "Optional",
                !listener, () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        addHeader("2. Start");
        boolean on = Prefs.enabled(this);
        addButton(on ? "Stop BudsPop" : "Start BudsPop", () -> toggleService(!on));
        addText(on ? "BudsPop is running. Connect your earbuds to see it."
                : "Not running. Tap Start, then connect your earbuds.", 13, false, 0xFF555555);

        addHeader("3. Settings");
        addButton("Theme: " + BudsScene.THEME_NAMES[Prefs.theme(this)], () -> {
            Prefs.setTheme(this, (Prefs.theme(this) + 1) % BudsScene.THEME_NAMES.length);
            render();
        });
        String dev = Prefs.deviceAddress(this).isEmpty() ? "Any audio device" : Prefs.deviceName(this);
        addButton("Trigger device: " + dev, () -> pickDevice());
        addButton("Sound & vibration: " + (Prefs.sound(this) ? "On" : "Off"), () -> {
            Prefs.setSound(this, !Prefs.sound(this));
            render();
        });
        addButton("Show case battery: " + (Prefs.showCase(this) ? "On" : "Off"), () -> {
            Prefs.setShowCase(this, !Prefs.showCase(this));
            if (on) startService(new Intent(this, BudsService.class));
            render();
        });

        addButton("Pick my GIF / video for the pop-up", this::pickMedia);
        String chosen = Prefs.mediaName(this);
        addText("My Animation plays your own file: "
                + (chosen.isEmpty() ? "none chosen yet (until then, Saiyan Rush plays)" : chosen), 12, false, 0xFF555555);
        addButton("My Animation sound: " + (Prefs.mediaSound(this) ? "On" : "Off"), () -> {
            Prefs.setMediaSound(this, !Prefs.mediaSound(this));
            render();
        });

        addHeader("4. Status");
        renderStatus();
        addButton("Refresh status", () -> render());

        addHeader("5. Test");
        addButton("Test pop-up", () -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission off: showing as a notification instead", Toast.LENGTH_SHORT).show();
            }
            OverlayPresenter.show(this, "Test earbuds", Prefs.theme(this), Prefs.sound(this));
        });
        addText("Battery honesty: most earbuds send only one combined level to Android. Left, right and case show \"--\" unless the earbuds report that exact value. boAt's own ANC/EQ controls are not available to apps.", 12, false, 0xFF777777);
    }

    private void addHeader(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF222222);
        t.setPadding(0, dp(22), 0, dp(6));
        root.addView(t);
    }

    private void addText(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        root.addView(t);
    }

    private void addButton(String text, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        root.addView(b, lp);
    }

    private void addStep(String title, String status, boolean needed, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundColor(needed ? 0xFFFFF4E5 : 0xFFEAF7EE);
        TextView t = new TextView(this);
        t.setText((needed ? "⬜ " : "✅ ") + title);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF222222);
        row.addView(t);
        TextView s = new TextView(this);
        s.setText(status);
        s.setTextSize(12);
        s.setTextColor(0xFF555555);
        row.addView(s);
        if (needed) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText("Open / allow");
            b.setOnClickListener(v -> action.run());
            row.addView(b);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        root.addView(row, lp);
    }

    private boolean needsBtPerm() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
    }

    private boolean needsNotifPerm() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private boolean isIgnoringBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBt() {
        List<String> need = new ArrayList<>();
        if (needsBtPerm()) need.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (needsNotifPerm()) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    private void requestNotif() {
        requestBt();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }

    private void toggleService(boolean start) {
        if (start) {
            Prefs.setUserStopped(this, false);
            if (needsBtPerm()) { requestBt(); return; }
            try {
                Intent i = new Intent(this, BudsService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
                else startService(i);
            } catch (RuntimeException e) {
                Toast.makeText(this, "Could not start: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            stopService(new Intent(this, BudsService.class));
            Prefs.setEnabled(this, false);
            Prefs.setUserStopped(this, true);
        }
        render();
    }

    private void pickDevice() {
        if (needsBtPerm()) { requestBt(); return; }
        final List<String> labels = new ArrayList<>();
        final List<String> addrs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        labels.add("Any audio device (default)");
        addrs.add("");
        names.add("");
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad != null) {
            try {
                Set<BluetoothDevice> bonded = ad.getBondedDevices();
                for (BluetoothDevice d : bonded) {
                    String n = d.getName() == null ? d.getAddress() : d.getName();
                    labels.add(n + "  (" + d.getAddress() + ")");
                    addrs.add(d.getAddress());
                    names.add(n);
                }
            } catch (SecurityException ignored) { }
        }
        new AlertDialog.Builder(this)
                .setTitle("Which earbuds should trigger the pop-up?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    Prefs.setDevice(this, addrs.get(which), names.get(which));
                    render();
                })
                .show();
    }

    private void renderStatus() {
        addText("Service running: " + (BudsService.running ? "yes" : "no")
                + (Prefs.userStopped(this) ? " (stopped by you)" : ""), 13, false, 0xFF333333);
        addText("Bluetooth access: " + (needsBtPerm() ? "MISSING (allow it in step 1)" : "granted"), 13, false, 0xFF333333);
        addText("Notifications: " + (needsNotifPerm() ? "MISSING" : "granted"), 13, false, 0xFF333333);
        addText("Show over other apps: " + (Settings.canDrawOverlays(this)
                ? "granted"
                : "MISSING (you get a notification and a toast instead of the full pop-up)"), 13, false, 0xFF333333);
        addText("Battery exemption: " + (isIgnoringBattery() ? "granted" : "not granted"), 13, false, 0xFF333333);
        addText("Last Bluetooth event: " + BudsService.lastEvent
                + (BudsService.lastEventAt > 0 ? " at " + new SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new Date(BudsService.lastEventAt)) : ""),
                13, false, 0xFF333333);
        addText("Last battery reading source: " + (BatteryProbe.source.isEmpty() ? "none yet" : BatteryProbe.source), 13, false, 0xFF333333);
        String battery;
        if (BatteryProbe.left == null && BatteryProbe.right == null && BatteryProbe.caseLevel == null && BatteryProbe.combined == null) {
            battery = "not reported yet";
        } else {
            battery = (BatteryProbe.combined != null ? "Battery " + BatteryProbe.combined + "%  " : "")
                    + "Left " + (BatteryProbe.left == null ? "--" : BatteryProbe.left + "%")
                    + "  Right " + (BatteryProbe.right == null ? "--" : BatteryProbe.right + "%")
                    + "  Case " + (BatteryProbe.caseLevel == null ? "--" : BatteryProbe.caseLevel + "%");
        }
        addText("Battery now: " + battery, 13, false, 0xFF333333);
    }

    private void pickMedia() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/gif", "image/*", "video/*"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_PICK_MEDIA);
        } catch (RuntimeException e) {
            Toast.makeText(this, "No file picker found on this phone", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_MEDIA || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // this file's app did not allow a lasting grant; it works until the phone restarts
        }
        String mime = getContentResolver().getType(u);
        String name = displayName(u);
        Prefs.setMedia(this, u.toString(), mime == null ? "" : mime, name);
        Prefs.setTheme(this, BudsScene.THEME_MY_ANIMATION);
        Toast.makeText(this, "Saved. Theme set to My Animation.", Toast.LENGTH_SHORT).show();
        render();
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (RuntimeException ignored) { }
        String last = u.getLastPathSegment();
        return last == null ? "my file" : last;
    }
}
