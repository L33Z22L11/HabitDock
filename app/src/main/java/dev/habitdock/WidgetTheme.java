package dev.habitdock;

import android.content.*;
import android.content.res.*;
import android.graphics.Color;
import android.os.Build;
import androidx.core.graphics.ColorUtils;

/**
 * Widget surface settings are independent of the application's theme and icons.
 */
final class WidgetTheme {
    static final int SYSTEM = 0, LIGHT = 1, DARK = 2, DEVICE = 3, CUSTOM = 4;
    static final String[] LABELS = {"系统", "浅色", "深色", "设备", "自定义"};
    final int mode, transparency, color;

    WidgetTheme(int mode, int transparency, int color) {
        this.mode = Math.max(SYSTEM, Math.min(CUSTOM, mode));
        this.transparency = Math.max(0, Math.min(100, transparency));
        this.color = color | 0xff000000;
    }

    static int previewId(Context context) {
        int[] active = HabitWidget.ids(context);
        int preferred = Repository.prefs(context).getInt("widget.preview_id", active.length > 0 ? active[0] : 0);
        if (preferred == 0 || preferred == WidgetPreferences.COMPACT_DEFAULT)
            return preferred;
        for (int id : active)
            if (id == preferred)
                return id;
        return active.length > 0 ? active[0] : 0;
    }

    static WidgetTheme defaults() {
        return new WidgetTheme(SYSTEM, 60, 0xffdce8df);
    }

    static WidgetTheme load(Context context, int id) {
        SharedPreferences prefs = Repository.prefs(context);
        String key = WidgetPreferences.key(id),
                template = WidgetPreferences.key(WidgetPreferences.templateId(context, id));
        WidgetTheme defaults = defaults();
        return new WidgetTheme(prefs.getInt(key + "theme", prefs.getInt(template + "theme", defaults.mode)),
                prefs.getInt(key + "transparency", prefs.getInt(template + "transparency", defaults.transparency)),
                prefs.getInt(key + "color", prefs.getInt(template + "color", defaults.color)));
    }

    void save(Context context, int id) {
        String key = WidgetPreferences.key(id);
        Repository.prefs(context).edit().putInt(key + "theme", mode).putInt(key + "transparency", transparency)
                .putInt(key + "color", color).apply();
    }

    static void ensure(Context context, int id) {
        if (!Repository.prefs(context).contains(WidgetPreferences.key(id) + "theme"))
            load(context, id).save(context, id);
    }

    boolean sameAs(WidgetTheme other) {
        return other != null && mode == other.mode && transparency == other.transparency && color == other.color;
    }

    boolean dark() {
        return mode == DARK || (mode != LIGHT && mode != CUSTOM
                && (Resources.getSystem().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }

    int surface(Context context) {
        if (mode == CUSTOM)
            return color;
        if (mode == DEVICE && Build.VERSION.SDK_INT >= 31)
            return context.getColor(dark() ? android.R.color.system_accent1_800 : android.R.color.system_accent1_100);
        return dark() ? 0xff171d1b : Color.WHITE;
    }

    int background(Context context) {
        return ColorUtils.setAlphaComponent(surface(context), Math.round(255 * (100 - transparency) / 100f));
    }

    void applySurface(Context context, android.widget.RemoteViews views) {
        if (Build.VERSION.SDK_INT >= 31) {
            // Launcher animates the actual background drawable, not ImageView content.
            views.setInt(android.R.id.background, "setBackgroundResource", R.drawable.widget_bg);
            views.setColorStateList(android.R.id.background, "setBackgroundTintList",
                    ColorStateList.valueOf(hostBackground(context, false)),
                    ColorStateList.valueOf(hostBackground(context, true)));
            views.setViewVisibility(R.id.widget_background, android.view.View.GONE);
        } else {
            views.setInt(R.id.widget_background, "setColorFilter", surface(context));
            views.setInt(R.id.widget_background, "setImageAlpha", Color.alpha(background(context)));
        }
        applyForeground(context, views, R.id.empty, "setTextColor");
    }

    private int hostBackground(Context context, boolean night) {
        int base = surface(context);
        if (mode == SYSTEM)
            base = night ? 0xff171d1b : Color.WHITE;
        else if (mode == DEVICE && Build.VERSION.SDK_INT >= 31)
            base = context.getColor(night ? android.R.color.system_accent1_800 : android.R.color.system_accent1_100);
        return ColorUtils.setAlphaComponent(base, Math.round(255 * (100 - transparency) / 100f));
    }

    void applyForeground(Context context, android.widget.RemoteViews views, int id, String method) {
        if (Build.VERSION.SDK_INT >= 31 && mode == DEVICE)
            views.setColor(id, method, R.color.widget_device_ink);
        else if (Build.VERSION.SDK_INT >= 31 && mode == SYSTEM)
            views.setColorInt(id, method, 0xff202823, 0xfff3f5f4);
        else
            views.setInt(id, method, foreground(context));
    }

    int foreground(Context context) {
        if (mode == DEVICE && Build.VERSION.SDK_INT >= 31)
            return context.getColor(dark() ? android.R.color.system_accent1_100 : android.R.color.system_accent1_800);
        return ColorUtils.calculateLuminance(surface(context)) < .3 ? 0xfff3f5f4 : 0xff202823;
    }
}
