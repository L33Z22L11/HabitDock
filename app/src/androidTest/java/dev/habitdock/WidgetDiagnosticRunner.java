package dev.habitdock;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;

/**
 * Read-only widget geometry diagnostics; never changes preferences or bindings.
 */
public final class WidgetDiagnosticRunner extends Instrumentation {
    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Context c = getTargetContext();
        DisplayMetrics real = new DisplayMetrics();
        c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(real);
        StringBuilder text = new StringBuilder("resource density=")
                .append(c.getResources().getDisplayMetrics().density).append(" display density=").append(real.density)
                .append(" display pixels=").append(real.widthPixels).append('x').append(real.heightPixels).append('\n');
        AppWidgetManager manager = c.getSystemService(AppWidgetManager.class);
        for (int id : HabitWidget.ids(c)) {
            Bundle options = manager.getAppWidgetOptions(id);
            text.append("widget ").append(id).append(' ');
            for (String key : options.keySet())
                text.append(key).append('=').append(options.get(key)).append(' ');
            text.append('\n');
        }
        Bundle result = new Bundle();
        result.putString("stream", text.toString());
        finish(Activity.RESULT_OK, result);
    }
}
