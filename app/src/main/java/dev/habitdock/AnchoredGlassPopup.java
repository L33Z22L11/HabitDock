package dev.habitdock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

/**
 * A glass surface positioned in screen coordinates, with an appropriate window
 * host.
 */
final class AnchoredGlassPopup {
    final GlassSurface surface;
    private final Activity activity;
    private final float cornerDp;
    private final int elevationDp;
    private final boolean nativeMaterial;
    private final boolean focusable;
    private Dialog dialog;
    private PopupWindow window;
    private View overlay;
    private androidx.activity.OnBackPressedCallback backCallback;
    private Runnable onDismiss;
    private boolean dismissed;

    AnchoredGlassPopup(Activity activity, float cornerDp, int elevationDp) {
        this(activity, cornerDp, elevationDp, false);
    }

    AnchoredGlassPopup(Activity activity, float cornerDp, int elevationDp, boolean continuous) {
        this(activity, cornerDp, elevationDp, continuous, true);
    }

    AnchoredGlassPopup(Activity activity, float cornerDp, int elevationDp, boolean continuous, boolean focusable) {
        this.activity = activity;
        this.cornerDp = cornerDp;
        this.elevationDp = elevationDp;
        this.focusable = focusable;
        nativeMaterial = ObricGlass.available(activity);
        surface = new GlassSurface(activity, cornerDp,
                nativeMaterial ? ObricGlass.VIEW_BACKGROUND : ObricGlass.WINDOW_BACKGROUND, continuous);
    }

    void setOnDismissListener(Runnable listener) {
        onDismiss = listener;
    }

    static Rect visibleFrame(Activity activity, View anchor) {
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        View decor = activity.getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30 && decor.getRootWindowInsets() != null) {
            android.graphics.Insets insets = decor.getRootWindowInsets().getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            int[] origin = new int[2];
            decor.getLocationOnScreen(origin);
            Rect safe = new Rect(origin[0] + insets.left, origin[1] + insets.top,
                    origin[0] + decor.getWidth() - insets.right, origin[1] + decor.getHeight() - insets.bottom);
            if (!frame.intersect(safe) && !safe.isEmpty())
                frame.set(safe);
        }
        return frame;
    }

    static Rect anchorBounds(View anchor) {
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
    }

    @android.annotation.SuppressLint("RtlHardcoded") // Bounds are physical screen coordinates.
    void show(Rect bounds) {
        View decor = activity.getWindow().getDecorView();
        if (nativeMaterial) {
            ViewGroup root = activity.findViewById(android.R.id.content);
            FrameLayout host = new FrameLayout(activity) {
                @Override
                public boolean dispatchKeyEvent(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        if (event.getAction() == KeyEvent.ACTION_UP)
                            dismiss();
                        return true;
                    }
                    return super.dispatchKeyEvent(event);
                }
            };
            host.setFocusableInTouchMode(true);
            host.setOnClickListener(v -> dismiss());
            int[] origin = new int[2];
            root.getLocationOnScreen(origin);
            FrameLayout.LayoutParams placement = new FrameLayout.LayoutParams(bounds.width(), bounds.height());
            placement.leftMargin = bounds.left - origin[0];
            placement.topMargin = bounds.top - origin[1];
            surface.setElevation(Ui.dp(activity, elevationDp));
            host.addView(surface, placement);
            overlay = host;
            root.addView(host, new ViewGroup.LayoutParams(-1, -1));
            if (activity instanceof androidx.activity.ComponentActivity) {
                androidx.activity.ComponentActivity owner = (androidx.activity.ComponentActivity) activity;
                backCallback = new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        dismiss();
                    }
                };
                owner.getOnBackPressedDispatcher().addCallback(owner, backCallback);
            }
            host.requestFocus();
        } else if (ScopedWindowBlur.available(activity)) {
            dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            Window dialogWindow = dialog.getWindow();
            dialog.setContentView(surface);
            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(ignored -> finishDismiss());
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            if (!focusable)
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            dialogWindow.setElevation(Ui.dp(activity, elevationDp));
            ScopedWindowBlur.configure(dialogWindow, activity, cornerDp);
            dialog.show();
            WindowManager.LayoutParams params = dialogWindow.getAttributes();
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = bounds.left;
            params.y = bounds.top;
            params.width = bounds.width();
            params.height = bounds.height();
            if (Build.VERSION.SDK_INT >= 30)
                params.setFitInsetsTypes(0);
            dialogWindow.setAttributes(params);
        } else {
            window = new PopupWindow(surface, bounds.width(), bounds.height(), focusable);
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setElevation(Ui.dp(activity, elevationDp));
            window.setOutsideTouchable(true);
            window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            window.setOnDismissListener(this::finishDismiss);
            int x = bounds.left, y = bounds.top;
            if (Build.VERSION.SDK_INT >= 29) {
                window.setIsLaidOutInScreen(true);
                window.setIsClippedToScreen(true);
            } else {
                int[] screen = new int[2], location = new int[2];
                decor.getLocationOnScreen(screen);
                decor.getLocationInWindow(location);
                x += location[0] - screen[0];
                y += location[1] - screen[1];
            }
            window.showAtLocation(decor, Gravity.TOP | Gravity.LEFT, x, y);
        }
    }

    void dismiss() {
        if (dismissed)
            return;
        if (overlay != null && overlay.getParent() instanceof ViewGroup)
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        if (dialog != null)
            dialog.dismiss();
        if (window != null)
            window.dismiss();
        finishDismiss();
    }

    private void finishDismiss() {
        if (dismissed)
            return;
        dismissed = true;
        if (backCallback != null)
            backCallback.remove();
        if (onDismiss != null)
            onDismiss.run();
    }
}
