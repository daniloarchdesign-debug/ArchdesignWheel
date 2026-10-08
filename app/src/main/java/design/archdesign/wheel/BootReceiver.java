package design.archdesign.wheel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Brings the wheel back after the phone restarts. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Prefs.autoStart(context) || !Settings.canDrawOverlays(context)) return;
        try {
            context.startForegroundService(new Intent(context, WheelService.class));
        } catch (Exception ignored) {
            // Some phones block this; the user can start it from the app.
        }
    }
}
