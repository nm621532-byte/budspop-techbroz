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
import android.provider.Settings;
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
        render();
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

        addHeader("4. Test");
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
}
