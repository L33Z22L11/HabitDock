package dev.habitdock;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

/**
 * Navigation helpers for the three destinations hosted by GlassHomeActivity.
 */
final class HomeNavigation {
    private HomeNavigation() {
    }

    private static GlassNavigation find(View view) {
        if (view instanceof GlassNavigation)
            return (GlassNavigation) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                GlassNavigation found = find(group.getChildAt(i));
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static View described(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
            return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = described(group.getChildAt(i), description);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    static View described(Activity activity, String description) {
        View found = described(activity.getWindow().getDecorView(), description);
        if (found == null && android.os.Build.VERSION.SDK_INT >= 29)
            for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                found = described(window, description);
                if (found != null)
                    break;
            }
        if (found == null)
            throw new AssertionError("missing view: " + description);
        return found;
    }

    static void openDisplay(Instrumentation instrumentation, Activity activity) {
        String label = Repository.prefs(activity).getBoolean("main.grid", false)
                ? "宫格，显示设置"
                : "平铺，显示设置";
        instrumentation.runOnMainSync(() -> described(activity, label).performClick());
        SystemClock.sleep(600);
        instrumentation.waitForIdleSync();
    }

    static boolean selected(Activity activity, int index) {
        GlassNavigation navigation = find(activity.getWindow().getDecorView());
        return navigation != null && navigation.getChildAt(index).isSelected();
    }

    static void select(Instrumentation instrumentation, Activity activity, int index) {
        instrumentation.runOnMainSync(() -> {
            GlassNavigation navigation = find(activity.getWindow().getDecorView());
            if (navigation == null)
                throw new AssertionError("missing home navigation");
            navigation.getChildAt(index).performClick();
        });
        SystemClock.sleep(500);
        instrumentation.waitForIdleSync();
        if (!selected(activity, index))
            throw new AssertionError("home destination not selected: " + index);
    }
}
