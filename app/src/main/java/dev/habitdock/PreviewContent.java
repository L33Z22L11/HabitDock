package dev.habitdock;

import android.content.Context;
import android.graphics.Bitmap;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Read-only local preview: never learns, refreshes or changes the
 * recommendation clock.
 */
final class PreviewContent {
    final List<Boolean> themed = new ArrayList<>();
    final List<Bitmap> sources = new ArrayList<>();
    final List<String> labels = new ArrayList<>();
    final List<String> packages = new ArrayList<>();
    long stamp;
    String time() {
        return stamp == 0
                ? ""
                : Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    static PreviewContent load(Context c, String pack, int widgetId, int limit, boolean fallback) {
        return load(c, pack, widgetId, limit, fallback, 0);
    }

    static PreviewContent load(Context c, String pack, int widgetId, int limit, boolean fallback, int sampleOffset) {
        PreviewContent result = new PreviewContent();
        result.stamp = RefreshPolicy.last(c);
        try {
            Repository.Snapshot data = Repository.loadAt(c,
                    result.stamp == 0 ? System.currentTimeMillis() : result.stamp,
                    result.stamp == 0 ? System.currentTimeMillis() : result.stamp, false);
            List<String> packages = new ArrayList<>();
            for (Predictor.Prediction prediction : data.predictions)
                if (data.apps.containsKey(prediction.pkg)
                        && c.getPackageManager().getLaunchIntentForPackage(prediction.pkg) != null)
                    packages.add(prediction.pkg);
            if (fallback && packages.size() < limit + sampleOffset) {
                List<Repository.App> apps = AppCatalog.filter(new ArrayList<>(data.apps.values()), Set.of(), "");
                Set<String> hidden = Repository.selection(c, "hidden");
                for (Repository.App app : apps)
                    if (!hidden.contains(app.pkg) && !packages.contains(app.pkg))
                        packages.add(app.pkg);
            }
            int offset = Math.max(0, sampleOffset);
            if (widgetId > 0) {
                for (int id : HabitWidget.ids(c)) {
                    if (id == widgetId)
                        break;
                    offset += WidgetPreferences.load(c, id).capacity();
                }
            }
            for (int i = Math.min(offset, packages.size()); i < packages.size() && result.sources.size() < limit; i++) {
                String pkg = packages.get(i);
                try {
                    IconPacks.Source source = IconPacks.loadSource(c, pkg, pack);
                    result.sources.add(source.bitmap);
                    result.themed.add(source.themed);
                    result.labels.add(data.apps.get(pkg).label);
                    result.packages.add(pkg);
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                }
            }
        } catch (RuntimeException ignored) {
        }
        return result;
    }
}
