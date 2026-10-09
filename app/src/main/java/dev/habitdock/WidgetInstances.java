package dev.habitdock;

import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.SizeF;
import java.util.ArrayList;

/**
 * Binding alone does not mean a host has placed a widget. Never delete another
 * host's IDs.
 */
final class WidgetInstances {
    static boolean hasSize(Bundle options) {
        if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) > 0
                && options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) > 0)
            return true;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            ArrayList<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (sizes != null)
                for (SizeF size : sizes)
                    if (size != null && size.getWidth() > 0 && size.getHeight() > 0)
                        return true;
        }
        return false;
    }

    static boolean active(Context c, int id) {
        android.content.SharedPreferences prefs = Repository.prefs(c);
        String key = "widget." + id + ".";
        return !prefs.getBoolean(key + "hidden", false)
                && (prefs.getBoolean(key + "confirmed", false)
                        || hasSize(AppWidgetManager.getInstance(c).getAppWidgetOptions(id)));
    }

    static void confirm(Context c, int id) {
        Repository.prefs(c).edit().putBoolean("widget." + id + ".confirmed", true).apply();
    }

    static void setHidden(Context c, int id, boolean hidden) {
        Repository.prefs(c).edit().putBoolean("widget." + id + ".hidden", hidden).apply();
        if (!hidden)
            confirm(c, id);
        WidgetAppearance.request(c);
        RefreshJob.schedule(c);
    }

    static void rememberHost(Context c, int id, String pkg) {
        if (id <= 0 || pkg == null || pkg.equals(c.getPackageName()) || pkg.equals("android"))
            return;
        try {
            c.getPackageManager().getApplicationInfo(pkg, 0);
            Repository.prefs(c).edit().putString("widget." + id + ".host", pkg).apply();
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    static String host(Context c, int id) {
        return Repository.prefs(c).getString("widget." + id + ".host", "");
    }

    static String hostLabel(Context c, int id) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(host(c, id), 0))
                    .toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return "未知宿主";
        }
    }

    static Drawable hostIcon(Context c, int id) {
        try {
            return c.getPackageManager().getApplicationIcon(host(c, id));
        } catch (PackageManager.NameNotFoundException ignored) {
            return androidx.appcompat.content.res.AppCompatResources.getDrawable(c, R.drawable.ic_add_widget);
        }
    }
}
