package dev.habitdock;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

final class Appearance {
    static final int SYSTEM = 0, LIGHT = 1, DARK = 2, DEVICE = 3, CUSTOM = 4;

    static int style(Context c) {
        android.content.SharedPreferences prefs = Repository.prefs(c);
        if (prefs.contains("appearance.style"))
            return Math.max(SYSTEM, Math.min(CUSTOM, prefs.getInt("appearance.style", SYSTEM)));
        if (prefs.getBoolean("appearance.dynamic", true))
            return DEVICE;
        int mode = prefs.getInt("appearance.mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        return mode == AppCompatDelegate.MODE_NIGHT_YES
                ? DARK
                : mode == AppCompatDelegate.MODE_NIGHT_NO ? LIGHT : SYSTEM;
    }

    static int colorMode(Context c) {
        return Repository.prefs(c).getInt("appearance.color_mode", legacyColorMode(c));
    }

    private static int legacyColorMode(Context c) {
        int old = style(c);
        return old == LIGHT ? 1 : old == DARK ? 2 : 0;
    }

    static int scheme(Context c) {
        int old = style(c);
        return Repository.prefs(c).getInt("appearance.scheme", old == DEVICE ? 1 : old == CUSTOM ? 2 : 0);
    }

    static void configure(Context c, int mode, int scheme) {
        Repository.prefs(c).edit().putInt("appearance.color_mode", mode).putInt("appearance.scheme", scheme).apply();
        AppCompatDelegate.setDefaultNightMode(mode(c));
    }

    static int mode(Context c) {
        return colorMode(c) == 2
                ? AppCompatDelegate.MODE_NIGHT_YES
                : colorMode(c) == 1 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    static boolean dynamic(Context c) {
        return scheme(c) == 1;
    }

    static boolean supportsDynamic() {
        return DynamicColors.isDynamicColorAvailable();
    }

    static int seed(Context c) {
        return Repository.prefs(c).getInt("appearance.seed", 0xff246e5c) | 0xff000000;
    }

    static String key(Context c) {
        return colorMode(c) + ":" + scheme(c) + ":"
                + (scheme(c) == 2
                        ? Integer.toString(seed(c))
                        : scheme(c) == 1 ? String.valueOf(automaticSeed(c)) : "default");
    }

    static String modeLabel(Context c) {
        return mode(c) == AppCompatDelegate.MODE_NIGHT_YES
                ? "深色"
                : mode(c) == AppCompatDelegate.MODE_NIGHT_NO ? "浅色" : "跟随系统";
    }

    static Integer automaticSeed(Context context) {
        if (MainStyle.load(context).background != MainStyle.CUSTOM_IMAGE)
            return null;
        return BackgroundImages.paletteSeed(context, BackgroundImages.selected(context, MainStyle.CUSTOM_IMAGE));
    }

    static void apply(Activity activity) {
        Integer source = scheme(activity) == 2
                ? Integer.valueOf(seed(activity))
                : scheme(activity) == 1 ? automaticSeed(activity) : null;
        if (source != null)
            DynamicColors.applyToActivityIfAvailable(activity,
                    new DynamicColorsOptions.Builder().setContentBasedSource(source).build());
        else if (scheme(activity) == 1)
            DynamicColors.applyToActivityIfAvailable(activity);
    }
}
