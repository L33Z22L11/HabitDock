package dev.habitdock;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Map;

/** Read-only interaction checks for an explicitly isolated QA installation. */
public final class ReleaseUiRunner extends Instrumentation {
    private Activity home;
    private Activity picker;
    private int checks;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    private void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }

    private void idle() {
        waitForIdleSync();
        SystemClock.sleep(300);
        waitForIdleSync();
    }

    private View tagged(Activity activity, String tag) {
        View view = activity.getWindow().getDecorView().findViewWithTag(tag);
        if (view == null)
            throw new AssertionError("Missing view: " + tag);
        return view;
    }

    private void click(Activity activity, String tag) {
        runOnMainSync(() -> {
            View view = tagged(activity, tag);
            view.requestRectangleOnScreen(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()), true);
            check(view.performClick(), "Could not click " + tag);
        });
        idle();
    }

    private GlassNavigation navigation(View view) {
        if (view instanceof GlassNavigation)
            return (GlassNavigation) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                GlassNavigation found = navigation(group.getChildAt(i));
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private void select(GlassNavigation navigation, int index) {
        runOnMainSync(() -> check(navigation.getChildAt(index).performClick(), "Navigation click " + index));
        idle();
        check(navigation.getChildAt(index).isSelected(), "Navigation destination selected: " + index);
        NavigationPager pager = findPager(home.getWindow().getDecorView());
        check(pager != null && pager.getCurrentItem() == index, "Page follows navigation: " + index);
    }

    private NavigationPager findPager(View view) {
        if (view instanceof NavigationPager)
            return (NavigationPager) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                NavigationPager found = findPager(group.getChildAt(i));
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo node(String label) {
        for (int attempt = 0; attempt < 80; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo candidate : root.findAccessibilityNodeInfosByText(label)) {
                    CharSequence text = candidate.getText();
                    CharSequence description = candidate.getContentDescription();
                    if (candidate.isVisibleToUser() && (label.contentEquals(text == null ? "" : text)
                            || label.contentEquals(description == null ? "" : description)))
                        return candidate;
                }
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Missing visible dialog element: " + label);
    }

    private void cancelDialog(String title, String control) {
        node(title);
        node(control);
        AccessibilityNodeInfo cancel = node("取消");
        check(cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK), title + " cancel action");
        idle();
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        boolean stillShowing = false;
        if (root != null) {
            for (AccessibilityNodeInfo candidate : root.findAccessibilityNodeInfosByText("取消")) {
                if (candidate.isVisibleToUser() && "取消".contentEquals(candidate.getText() == null
                        ? ""
                        : candidate.getText()))
                    stillShowing = true;
            }
        }
        check(!stillShowing, title + " closes after cancel");
    }

    private void checkPreview(GlassNavigation nav) throws Exception {
        select(nav, 0);
        HomeNavigation.openDisplay(this, home);
        check(node("预览其他时间").performAction(AccessibilityNodeInfo.ACTION_CLICK), "Open time preview");
        idle();
        check(node("星期一").performAction(AccessibilityNodeInfo.ACTION_CLICK), "Choose preview day");
        idle();
        check(node("确定").performAction(AccessibilityNodeInfo.ACTION_CLICK), "Confirm preview time");
        idle();
        java.lang.reflect.Field preview = GlassHomeActivity.class.getDeclaredField("previewTime");
        preview.setAccessible(true);
        check(preview.getLong(home) != 0, "Time selection starts a preview");
        check(tagged(home, "preview-indicator").getVisibility() == View.VISIBLE,
                "Home keeps a visible preview indicator after the drawer closes");
        check(node("回到实时并刷新").performAction(AccessibilityNodeInfo.ACTION_CLICK), "Return to live time");
        idle();
        check(preview.getLong(home) == 0, "Refresh exits time preview");
        check(tagged(home, "preview-indicator").getVisibility() == View.GONE,
                "Preview indicator disappears when returning to live time");
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        idle();
        select(nav, 2);
    }

    private float cornerRadius(GlassSurface surface) {
        try {
            java.lang.reflect.Field field = GlassSurface.class.getDeclaredField("cornerDp");
            field.setAccessible(true);
            return field.getFloat(surface);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect search corner radius", failure);
        }
    }

    private void checkPicker() {
        ActivityMonitor monitor = addMonitor(AppPickerActivity.class.getName(), null, false);
        try {
            click(home, "open-exclusions");
            picker = waitForMonitorWithTimeout(monitor, 8000);
        } finally {
            removeMonitor(monitor);
        }
        check(picker instanceof AppPickerActivity, "Exclusion picker opens");
        idle();
        runOnMainSync(() -> {
            View pickerView = tagged(picker, "exclusions-page");
            check(pickerView.isShown(), "Exclusion picker is visible");
            View search = tagged(picker, "open-search");
            check(search.isShown() && search.getParent() instanceof GlassSurface,
                    "Search button has a visible rounded shell");
            View shell = (View) search.getParent();
            int size = Ui.dp(picker, 48);
            check(Math.abs(shell.getWidth() - size) <= 1 && Math.abs(shell.getHeight() - size) <= 1
                    && Math.abs(cornerRadius((GlassSurface) shell) - 24f) < .01f,
                    "Search shell is a 48dp circle");
            ViewGroup toolbar = (ViewGroup) shell.getParent();
            TextView title = null;
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View child = toolbar.getChildAt(i);
                if (child instanceof TextView && ((TextView) child).getText().toString().startsWith("排除应用"))
                    title = (TextView) child;
            }
            check(title != null && title.isShown(), "Picker title is visible");
            float expected = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20,
                    title.getResources().getDisplayMetrics());
            check(Math.abs(title.getTextSize() - expected) < 0.5f, "Picker title is 20sp");
            Typeface face = title.getTypeface();
            check(Typeface.create("sans-serif-medium", Typeface.NORMAL).equals(face), "Picker title uses medium font");
        });
        click(picker, "open-search");
        runOnMainSync(() -> {
            EditText input = (EditText) tagged(picker, "search");
            View clear = tagged(picker, "clear-search");
            check(input.isShown() && clear.isShown(), "Expanded search input and clear button are visible");
            check(input.getParent() instanceof ViewGroup
                    && ((View) input.getParent()).getParent() instanceof GlassSurface,
                    "Expanded search has a rounded container");
            View container = (View) ((View) input.getParent()).getParent();
            check(container.getWidth() > 0 && container.getHeight() == Ui.dp(picker, 48)
                    && Math.abs(cornerRadius((GlassSurface) container) - 24f) < .01f,
                    "Expanded search container is a rounded 48dp-high capsule: width=" + container.getWidth()
                            + " height=" + container.getHeight() + " expected=" + Ui.dp(picker, 48)
                            + " radius=" + cornerRadius((GlassSurface) container));
            input.setText("release ui check");
        });
        idle();
        click(picker, "clear-search");
        runOnMainSync(() -> {
            EditText input = (EditText) tagged(picker, "search");
            check(input.length() == 0 && input.isShown() && tagged(picker, "clear-search").isShown(),
                    "First clear empties text and leaves search open");
        });
        click(picker, "clear-search");
        runOnMainSync(() -> {
            check(tagged(picker, "open-search").isShown(), "Second clear closes search");
            check(picker.getWindow().getDecorView().findViewWithTag("clear-search") == null,
                    "Closed search removes clear action");
        });
        runOnMainSync(picker::finish);
        picker = null;
        idle();
    }

    @Override
    public void onStart() {
        Bundle report = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            Context context = getTargetContext();
            String packageName = context.getPackageName();
            if (!packageName.endsWith(".qa"))
                throw new SecurityException("Refusing target package without .qa suffix: " + packageName);
            Map<String, ?> before = Repository.prefs(context).getAll();
            Object limit = before.get("recommendation_limit");
            Object interval = before.get("refresh_minutes");
            boolean hasLimit = before.containsKey("recommendation_limit");
            boolean hasInterval = before.containsKey("refresh_minutes");
            home = startActivitySync(new Intent(context, GlassHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            idle();
            GlassNavigation nav = navigation(home.getWindow().getDecorView());
            check(nav != null && nav.isShown() && nav.getChildCount() == 3, "Three visible home destinations");
            String[] names = {"推荐", "全部", "设置"};
            for (int i = 0; i < names.length; i++) {
                check(names[i].contentEquals(nav.getChildAt(i).getContentDescription()),
                        "Destination label " + names[i]);
                select(nav, i);
            }
            check(tagged(home, "settings-page").isShown(), "Settings page opens");
            checkPicker();
            checkPreview(nav);
            click(home, "recommendation-limit");
            cancelDialog("推荐数量", "减少推荐数量");
            click(home, "refresh-interval");
            cancelDialog("更新频率", "小时");
            Map<String, ?> after = Repository.prefs(context).getAll();
            check(hasLimit == after.containsKey("recommendation_limit")
                    && java.util.Objects.equals(limit, after.get("recommendation_limit")),
                    "Cancelling preserves the recommendation count preference");
            check(hasInterval == after.containsKey("refresh_minutes")
                    && java.util.Objects.equals(interval, after.get("refresh_minutes")),
                    "Cancelling preserves the refresh interval preference");
            report.putString("stream", "\nPASS: " + checks + " release UI checks\n");
        } catch (Throwable failure) {
            status = Activity.RESULT_CANCELED;
            report.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(failure));
        } finally {
            if (picker != null)
                runOnMainSync(picker::finish);
            if (home != null)
                runOnMainSync(home::finish);
        }
        finish(status, report);
    }
}
