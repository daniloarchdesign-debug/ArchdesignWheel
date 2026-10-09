package design.archdesign.wheel;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Saved settings: which apps are on the wheel (in order) and where the bubble sits. */
final class Prefs {
    private static final String FILE = "wheel";

    /** Apps put on the wheel the first time, if they are installed. */
    private static final String[] DEFAULTS = {
            "com.google.android.gm",              // Gmail
            "com.whatsapp.w4b", "com.whatsapp",   // WhatsApp Business / WhatsApp
            "com.samsung.android.dialer",         // Phone
            "com.sec.android.app.camera",         // Camera
            "com.android.chrome",                 // Chrome
            "com.samsung.android.calendar", "com.google.android.calendar",
            "com.zoho.chat",                      // Zoho Cliq
            "com.anthropic.claude",               // Claude
            "com.google.android.apps.maps",       // Maps
            "com.sec.android.app.myfiles",        // My Files
    };

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static List<String> apps(Context c) {
        List<String> out = new ArrayList<>();
        String saved = sp(c).getString("apps", null);
        if (saved == null) {
            PackageManager pm = c.getPackageManager();
            for (String p : DEFAULTS) {
                if (out.size() >= MainActivity.MAX) break;
                if (pm.getLaunchIntentForPackage(p) != null) out.add(p);
            }
            saveApps(c, out);
            return out;
        }
        for (String p : saved.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void saveApps(Context c, Collection<String> apps) {
        sp(c).edit().putString("apps", String.join(",", apps)).apply();
    }

    /** A web address shown as a button on the wheel. */
    static final class Link {
        final String name, url;
        final int color;
        Link(String name, String url, int color) { this.name = name; this.url = url; this.color = color; }
    }

    static final int MAX_LINKS = 8;

    static List<Link> links(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean("linksSeeded", false)) {
            List<Link> first = new ArrayList<>();
            first.add(new Link("Jobs", "https://claude.ai/artifact/VHWP9PuJsEWT9wkJEKkbXx", 0xFF3DFF7A));
            saveLinks(c, first);
            p.edit().putBoolean("linksSeeded", true).apply();
            return first;
        }
        List<Link> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("links", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Link(o.optString("name"), o.optString("url"), o.optInt("color", 0xFF00E5FF)));
            }
        } catch (Exception ignored) {
            // bad data: start empty
        }
        return out;
    }

    static void saveLinks(Context c, List<Link> links) {
        JSONArray a = new JSONArray();
        try {
            for (Link l : links) a.put(new JSONObject().put("name", l.name).put("url", l.url).put("color", l.color));
        } catch (Exception ignored) {}
        sp(c).edit().putString("links", a.toString()).apply();
    }

    static int x(Context c, int def) { return sp(c).getInt("x", def); }
    static int y(Context c, int def) { return sp(c).getInt("y", def); }

    static void savePos(Context c, int x, int y) {
        sp(c).edit().putInt("x", x).putInt("y", y).apply();
    }

    static boolean autoStart(Context c) { return sp(c).getBoolean("autostart", true); }
}
