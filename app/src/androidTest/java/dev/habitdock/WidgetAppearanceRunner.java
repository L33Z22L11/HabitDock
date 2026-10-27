package dev.habitdock;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.database.DataSetObserver;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.google.android.material.slider.Slider;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Disposable emulator only: widget appearance persists through the real
 * rendering path.
 */
public final class WidgetAppearanceRunner extends Instrumentation {
    private volatile Activity current;
    private int checks;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        current = activity;
    }

    private void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }

    private View view(String tag) {
        return current.getWindow().getDecorView().findViewWithTag(tag);
    }

    private void click(String tag) {
        runOnMainSync(() -> view(tag).performClick());
        SystemClock.sleep(350);
        waitForIdleSync();
    }

    private void choose(String text) {
        for (int attempt = 0; attempt < 60; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null)
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                    if ((text.contentEquals(node.getText() == null ? "" : node.getText())
                            || text.contentEquals(
                                    node.getContentDescription() == null ? "" : node.getContentDescription()))
                            && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        SystemClock.sleep(250);
                        waitForIdleSync();
                        return;
                    }
            SystemClock.sleep(50);
        }
        throw new AssertionError("missing dialog option " + text);
    }

    private void screenshot(String name) throws Exception {
        SystemClock.sleep(400);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(
                new java.io.File(getTargetContext().getCacheDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }

    private void recreate() {
        ActivityMonitor monitor = addMonitor(current.getClass().getName(), null, false);
        runOnMainSync(() -> current.recreate());
        current = waitForMonitorWithTimeout(monitor, 8000);
        removeMonitor(monitor);
        check(current != null, "appearance recreation completes");
        waitForIdleSync();
    }

    private View background(Context context, int id) {
        Repository.Snapshot empty = new Repository.Snapshot(Collections.emptyMap(), Collections.emptyList(), 0, 0,
                true);
        View remote = HabitWidget.render(context, empty, Collections.emptyMap(), "12:00", 320, 160,
                WidgetPreferences.load(context, id), id).apply(context, new FrameLayout(context));
        remote.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY));
        remote.layout(0, 0, 640, 320);
        ImageView more = remote.findViewById(R.id.more_icon);
        check(more != null && more.getImageAlpha() == 255, "background transparency preserves the more icon");
        if (Build.VERSION.SDK_INT >= 31) {
            check(remote.getBackground() instanceof android.graphics.drawable.GradientDrawable,
                    "launcher animation sees the real rounded background");
            check(remote.findViewById(R.id.widget_background).getVisibility() == View.GONE,
                    "widget background is not painted twice");
            return remote;
        }
        return remote.findViewById(R.id.widget_background);
    }

    private int pixel(View image) {
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        if (image instanceof ImageView)
            image.draw(new Canvas(bitmap));
        else {
            image.getBackground().setBounds(0, 0, image.getWidth(), image.getHeight());
            image.getBackground().draw(new Canvas(bitmap));
        }
        int color = bitmap.getPixel(image.getWidth() / 2, image.getHeight() / 2);
        bitmap.recycle();
        return color;
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        AppWidgetHost host = null;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("this runner is emulator-only");
            Context context = getTargetContext();
            Repository.prefs(context).edit().clear().commit();
            IconStyle.defaults().save(context);
            host = new AppWidgetHost(context, 1960);
            host.deleteHost();
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int first = host.allocateAppWidgetId(), second = host.allocateAppWidgetId();
            check(manager.bindAppWidgetIdIfAllowed(first, HabitWidget.provider(context, false))
                    && manager.bindAppWidgetIdIfAllowed(second, HabitWidget.provider(context, true)),
                    "bind test widgets");
            WidgetInstances.confirm(context, first);
            WidgetInstances.confirm(context, second);
            WidgetTheme original = new WidgetTheme(WidgetTheme.LIGHT, 25, 0xffdce8df);
            WidgetTheme other = new WidgetTheme(WidgetTheme.DARK, 70, 0xffdce8df);
            original.save(context, first);
            other.save(context, second);
            SystemClock.sleep(400);
            Repository.WORK.submit(() -> {
            }).get();
            long stamp = System.currentTimeMillis();
            RefreshPolicy.succeeded(context, stamp, UsageStore.permitted(context));
            current = startActivitySync(
                    new Intent(context, WidgetSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check((current.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0,
                    "preview shows the system wallpaper without reading its file");
            check(view("widget-theme-1").isSelected() && !view("widget-save").isShown(),
                    "opening preserves the widget theme");
            click("widget-theme-3");
            runOnMainSync(() -> ((Slider) view("widget-transparency")).setValue(75));
            check(WidgetTheme.load(context, first).mode == WidgetTheme.DEVICE
                    && WidgetTheme.load(context, first).transparency == 75
                    && WidgetTheme.load(context, second).sameAs(other),
                    "theme and transparency save immediately for only the selected widget");
            check(view("widget-save").isShown(), "theme edits expose undo");
            screenshot("widget-appearance-device");
            check(view("widget-full-preview") == null, "redundant desktop preview button removed");
            recreate();
            check(WidgetTheme.load(context, first).transparency == 75, "theme survives recreation");
            click("widget-save");
            choose("恢复上次");
            check(WidgetTheme.load(context, first).sameAs(original) && WidgetTheme.load(context, second).sameAs(other),
                    "undo restores the opening theme across recreation without changing other widgets");
            click("widget-theme-4");
            check(view("widget-custom-color").isShown(), "custom theme exposes its color picker");
            click("widget-custom-color");
            choose("选择 #DCE8FF");
            choose("完成");
            check(WidgetTheme.load(context, first).color == 0xffdce8ff, "custom background uses the selected color");
            click("widget-custom-color");
            choose("选择 #252B29");
            choose("取消");
            check(WidgetTheme.load(context, first).color == 0xffdce8ff, "cancel restores the opening custom color");
            click("widget-layout-toggle");
            check(view("widget-columns").isShown(), "layout and entry controls expand inside the appearance editor");
            runOnMainSync(() -> ((Slider) view("widget-columns")).setValue(4));
            check(WidgetPreferences.load(context, first).columns == 4
                    && WidgetTheme.load(context, first).mode == WidgetTheme.CUSTOM,
                    "layout and theme changes coexist");
            click("widget-save");
            choose("恢复默认");
            check(WidgetTheme.load(context, first).sameAs(WidgetTheme.defaults())
                    && WidgetPreferences.load(context, first).sameAs(WidgetPreferences.defaults()),
                    "defaults restore both layout and theme");
            runOnMainSync(() -> {
                new WidgetTheme(WidgetTheme.CUSTOM, 100, Color.RED).save(context, first);
                View transparent = background(context, first);
                check(Color.alpha(pixel(transparent)) == 0,
                        "100 percent removes only the widget background");
                new WidgetTheme(WidgetTheme.CUSTOM, 0, Color.RED).save(context, first);
                View opaque = background(context, first);
                check(pixel(opaque) == Color.RED,
                        "zero transparency renders the actual custom color");
            });
            WidgetTheme pinned = new WidgetTheme(WidgetTheme.DEVICE, 42, 0xffdce8ff);
            Intent callback = WidgetPinRequest.callback(context, HabitWidget.provider(context, true),
                    new WidgetPreferences(3, 3, 82, true), "测试组件", pinned);
            callback.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, second);
            check(WidgetPinRequest.apply(context, callback) && WidgetTheme.load(context, second).sameAs(pinned),
                    "launcher pin callback preserves the previewed theme and transparency");
            WidgetPreferences.ensure(context, second);
            new WidgetTheme(WidgetTheme.CUSTOM, 80, Color.GREEN).save(context, WidgetPreferences.COMPACT_DEFAULT);
            check(WidgetTheme.load(context, second).sameAs(pinned),
                    "editing defaults leaves existing widgets unchanged");
            runOnMainSync(() -> {
                RecommendationList list = new RecommendationList(current);
                AtomicInteger updates = new AtomicInteger();
                list.getAdapter().registerDataSetObserver(new DataSetObserver() {
                    @Override
                    public void onChanged() {
                        updates.incrementAndGet();
                    }
                });
                Map<String, Repository.App> apps = Collections.singletonMap("a", new Repository.App("a", "A"));
                Repository.Snapshot data = new Repository.Snapshot(apps,
                        Collections.singletonList(new Predictor.Prediction("a", 1, "常用")), 1, 1, true);
                list.show(data);
                list.show(new Repository.Snapshot(new HashMap<>(apps), new ArrayList<>(data.predictions), 2, 1, true));
                check(updates.get() == 1, "unchanged recommendations do not rebind or flash on return");
                list.show(new Repository.Snapshot(apps,
                        Collections.singletonList(new Predictor.Prediction("a", 2, "刚使用")), 2, 1, true));
                check(updates.get() == 2, "changed recommendation content still refreshes");
            });
            check(RefreshPolicy.last(context) == stamp, "appearance work does not refresh the recommendation clock");
            runOnMainSync(() -> current.finish());
            current = startActivitySync(new Intent(context, GlassHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            Activity main = current;
            HomeNavigation.select(this, main, 2);
            check(current == main && HomeNavigation.selected(main, 2),
                    "settings opens inside the existing home");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(800);
            waitForIdleSync();
            check(current == main && !main.isFinishing() && HomeNavigation.selected(main, 0),
                    "back returns to recommendations in the existing home");
            result.putString("stream", "\nPASS: " + checks + " widget appearance/rendering/navigation checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error) + "\n");
        } finally {
            if (host != null)
                host.deleteHost();
        }
        finish(status, result);
    }
}
