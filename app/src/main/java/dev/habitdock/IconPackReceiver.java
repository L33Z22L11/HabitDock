package dev.habitdock;

import android.content.*;

public final class IconPackReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getData() == null || !intent.getData().getSchemeSpecificPart().equals(IconStyle.load(context).pack))
            return;
        IconPacks.invalidate();
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        Repository.WORK.execute(() -> {
            try {
                HabitWidget.repaint(app);
            } catch (RuntimeException error) {
                android.util.Log.w("HabitDock", "Icon pack repaint failed", error);
            } finally {
                pending.finish();
            }
        });
    }
}
