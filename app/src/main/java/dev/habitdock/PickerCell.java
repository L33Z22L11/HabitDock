package dev.habitdock;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Three horizontal blocks: icon, name/package, native checkbox. */
final class PickerCell extends LinearLayout {
    final CheckBox check;
    private final ImageView icon;
    private final TextView name, pkg;
    PickerCell(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(c, 16), 0, 0, 0);
        setMinimumHeight(Ui.dp(c, 48));
        icon = new ImageView(c);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, new LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
        LinearLayout copy = Ui.column(c);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(Ui.dp(c, 6), 0, 0, 0);
        copy.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        name = Ui.text(c, "", 14, R.color.ink);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        Ui.space(copy, 2);
        pkg = Ui.text(c, "", 9, R.color.muted);
        pkg.setSingleLine(true);
        pkg.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        copy.addView(pkg);
        addView(copy, new LayoutParams(0, -2, 1));
        check = new CheckBox(c);
        check.setMinWidth(0);
        check.setMinimumWidth(0);
        int buttonWidth = check.getButtonDrawable().getIntrinsicWidth();
        check.setPadding(0, 0, 0, 0);
        check.setGravity(Gravity.CENTER);
        check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{c.getColor(R.color.accent), c.getColor(R.color.muted)}));
        check.setStateListAnimator(null);
        check.setElevation(0);
        check.setBackground(null);
        // CompoundButton anchors its drawable to the edge regardless of horizontal
        // gravity.
        // Center the native control in a full-size touch target instead of adding
        // ineffective padding.
        FrameLayout checkTarget = new FrameLayout(c);
        checkTarget.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        checkTarget.addView(check, new FrameLayout.LayoutParams(buttonWidth, Ui.dp(c, 48), Gravity.CENTER));
        checkTarget.setOnClickListener(v -> check.performClick());
        addView(checkTarget, new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setOnClickListener(v -> check.performClick());
    }

    void bind(Repository.App app, boolean selected, CompoundButton.OnCheckedChangeListener listener) {
        setTag(app.pkg);
        check.setTag("check:" + app.pkg);
        name.setText(app.label);
        pkg.setText(app.pkg);
        check.setOnCheckedChangeListener(null);
        check.setChecked(selected);
        check.setContentDescription(app.label + "，" + app.pkg);
        // Inset only the painted card: keep the compact 48dp touch target intact.
        android.graphics.drawable.Drawable card = Ui.shape(getContext(), selected ? R.color.soft : R.color.page, 8);
        android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(card, 0,
                Ui.dp(getContext(), 2), 0, Ui.dp(getContext(), 2));
        setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(getContext().getColor(R.color.soft)), inset, null));
        setPadding(Ui.dp(getContext(), 16), 0, 0, 0);
        IconLoader.bind(getContext(), app.pkg, icon);
        check.setOnCheckedChangeListener(listener);
    }
}
