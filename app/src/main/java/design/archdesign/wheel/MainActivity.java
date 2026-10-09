package design.archdesign.wheel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Setup screen: allow the overlay, start/stop the wheel, pick the apps. */
public class MainActivity extends Activity {
    static final int MAX = 12;

    private static final int BG = 0xFF03080F, INK = 0xFFDFF7FF, MUTED = 0xFF8FB3C7, CYAN = 0xFF00E5FF, ORANGE = 0xFFFF9A1F;

    private TextView status;
    private Button permBtn, startBtn;
    private final Set<String> chosen = new LinkedHashSet<>();
    private List<Prefs.Link> links;
    private LinearLayout linkList;

    /** Colors you can pick for a web link button. */
    private static final int[] LINK_COLORS = {0xFF00E5FF, 0xFFFF9A1F, 0xFF3DFF7A, 0xFFB46BFF, 0xFFFFE14D, 0xFFFF5A4F, 0xFF2F8CFF, 0xFFFF2BD6};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        chosen.addAll(Prefs.apps(this));
        links = Prefs.links(this);

        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(36), pad, dp(36));
        scroll.addView(root);

        root.addView(text("ARCHDESIGN WHEEL  \u00b7  SETTINGS", 24, CYAN, true));
        root.addView(text("A round launcher that floats on top of every app. Drag the bubble anywhere. "
                + "Tap it to open your wheel of apps. Long-press it to come back to this screen.", 15, MUTED, false));

        permBtn = button("1.  Allow display over other apps", v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        root.addView(permBtn);
        startBtn = button("2.  Start the wheel", v -> toggle());
        root.addView(startBtn);

        status = text("", 15, ORANGE, true);
        root.addView(status);
        root.addView(text("3.  Web links on your wheel", 18, CYAN, true));
        root.addView(text("Any web address can be a button on the wheel. Tap a link below to change it.", 14, MUTED, false));
        linkList = new LinearLayout(this);
        linkList.setOrientation(LinearLayout.VERTICAL);
        root.addView(linkList);
        root.addView(button("+  Add a web link", v -> editLink(-1)));
        renderLinks();

        root.addView(text("4.  Tick the apps for your wheel (up to " + MAX + "). They go around the wheel in the order you tick them.", 15, INK, true));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        setContentView(scroll);
        fillApps(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean allowed = Settings.canDrawOverlays(this);
        permBtn.setEnabled(!allowed);
        permBtn.setText(allowed ? "✓  Display over other apps is allowed" : "1.  Allow display over other apps");
        startBtn.setText(WheelService.running ? "Stop the wheel" : "2.  Start the wheel");
        status.setText(chosen.size() + " apps and " + links.size() + (links.size() == 1 ? " link" : " links") + " on your wheel");
    }

    private void toggle() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "First tap step 1 and allow Archdesign Wheel to display over other apps.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, WheelService.class);
        if (WheelService.running) stopService(i);
        else startForegroundService(i);
        startBtn.postDelayed(this::refresh, 500);
    }

    private void fillApps(LinearLayout list) {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(q, 0));
        Collections.sort(found, (a, b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        Set<String> seen = new LinkedHashSet<>();
        for (ResolveInfo ri : found) {
            final String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText("  " + ri.loadLabel(pm));
            cb.setTextColor(INK);
            cb.setTextSize(17);
            cb.setPadding(dp(6), dp(10), dp(6), dp(10));
            Drawable icon = ri.loadIcon(pm);
            icon.setBounds(0, 0, dp(34), dp(34));
            cb.setCompoundDrawables(null, null, icon, null);
            cb.setChecked(chosen.contains(pkg));
            cb.setOnCheckedChangeListener((v, on) -> {
                if (on) {
                    if (chosen.size() >= MAX) {
                        v.setChecked(false);
                        Toast.makeText(this, "The wheel holds " + MAX + " apps. Untick one first.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    chosen.add(pkg);
                } else {
                    chosen.remove(pkg);
                }
                Prefs.saveApps(this, chosen);
                WheelService.appsChanged();
                refresh();
            });
            list.addView(cb);
        }
    }

    // ---------- web links ----------

    private void renderLinks() {
        linkList.removeAllViews();
        if (links.isEmpty()) linkList.addView(text("No links yet.", 14, MUTED, false));
        for (int i = 0; i < links.size(); i++) {
            final int idx = i;
            Prefs.Link l = links.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(10), 0, dp(10));
            row.setOnClickListener(v -> editLink(idx));

            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(l.color);
            dot.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(26), dp(26));
            dl.rightMargin = dp(14);
            row.addView(dot, dl);

            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(l.name, 17, INK, true);
            name.setPadding(0, 0, 0, 0);
            TextView url = text(l.url, 12, MUTED, false);
            url.setPadding(0, dp(2), 0, 0);
            url.setSingleLine(true);
            url.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            words.addView(name);
            words.addView(url);
            row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setAllCaps(false);
            remove.setTextColor(ORANGE);
            GradientDrawable rb = new GradientDrawable();
            rb.setColor(BG);
            rb.setStroke(dp(1), ORANGE);
            rb.setCornerRadius(dp(18));
            remove.setBackground(rb);
            remove.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Remove \"" + l.name + "\"?")
                    .setMessage("The button comes off your wheel. You can add it again any time.")
                    .setPositiveButton("Remove", (d, w) -> {
                        links.remove(idx);
                        saveLinks();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            row.addView(remove, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)));
            linkList.addView(row);
        }
    }

    private void saveLinks() {
        Prefs.saveLinks(this, links);
        WheelService.appsChanged();
        renderLinks();
        refresh();
    }

    /** Add (index -1) or change a web link: name, address and color. */
    private void editLink(int index) {
        if (index < 0 && links.size() >= Prefs.MAX_LINKS) {
            Toast.makeText(this, "The wheel holds " + Prefs.MAX_LINKS + " links. Remove one first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.Link old = index >= 0 ? links.get(index) : null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        box.addView(text("Name on the button", 13, MUTED, false));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("e.g. Jobs");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (old != null) name.setText(old.name);
        box.addView(name);

        box.addView(text("Web address", 13, MUTED, false));
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("https://...");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (old != null) url.setText(old.url);
        box.addView(url);

        box.addView(text("Color", 13, MUTED, false));
        final int[] pick = {old != null ? old.color : LINK_COLORS[links.size() % LINK_COLORS.length]};
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        final List<View> sw = new ArrayList<>();
        for (int col : LINK_COLORS) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            v.setLayoutParams(lp);
            v.setTag(col);
            v.setOnClickListener(x -> {
                pick[0] = (int) x.getTag();
                paintSwatches(sw, pick[0]);
            });
            sw.add(v);
            swatches.addView(v);
        }
        paintSwatches(sw, pick[0]);
        box.addView(swatches);

        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(old == null ? "Add a web link" : "Change link")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String u = url.getText().toString().trim();
            if (n.isEmpty()) { name.setError("Give it a name"); return; }
            if (!u.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) u = "https://" + u;
            if (!Patterns.WEB_URL.matcher(u).matches()) { url.setError("That doesn't look like a web address"); return; }
            Prefs.Link l = new Prefs.Link(n, u, pick[0]);
            if (index >= 0) links.set(index, l); else links.add(l);
            saveLinks();
            dlg.dismiss();
        }));
        dlg.show();
    }

    private void paintSwatches(List<View> sw, int selected) {
        for (View v : sw) {
            int col = (int) v.getTag();
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(17));
            g.setColor(col);
            if (col == selected) g.setStroke(dp(3), 0xFFFFFFFF);
            v.setBackground(g);
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(BG);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CYAN);
        bg.setCornerRadius(dp(26));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
