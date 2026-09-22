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
        v.setTextColor(c.getColor(color));
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(c.getColor(color));
        bg.setCornerRadius(dp(c, radius));
        return bg;
    }

    static void touchBackground(View v, int color, boolean selected) {
        Context c = v.getContext();
        v.setStateListAnimator(null);
        v.setElevation(0);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(c.getColor(R.color.soft)), shape(c, color, 8), null));
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
        v.setImageTintList(ColorStateList.valueOf(c.getColor(R.color.ink)));
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
        root.setBackgroundColor(a.getColor(R.color.page));
        insets(root);
        a.setContentView(root);
        return root;
    }

    static LinearLayout toolbar(Activity a, LinearLayout root, String title, boolean back) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(a, 56));
        bar.setPadding(dp(a, 4), 0, dp(a, 4), 0);
        if (back)
            bar.addView(icon(a, R.drawable.ic_back, "返回", v -> a.finish()));
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
        line.setBackgroundColor(parent.getContext().getColor(R.color.stroke));
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
