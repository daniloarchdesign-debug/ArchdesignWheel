package design.archdesign.wheel;

import android.app.Activity;
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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        chosen.addAll(Prefs.apps(this));

        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(36), pad, dp(36));
        scroll.addView(root);

        root.addView(text("ARCHDESIGN WHEEL", 24, CYAN, true));
        root.addView(text("A round launcher that floats on top of every app. Drag the bubble anywhere. "
                + "Tap it to open your wheel of apps. Long-press it to come back to this screen.", 15, MUTED, false));

        permBtn = button("1.  Allow display over other apps", v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        root.addView(permBtn);
        startBtn = button("2.  Start the wheel", v -> toggle());
        root.addView(startBtn);

        status = text("", 15, ORANGE, true);
        root.addView(status);
        root.addView(text("3.  Tick the apps for your wheel (up to " + MAX + "). They go around the wheel in the order you tick them.", 15, INK, true));

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
        status.setText(chosen.size() + " apps on your wheel");
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
