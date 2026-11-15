package dev.habitdock;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;

/**
 * Checks that system-blurred navigation remains interactive and follows its
 * anchor.
 */
public final class NavigationWindowRunner extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Context context = getTargetContext();
        android.content.SharedPreferences prefs = Repository.prefs(context);
        boolean hadMaterial = prefs.contains("main.material");
        int oldMaterial = prefs.getInt("main.material", MainStyle.MATERIAL_GLASS);
        Activity home = null;
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            prefs.edit().putInt("main.material", MainStyle.MATERIAL_SYSTEM).commit();
            home = startActivitySync(
                    new Intent(context, GlassHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            if (!ScopedWindowBlur.available(home))
                throw new AssertionError("system window blur unavailable on test device");
            java.lang.reflect.Field hostField = GlassHomeActivity.class.getDeclaredField("navigationWindow");
            java.lang.reflect.Field anchorField = GlassHomeActivity.class.getDeclaredField("navigationAnchor");
            java.lang.reflect.Field navField = GlassHomeActivity.class.getDeclaredField("navigation");
            java.lang.reflect.Field dialogField = NavigationWindow.class.getDeclaredField("dialog");
            hostField.setAccessible(true);
            anchorField.setAccessible(true);
            navField.setAccessible(true);
            dialogField.setAccessible(true);
            NavigationWindow host = (NavigationWindow) hostField.get(home);
            FrameLayout anchor = (FrameLayout) anchorField.get(home);
            GlassNavigation nav = (GlassNavigation) navField.get(home);
            Dialog[] dialog = new Dialog[1];
            for (int i = 0; i < 30 && dialog[0] == null; i++) {
                SystemClock.sleep(100);
                runOnMainSync(() -> {
                    try {
                        dialog[0] = (Dialog) dialogField.get(host);
                    } catch (IllegalAccessException failure) {
                        throw new AssertionError(failure);
                    }
                });
            }
            if (dialog[0] == null || !dialog[0].isShowing() || nav.getParent() == anchor)
                throw new AssertionError("navigation is not hosted in a blur window");
            int[] origin = new int[2];
            runOnMainSync(() -> anchor.getLocationOnScreen(origin));
            WindowManager.LayoutParams params = dialog[0].getWindow().getAttributes();
            if (params.x != origin[0] || params.y != origin[1]
                    || params.width != anchor.getWidth() || params.height != anchor.getHeight())
                throw new AssertionError("blur window does not match navigation bounds");
            int x = origin[0] + anchor.getWidth() / 2;
            int y = origin[1] + anchor.getHeight() / 2;
            long downTime = SystemClock.uptimeMillis();
            sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0));
            sendPointerSync(MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, x, y, 0));
            SystemClock.sleep(400);
            waitForIdleSync();
            if (!nav.getChildAt(1).isSelected())
                throw new AssertionError("navigation window did not receive touch");
            runOnMainSync(() -> anchor.setVisibility(View.GONE));
            SystemClock.sleep(200);
            waitForIdleSync();
            if (dialogField.get(host) != null || nav.getParent() != anchor)
                throw new AssertionError("hidden navigation did not release its window");
            result.putString("stream", "\nPASS: blurred navigation placement, touch and cleanup\n");
        } catch (Throwable failure) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\n" + android.util.Log.getStackTraceString(failure));
        } finally {
            if (home != null) {
                Activity target = home;
                runOnMainSync(target::finish);
            }
            android.content.SharedPreferences.Editor restore = prefs.edit();
            if (hadMaterial)
                restore.putInt("main.material", oldMaterial);
            else
                restore.remove("main.material");
            restore.commit();
            finish(status, result);
        }
    }
}
