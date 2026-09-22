package dev.habitdock;

import android.content.*;
import android.content.pm.*;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

final class Repository {
    static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    static final Object PRIVACY_LOCK = new Object();
    static int privacyVersion = 0;
    static final class Current {
        final Snapshot data;
        final long stamp;
        Current(Snapshot data, long stamp) {
            this.data = data;
            this.stamp = stamp;
        }
    }
    // Called on WORK. Reconstruct the same recommendation at the saved cutoff after
    // process death.
    // Freezing both history cutoff and decay time keeps opening the app from
    // reshuffling the list.
    static Current current(Context c, boolean force) {
        while (true) {
            int version;
            synchronized (PRIVACY_LOCK) {
                version = privacyVersion;
            }
            long now = System.currentTimeMillis();
            boolean refresh = force || RefreshPolicy.due(c, now);
            long stamp = refresh ? now : RefreshPolicy.last(c);
            Snapshot data = loadAt(c, stamp, stamp, refresh);
            synchronized (PRIVACY_LOCK) {
                if (version == privacyVersion) {
                    if (refresh)
                        RefreshPolicy.succeeded(c, stamp, data.permitted);
                    return new Current(data, stamp);
                }
            }
            force = true;
        }
    }
    static final class App extends AppCatalog.Entry {
        App(String pkg, String label) {
            super(pkg, label);
        }
    }
    static final class Snapshot {
        final Map<String, App> apps;
        final List<Predictor.Prediction> predictions;
        final int samples, days;
        final boolean permitted;
        Snapshot(Map<String, App> apps, List<Predictor.Prediction> predictions, int samples, int days,
                boolean permitted) {
            this.apps = apps;
            this.predictions = predictions;
            this.samples = samples;
            this.days = days;
            this.permitted = permitted;
        }
    }
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static Set<String> selection(Context c, String key) {
        return new HashSet<>(prefs(c).getStringSet(key, Collections.emptySet()));
    }

    static Set<String> eligible(Context c, Set<String> installed) {
        return PrivacyPolicy.eligible(installed, selection(c, "hidden"));
    }

    static void setExcluded(Context c, String pkg, boolean excluded) {
        synchronized (PRIVACY_LOCK) {
            Set<String> hidden = selection(c, "hidden"), pins = selection(c, "pinned");
            if (excluded) {
                hidden.add(pkg);
                pins.remove(pkg);
            } else
                hidden.remove(pkg);
            prefs(c).edit().putStringSet("hidden", hidden).putStringSet("pinned", pins).apply();
            selectionChanged(c);
        }
    }

    static void selectionChanged(Context c) {
        synchronized (PRIVACY_LOCK) {
            privacyVersion++;
            RefreshPolicy.invalidate(c);
            HabitWidget.message(c, "正在更新推荐…");
        }
        WORK.execute(() -> {
            try {
                try (UsageStore store = new UsageStore(c)) {
                    store.enforcePrivacy(eligible(c, apps(c).keySet()));
                    store.sync(System.currentTimeMillis());
                }
                HabitWidget.update(c, false);
            } catch (RuntimeException e) {
                HabitWidget.message(c, "点此打开知时，检查使用情况权限");
            }
        });
    }

    static Map<String, App> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> home = new HashSet<>();
        for (ResolveInfo info : pm
                .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            home.add(info.activityInfo.packageName);
        List<App> found = new ArrayList<>();
        for (ResolveInfo info : pm
                .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || home.contains(pkg))
                continue;
            found.add(new App(pkg, info.loadLabel(pm).toString()));
        }
        found.sort(Comparator.comparing((App a) -> a.label).thenComparing(a -> a.pkg));
        Map<String, App> result = new LinkedHashMap<>();
        for (App app : found)
            result.putIfAbsent(app.pkg, app);
        return result;
    }

    static Snapshot load(Context c, long target, boolean sync) {
        return loadAt(c, System.currentTimeMillis(), target, sync);
    }

    static Snapshot loadAt(Context c, long now, long target, boolean sync) {
        Map<String, App> apps = apps(c);
        boolean permitted = UsageStore.permitted(c);
        List<Predictor.Launch> history = Collections.emptyList();
        if (permitted)
            try (UsageStore db = new UsageStore(c)) {
                if (sync)
                    db.sync(now);
                history = db.read(now);
            }
        Set<String> eligible = eligible(c, apps.keySet());
        List<Predictor.Prediction> predictions = new ArrayList<>();
        Set<String> pinned = selection(c, "pinned");
        for (String pkg : apps.keySet())
            if (pinned.contains(pkg) && eligible.contains(pkg))
                predictions.add(new Predictor.Prediction(pkg, 1, "已固定"));
        for (Predictor.Prediction p : Predictor.rank(history, now, target, ZoneId.systemDefault(), eligible))
            if (!pinned.contains(p.pkg))
                predictions.add(p);
        Set<String> days = new HashSet<>();
        int count = 0;
        for (Predictor.Launch e : history)
            if (eligible.contains(e.pkg)) {
                count++;
                days.add(
                        java.time.Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).toLocalDate().toString());
            }
        return new Snapshot(apps, predictions, count, days.size(), permitted);
    }
}
