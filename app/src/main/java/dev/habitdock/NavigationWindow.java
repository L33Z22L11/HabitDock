package dev.habitdock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.*;
import android.widget.FrameLayout;

/** Hosts the navigation controls in a bounded blur window when available. */
final class NavigationWindow {
    private final Activity activity;
    private final FrameLayout anchor;
    private final GlassNavigation navigation;
    private final Rect last = new Rect();
    private Dialog dialog;
    private ViewTreeObserver.OnPreDrawListener tracker;
    private boolean failed;

    NavigationWindow(Activity activity, FrameLayout anchor, GlassNavigation navigation) {
        this.activity = activity;
        this.anchor = anchor;
        this.navigation = navigation;
    }

    void resume() {
        if (tracker != null || MainStyle.material(activity) != MainStyle.MATERIAL_SYSTEM || failed)
            return;
        tracker = () -> {
            update();
            return true;
        };
        anchor.getViewTreeObserver().addOnPreDrawListener(tracker);
    }

    private void update() {
        if (activity.isFinishing() || activity.isDestroyed() || !anchor.isShown()
                || !ScopedWindowBlur.available(activity) || anchor.getWidth() <= 0
                || anchor.getHeight() <= 0) {
            close();
            return;
        }
        int[] position = new int[2];
        anchor.getLocationOnScreen(position);
        Rect bounds = new Rect(position[0], position[1], position[0] + anchor.getWidth(),
                position[1] + anchor.getHeight());
        if (dialog == null && !open(bounds))
            return;
        if (!last.equals(bounds)) {
            last.set(bounds);
            Window window = dialog.getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = bounds.left;
            params.y = bounds.top;
            params.width = bounds.width();
            params.height = bounds.height();
            if (android.os.Build.VERSION.SDK_INT >= 30)
                params.setFitInsetsTypes(0);
            window.setAttributes(params);
        }
    }

    private boolean open(Rect bounds) {
        Dialog candidate = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = candidate.getWindow();
        if (window == null)
            return false;
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.setWindowAnimations(0);
            float radiusDp = Math.min(bounds.width(), bounds.height())
                    / activity.getResources().getDisplayMetrics().density / 2f;
            ScopedWindowBlur.configure(window, activity, radiusDp);
            anchor.removeView(navigation);
            candidate.setContentView(navigation);
            candidate.setCanceledOnTouchOutside(false);
            window.setGravity(Gravity.TOP | Gravity.LEFT);
            WindowManager.LayoutParams params = window.getAttributes();
            params.x = bounds.left;
            params.y = bounds.top;
            params.width = bounds.width();
            params.height = bounds.height();
            if (android.os.Build.VERSION.SDK_INT >= 30)
                params.setFitInsetsTypes(0);
            window.setAttributes(params);
            candidate.show();
            dialog = candidate;
            last.setEmpty();
            return true;
        } catch (RuntimeException unavailable) {
            candidate.dismiss();
            restoreNavigation();
            failed = true;
            android.util.Log.w("NavigationWindow", "Window blur unavailable", unavailable);
            return false;
        }
    }

    private void restoreNavigation() {
        if (navigation.getParent() instanceof ViewGroup)
            ((ViewGroup) navigation.getParent()).removeView(navigation);
        anchor.addView(navigation, new FrameLayout.LayoutParams(-1, -1));
    }

    private void close() {
        if (dialog == null)
            return;
        dialog.dismiss();
        dialog = null;
        last.setEmpty();
        restoreNavigation();
    }

    void pause() {
        if (tracker != null && anchor.getViewTreeObserver().isAlive())
            anchor.getViewTreeObserver().removeOnPreDrawListener(tracker);
        tracker = null;
        close();
    }
}
