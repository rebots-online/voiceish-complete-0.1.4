package com.voiceish.sim;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class SmsStore extends SQLiteOpenHelper {
    private static SmsStore instance;
    static synchronized SmsStore get(Context context) {
        if (instance == null) instance = new SmsStore(context.getApplicationContext());
        return instance;
    }
    private SmsStore(Context context) { super(context, "sms-receipts.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sends (id TEXT PRIMARY KEY, recipient TEXT NOT NULL, body TEXT NOT NULL, created INTEGER NOT NULL, state TEXT NOT NULL, subscription_id INTEGER NOT NULL, part_count INTEGER NOT NULL, token TEXT NOT NULL, error TEXT)");
        db.execSQL("CREATE TABLE parts (send_id TEXT NOT NULL, part_index INTEGER NOT NULL, result INTEGER, PRIMARY KEY(send_id,part_index))");
        db.execSQL("CREATE INDEX sends_created ON sends(created DESC)");
        db.execSQL("CREATE TABLE requests (id TEXT PRIMARY KEY, accepted INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Unsupported receipt schema upgrade"); }
    synchronized void rememberRequest(String id, long now) throws BridgeException {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("requests", "accepted<?", new String[]{Long.toString(now - 180000)});
            if (DatabaseUtils.queryNumEntries(db, "requests", "id=?", new String[]{id}) != 0)
                throw new BridgeException("REPLAY_REJECTED", "This request has already been used. Reconnect and try again.");
            if (DatabaseUtils.queryNumEntries(db, "requests") >= 10000)
                throw new BridgeException("BUSY", "Too many recent requests. Wait a few minutes and reconnect.");
            ContentValues values = new ContentValues(); values.put("id", id); values.put("accepted", now);
            db.insertOrThrow("requests", null, values); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized JSONObject existing(String id, String to, String body, int subId) throws Exception {
        expirePending();
        try (Cursor cursor = getReadableDatabase().query("sends", null, "id=?", new String[]{id}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            if (!cursor.getString(cursor.getColumnIndexOrThrow("recipient")).equals(to)
                || !cursor.getString(cursor.getColumnIndexOrThrow("body")).equals(body)
                || cursor.getInt(cursor.getColumnIndexOrThrow("subscription_id")) != subId)
                throw new BridgeException("SEND_ID_CONFLICT", "This send ID already belongs to a different message. Nothing was sent.");
            return result(id, cursor.getString(cursor.getColumnIndexOrThrow("state")));
        }
    }
    synchronized void reserve(String id, String to, String body, int subId, int count, String token) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("id", id); values.put("recipient", to); values.put("body", body);
            values.put("created", System.currentTimeMillis()); values.put("state", "sending");
            values.put("subscription_id", subId); values.put("part_count", count); values.put("token", token);
            db.insertOrThrow("sends", null, values);
            for (int part = 0; part < count; part++) {
                ContentValues segment = new ContentValues(); segment.put("send_id", id); segment.put("part_index", part);
                db.insertOrThrow("parts", null, segment);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void markUnknown(String id, String message) {
        ContentValues values = new ContentValues(); values.put("state", "unknown"); values.put("error", message);
        getWritableDatabase().update("sends", values, "id=? AND state='sending'", new String[]{id});
    }
    synchronized void markUnsubmitted(String id, String message) {
        ContentValues values = new ContentValues(); values.put("state", "failed"); values.put("error", "Not submitted: " + message);
        getWritableDatabase().update("sends", values, "id=? AND state='sending'", new String[]{id});
    }
    synchronized void markInterrupted() {
        ContentValues values = new ContentValues(); values.put("state", "unknown");
        values.put("error", "The bridge restarted before all send confirmations arrived. Check your phone inbox before sending again.");
        getWritableDatabase().update("sends", values, "state='sending'", null);
    }
    private void expirePending() {
        ContentValues values = new ContentValues(); values.put("state", "unknown");
        values.put("error", "Send confirmation timed out. Check your phone inbox before sending again; automatic retry is disabled.");
        getWritableDatabase().update("sends", values, "state='sending' AND created<?", new String[]{Long.toString(System.currentTimeMillis() - 120000)});
    }
    synchronized void completePart(String id, int index, String token, int result) {
        if (id == null || token == null || index < 0) return;
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            int total;
            try (Cursor cursor = db.query("sends", new String[]{"token", "part_count"}, "id=?", new String[]{id}, null, null, null)) {
                if (!cursor.moveToFirst() || !token.equals(cursor.getString(0))) return;
                total = cursor.getInt(1);
                if (index >= total) return;
            }
            ContentValues part = new ContentValues(); part.put("result", result);
            db.update("parts", part, "send_id=? AND part_index=? AND result IS NULL", new String[]{id, Integer.toString(index)});
            int completed = 0, failed = 0;
            try (Cursor cursor = db.rawQuery("SELECT result FROM parts WHERE send_id=? AND result IS NOT NULL", new String[]{id})) {
                while (cursor.moveToNext()) { completed++; if (cursor.getInt(0) != android.app.Activity.RESULT_OK) failed++; }
            }
            ContentValues values = new ContentValues();
            if (failed > 0) {
                values.put("state", "failed");
                values.put("error", "Android could not send one or more SMS parts. Some parts may have sent; check your phone before resending.");
                db.update("sends", values, "id=?", new String[]{id});
            } else if (completed == total) {
                values.put("state", "sent"); values.putNull("error");
                db.update("sends", values, "id=?", new String[]{id});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized JSONArray recent(int subscriptionId) throws JSONException {
        expirePending(); JSONArray items = new JSONArray();
        try (Cursor cursor = getReadableDatabase().query("sends", null, "subscription_id=?", new String[]{Integer.toString(subscriptionId)}, null, null, "created DESC", "100")) {
            while (cursor.moveToNext()) {
                JSONObject item = new JSONObject().put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                    .put("to", cursor.getString(cursor.getColumnIndexOrThrow("recipient")))
                    .put("body", cursor.getString(cursor.getColumnIndexOrThrow("body")))
                    .put("date", cursor.getLong(cursor.getColumnIndexOrThrow("created")))
                    .put("status", cursor.getString(cursor.getColumnIndexOrThrow("state")))
                    .put("subscriptionId", cursor.getInt(cursor.getColumnIndexOrThrow("subscription_id")));
                String error = cursor.getString(cursor.getColumnIndexOrThrow("error"));
                if (error != null) item.put("error", error);
                items.put(item);
            }
        }
        return items;
    }
    static JSONObject result(String id, String status) throws JSONException { return new JSONObject().put("sendId", id).put("status", status); }
}
