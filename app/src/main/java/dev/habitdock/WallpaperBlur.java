package dev.habitdock;

import android.os.Build;
import android.view.*;
import java.util.function.Consumer;

/** Blur only the surface behind the window; keep application artwork sharp. */
final class WallpaperBlur {
    private final ThemedActivity activity;
    private final Consumer<Boolean> listener;
    private boolean listening;
    private int radius;

    WallpaperBlur(ThemedActivity activity) {
        this.activity = activity;
        listener = enabled -> {
            apply();
            activity.blurAvailabilityChanged();
        };
    }

    boolean available() {
        return Build.VERSION.SDK_INT >= 31 && activity.getWindowManager().isCrossWindowBlurEnabled();
    }

    void start() {
        if (Build.VERSION.SDK_INT >= 31 && !listening) {
            listening = true;
            activity.getWindowManager().addCrossWindowBlurEnabledListener(activity.getMainExecutor(), listener);
        }
        set(activity.wallpaperBlurRadius());
    }

    void stop() {
        if (Build.VERSION.SDK_INT >= 31 && listening) {
            activity.getWindowManager().removeCrossWindowBlurEnabledListener(listener);
            listening = false;
        }
    }

    void set(int value) {
        radius = Math.max(0, Math.min(100, value));
        apply();
    }

    private void apply() {
        if (Build.VERSION.SDK_INT < 31)
            return;
        int effective = available() ? radius : 0;
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean enabled = (params.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0;
        if (params.getBlurBehindRadius() == effective && enabled == (effective > 0))
            return;
        params.setBlurBehindRadius(effective);
        if (effective > 0)
            params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        else
            params.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        window.setAttributes(params);
    }
}
