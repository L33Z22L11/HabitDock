package dev.habitdock;

import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int FORM_ROW_HEIGHT = 56;
    static final int SEGMENT_HEIGHT = 40;
    static int color(Context c, int resource) {
        int attr = resource == R.color.accent
                ? com.google.android.material.R.attr.colorPrimary
                : resource == R.color.page
                        ? com.google.android.material.R.attr.colorSurface
                        : resource == R.color.surface
                                ? com.google.android.material.R.attr.colorSurfaceContainer
                                : resource == R.color.ink
                                        ? com.google.android.material.R.attr.colorOnSurface
                                        : resource == R.color.muted
                                                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                                                : resource == R.color.soft
                                                        ? com.google.android.material.R.attr.colorSecondaryContainer
                                                        : resource == R.color.stroke
                                                                ? com.google.android.material.R.attr.colorOutlineVariant
                                                                : 0;
        int resolved = attr == 0
                ? c.getColor(resource)
                : com.google.android.material.color.MaterialColors.getColor(c, attr, c.getColor(resource));
        return resolved;
    }

    static void sliderRow(LinearLayout body, String title,
            com.google.android.material.slider.Slider slider, TextView value, float resetValue) {
        sliderRow(body, title, slider, value, () -> resetValue);
    }

    static void sliderRow(LinearLayout body, String title,
            com.google.android.material.slider.Slider slider, TextView value,
            java.util.function.DoubleSupplier defaultValue) {
        FormRow row = new FormRow(body.getContext(), title);
        styleSlider(slider);
        row.control(slider, FORM_ROW_HEIGHT, true, value);
        row.reset(String.valueOf(slider.getTag()),
                () -> Math.abs(slider.getValue() - defaultValue.getAsDouble()) > .001,
                () -> slider.setValue((float) defaultValue.getAsDouble()));
        slider.addOnChangeListener((view, current, user) -> row.refreshReset());
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    static int pageSurface(Context context) {
        return MainStyle.load(context).hasBackground()
                ? android.graphics.Color.TRANSPARENT
                : color(context, R.color.page);
    }

    static int selectionColor(Context context) {
        return MainStyle.load(context).hasBackground()
                ? androidx.core.graphics.ColorUtils.setAlphaComponent(color(context, R.color.accent), 48)
                : color(context, R.color.soft);
    }

    static GradientDrawable panel(Context context, int radius) {
        GradientDrawable background = shape(context, R.color.surface, radius);
        if (MainStyle.load(context).hasBackground())
            background.setAlpha(220);
        return background;
    }

    static void styleSlider(com.google.android.material.slider.Slider slider) {
        Context c = slider.getContext();
        slider.setTrackHeight(dp(c, 16));
        slider.setThumbWidth(dp(c, 4));
        slider.setThumbHeight(dp(c, 32));
        slider.setThumbTrackGapSize(dp(c, 4));
        slider.setTrackInsideCornerSize(dp(c, 4));
        slider.setTickVisible(false);
        slider.setTrackStopIndicatorSize(0);
        slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
    }

    static Button textButton(Context c) {
        return new com.google.android.material.button.MaterialButton(c, null,
                com.google.android.material.R.attr.borderlessButtonStyle);
    }

    static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    static TextView text(Context c, String value, int size, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color(c, color));
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(c, color));
        bg.setCornerRadius(dp(c, radius));
        return bg;
    }

    static void touchBackground(View v, int color, boolean selected) {
        Context c = v.getContext();
        v.setStateListAnimator(null);
        v.setElevation(0);
        GradientDrawable background = shape(c, color, 8);
        if (MainStyle.load(c).hasBackground())
            background.setColor(selected ? selectionColor(c) : pageSurface(c));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(selectionColor(c)), background, null));
    }

    static void nativeTouch(View v) {
        TypedValue value = new TypedValue();
        v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        v.setBackgroundResource(value.resourceId);
        v.setStateListAnimator(null);
        v.setElevation(0);
    }

    static ImageButton icon(Context c, int drawable, String description, View.OnClickListener click) {
        ImageButton v = new ImageButton(c);
        v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(Ui.color(c, R.color.ink)));
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        nativeTouch(v);
        v.setContentDescription(description);
        v.setTooltipText(description);
        v.setOnClickListener(click);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        return v;
    }

    static LinearLayout screen(Activity a) {
        LinearLayout root = column(a);
        WallpaperStage.prepare(a, root);
        insets(root);
        a.setContentView(root);
        if (a instanceof ThemedActivity)
            ((ThemedActivity) a).applySystemBars();
        else
            systemBars(a);
        return root;
    }

    static void systemBars(Activity a) {
        boolean light = (a.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        systemBars(a, pageSurface(a), light);
    }

    static void systemBars(Activity a, int color, boolean light) {
        androidx.core.view.WindowInsetsControllerCompat bars = new androidx.core.view.WindowInsetsControllerCompat(
                a.getWindow(), a.getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(light);
        bars.setAppearanceLightNavigationBars(light);
        a.getWindow().setNavigationBarColor(color);
        a.getWindow().setStatusBarColor(color);
        if (android.os.Build.VERSION.SDK_INT >= 29)
            a.getWindow().setStatusBarContrastEnforced(false);
    }

    static LinearLayout toolbar(Activity a, LinearLayout root, String title, boolean back) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(a, 56));
        bar.setPadding(dp(a, 4), 0, dp(a, 4), 0);
        if (back) {
            ImageButton button = icon(a, R.drawable.ic_back, "返回", v -> {
                if (a instanceof ThemedActivity)
                    ((ThemedActivity) a).getOnBackPressedDispatcher().onBackPressed();
                else
                    a.finish();
            });
            button.setTag("screen-back");
            bar.addView(button);
        }
        TextView label = text(a, title, 20, R.color.ink);
        title(label);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(a, back ? 0 : 12), 0, 0, 0);
        bar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(a, 56)));
        return bar;
    }

    static void divider(LinearLayout parent) {
        View line = new View(parent.getContext());
        line.setBackgroundColor(Ui.color(parent.getContext(), R.color.stroke));
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(parent.getContext(), 1)));
    }

    static void title(TextView v) {
        v.setTypeface(null, Typeface.BOLD);
    }

    static void space(LinearLayout parent, int dp) {
        parent.addView(new View(parent.getContext()), new LinearLayout.LayoutParams(1, dp(parent.getContext(), dp)));
    }

    static void insets(View root) {
        root.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(), i.getSystemWindowInsetRight(),
                    i.getSystemWindowInsetBottom());
            return i;
        });
    }

    static void launch(Context activity, String pkg) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        try {
            if (intent != null) {
                activity.startActivity(intent);
                activity.sendBroadcast(new Intent(activity, HabitWidget.class).setAction(HabitWidget.REFRESH_IF_DUE));
            } else
                toast(activity, "这个 App 暂时无法打开");
        } catch (RuntimeException e) {
            toast(activity, "这个 App 已移除或被系统限制");
        }
    }

    static void toast(Context c, String value) {
        Toast.makeText(c, value, Toast.LENGTH_SHORT).show();
    }
}
