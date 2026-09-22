package dev.habitdock;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action))
            return;
        RefreshJob.schedule(context);
        context.sendBroadcast(new Intent(context, HabitWidget.class).setAction(HabitWidget.REFRESH));
    }
}
