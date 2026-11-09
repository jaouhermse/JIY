package de.jiy.family;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Plant die Erinnerungen nach einem Neustart des Handys neu. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Scheduler.restore(context);
            if (AppGuard.isOn(context) && AppGuard.hasUsage(context)) AppGuard.start(context);
        }
    }
}
