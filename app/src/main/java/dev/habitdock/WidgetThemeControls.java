package dev.habitdock;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.slider.Slider;
import java.util.function.Consumer;

/** Compact theme swatches and background-only transparency. */
// Created with required data and callbacks; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class WidgetThemeControls extends LinearLayout {
    private final Activity activity;
    private final Consumer<WidgetTheme> changed;
    private final LinearLayout[] choices = new LinearLayout[5];
    private final View[] swatches = new View[5];
    private final WidgetTheme[] samples = new WidgetTheme[5];
    private final Slider opacity;
    private final TextView opacityValue;
    private final ColorSettingRow custom;
    private WidgetTheme theme = WidgetTheme.defaults();
    private boolean binding;

    WidgetThemeControls(Activity activity, Consumer<WidgetTheme> changed) {
        super(activity);
        this.activity = activity;
        this.changed = changed;
        setOrientation(VERTICAL);
        setPadding(Ui.dp(activity, 16), Ui.dp(activity, 20), Ui.dp(activity, 16), Ui.dp(activity, 12));
        setBackground(Ui.shape(activity, R.color.surface, 24));
        TextView title = Ui.text(activity, "主题", 16, R.color.ink);
        Ui.title(title);
        addView(title);
        Ui.space(this, 16);
        HorizontalScrollView scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        LinearLayout options = new LinearLayout(activity);
        scroller.addView(options, new HorizontalScrollView.LayoutParams(-1, -2));
        addView(scroller, new LayoutParams(-1, -2));
        for (int index = 0; index < choices.length; index++) {
            final int mode = index;
            LinearLayout choice = Ui.column(activity);
            choice.setTag("widget-theme-" + mode);
            choice.setGravity(Gravity.CENTER);
            choice.setMinimumWidth(Ui.dp(activity, 54));
            choice.setPadding(Ui.dp(activity, 2), Ui.dp(activity, 4), Ui.dp(activity, 2), Ui.dp(activity, 8));
            Ui.nativeTouch(choice);
            View swatch = new View(activity) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override
                protected void onDraw(Canvas canvas) {
                    float density = getResources().getDisplayMetrics().density;
                    float inset = 4 * density, right = getWidth() - inset, bottom = getHeight() - inset;
                    WidgetTheme sample = samples[mode];
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(sample.surface(getContext()));
                    canvas.drawRoundRect(inset, inset, right, bottom, 12 * density, 12 * density, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(density);
                    paint.setColor(Ui.color(getContext(), R.color.stroke));
                    canvas.drawRoundRect(inset, inset, right, bottom, 12 * density, 12 * density, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(sample.foreground(getContext()));
                    for (int row = 0; row < 2; row++)
                        for (int column = 0; column < 3; column++) {
                            float x = getWidth() / 2f + (column - 1) * 9 * density;
                            float y = getHeight() / 2f + (row == 0 ? -5 : 5) * density;
                            canvas.drawRoundRect(x - 2.5f * density, y - 2.5f * density,
                                    x + 2.5f * density, y + 2.5f * density, density, density, paint);
                        }
                    if (theme.mode == mode) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.5f * density);
                        paint.setColor(Ui.color(getContext(), R.color.accent));
                        canvas.drawRoundRect(density, density, getWidth() - density, getHeight() - density,
                                15 * density, 15 * density, paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                }
            };
            swatches[index] = swatch;
            choice.addView(swatch, new LayoutParams(Ui.dp(activity, 50), Ui.dp(activity, 50)));
            TextView label = Ui.text(activity, WidgetTheme.LABELS[index], 12, R.color.ink);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Ui.dp(activity, 6), 0, 0);
            choice.addView(label);
            choice.setOnClickListener(v -> update(new WidgetTheme(mode, theme.transparency, theme.color)));
            choices[index] = choice;
            options.addView(choice, new LayoutParams(0, -2, 1));
        }
        custom = new ColorSettingRow(activity, "背景颜色",
                () -> IconColorDialog.show(activity, "组件背景颜色", theme.color, false,
                        color -> update(new WidgetTheme(WidgetTheme.CUSTOM, theme.transparency, color))));
        custom.setTag("widget-custom-color");
        addView(custom, new LayoutParams(-1, -2));
        Ui.space(this, 12);
        opacityValue = Ui.text(activity, "", 13, R.color.muted);
        opacityValue.setTag("widget-transparency-value");
        opacity = new Slider(activity);
        opacity.setTag("widget-transparency");
        opacity.setContentDescription("组件背景透明度");
        opacity.setValueTo(100);
        opacity.setStepSize(1);
        opacity.setTickVisible(false);
        opacity.setTrackStopIndicatorSize(0);
        opacity.setLabelFormatter(value -> Math.round(value) + "%");
        opacity.addOnChangeListener((slider, value, fromUser) -> {
            if (!binding)
                update(new WidgetTheme(theme.mode, Math.round(value), theme.color));
        });
        Ui.sliderRow(this, "背景透明度", opacity, opacityValue);
        bind(theme);
    }

    void bind(WidgetTheme value) {
        binding = true;
        theme = value;
        opacity.setValue(value.transparency);
        opacityValue.setText(activity.getString(R.string.widget_scale, value.transparency));
        custom.setVisibility(value.mode == WidgetTheme.CUSTOM ? VISIBLE : GONE);
        custom.bind(value.color, false);
        for (int i = 0; i < choices.length; i++) {
            if (samples[i] == null || samples[i].color != value.color)
                samples[i] = new WidgetTheme(i, 0, value.color);
            choices[i].setSelected(i == value.mode);
            choices[i].setContentDescription(WidgetTheme.LABELS[i] + (i == value.mode ? "，已选中" : ""));
            swatches[i].invalidate();
        }
        binding = false;
    }

    private void update(WidgetTheme value) {
        bind(value);
        changed.accept(value);
    }
}
