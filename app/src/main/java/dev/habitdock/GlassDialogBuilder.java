package dev.habitdock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Material dialog behavior with a shared glass surface behind its content. */
final class GlassDialogBuilder extends MaterialAlertDialogBuilder {
    private final boolean localBlur;

    GlassDialogBuilder(Context context) {
        this(context, ScopedWindowBlur.available(context));
    }

    private GlassDialogBuilder(Context context, boolean localBlur) {
        super(context, localBlur ? R.style.GlassBlurDialogTheme : 0);
        this.localBlur = localBlur;
        if (localBlur) {
            setBackgroundInsetStart(0);
            setBackgroundInsetTop(0);
            setBackgroundInsetEnd(0);
            setBackgroundInsetBottom(0);
        }
    }

    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        if (!MainStyle.liquid(getContext()))
            return dialog;
        Window window = dialog.getWindow();
        if (window == null)
            return dialog;
        View decor = window.getDecorView();
        Drawable background = decor.getBackground();
        Rect inset = new Rect();
        if (background != null)
            background.getPadding(inset);
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private void install(View view) {
                if (!view.isAttachedToWindow())
                    return;
                View panel = view.findViewById(androidx.appcompat.R.id.parentPanel);
                if (panel == null)
                    return; // Keep the original Material background if no panel exists.
                GlassPanels.attach(panel, 28, ObricGlass.WINDOW_BACKGROUND);
                // Preserve the configured Material insets and outside-touch behavior.
                // Do not make the window transparent until its replacement surface exists.
                Drawable replacement = new ColorDrawable(Color.TRANSPARENT);
                if (localBlur && Build.VERSION.SDK_INT >= 31) {
                    GradientDrawable shape = new GradientDrawable();
                    shape.setColor(0x01000000);
                    shape.setCornerRadius(Ui.dp(view.getContext(), 28));
                    replacement = shape;
                }
                window.setBackgroundDrawable(new InsetDrawable(replacement,
                        inset.left, inset.top, inset.right, inset.bottom));
                if (localBlur && Build.VERSION.SDK_INT >= 31) {
                    try {
                        window.setBackgroundBlurRadius(Ui.dp(view.getContext(), 20));
                    } catch (RuntimeException unavailable) {
                        android.util.Log.w("GlassDialogBuilder", "Local blur unavailable", unavailable);
                        window.setBackgroundDrawable(new InsetDrawable(new ColorDrawable(Color.TRANSPARENT),
                                inset.left, inset.top, inset.right, inset.bottom));
                    }
                }
            }

            @Override
            public void onViewAttachedToWindow(View view) {
                if (view.findViewById(androidx.appcompat.R.id.parentPanel) != null)
                    install(view);
                else
                    view.post(() -> install(view));
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
            }
        });
        return dialog;
    }
}
