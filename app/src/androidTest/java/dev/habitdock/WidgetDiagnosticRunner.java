package dev.habitdock;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;

/**
 * Widget geometry diagnostics; optional explicit hostHints record independently
 * verified host labels. Never resets app data or changes system bindings.
 */
public final class WidgetDiagnosticRunner extends Instrumentation {
    private String hostHints;
    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        hostHints = args == null ? null : args.getString("hostHints");
        start();
    }

    @Override
    public void onStart() {
        Context c = getTargetContext();
        if (hostHints != null) {
            for (String hint : hostHints.split(",")) {
                String[] parts = hint.split("=", 2);
                if (parts.length != 2)
                    continue;
                int id = Integer.parseInt(parts[0]);
                android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
                if (info != null && HabitWidget.owns(c, info.provider))
                    WidgetInstances.rememberHost(c, id, parts[1]);
            }
            // Instrumentation can terminate before asynchronous preference writes finish.
            Repository.prefs(c).edit().commit();
        }
        DisplayMetrics real = new DisplayMetrics();
        c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(real);
        StringBuilder text = new StringBuilder("resource density=")
                .append(c.getResources().getDisplayMetrics().density).append(" display density=").append(real.density)
                .append(" display pixels=").append(real.widthPixels).append('x').append(real.heightPixels).append('\n');
        AppWidgetManager manager = c.getSystemService(AppWidgetManager.class);
        for (int id : HabitWidget.boundIds(c)) {
            Bundle options = manager.getAppWidgetOptions(id);
            text.append("widget ").append(id).append(" active=").append(WidgetInstances.active(c, id))
                    .append(" host=").append(WidgetInstances.host(c, id)).append(' ');
            for (String key : options.keySet())
                text.append(key).append('=').append(options.get(key)).append(' ');
            text.append('\n');
        }
        Bundle result = new Bundle();
        result.putString("stream", text.toString());
        finish(Activity.RESULT_OK, result);
    }
}
