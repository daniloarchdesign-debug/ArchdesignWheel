package design.archdesign.wheel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Self-update: looks at the latest GitHub release, and when it is newer than this app,
 * shows a "New version ready" notification. Tapping it downloads the new app and opens
 * Android's own "Update" screen (Android always asks for that one tap).
 */
final class Updater {
    static final String ACTION_UPDATE = "design.archdesign.wheel.UPDATE";
    static final String ACTION_INSTALL_STATUS = "design.archdesign.wheel.INSTALL_STATUS";
    private static final String LATEST = "https://api.github.com/repos/daniloarchdesign-debug/ArchdesignWheel/releases/latest";
    private static final String APK_NAME = "ArchdesignWheel.apk";
    private static final int NOTE_ID = 7;

    interface Result { void done(int latest, String apkUrl, String error); }

    private Updater() {}

    static int currentVersion(Context c) {
        try {
            return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Asks GitHub for the newest release, off the main thread; answers on the main thread. */
    static void check(Context c, Result r) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            int latest = 0;
            String apk = null, err = null;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(LATEST).openConnection();
                h.setConnectTimeout(15000);
                h.setReadTimeout(15000);
                h.setRequestProperty("Accept", "application/vnd.github+json");
                h.setRequestProperty("User-Agent", "ArchdesignWheel");
                if (h.getResponseCode() != 200) throw new Exception("GitHub answered " + h.getResponseCode());
                JSONObject o = new JSONObject(read(h.getInputStream()));
                String digits = o.optString("tag_name", "").replaceAll("[^0-9]", "");
                latest = digits.isEmpty() ? 0 : Integer.parseInt(digits);
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (APK_NAME.equals(a.optString("name"))) apk = a.optString("browser_download_url");
                }
                if (apk == null) throw new Exception("No app file in the newest release");
            } catch (Exception e) {
                err = e.getMessage() == null ? "Couldn't reach GitHub" : e.getMessage();
            }
            final int fl = latest; final String fa = apk, fe = err;
            main.post(() -> r.done(fl, fa, fe));
        }).start();
    }

    /** Background check (from the wheel service): notify only when there is something new. */
    static void checkAndNotify(Context c) {
        check(c, (latest, apk, err) -> {
            if (err != null || latest <= currentVersion(c)) return;
            if (Prefs.notifiedVersion(c) == latest) return;   // don't nag about the same version twice
            Prefs.setNotifiedVersion(c, latest);
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(c, MainActivity.class).setAction(ACTION_UPDATE);
            PendingIntent pi = PendingIntent.getActivity(c, 2, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, "updates")
                    .setContentTitle("Archdesign Wheel: new version ready")
                    .setContentText("Tap to update to version " + latest)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(NOTE_ID, n);
        });
    }

    static boolean canInstall(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    static Intent allowInstallsIntent(Context c) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
    }

    /**
     * Downloads the app file straight into Android's installer. When it is ready, Android
     * sends MainActivity an INSTALL_STATUS intent holding the "Update?" screen to show.
     */
    static void downloadAndInstall(Context c, String apkUrl, Result r) {
        Context app = c.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String err = null;
            PackageInstaller pi = app.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            try {
                ((NotificationManager) app.getSystemService(NotificationManager.class)).cancel(NOTE_ID);
                PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                p.setAppPackageName(app.getPackageName());
                sessionId = pi.createSession(p);
                try (PackageInstaller.Session s = pi.openSession(sessionId)) {
                    HttpURLConnection h = open(apkUrl);
                    long size = h.getContentLengthLong();
                    try (InputStream in = h.getInputStream(); OutputStream out = s.openWrite("wheel.apk", 0, size)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    }
                    Intent back = new Intent(app, MainActivity.class).setAction(ACTION_INSTALL_STATUS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent sender = PendingIntent.getActivity(app, 3, back, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    s.commit(sender.getIntentSender());
                }
            } catch (Exception e) {
                err = e.getMessage() == null ? "Download failed" : e.getMessage();
                if (sessionId != -1) try { pi.abandonSession(sessionId); } catch (Exception ignored) {}
            }
            final String fe = err;
            main.post(() -> r.done(0, apkUrl, fe));
        }).start();
    }

    /** Follows GitHub's redirect to the real file host. */
    private static HttpURLConnection open(String url) throws Exception {
        for (int hops = 0; hops < 5; hops++) {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(20000);
            h.setReadTimeout(60000);
            h.setRequestProperty("User-Agent", "ArchdesignWheel");
            int code = h.getResponseCode();
            if (code >= 300 && code < 400 && h.getHeaderField("Location") != null) {
                url = h.getHeaderField("Location");
                h.disconnect();
                continue;
            }
            if (code != 200) throw new Exception("Download answered " + code);
            return h;
        }
        throw new Exception("Too many redirects");
    }

    private static String read(InputStream in) throws Exception {
        try (InputStream i = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }
}
