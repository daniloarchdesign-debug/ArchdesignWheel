package design.archdesign.wheel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

/** Keeps the floating bubble on screen and opens the wheel when it is tapped. */
public class WheelService extends Service {
    static volatile boolean running = false;
    private static WheelService instance;

    private WindowManager wm;
    private BubbleView bubble;
    private WindowManager.LayoutParams bubbleLp;
    private WheelView wheel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Looks for a new version 20 s after start, then every 6 hours. */
    private final Runnable updateCheck = new Runnable() {
        @Override public void run() {
            Updater.checkAndNotify(WheelService.this);
            handler.postDelayed(this, 6L * 60 * 60 * 1000);
        }
    };

    static void appsChanged() {
        if (instance != null) instance.handler.post(instance::closeWheel);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        running = true;
        goForeground();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler.postDelayed(updateCheck, 20000);
        try {
            addBubble();
        } catch (Exception e) {
            Toast.makeText(this, "Allow \"display over other apps\" for Archdesign Wheel first.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel("wheel", "Floating wheel", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "wheel")
                .setContentTitle("Archdesign Wheel is on")
                .setContentText("Tap to change the apps on your wheel")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void addBubble() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int size = dp(60);
        bubble = new BubbleView(this);
        bubbleLp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = Prefs.x(this, dm.widthPixels - size - dp(6));
        bubbleLp.y = Prefs.y(this, dm.heightPixels / 3);
        clamp();

        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved, longFired;
            final Runnable longPress = () -> {
                longFired = true;
                openSettings();
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        moved = false;
                        longFired = false;
                        bubble.setPressedLook(true);
                        handler.postDelayed(longPress, 650);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!moved && Math.hypot(dx, dy) > slop) {
                            moved = true;
                            handler.removeCallbacks(longPress);
                        }
                        if (moved) {
                            bubbleLp.x = startX + Math.round(dx);
                            bubbleLp.y = startY + Math.round(dy);
                            clamp();
                            wm.updateViewLayout(bubble, bubbleLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPress);
                        bubble.setPressedLook(false);
                        if (moved) Prefs.savePos(WheelService.this, bubbleLp.x, bubbleLp.y);
                        else if (!longFired && e.getActionMasked() == MotionEvent.ACTION_UP) openWheel();
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bubbleLp);
    }

    private void clamp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int size = dp(60);
        bubbleLp.x = Math.max(0, Math.min(bubbleLp.x, dm.widthPixels - size));
        bubbleLp.y = Math.max(0, Math.min(bubbleLp.y, dm.heightPixels - size));
    }

    private void openSettings() {
        closeWheel();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    void openWheel() {
        if (wheel != null) return;
        wheel = new WheelView(this, Prefs.apps(this), Prefs.links(this), new WheelView.Listener() {
            @Override public void onLaunch(String pkg) { launch(pkg); }
            @Override public void onOpenLink(String url) { openLink(url); }
            @Override public void onClose() { closeWheel(); }
            @Override public void onSettings() { openSettings(); }
            @Override public void onCompose() { compose(); }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        wm.addView(wheel, lp);
        bubble.setVisibility(View.GONE);
    }

    void closeWheel() {
        if (wheel != null) {
            try { wm.removeView(wheel); } catch (Exception ignored) {}
            wheel = null;
        }
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
    }

    /** Opens a blank new email, in Gmail when it's installed. */
    private void compose() {
        closeWheel();
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent gmail = new Intent(i).setPackage("com.google.android.gm");
        try {
            if (gmail.resolveActivity(getPackageManager()) != null) startActivity(gmail);
            else startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Opens a web link from the wheel. It goes straight to Chrome (or Samsung Internet) so
     * another app that claims the address, like the Claude app, can't swallow it.
     */
    private void openLink(String url) {
        closeWheel();
        Uri u = Uri.parse(url);
        Toast.makeText(this, "Opening " + u.getHost() + "\u2026", Toast.LENGTH_SHORT).show();
        for (String browser : new String[]{"com.android.chrome", "com.sec.android.app.sbrowser"}) {
            Intent i = new Intent(Intent.ACTION_VIEW, u).setPackage(browser);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
                return;
            } catch (Exception ignored) {
                // that browser isn't installed; try the next one
            }
        }
        Intent any = new Intent(Intent.ACTION_VIEW, u);
        any.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(Intent.createChooser(any, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open that link.", Toast.LENGTH_LONG).show();
        }
    }

    private void launch(String pkg) {
        closeWheel();
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(this, "That app isn't installed any more.", Toast.LENGTH_SHORT).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open that app.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(updateCheck);
        closeWheel();
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        running = false;
        instance = null;
        super.onDestroy();
    }
}
