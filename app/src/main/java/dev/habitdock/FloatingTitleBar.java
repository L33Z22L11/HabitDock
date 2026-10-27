package dev.habitdock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import java.lang.reflect.Method;

/**
 * Transparent foreground controls over a separately enabled progressive
 * backdrop.
 */
final class FloatingTitleBar extends FrameLayout {
    static final int CONTROL_HEIGHT_DP = 48;
    static final int CONTENT_GAP_DP = 16;
    static final int MASK_HEIGHT_DP = CONTROL_HEIGHT_DP + CONTENT_GAP_DP;
    static final int CONTENT_TOP_DP = CONTROL_HEIGHT_DP + CONTENT_GAP_DP;
    private final View controls;
    private final Mask mask;

    FloatingTitleBar(Context context, View controls) {
        super(context);
        this.controls = controls;
        mask = new Mask(context);
        addView(mask, new LayoutParams(-1, -1));
        addView(controls, new LayoutParams(-1, Ui.dp(context, CONTROL_HEIGHT_DP)));
    }

    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        return row;
    }

    static LinearLayout title(Context context, String title, View... buttons) {
        LinearLayout row = row(context);
        TextView text = Ui.text(context, title, 20, R.color.ink);
        Ui.headerTitle(text);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        for (View button : buttons)
            addButton(row, button);
        return row;
    }

    static void addButton(LinearLayout row, View button) {
        Context context = row.getContext();
        GlassSurface shell = new GlassSurface(context);
        if (button instanceof android.widget.ImageView)
            ((android.widget.ImageView) button).setImageTintList(
                    android.content.res.ColorStateList.valueOf(Ui.color(context, R.color.ink)));
        shell.addView(button, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48));
        size.leftMargin = Ui.dp(context, 8);
        row.addView(shell, size);
    }

    void blurred(boolean enabled) {
        mask.request(enabled);
    }

    void topInset(int inset) {
        LayoutParams row = (LayoutParams) controls.getLayoutParams();
        if (row.topMargin != inset) {
            row.topMargin = inset;
            controls.setLayoutParams(row);
        }
        android.view.ViewGroup.LayoutParams size = getLayoutParams();
        int height = inset + Ui.dp(getContext(), MASK_HEIGHT_DP);
        if (size.height != height) {
            size.height = height;
            setLayoutParams(size);
        }
    }

    private static final class Mask extends View {
        // Verified in OUI BlurDirection and passed unchanged through its legacy
        // adapter.
        private static final int PROGRESS_DOWN_UP = 1, NONE = -1;
        // Verified OUI CurveType: fast growth from the lower edge, gentle approach to
        // full blur.
        private static final int IN_OUT_CUBIC = 6;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean wallpaper, liquid;
        private boolean requested = true, active, failed;
        private Object extension;
        private Method effect;
        private boolean legacy;

        Mask(Context context) {
            super(context);
            wallpaper = MainStyle.load(context).hasBackground();
            liquid = MainStyle.liquid(context);
            setBackgroundColor(Color.TRANSPARENT);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void request(boolean enabled) {
            if (requested == enabled)
                return;
            requested = enabled;
            apply();
            invalidate();
        }

        private static Object call(Class<?> type, Object receiver, String name, Class<?>[] signature,
                Object... arguments) throws ReflectiveOperationException {
            Method method = type.getDeclaredMethod(name, signature);
            method.setAccessible(true);
            return method.invoke(receiver, arguments);
        }

        private void initialize() throws ReflectiveOperationException {
            try {
                Class<?> api = Class.forName("android.view.IExtEffect");
                Object candidate = call(View.class, this, "getExtEffect", new Class<?>[]{});
                Object parameters = call(api, candidate, "getEffectParas", new Class<?>[]{int.class}, 1);
                call(api, parameters, "setObricEffectSource", new Class<?>[]{int.class, int.class}, 2, 1);
                // OUI's modern progressive token is 8; the legacy radius 40 is not
                // interchangeable.
                call(api, parameters, "setObricBlurEffect",
                        new Class<?>[]{float.class, float.class, int.class, int.class, float.class},
                        8f, 1f, PROGRESS_DOWN_UP, IN_OUT_CUBIC, 1f);
                call(api, parameters, "setObricBlendEffect", new Class<?>[]{int[].class}, (Object) new int[0]);
                effect = api.getDeclaredMethod("setCurrentEffectParas", int.class);
                effect.setAccessible(true);
                effect.invoke(candidate, 1);
                extension = candidate;
                legacy = false;
                active = true;
                Log.d("ProgressBlur", "Activated IExtEffect progressive slot 1, radius=8, direction=1, curve=6");
                return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                Log.d("ProgressBlur", "Modern progressive API unavailable; trying legacy API", unavailable);
            }
            Object candidate = call(View.class, this, "getExt", new Class<?>[]{});
            Method legacyEffect = Class.forName("android.view.IExtView").getDeclaredMethod(
                    "setBackdropBlurEffectExt", int.class, float.class, int[].class);
            legacyEffect.setAccessible(true);
            legacyEffect.invoke(candidate, PROGRESS_DOWN_UP, 40f, new int[0]);
            effect = legacyEffect;
            extension = candidate;
            legacy = true;
            active = true;
            Log.d("ProgressBlur", "Activated legacy progressive blur, radius=40, direction=1");
        }

        private void setEffect(boolean enabled) throws ReflectiveOperationException {
            if (legacy)
                effect.invoke(extension, enabled ? PROGRESS_DOWN_UP : NONE, enabled ? 40f : 0f, new int[0]);
            else
                effect.invoke(extension, enabled ? 1 : 0);
        }

        private void apply() {
            if (!liquid || failed || !isAttachedToWindow() || !isHardwareAccelerated()
                    || getWidth() == 0 || getHeight() == 0 || active == requested)
                return;
            try {
                if (extension == null)
                    initialize();
                else {
                    setEffect(requested);
                    active = requested;
                    Log.d("ProgressBlur", (legacy ? "Legacy" : "IExtEffect") + " requested=" + requested);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                active = false;
                failed = true;
                Log.d("ProgressBlur", "Both progressive paths unavailable; using translucent gradient", failure);
            }
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            post(this::apply);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (active && effect != null) {
                try {
                    setEffect(false);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            active = false;
            extension = null;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            post(this::apply);
            int color = Ui.color(getContext(), R.color.page);
            paint.setShader(new LinearGradient(0, 0, 0, Math.max(1, h),
                    new int[]{ColorUtils.setAlphaComponent(color, 120), ColorUtils.setAlphaComponent(color, 0)},
                    null, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // The list fades independently. A page-colored fallback gradient on
            // top of an image wallpaper makes the header visibly darker than
            // the rest of the wallpaper, especially in landscape.
            if (liquid && requested && !active && !wallpaper)
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}
