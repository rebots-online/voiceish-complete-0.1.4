package com.voiceish.setup;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity {
    static final String TARGET = "com.voiceish.sim";
    static final String CERT = "56c13674ef22df95deb1e5c468820e8cfa3ea2f522511749ab7b6e5bde3bd943";
    static WeakReference<MainActivity> visible = new WeakReference<>(null);
    private TextView status, permissionStatus;
    private Button install, confirm, cancel, open, settings;
    private static volatile boolean building;
    private SharedPreferences state() { return getSharedPreferences("setup", MODE_PRIVATE); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(4,15,27));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24),dp(36),dp(24),dp(32));
        scroll.addView(body);
        body.addView(label("VOICEISH / PHONE SETUP", 12, true));
        body.addView(label("Your SIM.\nYour messages.", 32, true));
        body.addView(label("Install Voiceish SIM entirely on your phone. No computer or USB connection needed.",17,false));
        body.addView(label("1  Allow this installer",20,true));
        body.addView(label("Android will ask you to allow installs from Voiceish Setup. This app only installs the included Voiceish SIM companion, signed with your organizational certificate.",15,false));
        body.addView(label("2  Install the companion",20,true));
        body.addView(label("Approve Android’s installation screen. An existing matching Voiceish installation is updated with its data kept. Keep this screen open until the prompt appears.",15,false));
        install = button(body,"Install / update Voiceish SIM",v -> begin());
        confirm = button(body,"Continue Android confirmation",v -> launchConfirmation());
        cancel = button(body,"Cancel pending setup",v -> cancelSession());
        status = label("",15,false); body.addView(status);
        body.addView(label("3  Allow SMS, then pair",20,true));
        body.addView(label("Open Voiceish SIM and allow SMS and phone access. If Send SMS is still denied, open its permissions below and check SMS. Setup does not grant permissions automatically or replace your usual messaging app.",15,false));
        open = button(body,"Open Voiceish SIM",v -> openCompanion());
        settings = button(body,"Voiceish SIM permissions",v -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+TARGET))));
        permissionStatus = label("",15,false); body.addView(permissionStatus);
        button(body,"Copy setup diagnostics",v -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Voiceish setup diagnostics",diagnostics()));
            Toast.makeText(this,"Diagnostics copied — no messages or pairing keys",Toast.LENGTH_SHORT).show();
        });
        body.addView(label("Setup 0.1.4 · Companion 0.1.4\nAfter setup succeeds you may uninstall Voiceish Setup. Keep Voiceish SIM installed.",12,false));
        setContentView(scroll);
        // Target 35 draws edge-to-edge. Keep controls clear of status, navigation and cutout insets.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
    @Override public void onResume() { super.onResume(); visible = new WeakReference<>(this); refresh(); }
    @Override public void onPause() { visible = new WeakReference<>(null); super.onPause(); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView label(String text,int size,boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(Color.rgb(235,243,252)); view.setPadding(0,dp(10),0,dp(10));
        if(bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }
    private Button button(LinearLayout parent,String text,View.OnClickListener handler) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.rgb(235,243,252)); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(19,54,87))); b.setOnClickListener(handler);
        parent.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    private boolean installed() {
        try { getPackageManager().getPackageInfo(TARGET,0); return true; }
        catch(PackageManager.NameNotFoundException e) { return false; }
    }
    private boolean granted(String permission) { return getPackageManager().checkPermission(permission,TARGET)==PackageManager.PERMISSION_GRANTED; }
    void refresh() {
        if(status==null) return;
        boolean pending = state().getInt("session",-1)>=0;
        install.setEnabled(!building && !pending);
        install.setText(getPackageManager().canRequestPackageInstalls() ? "Install / update Voiceish SIM" : "Allow Voiceish Setup to install");
        confirm.setVisibility(InstallReceiver.confirmation != null ? View.VISIBLE : View.GONE);
        cancel.setVisibility(pending ? View.VISIBLE : View.GONE);
        status.setText(state().getString("message","Ready to install the included Voiceish SIM companion."));
        open.setEnabled(installed()); settings.setEnabled(installed());
        permissionStatus.setText(!installed() ? "Companion not installed yet." :
            "Read SMS: " + (granted(Manifest.permission.READ_SMS)?"allowed":"not allowed")
            + "\nSend SMS: " + (granted(Manifest.permission.SEND_SMS)?"allowed":"not allowed")
            + "\nPhone / SIM: " + (granted(Manifest.permission.READ_PHONE_STATE)?"allowed":"not allowed")
            + "\n" + (granted(Manifest.permission.READ_SMS) && granted(Manifest.permission.SEND_SMS) && granted(Manifest.permission.READ_PHONE_STATE)
                ? "Permissions are ready. Start the bridge in Voiceish SIM."
                : "SMS setup is not complete until all three permissions are allowed."));
        if(InstallReceiver.confirmation!=null) launchConfirmation();
    }
    private void safeStart(Intent intent) {
        try { startActivity(intent); } catch(Exception e) { fail("Unable to open this Android screen: " + e.getMessage()); }
    }
    private void launchConfirmation() {
        Intent intent = InstallReceiver.confirmation;
        if(intent==null) return;
        InstallReceiver.confirmation=null;
        try { startActivity(intent); confirm.setVisibility(View.GONE); }
        catch(Exception e) {
            InstallReceiver.confirmation=intent;
            state().edit().putString("message","Tap Continue Android confirmation. If Android still blocks it, cancel pending setup and try again.").apply();
            status.setText(state().getString("message","")); confirm.setVisibility(View.VISIBLE);
        }
    }
    private void begin() {
        if(building || state().getInt("session",-1)>=0) return;
        if(!getPackageManager().canRequestPackageInstalls()) {
            safeStart(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));
            return;
        }
        building=true;
        state().edit().putString("message","Verifying the bundled companion and preparing Android’s installer…").apply(); refresh();
        new Thread(() -> {
            int sessionId=-1;
            File payload=new File(getCacheDir(),"voiceish-sim.apk");
            try {
                try(InputStream in=getAssets().open("voiceish-sim.apk"); OutputStream out=new FileOutputStream(payload)) {
                    byte[] buffer=new byte[16384]; int n;
                    while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                }
                if(!Payload.SHA256.equals(fileDigest(payload))) throw new SecurityException("Bundled APK checksum mismatch.");
                PackageManager pm=getPackageManager();
                PackageInfo info=pm.getPackageArchiveInfo(payload.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
                if(info==null || !TARGET.equals(info.packageName) || info.getLongVersionCode()!=5 || !signedByOrganization(info))
                    throw new SecurityException("The bundled APK does not match the expected Voiceish release and certificate.");
                if(installed()) {
                    PackageInfo old=pm.getPackageInfo(TARGET,PackageManager.GET_SIGNING_CERTIFICATES);
                    if(!signedByOrganization(old)) throw new SecurityException("Existing Voiceish uses a different signing certificate. No app was removed or changed.");
                    if(old.getLongVersionCode()>info.getLongVersionCode()) throw new SecurityException("A newer Voiceish is already installed. This setup will not downgrade it.");
                }
                PackageInstaller installer=pm.getPackageInstaller();
                PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(TARGET); params.setAppLabel("Voiceish SIM"); params.setSize(payload.length());
                params.setWhitelistedRestrictedPermissions(new HashSet<>(Arrays.asList(Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS)));
                if(Build.VERSION.SDK_INT>=31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                if(Build.VERSION.SDK_INT>=33) params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE);
                sessionId=installer.createSession(params);
                state().edit().putInt("session",sessionId).putString("message","Waiting for Android installation confirmation…").commit();
                try(PackageInstaller.Session session=installer.openSession(sessionId);
                    InputStream in=new FileInputStream(payload)) {
                    try(OutputStream out=session.openWrite("base.apk",0,payload.length())) {
                        byte[] buffer=new byte[16384]; int n;
                        while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                        session.fsync(out);
                    }
                    Intent callback=new Intent(this,InstallReceiver.class).setAction("com.voiceish.setup.INSTALL_RESULT");
                    int flags=PendingIntent.FLAG_UPDATE_CURRENT;
                    if(Build.VERSION.SDK_INT>=31) flags|=PendingIntent.FLAG_MUTABLE;
                    PendingIntent receiver=PendingIntent.getBroadcast(this,sessionId,callback,flags);
                    session.commit(receiver.getIntentSender());
                }
            } catch(Exception e) {
                if(sessionId>=0) try { getPackageManager().getPackageInstaller().abandonSession(sessionId); } catch(Exception ignored) {}
                state().edit().remove("session").putString("message","Setup stopped: "+e.getMessage()).commit();
            } finally {
                payload.delete();
                runOnUiThread(() -> {
                    building=false;
                    MainActivity current=visible.get();
                    if(current!=null && !current.isDestroyed()) current.refresh();
                });
            }
        },"voiceish-install").start();
    }
    private void cancelSession() {
        int id=state().getInt("session",-1);
        if(id>=0) try { getPackageManager().getPackageInstaller().abandonSession(id); } catch(Exception ignored) {}
        InstallReceiver.confirmation=null;
        state().edit().remove("session").putString("message","Setup cancelled. You can try again. The companion’s existing data was not erased.").commit(); refresh();
    }
    private void openCompanion() {
        Intent launch=getPackageManager().getLaunchIntentForPackage(TARGET);
        if(launch!=null) safeStart(launch); else fail("Voiceish SIM is not installed yet.");
    }
    private void fail(String message) { state().edit().putString("message",message).apply(); status.setText(message); }
    static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return out.toString();
    }
    static String fileDigest(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) { byte[] b=new byte[16384]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); }
        return hex(digest.digest());
    }
    static boolean signedByOrganization(PackageInfo info) throws Exception {
        if(info.signingInfo==null) return false;
        Signature[] signatures=info.signingInfo.getApkContentsSigners();
        return signatures.length==1 && CERT.equals(hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())));
    }
    private String diagnostics() {
        StringBuilder out=new StringBuilder("Voiceish Setup 0.1.4\nAndroid API: ").append(Build.VERSION.SDK_INT)
            .append("\nDevice: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append("\nCan request install: ").append(getPackageManager().canRequestPackageInstalls())
            .append("\nCompanion installed: ").append(installed());
        if(installed()) {
            for(String p:Arrays.asList(Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.READ_PHONE_STATE))
                out.append("\n").append(p).append(": ").append(granted(p)?"GRANTED":"DENIED");
            try {
                out.append("\nInstaller: ").append(getPackageManager().getInstallerPackageName(TARGET));
                Set<String> allowed=getPackageManager().getWhitelistedRestrictedPermissions(TARGET,PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER);
                out.append("\nInstaller SMS allowlist: ").append(allowed);
            } catch(Exception e) { out.append("\nInstaller allowlist: unavailable (").append(e.getClass().getSimpleName()).append(")"); }
        }
        return out.append("\nSetup status: ").append(state().getString("message","Ready")).toString();
    }
}
