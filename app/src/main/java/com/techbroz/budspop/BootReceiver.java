package com.techbroz.budspop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** After a reboot, bring BudsPop back if it was running before. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Prefs.enabled(ctx)) return;
        try {
            Intent s = new Intent(ctx, BudsService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(s);
            else ctx.startService(s);
        } catch (RuntimeException ignored) {
            // The system may refuse a foreground start this early; the user can open the app to start it.
        }
    }
}
