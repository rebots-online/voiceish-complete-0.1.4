package com.voiceish.sim;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(29, 112, 218), INK = Color.rgb(235, 243, 252), MUTED = Color.rgb(163, 183, 204);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, simText, permissions, pairing, networkText, permissionHeading, pairHeading;
    private Button start, stop, copy, share, reveal, allow, notificationAccess;
    private boolean pairingShown;
    private String pairingText = "";
    private final Runnable refresh = new Runnable() { @Override public void run() { updateState(); handler.postDelayed(this, 1500); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(4, 15, 27));
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(24), dp(30), dp(24), dp(32)); scroll.addView(layout);
        // Android 15 enforces edge-to-edge; keep all controls inside system bars.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView eyebrow = text("VOICEISH / YOUR PHONE. YOUR NUMBER.", 12, Color.rgb(255, 188, 50)); eyebrow.setLetterSpacing(.12f); layout.addView(eyebrow);
        TextView title = text("Voiceish SIM", 34, INK); title.setTypeface(null, Typeface.BOLD); layout.addView(title, spacing(0, 8));
        layout.addView(text("A little bridge for your everyday texts.", 17, MUTED), spacing(0, 22));
        LinearLayout card = card(); layout.addView(card, spacing(0, 18));
        status = text("Bridge stopped", 21, INK); status.setTypeface(null, Typeface.BOLD); card.addView(status);
        simText = text("", 15, MUTED); card.addView(simText, spacing(0, 10));
        networkText = text("", 14, MUTED); card.addView(networkText, spacing(0, 8));
        start = button("Start bridge", true); card.addView(start, spacing(0, 16));
        start.setOnClickListener(view -> startBridge());
        stop = button("Stop bridge", false); card.addView(stop, spacing(0, 6));
        stop.setOnClickListener(view -> { BridgeService.stopRunning(this); pairingShown = false; updateState(); });
        LinearLayout permissionCard = card(); layout.addView(permissionCard, spacing(0, 18));
        permissionHeading = heading("1  Allow phone access"); permissionCard.addView(permissionHeading);
        permissions = text("", 14, MUTED); permissionCard.addView(permissions, spacing(0, 10));
        allow = button("Grant permissions", true); permissionCard.addView(allow, spacing(0, 12)); allow.setOnClickListener(view -> { if (SimState.missingPermissions(this).isEmpty()) startBridge(); else requestAccess(); });
        notificationAccess = button("Allow notifications (optional)", false); permissionCard.addView(notificationAccess, spacing(0, 6));
        notificationAccess.setOnClickListener(view -> { if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11); });
        Button settings = button("Open app settings", false); permissionCard.addView(settings, spacing(0, 6)); settings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        Button diagnostics = button("Copy permission diagnostics", false); permissionCard.addView(diagnostics, spacing(0, 6)); diagnostics.setOnClickListener(view -> copyDiagnostics());
        LinearLayout pairCard = card(); layout.addView(pairCard, spacing(0, 18)); pairHeading = heading("2  Start bridge and pair browser"); pairCard.addView(pairHeading);
        pairCard.addView(text("Once step 1 says complete, tap Continue — start bridge. Keep the phone and computer on the same Wi-Fi, then copy the pairing text into the Voiceish website or Chrome extension.", 14, MUTED), spacing(0, 10));
        reveal = button("Show pairing text", false); pairCard.addView(reveal, spacing(0, 12)); reveal.setOnClickListener(view -> { pairingShown = !pairingShown; updateState(); });
        pairing = text("", 12, INK); pairing.setTypeface(Typeface.MONOSPACE); pairing.setTextIsSelectable(true); pairCard.addView(pairing, spacing(0, 10));
        copy = button("Copy pairing text", true); pairCard.addView(copy, spacing(0, 10)); copy.setOnClickListener(view -> copyPairing());
        share = button("Share pairing text…", false); pairCard.addView(share, spacing(0, 6)); share.setOnClickListener(view -> {
            if (pairingText.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, pairingText);
            startActivity(Intent.createChooser(intent, "Share your private pairing text"));
        });
        pairCard.addView(text("Pairing text is a password: anyone holding it on this network can read and send your SMS. Message content is encrypted between the phone and extension.", 12, MUTED), spacing(0, 12));
        Button rotate = button("Reset pairing & stop bridge", false); pairCard.addView(rotate, spacing(0, 12)); rotate.setOnClickListener(view -> {
            BridgeService.stopRunning(this);
            try { KeyStoreHelper.rotate(this); pairingShown = false; pairingText = ""; toast("Pairing reset. Pair your extension again."); }
            catch (Exception error) { toast("Could not reset pairing. Try restarting the app."); }
            updateState();
        });
        layout.addView(heading("Voiceish SIM 0.1.4"), spacing(0, 4));
        layout.addView(text("SMS through your default SIM only. Your usual phone messaging app stays in place. This first version does not handle MMS, attachments, RCS, calls, or remote networks. Your carrier's normal SMS charges apply.\n\nKeep the bridge running while texting from Chrome. If Android pauses its network connection, set Voiceish SIM battery use to Unrestricted in App settings. Stop the bridge when finished to save battery.", 13, MUTED), spacing(0, 12));
        setContentView(scroll); updateState();
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresh); pairingShown = false; pairing.setText(""); }
    private void startBridge() {
        try {
            SimState.resolve(this);
            if (localAddress().isEmpty()) { toast("Connect your phone to Wi-Fi or Ethernet first."); return; }
            startForegroundService(new Intent(this, BridgeService.class));
            handler.postDelayed(this::updateState, 250);
        } catch (BridgeException error) { toast(error.getMessage()); updateState(); }
        catch (SecurityException error) { BridgeService.lastError = "Android blocked bridge startup: " + error.getMessage(); updateState(); }
        catch (Exception error) { toast("Could not start the bridge. Check permissions in app settings."); }
    }
    private void requestAccess() {
        List<String> wanted = new ArrayList<>();
        for (String permission : SimState.REQUIRED_PERMISSIONS)
            if (!SimState.granted(this, permission)) wanted.add(permission);
        if (wanted.isEmpty()) { updateState(); return; }
        requestPermissions(wanted.toArray(new String[0]), 10);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] requested, int[] results) {
        super.onRequestPermissionsResult(requestCode, requested, results);
        updateState();
        if (requestCode == 10) {
            String missing = SimState.missingPermissions(this);
            toast(missing.isEmpty() ? "Phone access complete. Tap Continue — start bridge." : "Still missing: " + missing + ". See the permission details below.");
        }
    }
    private void copyDiagnostics() {
        StringBuilder report = new StringBuilder("Voiceish SIM 0.1.4 permission diagnostics\n");
        report.append("Android API: ").append(Build.VERSION.SDK_INT).append("\nDevice: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        for (String permission : SimState.REQUIRED_PERMISSIONS) {
            report.append(permission).append(": ").append(SimState.granted(this, permission) ? "GRANTED" : "DENIED")
                .append("; rationale=").append(shouldShowRequestPermissionRationale(permission)).append("\n");
        }
        report.append("Notifications: ").append(Build.VERSION.SDK_INT < 33 || SimState.granted(this, Manifest.permission.POST_NOTIFICATIONS) ? "GRANTED" : "DENIED (optional)")
            .append("\nBridge running: ").append(BridgeService.running).append("\nLocal network available: ").append(!localAddress().isEmpty());
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("Voiceish permission diagnostics", report.toString()));
        toast("Diagnostics copied. No messages, phone numbers or pairing keys included.");
    }
    private void updateState() {
        boolean active = BridgeService.running;
        status.setText(active ? "Bridge running" : "Bridge stopped"); status.setTextColor(active ? BLUE : INK);
        start.setEnabled(!active); stop.setEnabled(active);
        String sim;
        try {
            SimState current = SimState.resolve(this);
            sim = active ? "Pinned: " + BridgeService.simLabel + " · ID " + BridgeService.subscriptionId : "Default SMS SIM: " + current.label + " · ID " + current.id;
            if (active && current.id != BridgeService.subscriptionId) sim += "\nDefault SIM changed. Stop and restart the bridge before texting.";
        } catch (Exception error) { sim = error instanceof BridgeException ? error.getMessage() : "SIM details unavailable. Check phone permission."; }
        if (!BridgeService.lastError.isEmpty()) sim += "\n" + BridgeService.lastError;
        simText.setText(sim);
        boolean readSms = SimState.granted(this, Manifest.permission.READ_SMS);
        boolean sendSms = SimState.granted(this, Manifest.permission.SEND_SMS);
        boolean phone = SimState.granted(this, Manifest.permission.READ_PHONE_STATE);
        boolean ready = readSms && sendSms && phone;
        boolean notification = Build.VERSION.SDK_INT < 33 || SimState.granted(this, Manifest.permission.POST_NOTIFICATIONS);
        permissionHeading.setText(ready ? "1  Phone access complete" : "1  Allow phone access");
        allow.setText(ready ? (active ? "Phone access complete" : "Continue — start bridge") : "Grant missing permissions");
        allow.setEnabled(!active || !ready);
        notificationAccess.setVisibility(notification ? View.GONE : View.VISIBLE);
        pairHeading.setText(active ? "2  Ready to pair your browser" : "2  Start bridge and pair browser");
        String details = "Read SMS: " + (readSms ? "allowed" : "NOT granted")
            + "\nSend SMS: " + (sendSms ? "allowed" : "NOT granted")
            + "\nPhone / SIM status: " + (phone ? "allowed" : "NOT granted")
            + "\nNotifications: " + (notification ? "allowed" : "off (optional — does not block setup)");
        if (ready) details += active ? "\n\nPhone access is complete. Continue to step 2 to copy your pairing text." : "\n\nPhone access is complete. Tap Continue — start bridge below. Your normal messaging app stays the default.";
        else {
            details += "\n\nAndroid still reports the permissions above as not granted. Open app settings → Permissions and check SMS and Phone.";
            if (!readSms || !sendSms) details += "\nIf SMS access is blocked, look for App info → ⋮ → Allow restricted settings, if your phone offers it, then enable SMS. Some installers also restrict SMS access.";
            details += "\nIf Settings says Allowed but a line above remains NOT granted, copy the permission diagnostics so we can identify the exact permission.";
        }
        permissions.setText(details);
        String address = localAddress(); networkText.setText(address.isEmpty() ? "Connect to Wi-Fi or Ethernet to pair." : "Local address: http://" + address + ":8765");
        boolean pairable = active && !address.isEmpty();
        copy.setEnabled(pairable); share.setEnabled(pairable); reveal.setEnabled(pairable);
        try { pairingText = pairable ? new JSONObject().put("v", 1).put("url", "http://" + address + ":8765").put("key", KeyStoreHelper.base64(KeyStoreHelper.get(this))).put("device", Build.MANUFACTURER + " " + Build.MODEL).toString() : ""; }
        catch (Exception error) { pairingText = ""; copy.setEnabled(false); share.setEnabled(false); }
        pairing.setText(pairingShown ? pairingText : ""); pairing.setVisibility(pairingShown ? View.VISIBLE : View.GONE);
        reveal.setText(pairingShown ? "Hide pairing text" : "Show pairing text");
    }
    private void copyPairing() {
        if (pairingText.isEmpty()) return;
        ClipData clip = ClipData.newPlainText("Voiceish SIM pairing password", pairingText);
        PersistableBundle extras = new PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE", true); clip.getDescription().setExtras(extras);
        getSystemService(ClipboardManager.class).setPrimaryClip(clip); toast("Pairing text copied. Paste it into the Chrome extension.");
    }
    private String localAddress() {
        try {
            ConnectivityManager manager = getSystemService(ConnectivityManager.class);
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities == null || !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
                LinkProperties properties = manager.getLinkProperties(network); if (properties == null) continue;
                for (LinkAddress link : properties.getLinkAddresses()) { InetAddress address = link.getAddress(); if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress(); }
            }
            // Hotspot interfaces may not appear as an Android Network.
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || !(iface.getName().startsWith("wlan") || iface.getName().startsWith("ap") || iface.getName().startsWith("eth") || iface.getName().startsWith("swlan"))) continue;
                for (InetAddress address : Collections.list(iface.getInetAddresses())) if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
            }
        } catch (Exception ignored) { }
        return "";
    }
    private TextView text(String content, int size, int color) {
        TextView view = new TextView(this); view.setText(content); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3), 1); return view;
    }
    private TextView heading(String content) { TextView view = text(content, 18, INK); view.setTypeface(null, Typeface.BOLD); return view; }
    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); view.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(10, 28, 44)); background.setCornerRadius(dp(18)); background.setStroke(dp(1), Color.rgb(36, 60, 82)); view.setBackground(background); return view;
    }
    private Button button(String label, boolean primary) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(15); view.setMinHeight(dp(48)); view.setGravity(Gravity.CENTER);
        view.setTextColor(primary ? Color.WHITE : Color.rgb(128, 194, 255)); view.setBackgroundTintList(ColorStateList.valueOf(primary ? BLUE : Color.rgb(19, 42, 63))); return view;
    }
    private LinearLayout.LayoutParams spacing(int top, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); params.bottomMargin = dp(bottom); return params; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
