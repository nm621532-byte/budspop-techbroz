package com.techbroz.budspop;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;

import java.util.UUID;

/**
 * Battery readings, from the only routes a normal Android app can use. Each one reports what it
 * has; nothing is estimated. Left bud, right bud and case stay null unless a route gives that
 * exact value, and the notification prints "--" for them.
 *
 *  1. System battery broadcast (BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED): one number, the
 *     phone's own view of the headset battery. Android 13+ delivers it for many headsets.
 *  2. Apple-style HFP vendor event (AT+IPHONEACCEV battery indicator, 0-9 scale): one number.
 *     The phone's Bluetooth stack forwards it on some devices only.
 *  3. Standard Battery Service (GATT 0x180F / 0x2A19): one number per characteristic. Only
 *     BLE-capable earbuds expose it.
 */
final class BatteryProbe {
    static final String ACTION_SYSTEM_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    static final String EXTRA_SYSTEM_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL";
    static final String ACTION_VENDOR_EVENT = "android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT";
    static final String EXTRA_VENDOR_CMD = "android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_CMD";
    static final String EXTRA_VENDOR_ARGS = "android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_ARGS";

    private static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    /** Combined buds level in percent, or null when no route has reported one. */
    static volatile Integer combined = null;
    /** Which route gave the last combined value, for the notification's small print. */
    static volatile String source = "";
    static volatile Integer left = null;
    static volatile Integer right = null;
    static volatile Integer caseLevel = null;

    private BatteryProbe() {}

    static void clear() {
        combined = null;
        source = "";
        left = null;
        right = null;
        caseLevel = null;
    }

    static boolean handleBroadcast(Intent intent) {
        String action = intent.getAction();
        if (ACTION_SYSTEM_BATTERY.equals(action)) {
            int level = intent.getIntExtra(EXTRA_SYSTEM_BATTERY, -1);
            if (level >= 0 && level <= 100) {
                combined = level;
                source = "phone";
                return true;
            }
            return false;
        }
        if (ACTION_VENDOR_EVENT.equals(action)) {
            String cmd = intent.getStringExtra(EXTRA_VENDOR_CMD);
            if (cmd == null || !cmd.toUpperCase().contains("IPHONEACCEV")) return false;
            Object raw = intent.getExtras() == null ? null : intent.getExtras().get(EXTRA_VENDOR_ARGS);
            if (!(raw instanceof Object[])) return false;
            Object[] args = (Object[]) raw;
            // Layout: count, key, value, key, value ... ; key "1" is the battery indicator (0-9)
            for (int i = 1; i + 1 < args.length; i += 2) {
                if ("1".equals(String.valueOf(args[i]))) {
                    try {
                        int v = Integer.parseInt(String.valueOf(args[i + 1]).trim());
                        if (v >= 0 && v <= 9) {
                            combined = (v + 1) * 10;
                            source = "headset";
                            return true;
                        }
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        return false;
    }

    /** Reads the standard Battery Service over BLE, if the earbuds expose one. */
    static void readGatt(Context ctx, BluetoothDevice device, Runnable onDone) {
        try {
            device.connectGatt(ctx, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices();
                    } else {
                        gatt.close();
                        if (onDone != null) onDone.run();
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    BluetoothGattService s = gatt.getService(BATTERY_SERVICE);
                    BluetoothGattCharacteristic c = s == null ? null : s.getCharacteristic(BATTERY_LEVEL);
                    if (status != BluetoothGatt.GATT_SUCCESS || c == null || !gatt.readCharacteristic(c)) {
                        gatt.disconnect();
                    }
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic c, int status) {
                    byte[] v = c.getValue();
                    if (status == BluetoothGatt.GATT_SUCCESS && v != null && v.length > 0) {
                        int level = v[0] & 0xFF;
                        if (level <= 100) {
                            combined = level;
                            source = "gatt";
                        }
                    }
                    gatt.disconnect();
                }
            }, BluetoothDevice.TRANSPORT_AUTO);
        } catch (SecurityException | IllegalArgumentException e) {
            if (onDone != null) onDone.run();
        }
    }
}
