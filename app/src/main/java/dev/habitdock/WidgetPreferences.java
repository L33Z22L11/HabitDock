package dev.habitdock;

import android.content.*;
import android.appwidget.AppWidgetManager;

final class WidgetPreferences {
    static final int COMPACT_DEFAULT = -2;
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

    static int defaultId(int desktopColumns) {
        return desktopColumns == 2 ? COMPACT_DEFAULT : AppWidgetManager.INVALID_APPWIDGET_ID;
    }

    private static int templateId(Context c, int id) {
        if (id <= 0)
            return id;
        android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
        return info != null && HabitWidget.provider(c, true).equals(info.provider)
                ? COMPACT_DEFAULT
                : AppWidgetManager.INVALID_APPWIDGET_ID;
    }

    static WidgetPreferences defaults(Context c, int id) {
        return templateId(c, id) == COMPACT_DEFAULT ? new WidgetPreferences(3, 3, 82, true) : defaults();
    }

    boolean sameAs(WidgetPreferences other) {
        return columns == other.columns && rows == other.rows && percent == other.percent && more == other.more
                && actions == other.actions && moreTime == other.moreTime;
    }

    private static String key(int id) {
        if (id == COMPACT_DEFAULT)
            return "widget.compact.default.";
        return id == AppWidgetManager.INVALID_APPWIDGET_ID ? "widget.default." : "widget." + id + ".";
    }

    static WidgetPreferences load(Context c, int id) {
        SharedPreferences p = Repository.prefs(c);
        int template = templateId(c, id);
        String prefix = key(id), defaults = key(template);
        boolean compact = template == COMPACT_DEFAULT;
        return new WidgetPreferences(p.getInt(prefix + "columns", p.getInt(defaults + "columns", compact ? 3 : 5)),
                p.getInt(prefix + "rows", p.getInt(defaults + "rows", compact ? 3 : 2)),
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
                "more_time", "fill_color", "name"})
            edit.remove(prefix + name);
        edit.apply();
    }
}
