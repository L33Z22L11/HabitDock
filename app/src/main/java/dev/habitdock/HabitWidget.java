package dev.habitdock;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.*;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HabitWidget extends AppWidgetProvider {
    static final int[] ROWS = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5};
    static final String REFRESH = "dev.habitdock.REFRESH";
    static final String REFRESH_IF_DUE = "dev.habitdock.REFRESH_IF_DUE",
            MANUAL_REFRESH = "dev.habitdock.MANUAL_REFRESH";
    static ComponentName provider(Context context, boolean compact) {
        return new ComponentName(context, compact ? CompactHabitWidget.class : HabitWidget.class);
    }

    static boolean owns(Context context, ComponentName component) {
        for (WidgetSizes.Size size : WidgetSizes.ALL)
            if (new ComponentName(context, size.receiver).equals(component))
                return true;
        return false;
    }

    static int[] ids(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        List<Integer> active = new ArrayList<>();
        for (WidgetSizes.Size size : WidgetSizes.ALL)
            for (int id : manager.getAppWidgetIds(new ComponentName(context, size.receiver)))
                active.add(id);
        Collections.sort(active);
        int[] ids = new int[active.size()];
        for (int i = 0; i < ids.length; i++)
            ids[i] = active.get(i);
        return ids;
    }

    static void refreshIfDue(Context c) {
        long rendered = c.getSharedPreferences("widget_runtime", Context.MODE_PRIVATE).getLong("last_success", 0);
        if (RefreshPolicy.due(c, System.currentTimeMillis()) || rendered != RefreshPolicy.last(c))
            update(c, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (REFRESH.equals(action) || REFRESH_IF_DUE.equals(action) || MANUAL_REFRESH.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            RefreshJob.schedule(context);
            PendingResult pending = goAsync();
            Context app = context.getApplicationContext();
            // Repaints, automatic broadcasts and legacy timestamp taps all obey the same
            // interval.
            Repository.WORK.execute(() -> {
                try {
                    // Check on the serial worker: consecutive clicks see the previous successful
                    // update.
                    if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action))
                        repaint(app);
                    else if (REFRESH_IF_DUE.equals(action) || MANUAL_REFRESH.equals(action))
                        refreshIfDue(app);
                    else
                        update(app, false);
                } catch (RuntimeException e) {
                    // An opportunistic refresh failure should preserve the still-usable widget.
                    if (!REFRESH_IF_DUE.equals(action))
                        message(app, "点此打开知时，检查使用情况权限");
                } finally {
                    pending.finish();
                }
            });
        }
    }

    @Override
    public void onEnabled(Context c) {
        RefreshJob.schedule(c);
    }

    @Override
    public void onDisabled(Context c) {
        RefreshJob.schedule(c);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids)
            WidgetPreferences.remove(c, id);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
    }

    static PendingIntent openApp(Context c) {
        return PendingIntent.getActivity(c, 0, MainActivity.appIntent(c),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static RemoteViews base(Context c) {
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget);
        for (int i = 0; i < ROWS.length; i++) {
            views.removeAllViews(ROWS[i]);
            views.setViewVisibility(ROWS[i], i < 2 ? View.VISIBLE : View.GONE);
        }
        views.setViewVisibility(R.id.empty, View.GONE);
        views.setOnClickPendingIntent(R.id.empty, openApp(c));
        return views;
    }

    static void message(Context c, String text) {
        RemoteViews views = base(c);
        views.setViewVisibility(R.id.empty, View.VISIBLE);
        views.setTextViewText(R.id.empty, text);
        for (int row : ROWS)
            views.setViewVisibility(row, View.GONE);
        AppWidgetManager.getInstance(c).updateAppWidget(ids(c), views);
    }

    static void update(Context c, boolean force) {
        update(c, force, false);
    }

    static void repaint(Context c) {
        update(c, false, true);
    }

    private static void update(Context c, boolean force, boolean appearanceOnly) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        int[] ids = ids(c);
        if (ids.length == 0)
            return;
        int version;
        synchronized (Repository.PRIVACY_LOCK) {
            version = Repository.privacyVersion;
        }
        Repository.Current current;
        if (appearanceOnly) {
            long stamp = RefreshPolicy.last(c), cutoff = stamp == 0 ? System.currentTimeMillis() : stamp;
            current = new Repository.Current(Repository.loadAt(c, cutoff, cutoff, false), stamp);
        } else
            current = Repository.current(c, force);
        Repository.Snapshot data = current.data;
        int max = 0;
        for (int id : ids) {
            WidgetPreferences.ensure(c, id);
            max += WidgetPreferences.load(c, id).capacity();
        }
        Map<String, Bitmap> icons = new HashMap<>();
        List<Predictor.Prediction> eligible = new ArrayList<>();
        for (Predictor.Prediction p : data.predictions) {
            if (icons.size() == max)
                break;
            try {
                if (icons.containsKey(p.pkg) || !data.apps.containsKey(p.pkg)
                        || c.getPackageManager().getLaunchIntentForPackage(p.pkg) == null)
                    continue;
                icons.put(p.pkg, WidgetIcons.source(c.getPackageManager().getApplicationIcon(p.pkg)));
                eligible.add(p);
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            }
        }
        IconStyle style = IconStyle.load(c);
        Map<String, Bitmap> shaped = new HashMap<>();
        for (Map.Entry<String, Bitmap> icon : icons.entrySet())
            shaped.put(icon.getKey(), WidgetIcons.style(c, icon.getValue(), style));
        Map<Integer, Map<String, Bitmap>> scaled = new HashMap<>();
        boolean published = false;
        String stamp = current.stamp == 0
                ? ""
                : Instant.ofEpochMilli(current.stamp).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"));
        int offset = 0;
        for (int id : ids) {
            WidgetPreferences layout = WidgetPreferences.load(c, id);
            List<Predictor.Prediction> page = WidgetAllocation.page(eligible, offset, layout.capacity());
            offset += page.size();
            Repository.Snapshot portion = new Repository.Snapshot(data.apps, page, data.samples, data.days,
                    data.permitted);
            boolean exhausted = !eligible.isEmpty() && page.isEmpty();
            Map<String, Bitmap> fitted = scaled.computeIfAbsent(layout.percent,
                    percent -> WidgetIcons.inset(shaped, percent));
            Bundle options = manager.getAppWidgetOptions(id);
            float width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320);
            float height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120);
            RemoteViews views;
            if (Build.VERSION.SDK_INT >= 31) {
                ArrayList<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
                Map<SizeF, RemoteViews> layouts = new LinkedHashMap<>();
                if (sizes != null)
                    for (SizeF size : sizes) {
                        if (layouts.size() >= 16)
                            break;
                        if (size.getWidth() > 0 && size.getHeight() > 0)
                            layouts.put(size,
                                    renderFitted(c, portion, fitted, stamp, size.getWidth(), size.getHeight(), layout,
                                            id, exhausted));
                    }
                views = layouts.isEmpty()
                        ? renderFitted(c, portion, fitted, stamp, width, height, layout, id, exhausted)
                        : new RemoteViews(layouts);
            } else
                views = renderFitted(c, portion, fitted, stamp, width, height, layout, id, exhausted);
            synchronized (Repository.PRIVACY_LOCK) {
                if (version == Repository.privacyVersion) {
                    manager.updateAppWidget(id, views);
                    published = true;
                }
            }
        }
        synchronized (Repository.PRIVACY_LOCK) {
            if (published && version == Repository.privacyVersion)
                c.getSharedPreferences("widget_runtime", Context.MODE_PRIVATE).edit()
                        .putLong("last_success", current.stamp).apply();
        }
    }

    static RemoteViews render(Context c, Repository.Snapshot data, Map<String, Bitmap> icons, String stamp, float width,
            float height) {
        return render(c, data, icons, stamp, width, height, new WidgetPreferences(5, 2, 82, true));
    }

    static RemoteViews render(Context c, Repository.Snapshot data, Map<String, Bitmap> icons, String stamp, float width,
            float height, WidgetPreferences layout) {
        return render(c, data, icons, stamp, width, height, layout, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    static RemoteViews render(Context c, Repository.Snapshot data, Map<String, Bitmap> icons, String stamp, float width,
            float height, WidgetPreferences layout, int widgetId) {
        return renderFitted(c, data, WidgetIcons.inset(icons, layout.percent), stamp, width, height, layout, widgetId,
                false);
    }

    private static RemoteViews renderFitted(Context c, Repository.Snapshot data, Map<String, Bitmap> icons,
            String stamp, float width, float height, WidgetPreferences layout, int widgetId, boolean exhausted) {
        RemoteViews views = base(c);
        float iconSize = WidgetSizing.iconDp(width, height, layout.columns, layout.rows, layout.percent);
        int index = 0;
        float cellWidth = Math.max(0, width - 16) / layout.columns, cellHeight = Math.max(0, height - 16) / layout.rows;
        for (int i = 0; i < ROWS.length; i++)
            views.setViewVisibility(ROWS[i], i < layout.rows ? View.VISIBLE : View.GONE);
        for (Predictor.Prediction p : data.predictions) {
            if (index == layout.capacity())
                break;
            Bitmap icon = icons.get(p.pkg);
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(p.pkg);
            if (icon == null || launch == null)
                continue;
            RemoteViews cell = new RemoteViews(c.getPackageName(), R.layout.widget_app);
            // fitCenter uses the actual host cell. Percentage lives in transparent
            // bitmap margins, not fixed dp inferred from sometimes-inaccurate options.
            cell.setImageViewBitmap(R.id.app_icon, icon);
            if (layout.actions)
                launch = MainActivity.actionIntent(c, p.pkg, widgetId);
            cell.setContentDescription(R.id.app_cell,
                    data.apps.get(p.pkg).label + "，" + p.reason + (layout.actions ? "，点按显示操作菜单" : ""));
            cell.setOnClickPendingIntent(R.id.app_cell, PendingIntent.getActivity(c, 100, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            views.addView(ROWS[index / layout.columns], cell);
            index++;
        }
        if (index == 0 && !exhausted) {
            for (int row : ROWS)
                views.setViewVisibility(row, View.GONE);
            views.setViewVisibility(R.id.empty, View.VISIBLE);
            views.setTextViewText(R.id.empty, data.permitted ? "正在了解你的习惯\n点此打开知时" : "点此开启使用情况访问权限");
            return views;
        }
        while (index < layout.capacity()) {
            RemoteViews blank = new RemoteViews(c.getPackageName(), R.layout.widget_app);
            blank.setViewVisibility(R.id.app_icon, View.INVISIBLE);
            views.addView(ROWS[index / layout.columns], blank);
            index++;
        }
        if (layout.more) {
            RemoteViews more = new RemoteViews(c.getPackageName(), R.layout.widget_more);
            more.setTextViewText(R.id.more_time, stamp);
            more.setContentDescription(R.id.app_cell, "更多推荐应用");
            more.setOnClickPendingIntent(R.id.app_cell,
                    PendingIntent.getActivity(c, 0, MainActivity.widgetIntent(c, widgetId),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            more.setContentDescription(R.id.more_time, "上次推荐更新于 " + stamp);
            float moreSize = Math.min(28, iconSize * .5f);
            float boxSize = Build.VERSION.SDK_INT >= 31 ? moreSize : 28;
            float fontScale = c.getResources().getConfiguration().fontScale;
            boolean timeVisible = layout.moreTime && cellHeight >= boxSize + 2 * (11 * fontScale * 1.25f + 4)
                    && cellWidth >= 36;
            more.setViewVisibility(R.id.more_time, timeVisible ? View.VISIBLE : View.GONE);
            if (Build.VERSION.SDK_INT >= 31) {
                more.setViewLayoutWidth(R.id.more_icon, moreSize, TypedValue.COMPLEX_UNIT_DIP);
                more.setViewLayoutHeight(R.id.more_icon, moreSize, TypedValue.COMPLEX_UNIT_DIP);
            } else {
                int inset = Ui.dp(c, (28 - moreSize) / 2);
                more.setViewPadding(R.id.more_icon, inset, inset, inset, inset);
            }
            views.addView(ROWS[index / layout.columns], more);
        }
        return views;
    }
}
