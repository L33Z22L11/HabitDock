package dev.habitdock;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;

/**
 * Optional, non-interactive system-blur window confined to a floating surface.
 */
final class ScopedWindowBlur {
    private final Dialog backdrop;
    private View tracked;
    private ViewTreeObserver.OnPreDrawListener preDraw;
    private int x, y, width, height;
    private final int bottomExtension;

    static boolean available(Context context) {
        if (!MainStyle.liquid(context) || Build.VERSION.SDK_INT < 31)
            return false;
        WindowManager manager = context.getSystemService(WindowManager.class);
        return manager != null && manager.isCrossWindowBlurEnabled();
    }

    static void configure(Window window, Context context, float cornerDp) {
        if (Build.VERSION.SDK_INT < 31 || window == null)
            return;
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0x01000000);
        shape.setCornerRadius(Ui.dp(context, cornerDp));
        // PhoneWindow's blur setter requires DecorView even before the dialog is shown.
        window.getDecorView();
        window.setBackgroundDrawable(shape);
        window.setBackgroundBlurRadius(Ui.dp(context, 20));
    }

    static ScopedWindowBlur create(Activity activity, float cornerDp) {
        if (!available(activity) || activity.isFinishing() || activity.isDestroyed())
            return null;
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Window window = dialog.getWindow();
        if (window == null)
            return null;
        try {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.setWindowAnimations(0);
            configure(window, activity, cornerDp);
            dialog.setContentView(new View(activity));
            window.setGravity(Gravity.TOP | Gravity.LEFT);
            dialog.show();
            window.setLayout(1, 1);
            return new ScopedWindowBlur(dialog, Ui.dp(activity, cornerDp));
        } catch (RuntimeException error) {
            android.util.Log.w("ScopedWindowBlur", "Unable to create backdrop; using translucent surface", error);
            dialog.dismiss();
            return null;
        }
    }

    private ScopedWindowBlur(Dialog backdrop, int bottomExtension) {
        this.backdrop = backdrop;
        this.bottomExtension = bottomExtension;
    }

    void track(View panel) {
        tracked = panel;
        preDraw = () -> {
            if (!panel.isAttachedToWindow() || !backdrop.isShowing())
                return true;
            int[] position = new int[2];
            panel.getLocationOnScreen(position);
            int w = panel.getWidth(), h = panel.getHeight();
            if (w <= 0 || h <= 0 || (x == position[0] && y == position[1] && width == w && height == h))
                return true;
            x = position[0];
            y = position[1];
            width = w;
            height = h;
            Window window = backdrop.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.x = x;
                params.y = y;
                params.width = w;
                // Extend below the screen so the drawer's lower corners remain square.
                params.height = h + bottomExtension;
                if (Build.VERSION.SDK_INT >= 30)
                    params.setFitInsetsTypes(0);
                window.setAttributes(params);
            }
            return true;
        };
        panel.getViewTreeObserver().addOnPreDrawListener(preDraw);
    }

    void dismiss() {
        if (tracked != null && preDraw != null && tracked.getViewTreeObserver().isAlive())
            tracked.getViewTreeObserver().removeOnPreDrawListener(preDraw);
        backdrop.dismiss();
    }
}
