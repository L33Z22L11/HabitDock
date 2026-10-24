package dev.habitdock;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;

/** Recommendation row; one click target with an optional pinned badge. */
final class AppTile extends LinearLayout {
    private final ImageView icon, pin, hiddenBadge;
    private final FrameLayout iconFrame;
    private final TextView label, detail;
    AppTile(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4));
        iconFrame = new FrameLayout(context);
        iconFrame.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        icon = new ImageView(context);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        pin = new ImageView(context);
        pin.setImageResource(R.drawable.ic_pin);
        pin.setPadding(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3));
        pin.setTag("pin-badge");
        iconFrame.addView(pin,
                new FrameLayout.LayoutParams(Ui.dp(context, 19), Ui.dp(context, 19), Gravity.BOTTOM | Gravity.END));
        hiddenBadge = new ImageView(context);
        hiddenBadge.setTag("hidden-badge");
        hiddenBadge.setImageResource(R.drawable.ic_block);
        hiddenBadge.setPadding(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3));
        iconFrame.addView(hiddenBadge,
                new FrameLayout.LayoutParams(Ui.dp(context, 19), Ui.dp(context, 19), Gravity.TOP | Gravity.END));
        addView(iconFrame, new LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));
        LinearLayout copy = Ui.column(context);
        copy.setPadding(Ui.dp(context, 10), 0, 0, 0);
        copy.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        label = Ui.text(context, "", 16, R.color.ink);
        label.setTag("app-label");
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(label);
        detail = Ui.text(context, "", 12, R.color.muted);
        detail.setTag("app-reason");
        detail.setPadding(0, Ui.dp(context, 4), 0, 0);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(detail);
        addView(copy, new LayoutParams(0, -2, 1));
        setFocusable(true);
    }

    void bind(Repository.App app, String subtitle, IconStyle style) {
        bind(app, new Predictor.Prediction(app.pkg, 0, subtitle), style, MainStyle.load(getContext()),
                Repository.selection(getContext(), "pinned").contains(app.pkg));
    }

    void bind(Repository.App app, Predictor.Prediction prediction, IconStyle style, MainStyle main, boolean pinned) {
        Context context = getContext();
        iconFrame.setLayoutParams(new LayoutParams(Ui.dp(context, main.iconDp), Ui.dp(context, main.iconDp)));
        label.setText(app.label);
        label.setTextSize(main.labelSp);
        label.setTextColor(main.ink(context));
        String reason = main.reason(prediction);
        detail.setText(reason);
        detail.setMaxLines(main.reasons == 2 ? 2 : 1);
        // Keep the title and preview geometry stable when changing reason detail.
        android.view.ViewGroup.LayoutParams reasonSpace = detail.getLayoutParams();
        reasonSpace.height = detail.getLineHeight() * 2 + detail.getPaddingTop() + detail.getPaddingBottom();
        detail.setLayoutParams(reasonSpace);
        detail.setTextColor(ColorUtils.setAlphaComponent(main.ink(context), 190));
        detail.setVisibility(reason.isEmpty() ? GONE : VISIBLE);
        IconLoader.bind(context, app.pkg, icon, style);
        boolean hidden = Repository.selection(context, "hidden").contains(app.pkg);
        icon.setAlpha(hidden ? .5f : 1f);
        label.setAlpha(hidden ? .5f : 1f);
        detail.setAlpha(hidden ? .5f : 1f);
        hiddenBadge.setVisibility(hidden ? VISIBLE : GONE);
        pin.setVisibility(pinned ? VISIBLE : GONE);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(main.accent(context));
        badge.setCornerRadius(Ui.dp(context, 12));
        badge.setStroke(Ui.dp(context, 1.5f), ColorUtils.setAlphaComponent(main.surface(context), 255));
        pin.setBackground(badge);
        pin.setImageTintList(ColorStateList
                .valueOf(ColorUtils.calculateLuminance(main.accent(context)) > .45 ? Color.BLACK : Color.WHITE));
        hiddenBadge.setBackground(badge.getConstantState().newDrawable().mutate());
        hiddenBadge.setImageTintList(pin.getImageTintList());
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(Ui.dp(context, 12));
        setBackground(new RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(main.ink(context), 35)), null, mask));
        label.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        detail.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        setContentDescription(
                app.label + (hidden ? "，已隐藏" : "") + (pinned ? "，已置顶" : "") + (reason.isEmpty() ? "" : "，" + reason)
                        + "，点击打开，长按显示操作菜单");
        setTag(app.pkg);
    }
}
