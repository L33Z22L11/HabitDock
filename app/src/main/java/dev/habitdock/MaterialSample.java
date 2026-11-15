package dev.habitdock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;

/** Non-interactive material sample over the actual preview content. */
@android.annotation.SuppressLint("ViewConstructor")
final class MaterialSample extends FrameLayout {
    private final Activity activity;
    private final boolean windowBlur;
    private Dialog window;
    private ViewTreeObserver.OnPreDrawListener tracker;
    private final Rect last = new Rect();

    MaterialSample(Activity activity) {
        super(activity);
        this.activity = activity;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        windowBlur = MainStyle.material(activity) == MainStyle.MATERIAL_SYSTEM && ScopedWindowBlur.available(activity);
        if (!windowBlur)
            addView(panel(activity, false), new LayoutParams(-1, -1));
    }

    private static View panel(Activity activity, boolean blurred) {
        LinearLayout panel = Ui.column(activity);
        panel.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10), Ui.dp(activity, 14), Ui.dp(activity, 10));
        if (MainStyle.material(activity) == MainStyle.MATERIAL_GLASS)
            GlassPanels.attach(panel, 18, ObricGlass.VIEW_BACKGROUND);
        else
            GlassPanels.attachWindowSurface(panel, 18, false, blurred);
        TextView title = Ui.text(activity, "显示", 14, R.color.ink);
        panel.addView(title);
        Ui.space(panel, 6);
        LinearLayout icons = new LinearLayout(activity);
        icons.setGravity(Gravity.CENTER_VERTICAL);
        for (int resource : new int[]{R.drawable.ic_grid, R.drawable.ic_settings, R.drawable.ic_sparkles}) {
            ImageView icon = new ImageView(activity);
            icon.setImageResource(resource);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(activity, R.color.muted)));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18));
            size.setMarginEnd(Ui.dp(activity, 12));
            icons.addView(icon, size);
        }
        panel.addView(icons);
        return panel;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!windowBlur)
            return;
        tracker = () -> {
            updateWindow();
            return true;
        };
        getViewTreeObserver().addOnPreDrawListener(tracker);
    }

    private void updateWindow() {
        Rect visible = new Rect();
        if (!isShown() || !hasWindowFocus() || activity.isFinishing() || activity.isDestroyed()
                || !getGlobalVisibleRect(visible) || visible.width() != getWidth() || visible.height() != getHeight()) {
            closeWindow();
            return;
        }
        int[] location = new int[2];
        getLocationOnScreen(location);
        Rect bounds = new Rect(location[0], location[1], location[0] + getWidth(), location[1] + getHeight());
        if (window == null) {
            Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            Window surface = dialog.getWindow();
            if (surface == null)
                return;
            try {
                surface.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                surface.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                surface.setWindowAnimations(0);
                ScopedWindowBlur.configure(surface, activity, 18);
                dialog.setContentView(panel(activity, true));
                surface.setGravity(Gravity.TOP | Gravity.LEFT);
                surface.setLayout(1, 1);
                dialog.show();
                window = dialog;
                last.setEmpty();
            } catch (RuntimeException unavailable) {
                dialog.dismiss();
                addView(panel(activity, false), new LayoutParams(-1, -1));
                getViewTreeObserver().removeOnPreDrawListener(tracker);
                tracker = null;
                return;
            }
        }
        if (!last.equals(bounds)) {
            last.set(bounds);
            WindowManager.LayoutParams params = window.getWindow().getAttributes();
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = bounds.left;
            params.y = bounds.top;
            params.width = bounds.width();
            params.height = bounds.height();
            if (android.os.Build.VERSION.SDK_INT >= 30)
                params.setFitInsetsTypes(0);
            window.getWindow().setAttributes(params);
        }
    }

    private void closeWindow() {
        if (window != null) {
            window.dismiss();
            window = null;
        }
        last.setEmpty();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused)
            closeWindow();
        else
            invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (tracker != null && getViewTreeObserver().isAlive())
            getViewTreeObserver().removeOnPreDrawListener(tracker);
        closeWindow();
        super.onDetachedFromWindow();
    }
}
