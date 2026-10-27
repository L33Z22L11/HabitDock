package dev.habitdock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/**
 * Experimental glass navigation: only the backdrop is blurred, never labels.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class GlassNavigation extends LinearLayout {
    private final ObricGlass glass;
    private final GlassFallback fallback;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextView[] labels = new TextView[3];
    private final ImageView[] icons = new ImageView[3];
    private final IntConsumer changed;
    private final RectF bounds = new RectF(), active = new RectF();
    private final Path clip = new Path();
    private float stretch = 1, fingerPosition, pressStretch = 1.08f, pressOffset;
    private ValueAnimator catchUp, press;
    private boolean touching;
    private int downSelected, pressedIndex, activePointerId = -1;
    private float position;
    private int selected;
    private final boolean vertical;
    private ValueAnimator motion;
    java.util.function.Consumer<Float> onDrag = value -> {
    };
    Runnable onPressStart = () -> {
    };
    Runnable onDragEnd = () -> {
    };
    private boolean dragging;
    private float down;

    void followPage(float value) {
        if (touching || (motion != null && motion.isRunning()))
            return;
        position = Math.max(0, Math.min(2, value));
        invalidate();
    }

    void startFromPage(float visiblePosition) {
        position = Math.max(0, Math.min(2, visiblePosition));
        invalidate();
    }

    void markSelected(int index) {
        selected = Math.max(0, Math.min(2, index));
        int ink = Ui.color(getContext(), R.color.ink);
        for (int i = 0; i < 3; i++) {
            int color = i == selected ? ink : androidx.core.graphics.ColorUtils.setAlphaComponent(ink, 191);
            labels[i].setTextColor(color);
            icons[i].setImageTintList(android.content.res.ColorStateList.valueOf(color));
            getChildAt(i).setSelected(i == selected);
        }
    }

    GlassNavigation(Context context, IntConsumer changed) {
        super(context);
        this.changed = changed;
        vertical = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setWillNotDraw(false);
        setPadding(dp(4), dp(4), dp(4), dp(4));

        String[] names = {"推荐", "全部", "设置"};
        int[] images = {R.drawable.ic_sparkles, R.drawable.ic_grid, R.drawable.ic_settings};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout item = Ui.column(context);
            item.setGravity(Gravity.CENTER);
            item.setFocusable(true);
            item.setContentDescription(names[i]);
            item.setOnClickListener(v -> select(index, true));
            icons[i] = new ImageView(context);
            icons[i].setImageResource(images[i]);
            item.addView(icons[i], new LayoutParams(dp(26), dp(26)));
            labels[i] = Ui.text(context, names[i], 10, R.color.ink);
            labels[i].setGravity(Gravity.CENTER);
            labels[i].setPadding(0, dp(1), 0, 0);
            item.addView(labels[i]);
            addView(item, vertical ? new LayoutParams(-1, 0, 1) : new LayoutParams(0, -1, 1));
        }
        glass = new ObricGlass(this, 0);
        fallback = new GlassFallback(context, false, false, .84f);
        select(0, false);
    }

    void beginPageDrag() {
        if (touching)
            return;
        if (motion != null)
            motion.cancel();
        stretch = 1;
    }

    void select(int index, boolean notify) {
        stopPress();
        int destination = Math.max(0, Math.min(2, index));
        if (motion != null)
            motion.cancel();
        markSelected(destination);
        if (notify) {
            final float start = position, initialStretch = stretch;
            motion = ValueAnimator.ofFloat(0, 1);
            motion.setDuration(800);
            motion.setInterpolator(new android.view.animation.LinearInterpolator());
            motion.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                float move = 1 - (float) (Math.exp(-12 * t) * (1 + 12 * t));
                position = t == 1 ? destination : start + (destination - start) * move;
                stretch = t == 1
                        ? 1
                        : 1 + (float) (Math.exp(-4 * t)
                                * ((initialStretch - 1) * Math.cos(12 * t) + .15 * Math.sin(12 * t)));
                invalidate();
            });
            motion.start();
            changed.accept(destination);
        } else {
            position = destination;
            stretch = 1;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.min(getWidth(), getHeight()) / 2f;
        bounds.set(0, 0, getWidth(), getHeight());
        clip.rewind();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        boolean dark = GlassFallback.dark(getContext());
        if (!glass.active()) {
            fallback.draw(canvas, bounds, radius);
        } else {
            paint.setColor(GlassFallback.tint(getContext(), 40));
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
        int first = Math.max(0, Math.min(2, (int) Math.floor(position)));
        int next = Math.min(2, first + 1);
        float fraction = Math.max(0, Math.min(1, position - first));
        View from = getChildAt(first), to = getChildAt(next);
        active.set(from.getLeft() + (to.getLeft() - from.getLeft()) * fraction,
                from.getTop() + (to.getTop() - from.getTop()) * fraction,
                from.getRight() + (to.getRight() - from.getRight()) * fraction,
                from.getBottom() + (to.getBottom() - from.getBottom()) * fraction);
        indicatorBounds(active, active, getWidth(), getHeight(),
                vertical ? getPaddingTop() : getPaddingLeft(), dp(4), stretch, vertical);
        int indicatorAlpha = dark ? (touching ? 58 : 45) : (touching ? 38 : 30);
        int indicatorColor = androidx.core.graphics.ColorUtils.blendARGB(
                dark ? Color.WHITE : Color.BLACK, Ui.color(getContext(), R.color.accent), .18f);
        paint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                indicatorColor, indicatorAlpha));
        canvas.drawRoundRect(active, Math.min(active.width(), active.height()) / 2,
                Math.min(active.width(), active.height()) / 2, paint);
        canvas.restore();
    }

    /**
     * Keeps equal idle insets at both ends and preserves a minimum margin during
     * stretching.
     */
    static void indicatorBounds(RectF slot, RectF result, float barWidth, float barHeight,
            float axisPadding, float inset, float stretch, boolean vertical) {
        float cx = slot.centerX(), cy = slot.centerY();
        float axisLength = (vertical ? slot.height() : slot.width())
                - 2 * Math.max(0, inset - axisPadding);
        float crossLength = (vertical ? barWidth : barHeight) - 2 * inset;
        float width = Math.max(0, vertical ? crossLength * (2 - stretch) : axisLength * stretch);
        float height = Math.max(0, vertical ? axisLength * stretch : crossLength * (2 - stretch));
        width = Math.min(width, Math.max(0, barWidth - 2 * inset));
        height = Math.min(height, Math.max(0, barHeight - 2 * inset));
        // At an edge, shift the stretched capsule inward rather than clipping its
        // stretch.
        cx = Math.max(inset + width / 2, Math.min(barWidth - inset - width / 2, cx));
        cy = Math.max(inset + height / 2, Math.min(barHeight - inset - height / 2, cy));
        result.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2);
    }

    private void stopPress() {
        if (catchUp != null)
            catchUp.cancel();
        if (press != null)
            press.cancel();
    }

    private void pressAt(int target) {
        stopPress();
        fingerPosition = target;
        float start = position;
        catchUp = null;
        if (Math.abs(start - target) > .001f) {
            catchUp = ValueAnimator.ofFloat(0, 1);
            catchUp.setDuration(110);
            catchUp.setInterpolator(new android.view.animation.DecelerateInterpolator(2));
            catchUp.addUpdateListener(a -> {
                position = start + (fingerPosition - start) * (float) a.getAnimatedValue();
                onDrag.accept(position);
                invalidate();
            });
            catchUp.start();
        }
        pressStretch = 1.08f;
        stretch = Math.max(stretch, 1.02f);
        float initialOffset = stretch - pressStretch;
        pressOffset = initialOffset;
        press = ValueAnimator.ofFloat(0, 1);
        press.setDuration(320);
        press.setInterpolator(new android.view.animation.LinearInterpolator());
        press.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            pressOffset = t == 1 ? 0 : initialOffset * (float) (Math.exp(-5 * t) * Math.cos(14 * t));
            stretch = pressStretch + pressOffset;
            invalidate();
        });
        press.start();
        invalidate();
    }

    private float positionAt(float axis) {
        for (int i = 0; i < getChildCount() - 1; i++) {
            View a = getChildAt(i), b = getChildAt(i + 1);
            float start = vertical ? (a.getTop() + a.getBottom()) / 2f : (a.getLeft() + a.getRight()) / 2f;
            float end = vertical ? (b.getTop() + b.getBottom()) / 2f : (b.getLeft() + b.getRight()) / 2f;
            if (axis <= end)
                return Math.max(0, Math.min(2, i + (axis - start) / Math.max(1, end - start)));
        }
        return 2;
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
        return true;
    }

    private void finishGesture(boolean canceled) {
        if (!touching)
            return;
        int target = canceled ? downSelected : dragging ? Math.round(fingerPosition) : pressedIndex;
        stopPress();
        animateItem(pressedIndex, false);
        onDragEnd.run();
        touching = false;
        dragging = false;
        activePointerId = -1;
        select(target, true);
        if (!canceled)
            performClick();
        getParent().requestDisallowInterceptTouchEvent(false);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        float slot = ((vertical ? getHeight() : getWidth()) - dp(8)) / 3f;
        if (slot <= 0)
            return false;
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN :
                stopPress();
                if (motion != null)
                    motion.cancel();
                downSelected = selected;
                activePointerId = event.getPointerId(0);
                down = vertical ? event.getY(0) : event.getX(0);
                pressedIndex = Math.round(positionAt(down));
                animateItem(pressedIndex, true);
                dragging = false;
                touching = true;
                onPressStart.run();
                pressAt(pressedIndex);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case android.view.MotionEvent.ACTION_MOVE :
                if (!touching)
                    return true;
                int pointer = event.findPointerIndex(activePointerId);
                if (pointer < 0) {
                    finishGesture(true);
                    return true;
                }
                float axis = vertical ? event.getY(pointer) : event.getX(pointer);
                float delta = axis - down;
                int threshold = Math.max(dp(10), ViewConfiguration.get(getContext()).getScaledTouchSlop());
                if (Math.abs(delta) > threshold)
                    dragging = true;
                if (dragging) {
                    // Lock the pressed item as the anchor, so touching near its edge cannot jump.
                    fingerPosition = Math.max(0, Math.min(2, pressedIndex + delta / slot));
                    float eventDelta = fingerPosition - position;
                    pressStretch = 1.08f + Math.min(.07f, Math.abs(eventDelta) * .15f);
                    stretch = pressStretch + pressOffset;
                    if (catchUp == null || !catchUp.isRunning()) {
                        position = fingerPosition;
                        onDrag.accept(position);
                    }
                    invalidate();
                }
                return true;
            case android.view.MotionEvent.ACTION_UP :
                finishGesture(false);
                return true;
            case android.view.MotionEvent.ACTION_POINTER_UP :
                if (event.getPointerId(event.getActionIndex()) == activePointerId)
                    finishGesture(true);
                return true;
            case android.view.MotionEvent.ACTION_CANCEL :
                finishGesture(true);
                return true;
            default :
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPress();
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).animate().cancel();
            getChildAt(i).setScaleX(1);
            getChildAt(i).setScaleY(1);
        }
        if (touching)
            onDragEnd.run();
        touching = false;
        dragging = false;
        if (motion != null)
            motion.cancel();
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return Ui.dp(getContext(), value);
    }

    private void animateItem(int index, boolean down) {
        View item = getChildAt(index);
        item.animate().cancel();
        item.animate().scaleX(down ? .95f : 1f).scaleY(down ? .95f : 1f)
                .setDuration(down ? 90 : 300)
                .setInterpolator(down
                        ? new android.view.animation.DecelerateInterpolator()
                        : new android.view.animation.OvershootInterpolator(1.3f))
                .start();
    }
}
