package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/**
 * Disposable emulator: recommendation limit and update discovery/navigation.
 */
public final class SettingsPageRunner extends Instrumentation {
    private Activity activity;
    private int checks;
    private boolean checkNetwork;
    private Intent opened;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        checkNetwork = "true".equals(arguments.getString("checkNetwork"));
        start();
    }

    private void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }

    private View view(String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    private void click(String tag) {
        runOnMainSync(() -> view(tag).performClick());
        waitForIdleSync();
    }

    private AccessibilityNodeInfo node(String label) {
        for (int attempt = 0; attempt < 80; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null)
                for (AccessibilityNodeInfo candidate : root.findAccessibilityNodeInfosByText(label))
                    if (label.contentEquals(candidate.getText() == null ? "" : candidate.getText())
                            || label.contentEquals(candidate.getContentDescription() == null
                                    ? ""
                                    : candidate.getContentDescription()))
                        return candidate;
            SystemClock.sleep(50);
        }
        throw new AssertionError("missing UI: " + label);
    }

    private void choose(String label) {
        Rect bounds = new Rect();
        node(label).getBoundsInScreen(bounds);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(),
                0);
        sendPointerSync(down);
        sendPointerSync(up);
        down.recycle();
        up.recycle();
        SystemClock.sleep(ViewConfiguration.getPressedStateDuration() + 80);
        waitForIdleSync();
    }

    private void number(String value) {
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        check(node("推荐上限，1 到 100 个").performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text),
                "numeric field accepts input");
        waitForIdleSync();
    }

    private void focus() {
        for (int i = 0; i < 80 && !activity.hasWindowFocus(); i++)
            SystemClock.sleep(50);
        check(activity.hasWindowFocus(), "activity has focus for update result");
    }

    private void result(AppUpdates.Release release, String error) {
        focus();
        runOnMainSync(() -> ((SettingsPage) view("settings-page")).completeUpdate(release, error));
        waitForIdleSync();
    }

    private void screenshot(String name) throws Exception {
        // Window enter/exit animations can outlive the main looper's idle state.
        getUiAutomation().waitForIdle(150, 3000);
        SystemClock.sleep(400);
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        try (java.io.OutputStream stream = new java.io.FileOutputStream(
                new java.io.File(getTargetContext().getCacheDir(), name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        } finally {
            bitmap.recycle();
        }
    }

    @Override
    public void onStart() {
        Bundle report = new Bundle();
        ActivityMonitor web = null;
        int status = Activity.RESULT_OK;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("this test may only reset an emulator");
            Context context = getTargetContext();
            Repository.prefs(context).edit().clear().commit();
            List<String> installed = new ArrayList<>(Repository.apps(context).keySet());
            long now = System.currentTimeMillis();
            try (UsageStore store = new UsageStore(context)) {
                store.reset(now - 120_000);
                for (int i = 0; i < installed.size(); i++)
                    store.getWritableDatabase().execSQL("INSERT INTO launches(pkg,stamp) VALUES (?,?)",
                            new Object[]{installed.get(i), now - 60_000 - i * 1000});
            }
            RefreshPolicy.succeeded(context, now, true);
            activity = startActivitySync(
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            Repository.WORK.submit(() -> {
            }).get();
            click("open-settings");
            check(view("recommendation-limit").getContentDescription().toString().contains("20 个"),
                    "settings shows default twenty");
            click("recommendation-limit");
            number("0");
            choose("确定");
            check(RecommendationLimit.load(context) == 20
                    && "请输入 1–100".contentEquals(node("推荐上限，1 到 100 个").getError()),
                    "invalid limit stays in dialog without changing saved setting");
            number("2");
            screenshot("limit-dialog");
            choose("确定");
            runOnMainSync(WidgetAppearance::flush);
            Repository.WORK.submit(() -> {
            }).get();
            check(RecommendationLimit.load(context) == 2 && RefreshPolicy.last(context) == now,
                    "valid limit saves immediately without changing refresh clock");
            click("screen-back");
            Repository.WORK.submit(() -> {
            }).get();
            waitForIdleSync();
            check(Repository.current(context, false).data.predictions.size() == 2,
                    "recommendation page uses the changed limit on return");
            click("open-settings");
            click("recommendation-limit");
            number("40");
            choose("取消");
            check(RecommendationLimit.load(context) == 2, "cancel leaves the previous limit intact");

            web = new ActivityMonitor() {
                @Override
                public ActivityResult onStartActivity(Intent intent) {
                    if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                        opened = intent;
                        return new ActivityResult(Activity.RESULT_CANCELED, null);
                    }
                    return null;
                }
            };
            addMonitor(web);
            check(view("open-repository") != null && view("check-updates") instanceof android.widget.Button
                    && view("check-updates").getParent() == view("open-about"),
                    "update stays beside about and repository has an independent row");
            Repository.Snapshot expected = Repository.loadAt(context, now, now, false);
            String learning = "已学习 " + expected.days + " 天 · " + expected.samples + " 条记录";
            check(view("open-about").getContentDescription().toString().contains(learning),
                    "about description shows actual local days and record count");
            check(("关于知时 v" + AppUpdates.installedVersion(context))
                    .contentEquals(((android.widget.TextView) view("about-title")).getText()),
                    "about title appends installed version");
            click("open-about");
            check(node("知时 · HabitDock") != null, "about dialog displays application information");
            screenshot("about-dialog");
            choose("关闭");
            click("open-repository");
            check(opened != null && AppUpdates.REPOSITORY.equals(opened.getDataString())
                    && opened.hasCategory(Intent.CATEGORY_BROWSABLE), "repository opens the correct web destination");
            runOnMainSync(() -> ((SettingsPage) view("settings-page")).fullScroll(View.FOCUS_DOWN));
            waitForIdleSync();
            result(new AppUpdates.Release("v99.0.0"), null);
            check(node("发现新版本 v99.0.0") != null, "new version displays an update dialog");
            screenshot("update-found");
            choose("稍后");
            check("查看更新".contentEquals(((android.widget.Button) view("check-updates")).getText()),
                    "postponing changes the same about-row button to view update");
            screenshot("update-button");
            click("check-updates");
            check(opened.getDataString().equals(AppUpdates.RELEASES + "/tag/v99.0.0"),
                    "inline action opens the exact release page");
            result(new AppUpdates.Release("v99.0.0"), null);
            choose("前往下载");
            check(opened.getDataString().equals(AppUpdates.RELEASES + "/tag/v99.0.0"),
                    "dialog download opens the exact release page");
            result(new AppUpdates.Release(AppUpdates.installedVersion(context)), null);
            check("检查更新".contentEquals(((android.widget.Button) view("check-updates")).getText())
                    && view("check-updates").getContentDescription().toString().contains("已是最新")
                    && view("open-about").getContentDescription().toString().contains("条记录"),
                    "up-to-date result removes stale update action and preserves learning summary");
            result(null, "测试网络不可用");
            check(node("检查更新失败") != null, "offline check offers a visible failure instead of silent success");
            screenshot("update-error");
            choose("版本页面");
            check(opened.getDataString().equals(AppUpdates.RELEASES), "failed check can still open release history");
            check(view("check-updates").isEnabled(), "checking remains available after failure");
            check(RefreshPolicy.last(context) == now, "settings and update actions preserve recommendation time");
            ActivityMonitor recreated = addMonitor(MainActivity.class.getName(), null, false);
            runOnMainSync(activity::recreate);
            activity = waitForMonitorWithTimeout(recreated, 5000);
            removeMonitor(recreated);
            check(activity != null, "settings activity recreates");
            waitForIdleSync();
            Repository.WORK.submit(() -> {
            }).get();
            waitForIdleSync();
            check(view("open-about").getContentDescription().toString().contains(learning)
                    && RefreshPolicy.last(context) == now,
                    "restored settings loads learning summary without regenerating recommendations");
            if (checkNetwork) {
                AppUpdates.Release actual = AppUpdates.fetch();
                check(actual.tag.startsWith("v") && actual.url().startsWith(AppUpdates.RELEASES + "/tag/"),
                        "real HTTPS fetch resolves latest public release, including API rate-limit fallback");
                result(actual, null);
                screenshot("update-live");
            }
            report.putString("stream", "\nPASS: " + checks + " settings and update checks\n");
        } catch (Throwable failure) {
            status = Activity.RESULT_CANCELED;
            report.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(failure));
        } finally {
            if (web != null)
                removeMonitor(web);
            if (activity != null)
                runOnMainSync(activity::finish);
            Repository.prefs(getTargetContext()).edit().remove("recommendation_limit").commit();
        }
        finish(status, report);
    }
}
