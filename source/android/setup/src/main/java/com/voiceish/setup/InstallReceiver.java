package com.voiceish.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;

/** Only the explicit PendingIntent handed to Android can deliver installation results. */
public final class InstallReceiver extends BroadcastReceiver {
    static volatile Intent confirmation;
    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences state = context.getSharedPreferences("setup", Context.MODE_PRIVATE);
        int expected = state.getInt("session", -1);
        int received = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -2);
        if (expected < 0 || received != expected) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message;
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            message = "Confirm the Android installation prompt. Keep Voiceish Setup open until it finishes.";
        } else {
            confirmation = null;
            message = status == PackageInstaller.STATUS_SUCCESS
                ? "Voiceish SIM installed. Open it below, then allow SMS and phone access."
                : "Android did not complete installation (code " + status + "). "
                    + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        }
        SharedPreferences.Editor edit = state.edit().putInt("status", status).putString("message", message);
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) edit.remove("session");
        edit.commit();
        MainActivity activity = MainActivity.visible.get();
        if (activity != null) activity.runOnUiThread(activity::refresh);
    }
}
