package com.techbroz.budspop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.media.session.MediaSessionManager;
import android.media.session.MediaController;

import java.util.HashMap;
import java.util.Map;

/**
 * Foreground service: listens for earbuds connecting, fires the pop-up, and keeps the
 * battery + controls notification up to date while they stay connected.
 */
public class BudsService extends Service {
    static final String CHANNEL_STATUS = "budspop_status";
    static final int NOTIFY_ID = 1001;
    private static final long DEBOUNCE_MS = 15_000;
    private static final long GATT_DELAY_MS = 3_000;
    private static final long GATT_REFRESH_MS = 5 * 60_000;
    private static final long GATT_TIMEOUT_MS = 15_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastPop = new HashMap<>();
    private BluetoothDevice connected;
    private String connectedName = "";
    private boolean receiversOn = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (BatteryProbe.handleBroadcast(intent)) {
                refreshNotification();
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action) {
                case BluetoothDevice.ACTION_ACL_CONNECTED:
                    onConnected(device);
                    break;
                case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                    if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
                        onConnected(device);
                    } else if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_DISCONNECTED) {
                        onDisconnected(device);
                    }
                    break;
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    onDisconnected(device);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_STATUS, "Buds status", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Battery and controls for connected earbuds");
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFY_ID, n);
        }
        if (!receiversOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            f.addAction(BatteryProbe.ACTION_SYSTEM_BATTERY);
            f.addAction(BatteryProbe.ACTION_VENDOR_EVENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, f);
            }
            receiversOn = true;
        }
        Prefs.setEnabled(this, true);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiversOn) {
            try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
            receiversOn = false;
        }
        handler.removeCallbacksAndMessages(null);
        BatteryProbe.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** True if this device should trigger the pop-up, per the user's device choice. */
    static boolean matches(Context ctx, BluetoothDevice d) {
        if (d == null) return false;
        String wanted = Prefs.deviceAddress(ctx);
        if (!wanted.isEmpty()) return wanted.equalsIgnoreCase(d.getAddress());
        try {
            BluetoothClass bc = d.getBluetoothClass();
            return bc != null && bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static String nameOf(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null ? "" : n;
        } catch (SecurityException e) {
            return "";
        }
    }

    private void onConnected(BluetoothDevice device) {
        if (!matches(this, device)) return;
        connected = device;
        connectedName = nameOf(device);
        BatteryProbe.clear();
        long now = System.currentTimeMillis();
        Long last = lastPop.get(device.getAddress());
        if (last == null || now - last > DEBOUNCE_MS) {
            lastPop.put(device.getAddress(), now);
            OverlayPresenter.show(this, connectedName, Prefs.theme(this), Prefs.sound(this));
        }
        refreshNotification();
        // Ask the earbuds for their battery a few seconds after they settle
        handler.postDelayed(this::requestBattery, GATT_DELAY_MS);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (connected != null && connected.getAddress().equals(device.getAddress())) {
                    requestBattery();
                    handler.postDelayed(this, GATT_REFRESH_MS);
                }
            }
        }, GATT_REFRESH_MS + GATT_DELAY_MS);
    }

    private void requestBattery() {
        if (connected == null) return;
        BluetoothDevice d = connected;
        BatteryProbe.readGatt(this, d, () -> handler.post(this::refreshNotification));
    }

    private void onDisconnected(BluetoothDevice device) {
        if (connected == null || device == null || !connected.getAddress().equals(device.getAddress())) return;
        connected = null;
        connectedName = "";
        BatteryProbe.clear();
        refreshNotification();
    }

    void refreshNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFY_ID, buildNotification());
    }

    private static String pct(Integer v) {
        return v == null ? "--" : (v + "%");
    }

    private PendingIntent broadcast(String action, int code) {
        Intent i = new Intent(this, ControlReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    Notification buildNotification() {
        boolean on = connected != null;
        String title = on
                ? "🎧 " + (connectedName.isEmpty() ? "Earbuds" : connectedName) + " connected"
                : "BudsPop: waiting for earbuds";
        StringBuilder body = new StringBuilder();
        body.append("Left ").append(on ? pct(BatteryProbe.left) : "--")
            .append("  ·  Right ").append(on ? pct(BatteryProbe.right) : "--");
        if (Prefs.showCase(this)) body.append("  ·  Case ").append(on ? pct(BatteryProbe.caseLevel) : "--");
        String small = on && BatteryProbe.combined != null
                ? "Buds: " + BatteryProbe.combined + "% (one value from " + ("gatt".equals(BatteryProbe.source) ? "the earbuds"
                    : "headset".equals(BatteryProbe.source) ? "the headset" : "the phone") + ")"
                : (on ? "Buds battery not reported by these earbuds" : "Connect your earbuds to see battery");

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_STATUS)
                : new Notification.Builder(this);
        Intent open = new Intent(this, MainActivity.class);
        b.setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle(title)
                .setContentText(body.toString())
                .setSubText(small)
                .setContentIntent(PendingIntent.getActivity(this, 0, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action(android.R.drawable.ic_media_previous, "Prev", broadcast(ControlReceiver.ACTION_PREV, 1)))
                .addAction(new Notification.Action(android.R.drawable.ic_media_play, "Play/Pause", broadcast(ControlReceiver.ACTION_PLAY, 2)))
                .addAction(new Notification.Action(android.R.drawable.ic_media_next, "Next", broadcast(ControlReceiver.ACTION_NEXT, 3)))
                .addAction(new Notification.Action(android.R.drawable.btn_minus, "Vol -", broadcast(ControlReceiver.ACTION_VOL_DOWN, 4)))
                .addAction(new Notification.Action(android.R.drawable.btn_plus, "Vol +", broadcast(ControlReceiver.ACTION_VOL_UP, 5)));
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        b.setStyle(style);
        return b.build();
    }
}
