package dev.habitdock;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Disposable-emulator regression: injects real touch events instead of invoking
 * click listeners.
 */
public final class PickerTouchRunner extends Instrumentation {
    private int checks;
    private Activity current;
    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    private void check(boolean value, String description) {
        if (!value)
            throw new AssertionError(description);
        checks++;
    }

    private View tagged(String tag) {
        return findShown(current.getWindow().getDecorView(), tag);
    }

    private View findShown(View view, String tag) {
        if (!view.isShown())
            return null;
        if (tag.equals(view.getTag()))
            return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findShown(group.getChildAt(i), tag);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private void awaitTag(String tag) throws Exception {
        for (int i = 0; i < 100; i++) {
            final boolean[] ready = {false};
            runOnMainSync(() -> {
                View v = tagged(tag);
                ready[0] = v != null && v.isShown() && v.getWidth() > 0;
            });
            if (ready[0]) {
                waitForIdleSync();
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("missing visible view " + tag);
    }

    private void tap(String tag, boolean checkbox) throws Exception {
        awaitTag(tag);
        final float[] point = new float[2];
        runOnMainSync(() -> {
            View v = tagged(tag);
            int[] xy = new int[2];
            v.getLocationOnScreen(xy);
            point[0] = xy[0] + v.getWidth() * (checkbox ? .5f : .25f);
            point[1] = xy[1] + v.getHeight() * .45f;
        });
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        sendPointerSync(down);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, point[0], point[1], 0);
        sendPointerSync(up);
        up.recycle();
        waitForIdleSync();
        Thread.sleep(120);
    }

    private void longPress(String tag) throws Exception {
        awaitTag(tag);
        final float[] point = new float[2];
        runOnMainSync(() -> {
            View v = tagged(tag);
            int[] xy = new int[2];
            v.getLocationOnScreen(xy);
            point[0] = xy[0] + v.getWidth() * .3f;
            point[1] = xy[1] + v.getHeight() * .5f;
        });
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        sendPointerSync(down);
        down.recycle();
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 200);
        MotionEvent up = MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, point[0], point[1],
                0);
        sendPointerSync(up);
        up.recycle();
        waitForIdleSync();
    }

    private void query(String text) {
        runOnMainSync(() -> ((EditText) tagged("search")).setText(text));
        waitForIdleSync();
    }

    private void open(String mode) {
        current = startActivitySync(new Intent(getTargetContext(), AppPickerActivity.class).putExtra("mode", mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void close() {
        runOnMainSync(() -> current.finish());
        waitForIdleSync();
    }

    private void tapPopupAction(String label) throws Exception {
        // The bubble owns a separate window. Locate it through accessibility, then
        // inject a real touch.
        android.graphics.Rect actionBounds = new android.graphics.Rect();
        for (int i = 0; i < 60 && actionBounds.isEmpty(); i++) {
            android.view.accessibility.AccessibilityNodeInfo popup = getUiAutomation().getRootInActiveWindow();
            if (popup != null) {
                List<android.view.accessibility.AccessibilityNodeInfo> matches = popup
                        .findAccessibilityNodeInfosByText(label);
                if (!matches.isEmpty())
                    matches.get(0).getBoundsInScreen(actionBounds);
            }
            if (actionBounds.isEmpty())
                Thread.sleep(50);
        }
        if (actionBounds.isEmpty())
            throw new AssertionError("action bubble did not become accessible");
        long touch = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(touch, touch, MotionEvent.ACTION_DOWN, actionBounds.centerX(),
                actionBounds.centerY(), 0);
        sendPointerSync(down);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(touch, touch + 60, MotionEvent.ACTION_UP, actionBounds.centerX(),
                actionBounds.centerY(), 0);
        sendPointerSync(up);
        up.recycle();
        waitForIdleSync();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            Context c = getTargetContext();
            Repository.prefs(c).edit().clear().commit();
            List<Repository.App> apps = AppCatalog.filter(new ArrayList<>(Repository.apps(c).values()),
                    Collections.emptySet(), "");
            String first = apps.get(0).pkg, second = apps.get(1).pkg, last = apps.get(apps.size() - 1).pkg;
            open("hidden");
            awaitTag(first);
            tap(first, false);
            check(Repository.selection(c, "hidden").contains(first),
                    "first left cell body tap persists exclusion immediately");
            tap("check:" + first, true);
            check(!Repository.selection(c, "hidden").contains(first), "native checkbox tap removes exclusion once");
            query(second.toUpperCase(Locale.ROOT));
            awaitTag(second);
            check(((ListView) tagged("picker-list")).getAdapter().getCount() == 1,
                    "case-insensitive package search narrows result");
            tap(second, false);
            check(Repository.selection(c, "hidden").contains(second), "filtered cell tap selects correct package");
            query("");
            awaitTag(second);
            final String[] top = {null};
            runOnMainSync(() -> {
                ListView list = (ListView) tagged("picker-list");
                top[0] = (String) ((android.view.ViewGroup) list.getChildAt(0)).getChildAt(0).getTag();
            });
            check(second.equals(top[0]), "selected app moves to first cell");
            query(apps.get(apps.size() - 1).label);
            awaitTag(last);
            check(tagged(last) != null, "application name search resolves item");
            tap("check:" + last, true);
            check(Repository.selection(c, "hidden").contains(last),
                    "search result native checkbox persists correct package");
            ActivityMonitor monitor = addMonitor(AppPickerActivity.class.getName(), null, false);
            runOnMainSync(() -> current.recreate());
            current = waitForMonitorWithTimeout(monitor, 8000);
            removeMonitor(monitor);
            check(current != null, "picker recreated successfully");
            awaitTag(last);
            check(((EditText) tagged("search")).getText().toString().equals(apps.get(apps.size() - 1).label),
                    "query survives recreation");
            check(((CheckBox) tagged("check:" + last)).isChecked(), "native checked state survives recreation");
            close();
            open("hidden");
            awaitTag(second);
            check(((CheckBox) tagged("check:" + second)).isChecked(), "back and reopen needs no save button");
            close();
            Repository.prefs(c).edit().clear().commit();
            open("pinned");
            awaitTag(first);
            for (int i = 0; i < 5; i++) {
                query(apps.get(i).pkg);
                tap(apps.get(i).pkg, false);
            }
            check(Repository.selection(c, "pinned").size() == 5, "more than four pins persist through real taps");
            check(((CheckBox) tagged("check:" + apps.get(4).pkg)).isChecked(), "fifth pin stays checked");
            close();
            current = startActivitySync(MainActivity.actionIntent(c, apps.get(5).pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            tapPopupAction("固定推荐");
            check(Repository.selection(c, "pinned").size() == 6
                    && Repository.selection(c, "pinned").contains(apps.get(5).pkg),
                    "action bubble can pin a sixth app");
            close();

            Repository.prefs(c).edit().clear().commit();
            Repository.selectionChanged(c);
            current = startActivitySync(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            tap("open-settings", true);
            tap("open-exclusions", true);
            awaitTag("search");
            query(last);
            awaitTag(last);
            tap("screen-back", true);
            check(tagged("settings-page").isShown(), "exclusion toolbar back returns to settings");
            tap("open-exclusions", true);
            check(((EditText) tagged("search")).getText().toString().equals(last),
                    "search survives returning through settings");
            tap("check:" + last, true);
            check(Repository.selection(c, "hidden").contains(last), "embedded picker checkbox persists exclusion");
            ActivityMonitor mainMonitor = addMonitor(MainActivity.class.getName(), null, false);
            runOnMainSync(() -> current.recreate());
            current = waitForMonitorWithTimeout(mainMonitor, 8000);
            removeMonitor(mainMonitor);
            awaitTag("search");
            check(tagged("exclusions-page") != null && ((EditText) tagged("search")).getText().toString().equals(last),
                    "exclusion page and search survive recreation");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            awaitTag("settings-page");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            awaitTag("recommend-page");
            check(tagged("open-settings") != null, "system back follows exclusion to settings to recommendations");
            String target = Repository.load(c, System.currentTimeMillis(), false).predictions.get(0).pkg;
            awaitTag(target);
            longPress(target);
            check(!Repository.selection(c, "hidden").contains(target), "long press alone never excludes an app");
            tapPopupAction("复制包名");
            ClipData copied = c.getSystemService(ClipboardManager.class).getPrimaryClip();
            check(copied != null && copied.getItemCount() == 1
                    && target.contentEquals(copied.getItemAt(0).getText()),
                    "small header copy button copies the complete package name");
            longPress(target);
            tapPopupAction("不推荐");
            check(Repository.selection(c, "hidden").contains(target), "long-press menu exclusion persists");
            Repository.WORK.submit(() -> {
            }).get();
            waitForIdleSync();
            check(Repository.load(c, System.currentTimeMillis(), false).predictions.stream()
                    .noneMatch(p -> p.pkg.equals(target)), "long-press exclusion removes recommendation");
            close();
            Repository.prefs(c).edit().clear().commit();
            Repository.selectionChanged(c);
            result.putString("stream", "\nPASS: " + checks + " actual-touch picker/navigation/menu checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
