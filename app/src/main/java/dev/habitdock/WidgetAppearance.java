package dev.habitdock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Main-thread requests; coalesce slider events and keep at most one repaint in
 * flight.
 */
final class WidgetAppearance {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable APPLY = WidgetAppearance::apply;
    // Assigned only from getApplicationContext(); never retains an Activity or
    // View.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Context pending;
    private static boolean running;

    static void request(Context context) {
        boolean queued = pending != null;
        pending = context.getApplicationContext();
        if (!running && !queued) {
            MAIN.postDelayed(APPLY, 150);
        }
    }

    static void flush() {
        MAIN.removeCallbacks(APPLY);
        apply();
    }

    private static void apply() {
        if (running || pending == null)
            return;
        Context context = pending;
        pending = null;
        running = true;
        Repository.WORK.execute(() -> {
            try {
                HabitWidget.repaint(context);
            } catch (RuntimeException error) {
                MAIN.post(() -> Ui.toast(context, "设置已保存，组件暂未更新"));
            } finally {
                MAIN.post(() -> {
                    running = false;
                    if (pending != null)
                        MAIN.postDelayed(APPLY, 150);
                });
            }
        });
    }
}
