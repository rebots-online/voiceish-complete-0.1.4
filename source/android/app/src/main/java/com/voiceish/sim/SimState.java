package com.voiceish.sim;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

final class SimState {
    final int id;
    final String label;
    SimState(int id, String label) { this.id = id; this.label = label; }
    static boolean granted(Context context, String permission) { return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE};
    static String missingPermissions(Context context) {
        String[] labels = {"read SMS", "send SMS", "phone / SIM status"};
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < REQUIRED_PERMISSIONS.length; i++) {
            if (!granted(context, REQUIRED_PERMISSIONS[i])) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(labels[i]);
            }
        }
        return missing.toString();
    }
    static void requirePermissions(Context context) throws BridgeException {
        String missing = missingPermissions(context);
        if (!missing.isEmpty()) throw new BridgeException("PERMISSION_DENIED", "Android has not granted: " + missing + ". Open Voiceish SIM app settings → Permissions.");
    }
    static SimState resolve(Context context) throws BridgeException {
        requirePermissions(context);
        int id = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (!SubscriptionManager.isValidSubscriptionId(id))
            throw new BridgeException("NO_DEFAULT_SIM", "Choose a default SMS SIM in Android Settings, then start the bridge.");
        SubscriptionInfo info = context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfo(id);
        if (info == null) throw new BridgeException("SIM_INACTIVE", "The default SMS SIM is inactive. Check your SIM settings.");
        return new SimState(id, info.getDisplayName() + " · SIM " + (info.getSimSlotIndex() + 1));
    }
    void requireReady(Context context) throws BridgeException {
        requirePermissions(context);
        if (SubscriptionManager.getDefaultSmsSubscriptionId() != id)
            throw new BridgeException("DEFAULT_SIM_CHANGED", "The default SMS SIM changed. Stop and restart the bridge on your phone to select it.");
        if (context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfo(id) == null)
            throw new BridgeException("SIM_INACTIVE", "The pinned SMS SIM is inactive. Restore it, or stop and restart the bridge after selecting a new default.");
    }
}
