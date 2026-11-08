package com.techbroz.budspop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
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
import android.os.ParcelUuid;

import java.util.HashMap;
import java.util.Map;

/**
 * Foreground service: listens for earbuds connecting, fires the pop-up, and keeps the
 * battery + controls notification up to date while they stay connected.
 *
 * v1.1: the receiver is registered with an explicit export flag (Android 14 throws without it,
 * which is why v1.0 never saw a connect on newer phones); A2DP and headset profile events are
 * used as a second route next to ACL events; earbuds that were already connected when the
 * service starts show as a state update; the notification shows one "Battery" value when that
 * is all the earbuds report.
 */
public class BudsService extends Service {
    static final String CHANNEL_STATUS = "budspop_status";
    static final int NOTIFY_ID = 1001;
    private static final long DEBOUNCE_MS = 15_000;
    private static final long GATT_DELAY_MS = 3_000;
    private static final long GATT_REFRESH_MS = 5 * 60_000;

    /** Facts for the Status section of the app (same process). */
    static volatile boolean running = false;
    static volatile String lastEvent = "none yet";
    static volatile long lastEventAt = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastPop = new HashMap<>();
    private BluetoothDevice connected;
    private String connectedName = "";
    private boolean receiversOn = false;

    private final Runnable batteryTick = new Runnable() {
        @Override
        public void run() {
            if (connected == null) return;
            readBattery(connected);
            handler.postDelayed(this, GATT_REFRESH_MS);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (BatteryProbe.handleBroadcast(intent)) {
                note("battery reading (" + BatteryProbe.source + ")");
                refreshNotification();
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            note(shortName(action) + (state >= 0 ? " (state " + state + ")" : "") + " " + nameOf(device));
            switch (action) {
                case BluetoothDevice.ACTION_ACL_CONNECTED:
                    onConnected(device, false);
                    break;
                case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        onConnected(device, true);
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
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

    static void note(String what) {
        lastEvent = what;
        lastEventAt = System.currentTimeMillis();
    }

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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFY_ID, n);
            }
        } catch (RuntimeException e) {
            // Bluetooth access not granted yet (Android 12+ needs it for a connected-device service)
            note("could not start: " + e.getClass().getSimpleName() + " (give Bluetooth access)");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!receiversOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            f.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            f.addAction(BatteryProbe.ACTION_SYSTEM_BATTERY);
            f.addAction(BatteryProbe.ACTION_VENDOR_EVENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, f);
            }
            receiversOn = true;
        }
        running = true;
        Prefs.setEnabled(this, true);
        scanConnected();
        refreshNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiversOn) {
            try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
            receiversOn = false;
        }
        running = false;
        handler.removeCallbacksAndMessages(null);
        BatteryProbe.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** True if the user's device choice allows this device ("any audio device" when none chosen). */
    static boolean wantedDevice(Context ctx, BluetoothDevice d) {
        if (d == null) return false;
        String wanted = Prefs.deviceAddress(ctx);
        return wanted.isEmpty() || wanted.equalsIgnoreCase(d.getAddress());
    }

    /** ACL connects come for every Bluetooth device; only audio-looking ones count. */
    static boolean looksLikeAudio(BluetoothDevice d) {
        try {
            BluetoothClass bc = d.getBluetoothClass();
            if (bc != null && bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) return true;
            ParcelUuid[] uuids = d.getUuids();
            if (uuids != null) {
                for (ParcelUuid u : uuids) {
                    String s = u.toString().toLowerCase(java.util.Locale.ROOT);
                    if (s.startsWith("0000110b") || s.startsWith("0000110a") || s.startsWith("00001108")
                            || s.startsWith("0000111e") || s.startsWith("0000110e")) {
                        return true;
                    }
                }
            }
            String n = nameOf(d).toLowerCase(java.util.Locale.ROOT);
            return n.contains("buds") || n.contains("airdopes") || n.contains("boat") || n.contains("earbud")
                    || n.contains("earphone") || n.contains("headphone") || n.contains("headset")
                    || n.contains("tws") || n.contains("pods");
        } catch (SecurityException e) {
            return false;
        }
    }

    private static String nameOf(BluetoothDevice d) {
        if (d == null) return "";
        try {
            String n = d.getName();
            return n == null ? "" : n;
        } catch (SecurityException e) {
            return "";
        }
    }

    private static String shortName(String action) {
        int dot = action.lastIndexOf('.');
        return dot >= 0 ? action.substring(dot + 1) : action;
    }

    private void onConnected(BluetoothDevice device, boolean audioProfile) {
        if (device == null) return;
        if (!audioProfile && !looksLikeAudio(device)) return;
        if (!wantedDevice(this, device)) return;
        adopt(device);
        long now = System.currentTimeMillis();
        Long last = lastPop.get(device.getAddress());
        if (last == null || now - last > DEBOUNCE_MS) {
            lastPop.put(device.getAddress(), now);
            OverlayPresenter.show(this, connectedName, Prefs.theme(this), Prefs.sound(this));
        }
    }

    /** Makes this device the connected one: notification and battery follow it. No pop-up here. */
    private void adopt(BluetoothDevice device) {
        boolean same = connected != null && connected.getAddress().equals(device.getAddress());
        connected = device;
        connectedName = nameOf(device);
        if (!same) BatteryProbe.clear();
        handler.removeCallbacks(batteryTick);
        refreshNotification();
        handler.postDelayed(batteryTick, GATT_DELAY_MS);
    }

    /** Earbuds already connected when the service starts: a state update, not a pop-up. */
    private void scanConnected() {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) return;
        int[] profiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
        for (int profile : profiles) {
            try {
                ad.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int p, BluetoothProfile proxy) {
                        try {
                            for (BluetoothDevice d : proxy.getConnectedDevices()) {
                                if (connected == null && wantedDevice(BudsService.this, d)) {
                                    note("already connected when BudsPop started: " + nameOf(d));
                                    adopt(d);
                                    break;
                                }
                            }
                        } catch (SecurityException ignored) {
                            note("Bluetooth access missing: cannot read connected earbuds");
                        } finally {
                            ad.closeProfileProxy(p, proxy);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int p) { }
                }, profile);
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
    }

    private void onDisconnected(BluetoothDevice device) {
        if (device == null) return;
        lastPop.remove(device.getAddress()); // a reconnect after this may pop again
        if (connected == null || !connected.getAddress().equals(device.getAddress())) return;
        connected = null;
        connectedName = "";
        handler.removeCallbacks(batteryTick);
        BatteryProbe.clear();
        refreshNotification();
    }

    private void readBattery(BluetoothDevice d) {
        BatteryProbe.readReflection(d);
        refreshNotification();
        BatteryProbe.readGatt(this, d, () -> handler.post(this::refreshNotification));
    }

    void refreshNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFY_ID, buildNotification());
    }

    private static String pct(Integer v) {
        return v == null ? "--" : (v + "%");
    }

    private static String sourceWords() {
        switch (BatteryProbe.source) {
            case "gatt": return "the earbuds' battery service";
            case "headset": return "the headset's report";
            case "device": return "the phone's Bluetooth";
            case "phone": return "the phone's battery broadcast";
            default: return "the earbuds";
        }
    }

    private PendingIntent broadcast(String action, int code) {
        Intent i = new Intent(this, ControlReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    Notification buildNotification() {
        boolean on = connected != null;
        boolean showCase = Prefs.showCase(this);
        String title = on
                ? "🎧 " + (connectedName.isEmpty() ? "Earbuds" : connectedName) + " connected"
                : "BudsPop: waiting for earbuds";
        boolean perBud = BatteryProbe.left != null || BatteryProbe.right != null
                || (showCase && BatteryProbe.caseLevel != null);
        String body;
        if (!on) {
            body = "Connect your earbuds to see battery";
        } else if (perBud) {
            body = "Left " + pct(BatteryProbe.left) + "  ·  Right " + pct(BatteryProbe.right);
            if (showCase) body += "  ·  Case " + pct(BatteryProbe.caseLevel);
        } else if (BatteryProbe.combined != null) {
            body = "Battery " + BatteryProbe.combined + "%";
        } else {
            body = "Battery: not reported by these earbuds";
        }
        String small;
        if (!on) {
            small = "Waiting for a connection";
        } else if (BatteryProbe.combined != null) {
            small = "From " + sourceWords() + (perBud ? "" : "; left, right and case not reported");
        } else {
            small = "Left, right and case not reported by these earbuds (shown as --)";
        }

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_STATUS)
                : new Notification.Builder(this);
        Intent open = new Intent(this, MainActivity.class);
        b.setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle(title)
                .setContentText(body)
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
