package dev.habitdock;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/**
 * Shared glass shell for header controls, separate from the progressive
 * backdrop mask.
 */
final class GlassSurface extends FrameLayout {
    private final ObricGlass material;
    private final GlassFallback fallback;
    private final RectF bounds = new RectF();
    private final float cornerDp;
    private final boolean windowBacked;
    private final boolean liquid;
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator pressLight;
    private float pressAmount;

    GlassSurface(Context context) {
        this(context, 24, ObricGlass.VIEW_BACKGROUND);
    }

    GlassSurface(Context context, float cornerDp, int source) {
        this(context, cornerDp, source, source == ObricGlass.WINDOW_BACKGROUND);
    }

    GlassSurface(Context context, float cornerDp, int source, boolean continuous) {
        super(context);
        this.cornerDp = cornerDp;
        this.liquid = MainStyle.liquid(context);
        this.windowBacked = source == ObricGlass.WINDOW_BACKGROUND;
        setWillNotDraw(false);
        setClipToOutline(true);
        material = new ObricGlass(this, cornerDp, source, false, continuous);
        fallback = new GlassFallback(context, false, continuous);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!material.active() || windowBacked) {
            bounds.set(0, 0, getWidth(), getHeight());
            fallback.draw(canvas, bounds, Ui.dp(getContext(), cornerDp));
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (liquid && !windowBacked && action == MotionEvent.ACTION_DOWN)
            press(true);
        boolean handled = super.dispatchTouchEvent(event);
        if (liquid && !windowBacked && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || (action == MotionEvent.ACTION_DOWN && !handled)))
            press(false);
        return handled;
    }

    private void press(boolean down) {
        animate().cancel();
        animate().scaleX(down ? .96f : 1f).scaleY(down ? .96f : 1f)
                .setDuration(down ? 90 : 300)
                .setInterpolator(down ? new DecelerateInterpolator() : new OvershootInterpolator(1.3f))
                .start();
        if (pressLight != null)
            pressLight.cancel();
        pressLight = ValueAnimator.ofFloat(pressAmount, down ? 1 : 0);
        pressLight.setDuration(down ? 90 : 200);
        pressLight.addUpdateListener(animation -> {
            pressAmount = (float) animation.getAnimatedValue();
            invalidate();
        });
        pressLight.start();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (pressAmount <= 0)
            return;
        bounds.set(0, 0, getWidth(), getHeight());
        pressedPaint.setColor(Color.argb(Math.round(22 * pressAmount), 255, 255, 255));
        canvas.drawRoundRect(bounds, Ui.dp(getContext(), cornerDp), Ui.dp(getContext(), cornerDp), pressedPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        animate().cancel();
        if (pressLight != null)
            pressLight.cancel();
        setScaleX(1);
        setScaleY(1);
        pressAmount = 0;
        super.onDetachedFromWindow();
    }
}
