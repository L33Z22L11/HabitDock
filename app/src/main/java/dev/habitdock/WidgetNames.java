package dev.habitdock;

import android.appwidget.AppWidgetManager;
import android.content.*;
import java.util.Arrays;

/**
 * Local names identify instances; launcher-owned labels are package resources.
 */
final class WidgetNames {
    static String get(Context context, int id) {
        return Repository.prefs(context).getString("widget." + id + ".name", "");
    }

    static void save(Context context, int id, String value) {
        if (id <= 0)
            return;
        String name = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (name.codePointCount(0, name.length()) > 40)
            name = name.substring(0, name.offsetByCodePoints(0, 40));
        SharedPreferences.Editor edit = Repository.prefs(context).edit();
        String key = "widget." + id + ".name";
        if (name.isEmpty())
            edit.remove(key);
        else
            edit.putString(key, name);
        edit.apply();
    }

    static String label(Context context, int id) {
        if (id == WidgetPreferences.COMPACT_DEFAULT)
            return "2×2 默认布局";
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID)
            return "4×2 默认布局";
        String name = get(context, id);
        if (!name.isEmpty())
            return name;
        int[] ids = HabitWidget.ids(context);
        Arrays.sort(ids);
        int position = Arrays.binarySearch(ids, id);
        return position < 0 ? "桌面组件" : "桌面组件 " + (position + 1);
    }
}
