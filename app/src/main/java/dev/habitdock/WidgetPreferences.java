package dev.habitdock;

import android.content.*;
import android.appwidget.AppWidgetManager;

final class WidgetPreferences {
    final int columns, rows, percent;
    final boolean more, moreTime, actions;
    WidgetPreferences(int columns, int rows, int percent, boolean more) {
        this(columns, rows, percent, more, false);
    }

    WidgetPreferences(int columns, int rows, int percent, boolean more, boolean actions) {
        this(columns, rows, percent, more, actions, true);
    }

    WidgetPreferences(int columns, int rows, int percent, boolean more, boolean actions, boolean moreTime) {
        this.columns = Math.max(2, Math.min(6, columns));
        this.rows = Math.max(1, Math.min(5, rows));
        this.percent = Math.max(50, Math.min(100, percent));
        this.more = more;
        this.moreTime = moreTime;
        this.actions = actions;
    }

    int capacity() {
        return columns * rows - (more ? 1 : 0);
    }

    static WidgetPreferences defaults() {
        return new WidgetPreferences(5, 2, 82, true, false, true);
    }

    boolean sameAs(WidgetPreferences other) {
        return columns == other.columns && rows == other.rows && percent == other.percent && more == other.more
                && actions == other.actions && moreTime == other.moreTime;
    }

    private static String key(int id) {
        return id == AppWidgetManager.INVALID_APPWIDGET_ID ? "widget.default." : "widget." + id + ".";
    }

    static WidgetPreferences load(Context c, int id) {
        SharedPreferences p = Repository.prefs(c);
        String prefix = key(id), defaults = key(AppWidgetManager.INVALID_APPWIDGET_ID);
        return new WidgetPreferences(p.getInt(prefix + "columns", p.getInt(defaults + "columns", 5)),
                p.getInt(prefix + "rows", p.getInt(defaults + "rows", 2)),
                p.getInt(prefix + "percent", p.getInt(defaults + "percent", 82)),
                p.getBoolean(prefix + "more", p.getBoolean(defaults + "more", true)),
                p.getBoolean(prefix + "actions", p.getBoolean(defaults + "actions", false)),
                p.getBoolean(prefix + "more_time", p.getBoolean(defaults + "more_time", true)));
    }

    void save(Context c, int id) {
        synchronized (WidgetPreferences.class) {
            String prefix = key(id);
            Repository.prefs(c).edit().putInt(prefix + "columns", columns).putInt(prefix + "rows", rows)
                    .putInt(prefix + "percent", percent).putBoolean(prefix + "more", more)
                    .putBoolean(prefix + "actions", actions).putBoolean(prefix + "more_time", moreTime).apply();
        }
    }

    static void ensure(Context c, int id) {
        synchronized (WidgetPreferences.class) {
            if (!Repository.prefs(c).contains(key(id) + "more_time"))
                load(c, id).save(c, id);
        }
    }

    static void remove(Context c, int id) {
        IconStyle.load(c);
        String prefix = key(id);
        SharedPreferences.Editor edit = Repository.prefs(c).edit();
        for (String name : new String[]{"columns", "rows", "percent", "more", "actions", "roundness", "fill_background",
                "more_time", "fill_color"})
            edit.remove(prefix + name);
        edit.apply();
    }
}
