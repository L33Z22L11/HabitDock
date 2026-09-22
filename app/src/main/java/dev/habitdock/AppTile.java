package dev.habitdock;

import android.content.Context;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Recommendation row; a single click target, no nested buttons. */
final class AppTile extends LinearLayout {
    private final ImageView icon;
    private final TextView label, detail;
    AppTile(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        setMinimumHeight(Ui.dp(c, 72));
        Ui.nativeTouch(this);
        icon = new ImageView(c);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        LinearLayout copy = Ui.column(c);
        copy.setPadding(Ui.dp(c, 8), 0, 0, 0);
        copy.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        label = Ui.text(c, "", 14, R.color.ink);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(label);
        Ui.space(copy, 2);
        detail = Ui.text(c, "", 11, R.color.muted);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(detail);
        addView(copy, new LayoutParams(0, -2, 1));
        setFocusable(true);
    }

    void bind(Repository.App app, String subtitle, IconStyle style) {
        label.setText(app.label);
        detail.setText(subtitle.equals("刚开始了解你的习惯") ? "学习中" : subtitle);
        IconLoader.bind(getContext(), app.pkg, icon, style);
        setContentDescription(app.label + "，" + subtitle + "，点击打开，长按显示操作菜单");
        setTag(app.pkg);
    }
}
