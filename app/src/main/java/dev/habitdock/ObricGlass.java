package dev.habitdock;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import java.lang.reflect.Method;

/**
 * Optional OEM backdrop material; the normal translucent drawing remains the
 * fallback.
 */
final class ObricGlass {
    static final int VIEW_BACKGROUND = 2, WINDOW_BACKGROUND = 3;
    private static final int MATERIAL = 1;
    private static final boolean SUPPORTED = supported();
    private final View view;
    private final float cornerDp;
    private final int source;
    private final boolean topOnly;
    private final android.graphics.drawable.Drawable originalBackground;
    private boolean active, failed;
    private Object extension;
    private Class<?> api;

    static boolean available(android.content.Context context) {
        return MainStyle.liquid(context) && SUPPORTED;
    }

    private static boolean supported() {
        try {
            Class.forName("android.view.IExtEffect");
            View.class.getDeclaredMethod("getExtEffect");
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    ObricGlass(View view, float cornerDp) {
        this(view, cornerDp, VIEW_BACKGROUND, false, false);
    }

    ObricGlass(View view, float cornerDp, int source, boolean topOnly) {
        this(view, cornerDp, source, topOnly, source == WINDOW_BACKGROUND && !topOnly);
    }

    ObricGlass(View view, float cornerDp, int source, boolean topOnly, boolean continuous) {
        this.view = view;
        this.cornerDp = cornerDp;
        this.source = source;
        this.topOnly = topOnly;
        this.originalBackground = view.getBackground();
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View target, Outline outline) {
                if (topOnly || continuous) {
                    Path path = new Path();
                    GlassFallback.shape(path, new RectF(0, 0, target.getWidth(), target.getHeight()), radius(), topOnly,
                            continuous);
                    outline.setConvexPath(path);
                } else {
                    outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), radius());
                }
            }
        });
        if (!available(view.getContext()))
            return;
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View target) {
                target.post(ObricGlass.this::apply);
            }

            @Override
            public void onViewDetachedFromWindow(View target) {
                clear();
            }
        });
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot)
                apply();
        });
        if (view.isAttachedToWindow())
            view.post(this::apply);
    }

    boolean active() {
        return active;
    }

    private float radius() {
        return cornerDp > 0 ? Ui.dp(view.getContext(), cornerDp) : Math.min(view.getWidth(), view.getHeight()) / 2f;
    }

    private void apply() {
        if (!MainStyle.liquid(view.getContext()) || failed || !view.isAttachedToWindow()
                || !view.isHardwareAccelerated()
                || view.getWidth() == 0 || view.getHeight() == 0)
            return;
        if (active) {
            if (view.getBackground() instanceof GradientDrawable && !topOnly)
                ((GradientDrawable) view.getBackground()).setCornerRadius(radius());
            view.invalidateOutline();
            return;
        }
        try {
            api = Class.forName("android.view.IExtEffect");
            extension = invoke(View.class, view, "getExtEffect", new Class<?>[]{});
            Object parameters = invoke(api, extension, "getEffectParas", new Class<?>[]{int.class}, MATERIAL);
            invoke(api, parameters, "setObricEffectSource", new Class<?>[]{int.class, int.class}, source, 1);
            // OEM token units are passed through, not converted from dp.
            invoke(api, parameters, "setObricBlurEffect", new Class<?>[]{float.class}, 2f);
            boolean dark = (view.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            // OUI regular-fluid dark token, not the older black navbar mask.
            int[] blend = dark
                    ? new int[]{0x99989898, 28, 0xccb4b4b4, 19, 0x14000000, 3}
                    : new int[]{0x66f7f7f7, 28, 0x4df7f7f7, 18, 0x4df7f7f7, 3};
            invoke(api, parameters, "setObricBlendEffect", new Class<?>[]{int[].class}, (Object) blend);
            // OUI regular-fluid day/night tokens. Colors retain raw bits, including NaN
            // encodings.
            float[] rr = {40, -120, 1.5f, 3, 6, dark ? .8f : 1f, 0, 2,
                    Float.intBitsToFloat(0xff808080), 18,
                    Float.intBitsToFloat(dark ? 0x33ffffff : 0x1affffff), 102, 0};
            invoke(api, parameters, "setObricLiquidGlassEffectRR", new Class<?>[]{float[].class}, (Object) rr);
            GradientDrawable background = new GradientDrawable();
            background.setColor(topOnly ? GlassFallback.tint(view.getContext(), 40) : Color.TRANSPARENT);
            if (topOnly) {
                float r = radius();
                background.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            } else {
                background.setCornerRadius(radius());
            }
            if (source != WINDOW_BACKGROUND)
                view.setBackground(background);
            // Configuration alone does not reach the renderer. Commit the parameter slot
            // last.
            invoke(api, extension, "setCurrentEffectParas", new Class<?>[]{int.class}, MATERIAL);
            active = true;
            view.invalidateOutline();
            view.invalidate();
            Log.d("ObricGlass", "Activated parameter slot 1: " + view.getClass().getSimpleName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            clear();
            failed = true;
            Log.d("ObricGlass", "OEM material unavailable; using translucent surface", failure);
        }
    }

    private void clear() {
        if (extension != null && api != null) {
            try {
                invoke(api, extension, "setCurrentEffectParas", new Class<?>[]{int.class}, 0);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        extension = null;
        active = false;
        view.setBackground(originalBackground);
        view.invalidate();
    }

    private static Object invoke(Class<?> type, Object receiver, String name, Class<?>[] signature, Object... args)
            throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }
}
