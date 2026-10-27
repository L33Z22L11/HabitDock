package dev.habitdock;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.function.BooleanSupplier;

/**
 * Shared label, optional reset and trailing control; wraps on narrow screens.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class FormRow extends LinearLayout {
    private static final int HEADING_MIN_WIDTH = 144, RESET_WIDTH = 48, VALUE_WIDTH = 36, GAP = 4;
    private final LinearLayout heading;
    private final TextView label;
    private ImageButton reset;
    private BooleanSupplier modified;
    private View control;
    private TextView value;
    private boolean flexible;

    FormRow(Context context, String title) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(context, Ui.FORM_ROW_HEIGHT));
        heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        label = Ui.text(context, title, Ui.FORM_TEXT_SP, R.color.ink);
        heading.addView(label, new LayoutParams(-2, -2));
        addView(heading);
    }

    void control(View view, int height, boolean stretch, TextView number) {
        control = view;
        flexible = stretch;
        value = number;
        addView(view, new LayoutParams(-2, Ui.dp(getContext(), height)));
        if (number != null) {
            number.setTextColor(Ui.color(getContext(), R.color.muted));
            number.setTextSize(13);
            number.setGravity(Gravity.END);
            addView(number, new LayoutParams(Ui.dp(getContext(), VALUE_WIDTH), -2));
        }
    }

    void reset(String tag, BooleanSupplier changed, Runnable action) {
        modified = changed;
        reset = Ui.icon(getContext(), R.drawable.ic_refresh, "重置" + label.getText(), v -> action.run());
        reset.setTag(tag + "-reset");
        // The full touch target begins immediately after the label.
        LayoutParams resetSlot = new LayoutParams(Ui.dp(getContext(), RESET_WIDTH), Ui.dp(getContext(), 48));
        reset.setLayoutParams(resetSlot);
        int padding = Ui.dp(getContext(), 16);
        reset.setPadding(padding, padding, padding, padding);
        reset.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(getContext(), R.color.muted)));
        heading.addView(reset);
        refreshReset();
    }

    void refreshReset() {
        if (reset != null)
            reset.setVisibility(modified.getAsBoolean() ? VISIBLE : INVISIBLE);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        refreshReset();
        int labelWidth = (int) Math.ceil(label.getPaint().measureText(label.getText().toString()));
        label.getLayoutParams().width = labelWidth;
        int resetWidth = reset == null ? 0 : Ui.dp(getContext(), RESET_WIDTH);
        // One shared minimum for the complete label + reset group; spare width goes
        // after the button.
        int headingWidth = Math.max(
                Math.round(Ui.dp(getContext(), HEADING_MIN_WIDTH)
                        * Math.max(1f, getResources().getConfiguration().fontScale)),
                labelWidth + resetWidth);
        control.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(control.getLayoutParams().height, MeasureSpec.EXACTLY));
        int valueWidth = value == null
                ? 0
                : Math.max(Ui.dp(getContext(), VALUE_WIDTH),
                        (int) Math.ceil(value.getPaint().measureText("100%")));
        int required = headingWidth + Ui.dp(getContext(), GAP)
                + (flexible ? Ui.dp(getContext(), 120) : control.getMeasuredWidth())
                + (value == null ? 0 : valueWidth + Ui.dp(getContext(), GAP));
        boolean stacked = getResources().getConfiguration().fontScale > 1.35f || required > width;
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        if (value != null && (value.getParent() == heading) != stacked) {
            LayoutParams number = (LayoutParams) value.getLayoutParams();
            ((android.view.ViewGroup) value.getParent()).removeView(value);
            number.width = stacked ? 0 : valueWidth;
            number.weight = stacked ? 1 : 0;
            if (stacked)
                heading.addView(value, number);
            else
                addView(value, number);
        }
        LayoutParams title = (LayoutParams) heading.getLayoutParams();
        title.width = stacked ? -1 : flexible ? headingWidth : 0;
        title.weight = !stacked && !flexible ? 1 : 0;
        LayoutParams slot = (LayoutParams) control.getLayoutParams();
        slot.width = stacked ? -1 : flexible ? 0 : -2;
        slot.weight = !stacked && flexible ? 1 : 0;
        slot.setMarginStart(stacked ? 0 : Ui.dp(getContext(), GAP));
        slot.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        if (value != null) {
            LayoutParams number = (LayoutParams) value.getLayoutParams();
            number.width = stacked ? 0 : valueWidth;
            number.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            number.setMarginStart(stacked ? 0 : Ui.dp(getContext(), GAP));
        }
        super.onMeasure(widthSpec, heightSpec);
    }

}
