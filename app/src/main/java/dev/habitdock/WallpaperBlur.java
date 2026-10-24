package dev.habitdock;

import android.os.Build;
import android.view.*;
import java.util.function.Consumer;

/** Blur only the surface behind the window; keep application artwork sharp. */
final class WallpaperBlur {
    private final ThemedActivity activity;
    private final Consumer<Boolean> listener;
    private boolean listening;
    private int radius, appliedRadius = -1;

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

    int appliedRadius() {
        return Math.max(0, appliedRadius);
    }

    private void apply() {
        if (Build.VERSION.SDK_INT < 31)
            return;
        int effective = available() && activity.wallpaperScene() ? radius : 0;
        if (effective == appliedRadius)
            return;
        // Blur this window's background, not every surface behind a focusable window.
        // The drawable stays owned by this page when a dialog takes focus.
        activity.getWindow().setBackgroundBlurRadius(effective);
        appliedRadius = effective;
    }
}
