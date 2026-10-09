package design.archdesign.wheel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Brings the wheel back after the phone restarts or the app updates. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        // after a restart, or right after the wheel updated itself
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (!Prefs.autoStart(context) || !Settings.canDrawOverlays(context)) return;
        try {
            context.startForegroundService(new Intent(context, WheelService.class));
        } catch (Exception ignored) {
            // Some phones block this; the user can start it from the app.
        }
    }
}
