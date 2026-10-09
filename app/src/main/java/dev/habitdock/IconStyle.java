package dev.habitdock;

import android.appwidget.AppWidgetManager;
import android.content.*;
import java.util.Arrays;

/** One appearance for every application icon, independent of widget layout. */
final class IconStyle {
    final int roundness, fillColor, fallbackScale, exponent, saturation, brightness, contrast;
    final boolean fillBackground, continuous;
    final String pack;
    IconStyle(int roundness, boolean fillBackground, int fillColor) {
        this(roundness, fillBackground, fillColor, "");
    }

    IconStyle(int roundness, boolean fillBackground, int fillColor, String pack) {
        this(roundness, fillBackground, fillColor, pack, 100);
    }

    IconStyle(int roundness, boolean fillBackground, int fillColor, String pack, int fallbackScale) {
        this(roundness, fillBackground, fillColor, pack, fallbackScale, false, 40);
    }

    IconStyle(int roundness, boolean fillBackground, int fillColor, String pack, int fallbackScale,
            boolean continuous, int exponent) {
        this(roundness, fillBackground, fillColor, pack, fallbackScale, continuous, exponent, 100);
    }

    IconStyle(int roundness, boolean fillBackground, int fillColor, String pack, int fallbackScale,
            boolean continuous, int exponent, int saturation) {
        this(roundness, fillBackground, fillColor, pack, fallbackScale, continuous, exponent, saturation, 100);
    }

    IconStyle(int roundness, boolean fillBackground, int fillColor, String pack, int fallbackScale,
            boolean continuous, int exponent, int saturation, int brightness) {
        this(roundness, fillBackground, fillColor, pack, fallbackScale, continuous, exponent, saturation, brightness,
                100);
    }

    IconStyle(int roundness, boolean fillBackground, int fillColor, String pack, int fallbackScale,
            boolean continuous, int exponent, int saturation, int brightness, int contrast) {
        this.contrast = Math.max(0, Math.min(200, contrast));
        this.brightness = Math.max(0, Math.min(200, brightness));
        this.saturation = Math.max(0, Math.min(200, saturation));
        this.continuous = continuous;
        this.exponent = Math.max(20, Math.min(80, exponent));
        this.fallbackScale = Math.max(50, Math.min(100, fallbackScale));
        this.pack = pack == null ? "" : pack;
        this.roundness = Math.max(0, Math.min(100, roundness));
        this.fillBackground = fillBackground;
        this.fillColor = fillColor == 0 ? 0 : fillColor | 0xff000000;
    }

    String cacheKey(Context c) {
        // App-provided icon resources can themselves differ between light/dark mode.
        return IconPacks.version(c, pack) + ":" + roundness + ":" + fillBackground + ":" + fillColor + ":"
                + fallbackScale + ":" + continuous + ":" + exponent + ":" + saturation + ":" + brightness + ":"
                + contrast + ":"
                + (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
    }

    static IconStyle defaults() {
        return new IconStyle(40, false, 0);
    }

    boolean sameAs(IconStyle other) {
        return roundness == other.roundness && fillBackground == other.fillBackground && fillColor == other.fillColor
                && pack.equals(other.pack) && fallbackScale == other.fallbackScale
                && continuous == other.continuous && exponent == other.exponent && saturation == other.saturation
                && brightness == other.brightness && contrast == other.contrast;
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
        int fill = p.getInt("icon.fill_color", 0);
        if (fill != 0 && fill != android.graphics.Color.WHITE) {
            fill = 0;
            p.edit().putInt("icon.fill_color", 0).apply();
        }
        return new IconStyle(p.getInt("icon.roundness", 40), p.getBoolean("icon.fill_background", false),
                fill, p.getString("icon.pack", ""), p.getInt("icon.fallback_scale", 100),
                p.getBoolean("icon.continuous", false), p.getInt("icon.exponent", 40),
                p.getInt("icon.saturation", 100), p.getInt("icon.brightness", 100), p.getInt("icon.contrast", 100));
    }

    void save(Context c) {
        synchronized (IconStyle.class) {
            Repository.prefs(c).edit().putInt("icon.roundness", roundness)
                    .putString("icon.pack", pack).putBoolean("icon.fill_background", fillBackground)
                    .putInt("icon.fill_color", fillColor).putInt("icon.fallback_scale", fallbackScale)
                    .putBoolean("icon.continuous", continuous).putInt("icon.exponent", exponent)
                    .putInt("icon.saturation", saturation).putInt("icon.brightness", brightness)
                    .putInt("icon.contrast", contrast).apply();
        }
    }
}
