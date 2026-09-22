package dev.habitdock;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Disposable emulator only: immediate settings, restore points and real widget
 * repaints.
 */
public final class StyleSettingsRunner extends Instrumentation {
    private Activity current;
    private int checks;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    private void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }

    private View view(String tag) {
        return current.getWindow().getDecorView().findViewWithTag(tag);
    }

    private void open(Class<? extends Activity> type) {
        current = startActivitySync(new Intent(getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
    }

    private void close() {
        runOnMainSync(() -> current.finish());
        waitForIdleSync();
    }

    private void recreate() {
        ActivityMonitor monitor = addMonitor(current.getClass().getName(), null, false);
        runOnMainSync(() -> current.recreate());
        current = waitForMonitorWithTimeout(monitor, 8000);
        removeMonitor(monitor);
        if (current == null)
            throw new AssertionError("settings recreation failed");
        waitForIdleSync();
    }

    private void tap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0);
        sendPointerSync(down);
        sendPointerSync(up);
        down.recycle();
        up.recycle();
        // Native ListView dispatches its item click after the pressed-state delay.
        SystemClock.sleep(ViewConfiguration.getPressedStateDuration() + 80);
        waitForIdleSync();
    }

    private void tap(String tag) {
        Rect bounds = new Rect();
        runOnMainSync(() -> view(tag).getGlobalVisibleRect(bounds));
        tap(bounds.centerX(), bounds.centerY());
    }

    private void choose(String text) throws Exception {
        Rect bounds = new Rect();
        for (int i = 0; i < 80 && bounds.isEmpty(); i++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null)
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                    if (text.contentEquals(node.getText() == null ? "" : node.getText())
                            || text.contentEquals(
                                    node.getContentDescription() == null ? "" : node.getContentDescription())) {
                        node.getBoundsInScreen(bounds);
                        break;
                    }
            if (bounds.isEmpty())
                Thread.sleep(50);
        }
        if (bounds.isEmpty())
            throw new AssertionError("missing dialog action: " + text);
        tap(bounds.centerX(), bounds.centerY());
    }

    private void progress(String tag, int value) {
        runOnMainSync(() -> ((SeekBar) view(tag)).setProgress(value));
        waitForIdleSync();
    }

    private void selection(String tag, int value) {
        runOnMainSync(() -> ((Spinner) view(tag)).setSelection(value));
        waitForIdleSync();
    }

    private boolean action(String tag, String label) {
        return view(tag).isShown() && label.contentEquals(view(tag).getContentDescription());
    }

    private void settle() throws Exception {
        runOnMainSync(WidgetAppearance::flush);
        Repository.WORK.submit(() -> {
        }).get();
        waitForIdleSync();
        runOnMainSync(WidgetAppearance::flush);
        Repository.WORK.submit(() -> {
        }).get();
        waitForIdleSync();
    }

    private void awaitUpdate(AtomicInteger updates, int previous) throws Exception {
        for (int i = 0; i < 100 && updates.get() <= previous; i++)
            Thread.sleep(50);
        check(updates.get() > previous, "widget host receives an automatic repaint while settings remain open");
        waitForIdleSync();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        AppWidgetHost host = null;
        int status = Activity.RESULT_OK;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("this test may only reset an emulator");
            Context c = getTargetContext();
            Repository.prefs(c).edit().clear().commit();
            IconStyle original = new IconStyle(73, true, 0xffe2c4dd);
            original.save(c);
            AppWidgetManager manager = c.getSystemService(AppWidgetManager.class);
            AtomicInteger updates = new AtomicInteger();
            host = new AppWidgetHost(c, 1946) {
                @Override
                protected AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) {
                    return new AppWidgetHostView(context) {
                        @Override
                        public void updateAppWidget(RemoteViews views) {
                            super.updateAppWidget(views);
                            updates.incrementAndGet();
                        }
                    };
                }
            };
            host.deleteHost();
            int first = host.allocateAppWidgetId(), second = host.allocateAppWidgetId();
            ComponentName provider = new ComponentName(c, HabitWidget.class);
            check(manager.bindAppWidgetIdIfAllowed(first, provider)
                    && manager.bindAppWidgetIdIfAllowed(second, provider),
                    "two real widget bindings (grantbind on the emulator before running)");
            AppWidgetHost testHost = host;
            runOnMainSync(() -> {
                testHost.startListening();
                testHost.createView(c, first, manager.getAppWidgetInfo(first));
            });
            // Binding emits an initial APPWIDGET_UPDATE; finish it before testing an
            // intentionally overdue clock. The periodic job is independent of edits.
            Thread.sleep(500);
            c.getSystemService(android.app.job.JobScheduler.class).cancel(1919);
            settle();
            long stamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000;
            RefreshPolicy.succeeded(c, stamp, UsageStore.permitted(c));
            open(IconStyleActivity.class);
            check(!view("icon-style-save").isShown(), "unchanged editor has no right toolbar action");
            int beforeUpdates = updates.get();
            progress("icon-roundness", 100);
            check(IconStyle.load(c).roundness == 100 && action("icon-style-save", "撤销更改"),
                    "roundness persists while editor stays open and toolbar switches to undo");
            awaitUpdate(updates, beforeUpdates);
            settle();
            check(RefreshPolicy.last(c) == stamp
                    && c.getSharedPreferences("widget_runtime", Context.MODE_PRIVATE).getLong("last_success",
                            -1) == stamp,
                    "real widget repaint keeps overdue recommendation timestamp unchanged: expected=" + stamp
                            + ", recommendation=" + RefreshPolicy.last(c) + ", widget="
                            + c.getSharedPreferences("widget_runtime", Context.MODE_PRIVATE).getLong("last_success",
                                    -1));
            tap("icon-style-save");
            choose("取消");
            check(IconStyle.load(c).roundness == 100, "cancel leaves live appearance unchanged");
            recreate();
            tap("icon-style-save");
            choose("恢复上次");
            check(IconStyle.load(c).sameAs(original) && !view("icon-style-save").isShown(),
                    "session restore point survives recreation and hides undo again");
            progress("icon-roundness", 0);
            tap("icon-style-save");
            choose("恢复默认");
            check(IconStyle.load(c).sameAs(IconStyle.defaults()) && action("icon-style-save", "撤销更改"),
                    "factory defaults reset all icon fields but keep original restore point");
            tap("icon-style-save");
            choose("恢复上次");
            tap("icon-fill-color");
            choose("选择 #DCE8FF");
            check(IconStyle.load(c).fillColor == 0xffdce8ff, "palette applies before color dialog confirmation");
            choose("取消");
            check(IconStyle.load(c).sameAs(original), "cancel color dialog restores its opening color");
            tap("icon-fill-color");
            choose("选择 #DBEFE2");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            check(IconStyle.load(c).sameAs(original), "system back also cancels color preview");
            tap("icon-fill-color");
            choose("自适应颜色");
            check(IconStyle.load(c).fillColor == 0, "adaptive edge fill applies immediately");
            tap("icon-fill-color");
            choose("完成");
            check(IconStyle.load(c).fillColor == 0,
                    "viewing color picker does not turn adaptive mode into a solid color");
            progress("icon-roundness", 25);
            close();
            open(IconStyleActivity.class);
            check(IconStyle.load(c).roundness == 25 && !view("icon-style-save").isShown(),
                    "leaving keeps changes; reopening starts a new restore point");
            close();

            WidgetPreferences before = new WidgetPreferences(3, 4, 67, false, true, false);
            WidgetPreferences other = new WidgetPreferences(6, 1, 90, true, false, true);
            before.save(c, first);
            other.save(c, second);
            WidgetPreferences.defaults().save(c, 0);
            open(WidgetSettingsActivity.class);
            check(!view("widget-save").isShown() && WidgetPreferences.load(c, first).sameAs(before),
                    "binding widget controls does not modify their saved values");
            beforeUpdates = updates.get();
            selection("widget-columns", 2);
            progress("widget-scale", 95);
            runOnMainSync(() -> ((Switch) view("widget-more")).setChecked(true));
            check(WidgetPreferences.load(c, first).columns == 4 && WidgetPreferences.load(c, first).percent == 95
                    && WidgetPreferences.load(c, first).more && action("widget-save", "撤销更改"),
                    "grid, scale and toggle apply immediately");
            awaitUpdate(updates, beforeUpdates);
            check(WidgetPreferences.load(c, second).sameAs(other), "editing one widget preserves the second");
            selection("widget-target", 2);
            selection("widget-columns", 0);
            check(WidgetPreferences.load(c, 0).columns == 2 && WidgetPreferences.load(c, first).columns == 4
                    && WidgetPreferences.load(c, second).sameAs(other),
                    "new-widget defaults do not alter existing widgets");
            selection("widget-target", 0);
            recreate();
            tap("widget-save");
            choose("恢复上次");
            check(WidgetPreferences.load(c, first).sameAs(before) && !view("widget-save").isShown()
                    && WidgetPreferences.load(c, 0).columns == 2,
                    "restore is scoped to selected widget and survives recreation");
            progress("widget-scale", 100);
            tap("widget-save");
            choose("恢复默认");
            check(WidgetPreferences.load(c, first).sameAs(WidgetPreferences.defaults()),
                    "restore defaults uses factory layout, not edited new-widget defaults");
            tap("widget-save");
            choose("取消");
            check(WidgetPreferences.load(c, first).sameAs(WidgetPreferences.defaults()),
                    "layout cancel preserves applied defaults");
            tap("widget-save");
            choose("恢复上次");
            check(WidgetPreferences.load(c, first).sameAs(before),
                    "restore original still works after restoring defaults");
            selection("widget-rows", 1);
            close();
            settle();
            open(WidgetSettingsActivity.class);
            check(WidgetPreferences.load(c, first).rows == 2 && !view("widget-save").isShown(),
                    "layout changes survive leaving without confirmation");
            settle();
            check(RefreshPolicy.last(c) == stamp, "all edits and restores leave recommendation clock untouched");
            close();
            result.putString("stream", "\nPASS: " + checks + " immediate-style/restore/widget checks\n");
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
            status = Activity.RESULT_CANCELED;
        } finally {
            if (host != null)
                host.deleteHost();
        }
        finish(status, result);
    }
}
