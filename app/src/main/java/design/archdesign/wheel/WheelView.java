package design.archdesign.wheel;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Full-screen overlay: a ring of app icons around a clock. Tap an icon to open it. */
class WheelView extends View {
    interface Listener {
        void onLaunch(String pkg);
        void onOpenLink(String url);
        void onClose();
        void onSettings();
        void onCompose();
    }

    private static final int[] COLORS = {0xFF00E5FF, 0xFFFF5A4F, 0xFF3DFF7A, 0xFFB46BFF, 0xFFFFE14D, 0xFFFFB020,
            0xFF2F8CFF, 0xFF3DFFC5, 0xFFFF2BD6, 0xFFFF9A1F, 0xFF8FB3C9, 0xFF00E5FF};

    private final Listener listener;
    private final List<String> pkgs = new ArrayList<>();
    private final List<Drawable> icons = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<Prefs.Link> links = new ArrayList<>();
    private final Paint linkFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint glyph = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    private final Paint dim = new Paint();
    private final Paint disk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint node = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint clock = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private float cx, cy, R, nodeR, hubR;
    private final RectF editBox = new RectF();
    private final RectF mailBox = new RectF();
    private float spin = 0f;

    WheelView(Context c, List<String> apps, List<Prefs.Link> webLinks, Listener l) {
        super(c);
        listener = l;
        d = getResources().getDisplayMetrics().density;
        PackageManager pm = c.getPackageManager();
        for (String p : apps) {
            try {
                Drawable ic = pm.getApplicationIcon(p);
                CharSequence name = pm.getApplicationLabel(pm.getApplicationInfo(p, 0));
                pkgs.add(p);
                icons.add(ic);
                labels.add(String.valueOf(name));
            } catch (PackageManager.NameNotFoundException ignored) {
                // app was removed
            }
        }
        links.addAll(webLinks);
        glyph.setTextAlign(Paint.Align.CENTER);
        glyph.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        dim.setColor(0xB3000306);
        stroke.setStyle(Paint.Style.STROKE);
        label.setColor(0xFFDFF7FF);
        label.setTextSize(11 * d);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        clock.setColor(0xFF00E5FF);
        clock.setTextAlign(Paint.Align.CENTER);
        clock.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        small.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setAlpha(0f);
        animate().alpha(1f).setDuration(160).start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        R = Math.min(w, h) * 0.36f;
        R = Math.min(R, 230 * d);
        int n = Math.max(count(), 1);
        nodeR = Math.max(22 * d, Math.min(36 * d, R * 0.22f));
        nodeR = Math.min(nodeR, (float) (R * Math.PI / n) * 0.78f);   // keep buttons from overlapping
        glyph.setTextSize(nodeR * 0.9f);
        hubR = R * 0.48f;
        clock.setTextSize(hubR * 0.42f);
        small.setTextSize(Math.max(10 * d, hubR * 0.11f));
    }

    private int count() { return pkgs.size() + links.size(); }

    private float[] pos(int i) {
        int n = Math.max(count(), 1);
        double a = -Math.PI / 2 + i * 2 * Math.PI / n;
        return new float[]{cx + (float) (R * Math.cos(a)), cy + (float) (R * Math.sin(a))};
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), dim);

        // rings
        stroke.setStrokeWidth(1.5f * d);
        stroke.setColor(0x5500E5FF);
        c.drawCircle(cx, cy, R + nodeR + 10 * d, stroke);
        stroke.setStrokeWidth(4 * d);
        stroke.setColor(0xFFFF9A1F);
        RectF o = new RectF(cx - R - nodeR - 4 * d, cy - R - nodeR - 4 * d, cx + R + nodeR + 4 * d, cy + R + nodeR + 4 * d);
        c.drawArc(o, spin - 90, 100, false, stroke);
        stroke.setStrokeWidth(2 * d);
        stroke.setColor(0x8800E5FF);
        c.drawCircle(cx, cy, R, stroke);

        // hub
        disk.setShader(new RadialGradient(cx, cy - hubR * 0.3f, hubR * 1.3f, 0xFF0E2742, 0xFF050D18, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, hubR, disk);
        stroke.setColor(0xFF00E5FF);
        c.drawCircle(cx, cy, hubR, stroke);
        small.setColor(0xFFFF9A1F);
        c.drawText("ARCHDESIGN", cx, cy - hubR * 0.58f, small);
        String t = new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date());
        c.drawText(t, cx, cy - hubR * 0.12f, clock);
        small.setColor(0xFFDFF7FF);
        c.drawText(new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault()),
                cx, cy + hubR * 0.12f, small);

        // NEW EMAIL pill
        float ts = small.getTextSize();
        float mw = small.measureText("\u2709  NEW EMAIL") / 2f + 14 * d, my = cy + hubR * 0.42f;
        mailBox.set(cx - mw, my - ts * 1.25f, cx + mw, my + ts * 0.65f);
        node.setShader(null);
        node.setColor(0xFFFF9A1F);
        c.drawRoundRect(mailBox, mailBox.height() / 2f, mailBox.height() / 2f, node);
        small.setColor(0xFF03080F);
        c.drawText("\u2709  NEW EMAIL", cx, my, small);

        small.setColor(0xFF00E5FF);
        float ew = small.measureText("\u2699  SETTINGS") / 2f + 12 * d, ey = cy + hubR * 0.76f;
        editBox.set(cx - ew, ey - ts * 1.25f, cx + ew, ey + ts * 0.65f);
        stroke.setColor(0xFF00E5FF);
        stroke.setStrokeWidth(1.5f * d);
        c.drawRoundRect(editBox, editBox.height() / 2f, editBox.height() / 2f, stroke);
        c.drawText("\u2699  SETTINGS", cx, ey, small);

        if (count() == 0) {
            label.setColor(0xFFFF9A1F);
            c.drawText("Tap SETTINGS to choose apps and links", cx, cy + R + 30 * d, label);
            label.setColor(0xFFDFF7FF);
        }

        // app nodes
        for (int i = 0; i < pkgs.size(); i++) {
            float[] p = pos(i);
            int col = COLORS[i % COLORS.length];
            node.setShader(new RadialGradient(p[0], p[1] - nodeR * 0.3f, nodeR * 1.3f, 0xFF0E2236, 0xFF050D18, Shader.TileMode.CLAMP));
            c.drawCircle(p[0], p[1], nodeR, node);
            stroke.setColor(col);
            stroke.setStrokeWidth(2.5f * d);
            c.drawCircle(p[0], p[1], nodeR, stroke);
            int s = Math.round(nodeR * 1.15f);
            Drawable ic = icons.get(i);
            ic.setBounds(Math.round(p[0] - s / 2f), Math.round(p[1] - s / 2f), Math.round(p[0] + s / 2f), Math.round(p[1] + s / 2f));
            ic.draw(c);
            CharSequence name = TextUtils.ellipsize(labels.get(i), label, nodeR * 2.6f, TextUtils.TruncateAt.END);
            c.drawText(name.toString(), p[0], p[1] + nodeR + 14 * d, label);
        }

        // web link buttons
        for (int j = 0; j < links.size(); j++) {
            Prefs.Link l = links.get(j);
            float[] p = pos(pkgs.size() + j);
            node.setShader(new RadialGradient(p[0], p[1] - nodeR * 0.3f, nodeR * 1.3f, 0xFF0E2236, 0xFF050D18, Shader.TileMode.CLAMP));
            c.drawCircle(p[0], p[1], nodeR, node);
            linkFill.setColor((l.color & 0x00FFFFFF) | 0x33000000);
            c.drawCircle(p[0], p[1], nodeR * 0.78f, linkFill);
            stroke.setColor(l.color);
            stroke.setStrokeWidth(2.5f * d);
            c.drawCircle(p[0], p[1], nodeR, stroke);
            String letter = l.name.isEmpty() ? "\u2197" : l.name.substring(0, 1).toUpperCase(Locale.getDefault());
            glyph.setColor(l.color);
            c.drawText(letter, p[0], p[1] + glyph.getTextSize() * 0.36f, glyph);
            CharSequence name = TextUtils.ellipsize(l.name, label, nodeR * 2.6f, TextUtils.TruncateAt.END);
            c.drawText(name.toString(), p[0], p[1] + nodeR + 14 * d, label);
        }

        spin = (spin + 0.6f) % 360f;
        postInvalidateDelayed(33);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();
        for (int i = 0; i < pkgs.size(); i++) {
            float[] p = pos(i);
            if (Math.hypot(x - p[0], y - p[1]) <= nodeR * 1.25f) {
                listener.onLaunch(pkgs.get(i));
                return true;
            }
        }
        for (int j = 0; j < links.size(); j++) {
            float[] p = pos(pkgs.size() + j);
            if (Math.hypot(x - p[0], y - p[1]) <= nodeR * 1.25f) {
                listener.onOpenLink(links.get(j).url);
                return true;
            }
        }
        if (mailBox.contains(x, y)) {
            listener.onCompose();
            return true;
        }
        if (editBox.contains(x, y)) {
            listener.onSettings();
            return true;
        }
        listener.onClose();
        return true;
    }
}
