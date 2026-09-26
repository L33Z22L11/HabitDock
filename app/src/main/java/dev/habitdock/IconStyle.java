package dev.habitdock;

import android.appwidget.AppWidgetManager;
import android.content.*;
import java.util.Arrays;

/** One appearance for every application icon, independent of widget layout. */
final class IconStyle {
    final int roundness, fillColor;
    final boolean fillBackground;
    IconStyle(int roundness, boolean fillBackground, int fillColor) {
        this.roundness = Math.max(0, Math.min(100, roundness));
        this.fillBackground = fillBackground;
        this.fillColor = fillColor == 0 ? 0 : fillColor | 0xff000000;
    }

    String cacheKey(Context c) {
        // App-provided icon resources can themselves differ between light/dark mode.
        return roundness + ":" + fillBackground + ":" + fillColor + ":"
                + (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
    }

    static IconStyle defaults() {
        return new IconStyle(40, false, 0);
    }

    boolean sameAs(IconStyle other) {
        return roundness == other.roundness && fillBackground == other.fillBackground && fillColor == other.fillColor;
    }

    static synchronized IconStyle load(Context c) {
        SharedPreferences p = Repository.prefs(c);
        if (!p.contains("icon.roundness")) {
            // Keep the style shown on the recommendation page before upgrading.
            int[] ids = HabitWidget.ids(c);
            Arrays.sort(ids);
            int fallback = ids.length == 0 ? AppWidgetManager.INVALID_APPWIDGET_ID : ids[0];
            int id = p.getInt("recommendation_style_widget", fallback);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && Arrays.binarySearch(ids, id) < 0)
                id = fallback;
            String defaults = "widget.default.",
                    prefix = id == AppWidgetManager.INVALID_APPWIDGET_ID ? defaults : "widget." + id + ".";
            new IconStyle(p.getInt(prefix + "roundness", p.getInt(defaults + "roundness", 40)),
                    p.getBoolean(prefix + "fill_background", p.getBoolean(defaults + "fill_background", false)),
                    p.getInt(prefix + "fill_color", p.getInt(defaults + "fill_color", 0))).save(c);
        }
        return new IconStyle(p.getInt("icon.roundness", 40), p.getBoolean("icon.fill_background", false),
                p.getInt("icon.fill_color", 0));
    }

    void save(Context c) {
        synchronized (IconStyle.class) {
            Repository.prefs(c).edit().putInt("icon.roundness", roundness)
                    .putBoolean("icon.fill_background", fillBackground).putInt("icon.fill_color", fillColor).apply();
        }
    }
}
