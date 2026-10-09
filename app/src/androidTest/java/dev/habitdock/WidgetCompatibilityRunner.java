package dev.habitdock;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.Xml;
import android.view.*;
import android.widget.*;
import java.io.StringReader;
import java.util.*;

/** Destructive fixtures run only on a disposable emulator. */
public final class WidgetCompatibilityRunner extends Instrumentation {
    private int checks;
    private void check(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
        checks++;
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        AppWidgetHost host = null;
        int status = Activity.RESULT_OK;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("disposable emulator required");
            Context c = getTargetContext();
            Repository.prefs(c).edit().clear().commit();
            AppWidgetManager manager = AppWidgetManager.getInstance(c);
            host = new AppWidgetHost(c, 1950);
            host.deleteHost();
            int placed = host.allocateAppWidgetId(), pending = host.allocateAppWidgetId(),
                    configured = host.allocateAppWidgetId();
            for (int id : new int[]{placed, pending, configured})
                check(manager.bindAppWidgetIdIfAllowed(id, HabitWidget.provider(c, false)), "real widget binding");
            check(HabitWidget.boundIds(c).length == 3 && HabitWidget.ids(c).length == 0,
                    "unplaced bindings are not active");
            Bundle size = new Bundle();
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320);
            size.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160);
            manager.updateAppWidgetOptions(placed, size);
            check(Arrays.equals(HabitWidget.ids(c), new int[]{placed}), "first layout activates a real widget");
            WidgetInstances.confirm(c, configured);
            check(HabitWidget.ids(c).length == 2, "explicit configuration supports hosts with no size hints");
            runOnMainSync(() -> WidgetInstances.setHidden(c, configured, true));
            manager.updateAppWidgetOptions(configured, size);
            check(Arrays.equals(HabitWidget.ids(c), new int[]{placed}), "hidden remains excluded after size updates");
            runOnMainSync(() -> WidgetInstances.setHidden(c, configured, false));
            check(HabitWidget.ids(c).length == 2, "hidden can be restored");
            for (int id : new int[]{placed, pending, configured}) {
                WidgetPreferences.ensure(c, id);
                check(WidgetPreferences.load(c, id).more, "binding, activation and hiding never turn off more");
            }
            WidgetPreferences chosen = new WidgetPreferences(3, 3, 82, false);
            chosen.save(c, placed);
            WidgetPreferences.ensure(c, placed);
            check(!WidgetPreferences.load(c, placed).more, "explicitly disabled more remains disabled");
            WidgetInstances.rememberHost(c, placed, getContext().getPackageName());
            check(WidgetInstances.host(c, placed).equals(getContext().getPackageName())
                    && WidgetInstances.hostIcon(c, placed) != null, "recorded host has an icon");
            check(!RefreshPolicy.due(c, RefreshPolicy.last(c)) || RefreshPolicy.last(c) == 0,
                    "metadata does not force a new timestamp");
            Repository.Snapshot empty = new Repository.Snapshot(Map.of(), List.of(), 0, 0, true);
            runOnMainSync(() -> {
                View root = HabitWidget.render(c, empty, Map.of(), "12:34", 320, 160,
                        new WidgetPreferences(3, 3, 82, true)).apply(c, new FrameLayout(c));
                check(root.findViewById(R.id.more_icon) != null, "empty recommendations still render more");
                check(root.findViewById(R.id.empty).getVisibility() == View.GONE, "empty overlay does not cover more");
            });
            String pack = getContext().getPackageName();
            check(IconPacks.installed(c).stream().anyMatch(item -> item.pkg.equals(pack)),
                    "discover installed compatible pack");
            Bitmap themed = IconPacks.source(c, c.getPackageName(), pack);
            check(themed.getPixel(64, 64) == 0xffff3344, "exact launcher component beats package fallback");
            Bitmap original = IconPacks.source(c, c.getPackageName(), "");
            check(IconPacks.source(c, c.getPackageName(), "missing.pack").sameAs(original),
                    "missing pack falls back to original");
            String settings = "com.android.settings";
            check(IconPacks.source(c, settings, pack).sameAs(IconPacks.source(c, settings, "")),
                    "missing drawable falls back to original");
            org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(
                    "<resources><calendar component='ComponentInfo{x.y/.Main}' prefix='day_'/><item component='bad' drawable='x'/></resources>"));
            Map<String, String> mapping = IconPacks.parse(parser);
            check(mapping.size() == 1 && "@calendar:day_".equals(mapping.get("x.y/x.y.Main")),
                    "calendar and abbreviated activity mapping");
            IconStyle style = new IconStyle(0, false, 0, pack);
            style.save(c);
            check(IconStyle.load(c).sameAs(style), "pack persists with global icon style");
            check(!style.cacheKey(c).equals(new IconStyle(0, false, 0).cacheKey(c)),
                    "pack selection changes icon cache key");
            ImageView[] target = {null};
            runOnMainSync(() -> {
                target[0] = new ImageView(c);
                IconLoader.bind(c, c.getPackageName(), target[0], style);
            });
            boolean[] loaded = {false};
            for (int i = 0; i < 100 && !loaded[0]; i++) {
                Thread.sleep(40);
                runOnMainSync(() -> {
                    if (target[0].getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
                        loaded[0] = ((android.graphics.drawable.BitmapDrawable) target[0].getDrawable()).getBitmap()
                                .getPixel(64, 64) == 0xffff3344;
                });
            }
            check(loaded[0], "shared application icon loader displays selected pack");
            result.putString("stream", "\nPASS: " + checks + " icon-pack/widget-host checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
        } finally {
            if (host != null)
                host.deleteHost();
        }
        finish(status, result);
    }
}
