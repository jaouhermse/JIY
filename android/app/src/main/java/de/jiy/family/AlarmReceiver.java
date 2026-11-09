package de.jiy.family;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wird zur geplanten Zeit aufgerufen und zeigt die Erinnerung an. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id");
        Notifier.show(context, id == null ? 1 : id.hashCode(),
                intent.getStringExtra("title"), intent.getStringExtra("body"));
    }
}
