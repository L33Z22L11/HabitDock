package dev.habitdock;

import android.content.*;

/** One refresh clock for the app, widget clicks and periodic work. */
final class RefreshPolicy {
    static int minutes(Context c) {
        return Math.max(15, Math.min(1440, Repository.prefs(c).getInt("refresh_minutes", 30)));
    }

    static long interval(Context c) {
        return minutes(c) * 60_000L;
    }

    static SharedPreferences runtime(Context c) {
        return c.getSharedPreferences("recommendation_runtime", Context.MODE_PRIVATE);
    }

    static long last(Context c) {
        return runtime(c).getLong("last_success", 0);
    }

    static boolean due(Context c, long now) {
        long last = last(c);
        return last == 0 || now < last || now - last >= interval(c)
                || runtime(c).getBoolean("permitted", false) != UsageStore.permitted(c);
    }

    static void invalidate(Context c) {
        runtime(c).edit().remove("last_success").apply();
    }

    static void succeeded(Context c, long stamp, boolean permitted) {
        runtime(c).edit().putLong("last_success", stamp).putBoolean("permitted", permitted).apply();
    }
}
