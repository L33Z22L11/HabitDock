package dev.habitdock;

import android.app.job.*;
import android.content.*;
import android.appwidget.AppWidgetManager;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RefreshJob extends JobService {
    private AtomicBoolean stopped;
    static boolean needed(Context c) {
        return UsageStore.permitted(c) && AppWidgetManager.getInstance(c)
                .getAppWidgetIds(new ComponentName(c, HabitWidget.class)).length > 0;
    }

    static void schedule(Context c) {
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null)
            return;
        if (!needed(c)) {
            scheduler.cancel(1919);
            return;
        }
        // Do not restart the interval on every settings visit or widget update.
        long interval = RefreshPolicy.interval(c), flex = Math.max(5 * 60_000L, interval / 3);
        JobInfo existing = scheduler.getPendingJob(1919);
        if (existing == null || existing.getIntervalMillis() != interval)
            scheduler.schedule(new JobInfo.Builder(1919, new ComponentName(c, RefreshJob.class))
                    .setPeriodic(interval, flex).setPersisted(true).build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!needed(this)) {
            schedule(this);
            return false;
        }
        AtomicBoolean token = new AtomicBoolean(false);
        stopped = token;
        Repository.WORK.execute(() -> {
            boolean retry = false;
            try {
                if (!token.get()) {
                    if (!token.get())
                        HabitWidget.refreshIfDue(this);
                }
            } catch (RuntimeException e) {
                retry = true;
            }
            if (!token.get())
                jobFinished(params, retry);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (stopped != null)
            stopped.set(true);
        return true;
    }
}
