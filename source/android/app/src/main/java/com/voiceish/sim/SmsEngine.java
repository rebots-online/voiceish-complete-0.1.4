package com.voiceish.sim;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class SmsEngine {
    private final Context context;
    private final SimState sim;
    private final SmsStore store;
    private volatile boolean stopped;
    SmsEngine(Context context, SimState sim) {
        this.context = context; this.sim = sim; this.store = SmsStore.get(context);
        store.markInterrupted();
    }
    synchronized void stop() { stopped = true; }
    void rememberRequest(String id, long now) throws BridgeException { store.rememberRequest(id, now); }
    private void requireRunning() throws BridgeException {
        if (stopped) throw new BridgeException("BRIDGE_STOPPED", "The phone bridge has stopped. Start it on your phone to reconnect.");
    }
    JSONObject status() throws Exception {
        boolean ready = true; String reason = null;
        try { sim.requireReady(context); } catch (BridgeException error) { ready = false; reason = error.getMessage(); }
        JSONObject simJson = new JSONObject().put("id", sim.id).put("label", sim.label).put("ready", ready);
        if (reason != null) simJson.put("reason", reason);
        return new JSONObject().put("version", "0.1.4").put("device", Build.MANUFACTURER + " " + Build.MODEL)
            .put("sim", simJson).put("permissions", new JSONObject()
                .put("readSms", SimState.granted(context, Manifest.permission.READ_SMS))
                .put("sendSms", SimState.granted(context, Manifest.permission.SEND_SMS))
                .put("phoneState", SimState.granted(context, Manifest.permission.READ_PHONE_STATE)));
    }
    JSONObject messages(JSONObject params) throws Exception {
        requireRunning();
        sim.requireReady(context);
        int limit = Math.max(1, Math.min(500, params.optInt("limit", 300)));
        JSONArray messages = new JSONArray();
        int responseBytes = 0;
        String[] columns = {Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.SUBSCRIPTION_ID};
        String selection = Telephony.Sms.SUBSCRIPTION_ID + "=? AND " + Telephony.Sms.TYPE + " IN (1,2,4,5,6)";
        try (Cursor cursor = context.getContentResolver().query(Telephony.Sms.CONTENT_URI, columns, selection, new String[]{Integer.toString(sim.id)}, Telephony.Sms.DATE + " DESC")) {
            if (cursor == null) throw new BridgeException("INBOX_UNAVAILABLE", "Android did not provide the SMS inbox. Check permissions and try again.");
            while (messages.length() < limit && cursor.moveToNext()) {
                int type = cursor.getInt(4);
                JSONObject item = new JSONObject().put("id", "sms:" + cursor.getLong(0))
                    .put("address", cursor.isNull(1) ? "Unknown" : cursor.getString(1))
                    .put("body", cursor.isNull(2) ? "" : cursor.getString(2)).put("date", cursor.getLong(3))
                    .put("type", type == 1 ? "in" : "out")
                    .put("status", type == 1 ? "received" : type == 2 ? "sent" : type == 5 ? "failed" : "pending")
                    .put("subscriptionId", cursor.getInt(5));
                int bytes = item.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (responseBytes + bytes > 1000000) break;
                responseBytes += bytes;
                messages.put(item);
            }
        } catch (SecurityException exception) {
            throw new BridgeException("PERMISSION_DENIED", "SMS access was revoked. Grant SMS permission on your phone.");
        } catch (IllegalArgumentException exception) {
            throw new BridgeException("SIM_FILTER_UNSUPPORTED", "This phone's SMS provider cannot filter by SIM. Inbox access is unavailable to avoid mixing SIMs.");
        }
        return new JSONObject().put("messages", messages).put("sends", store.recent(sim.id));
    }
    synchronized JSONObject send(String requestId, JSONObject params) throws Exception {
        requireRunning();
        String id = params.optString("sendId", "");
        if (!Protocol.isUuid(id) || id.equals(requestId)) throw new BridgeException("INVALID_SEND_ID", "Use a unique send ID distinct from the request ID.");
        String to = params.optString("to", "").replaceAll("[\\s().-]", "");
        String body = params.optString("body", "");
        int subId = params.optInt("subscriptionId", -1);
        if (!to.matches("\\+?[0-9]{3,20}")) throw new BridgeException("INVALID_RECIPIENT", "Enter a phone number with digits and an optional leading +.");
        if (body.trim().isEmpty() || body.length() > 2000) throw new BridgeException("INVALID_BODY", "Write a message between 1 and 2,000 characters.");
        JSONObject previous = store.existing(id, to, body, subId);
        if (previous != null) return previous;
        sim.requireReady(context);
        if (subId != sim.id) throw new BridgeException("SIM_MISMATCH", "The requested SIM does not match the phone's pinned default SMS SIM.");
        SmsManager manager = Build.VERSION.SDK_INT >= 31
            ? context.getSystemService(SmsManager.class).createForSubscriptionId(sim.id)
            : SmsManager.getSmsManagerForSubscriptionId(sim.id);
        ArrayList<String> parts = manager.divideMessage(body);
        if (parts == null || parts.isEmpty()) throw new BridgeException("INVALID_BODY", "Android could not prepare this message.");
        String token = UUID.randomUUID().toString();
        ArrayList<PendingIntent> callbacks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Intent callback = new Intent(context, SmsSentReceiver.class).setAction("com.voiceish.sim.SMS_SENT")
                .setData(Uri.parse("voiceish-sms://sent/" + id + "/" + i))
                .putExtra("sendId", id).putExtra("part", i).putExtra("token", token);
            callbacks.add(PendingIntent.getBroadcast(context, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        // The durable reservation is committed before invoking the radio. Never retry this ID.
        store.reserve(id, to, body, subId, parts.size(), token);
        // A change caught before the radio call is a definite failure, with its actionable cause.
        try {
            sim.requireReady(context);
        } catch (BridgeException error) {
            store.markUnsubmitted(id, error.getMessage());
            throw error;
        }
        try {
            manager.sendMultipartTextMessage(to, null, parts, callbacks, null);
        } catch (Exception error) {
            store.markUnknown(id, "Submission could not be confirmed. Check your phone inbox before sending again; automatic retry is disabled.");
        }
        return store.existing(id, to, body, subId);
    }
}
