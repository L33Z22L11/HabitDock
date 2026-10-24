package dev.habitdock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.function.IntConsumer;

/**
 * Theme-token segmented choices with a sliding selection and horizontal drag.
 */
// Created with required data and callbacks; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class SlidingChoice extends FrameLayout {
    private final View thumb;
    private final TextView[] labels;
    private final IntConsumer changed;
    private final int inset, slop;
    private int selected;
    private FormRow form;
    private float downX, downY;
    private boolean dragging;

    SlidingChoice(Context context, String[] choices, String tagPrefix, IntConsumer changed) {
        super(context);
        this.changed = changed;
        inset = Ui.dp(context, 4);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int container = token(dark
                ? com.google.android.material.R.attr.colorSurfaceContainerLow
                : com.google.android.material.R.attr.colorSurfaceContainerHighest);
        int selection = token(dark
                ? com.google.android.material.R.attr.colorSurfaceContainerHighest
                : com.google.android.material.R.attr.colorSurfaceContainerLowest);
        if (MainStyle.load(context).hasBackground()) {
            container = androidx.core.graphics.ColorUtils.setAlphaComponent(container, 80);
            selection = androidx.core.graphics.ColorUtils.setAlphaComponent(selection, 160);
        }
        setBackground(shape(container, 14));
        setPadding(inset, inset, inset, inset);
        thumb = new View(context);
        thumb.setBackground(shape(selection, 10));
        thumb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(thumb, new LayoutParams(0, -1));
        LinearLayout row = new LinearLayout(context);
        addView(row, new LayoutParams(-1, -1));
        labels = new TextView[choices.length];
        for (int i = 0; i < choices.length; i++) {
            int index = i;
            TextView label = Ui.text(context, choices[i], 13, R.color.muted);
            label.setTag(tagPrefix + i);
            label.setGravity(Gravity.CENTER);
            label.setPadding(Ui.dp(context, 3), 0, Ui.dp(context, 3), 0);
            label.setFocusable(true);
            label.setOnClickListener(v -> commit(index));
            label.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true);
                    info.setChecked(selected == index);
                }
            });
            row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
            labels[i] = label;
        }
        select(0, false);
    }

    int selection() {
        return selected;
    }

    void addRow(LinearLayout body, String title, int defaultValue) {
        form = new FormRow(getContext(), title);
        form.control(this, Math.round(Math.max(Ui.SEGMENT_HEIGHT,
                20 * getResources().getConfiguration().fontScale + 16)), false, null);
        form.reset(getTag() == null ? title : getTag().toString(), () -> selected != defaultValue,
                () -> commit(defaultValue));
        body.addView(form, new LinearLayout.LayoutParams(-1, -2));
    }

    private int token(int attr) {
        return com.google.android.material.color.MaterialColors.getColor(getContext(), attr,
                Ui.color(getContext(), R.color.surface));
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(Ui.dp(getContext(), radius));
        return drawable;
    }

    void select(int index, boolean animate) {
        selected = Math.max(0, Math.min(labels.length - 1, index));
        for (int i = 0; i < labels.length; i++) {
            labels[i].setSelected(i == selected);
            labels[i].setTextColor(Ui.color(getContext(), i == selected ? R.color.ink : R.color.muted));
        }
        move(selected * segmentWidth(), animate);
        if (form != null)
            form.refreshReset();
    }

    private float segmentWidth() {
        return Math.max(0, getWidth() - inset * 2f) / labels.length;
    }

    private void move(float x, boolean animate) {
        thumb.animate().cancel();
        if (animate && isLaidOut())
            thumb.animate().translationX(x).setDuration(160).start();
        else
            thumb.setTranslationX(x);
    }

    private void commit(int index) {
        int old = selected;
        select(index, true);
        if (old != selected)
            changed.accept(selected);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float widest = 0;
        for (TextView label : labels)
            widest = Math.max(widest, label.getPaint().measureText(label.getText().toString()));
        int desired = Math.round((widest + Ui.dp(getContext(), 16)) * labels.length + inset * 2);
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY)
            widthSpec = MeasureSpec.makeMeasureSpec(resolveSize(desired, widthSpec), MeasureSpec.EXACTLY);
        super.onMeasure(widthSpec, heightSpec);
        // Size the thumb in this measurement pass. Requesting another layout from
        // onSizeChanged can leave its first rendered width at zero.
        int width = Math.round(Math.max(0, getMeasuredWidth() - inset * 2f) / labels.length);
        int height = Math.max(0, getMeasuredHeight() - inset * 2);
        ((LayoutParams) thumb.getLayoutParams()).width = width;
        thumb.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        move(selected * segmentWidth(), false);
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        float segment = segmentWidth();
        if (segment <= 0)
            return false;
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN :
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                thumb.animate().cancel();
                return true;
            case android.view.MotionEvent.ACTION_MOVE :
                float dx = event.getX() - downX, dy = event.getY() - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging)
                    move(Math.max(0, Math.min(segment * (labels.length - 1), event.getX() - inset - segment / 2)),
                            false);
                return true;
            case android.view.MotionEvent.ACTION_UP :
                int index = Math.max(0, Math.min(labels.length - 1, (int) ((event.getX() - inset) / segment)));
                getParent().requestDisallowInterceptTouchEvent(false);
                commit(index);
                performClick();
                return true;
            case android.view.MotionEvent.ACTION_CANCEL :
                getParent().requestDisallowInterceptTouchEvent(false);
                move(selected * segment, true);
                return true;
            default :
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
