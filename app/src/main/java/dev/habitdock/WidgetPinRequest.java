package dev.habitdock;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import java.util.UUID;

/**
 * Applies the selected default layout only after the launcher confirms an
 * actual widget ID.
 */
public final class WidgetPinRequest extends BroadcastReceiver {
    static Intent callback(Context context, ComponentName provider, WidgetPreferences layout, String name) {
        return new Intent(context, WidgetPinRequest.class)
                .setData(Uri.parse("habitdock://pin/" + UUID.randomUUID()))
                .putExtra("provider", provider.flattenToString()).putExtra("name", name)
                .putExtra("columns", layout.columns).putExtra("rows", layout.rows)
                .putExtra("percent", layout.percent).putExtra("more", layout.more)
                .putExtra("more-time", layout.moreTime).putExtra("actions", layout.actions);
    }

    static boolean request(Context context, int columns, int rows, WidgetPreferences layout, String name) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (!manager.isRequestPinAppWidgetSupported())
            return false;
        ComponentName provider = WidgetSizes.provider(context, columns, rows);
        // The launcher supplies EXTRA_APPWIDGET_ID. This explicit, one-shot callback
        // targets our non-exported receiver and never starts an activity.
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 31)
            flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent callback = PendingIntent.getBroadcast(context, 0, callback(context, provider, layout, name),
                flags);
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                    WidgetSettingsActivity.pinPreview(context, layout, columns, rows));
            if (manager.requestPinAppWidget(provider, extras, callback))
                return true;
        } catch (RuntimeException error) {
            callback.cancel();
            throw error;
        }
        callback.cancel();
        return false;
    }

    static boolean apply(Context context, Intent intent) {
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
        if (info == null || !HabitWidget.owns(context, info.provider)
                || !info.provider.flattenToString().equals(intent.getStringExtra("provider")))
            return false;
        new WidgetPreferences(intent.getIntExtra("columns", 5), intent.getIntExtra("rows", 2),
                intent.getIntExtra("percent", 82), intent.getBooleanExtra("more", true),
                intent.getBooleanExtra("actions", false), intent.getBooleanExtra("more-time", true)).save(context, id);
        String name = intent.getStringExtra("name");
        WidgetNames.save(context, id, name == null ? "" : name);
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!apply(context, intent))
            return;
        RefreshJob.schedule(context);
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        Repository.WORK.execute(() -> {
            try {
                HabitWidget.repaint(app);
            } catch (RuntimeException error) {
                android.util.Log.w("HabitDock", "New widget repaint failed", error);
            } finally {
                pending.finish();
            }
        });
    }
}
