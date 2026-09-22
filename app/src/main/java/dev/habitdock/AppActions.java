package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Compact anchored actions, shared by recommendation long-press and widget
 * entry.
 */
final class AppActions {
    static PopupWindow show(Activity activity, Repository.App app, View anchor, Runnable onChanged) {
        boolean pinned = Repository.selection(activity, "pinned").contains(app.pkg);
        String[] labels = {pinned ? "取消固定" : "固定推荐", "不推荐", "应用信息", "分享 APK"};
        int[] icons = {R.drawable.ic_pin, R.drawable.ic_block, R.drawable.ic_info, R.drawable.ic_share};
        Rect frame = new Rect();
        anchor.getWindowVisibleDisplayFrame(frame);
        View decor = activity.getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= 30 && decor.getRootWindowInsets() != null) {
            android.graphics.Insets insets = decor.getRootWindowInsets().getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            int[] origin = new int[2];
            decor.getLocationOnScreen(origin);
            Rect safe = new Rect(origin[0] + insets.left, origin[1] + insets.top,
                    origin[0] + decor.getWidth() - insets.right, origin[1] + decor.getHeight() - insets.bottom);
            if (!frame.intersect(safe) && !safe.isEmpty())
                frame.set(safe);
        }
        int padding = Ui.dp(activity, 8), margin = Ui.dp(activity, 12);
        float fontScale = activity.getResources().getConfiguration().fontScale;
        int cellWidth = Ui.dp(activity, Math.max(64, 52 * fontScale + 12));
        int available = Math.max(cellWidth, frame.width() - 2 * margin - 2 * padding);
        int columns = Math.max(1, Math.min(labels.length, available / cellWidth));
        int width = Math.min(frame.width() - 2 * margin, columns * cellWidth + 2 * padding);
        LinearLayout content = Ui.column(activity);
        content.setTag("app-actions-menu");
        content.setPadding(padding, padding, padding, padding);
        if (android.os.Build.VERSION.SDK_INT >= 28)
            content.setAccessibilityPaneTitle(app.label + "的操作");
        GradientDrawable background = Ui.shape(activity, R.color.surface, 16);
        background.setStroke(Ui.dp(activity, 1), activity.getColor(R.color.stroke));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(content);
        PopupWindow popup = new PopupWindow(scroll, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(background);
        popup.setElevation(Ui.dp(activity, 4));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        LinearLayout heading = header(activity, app, width - 2 * padding);
        heading.findViewWithTag("app-actions-copy").setOnClickListener(v -> {
            copyPackage(activity, app);
            popup.dismiss();
        });
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        for (int start = 0; start < labels.length; start += columns) {
            LinearLayout row = new LinearLayout(activity);
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            for (int col = 0; col < columns; col++) {
                int index = start + col;
                if (index >= labels.length) {
                    row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
                    continue;
                }
                LinearLayout item = Ui.column(activity);
                item.setGravity(Gravity.CENTER);
                item.setPadding(Ui.dp(activity, 2), Ui.dp(activity, 8), Ui.dp(activity, 2), Ui.dp(activity, 8));
                item.setMinimumHeight(Ui.dp(activity, 64));
                item.setFocusable(true);
                item.setContentDescription(labels[index]);
                item.setTag("app-action:" + index);
                Ui.nativeTouch(item);
                ImageView icon = new ImageView(activity);
                icon.setImageResource(icons[index]);
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.ink)));
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22)));
                Ui.space(item, 6);
                TextView text = Ui.text(activity, labels[index], 12, R.color.ink);
                text.setGravity(Gravity.CENTER);
                text.setMaxLines(2);
                text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(text, new LinearLayout.LayoutParams(-1, -2));
                item.setOnClickListener(v -> {
                    popup.dismiss();
                    perform(activity, app, index, onChanged);
                });
                row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
            }
        }
        int[] position = new int[2];
        anchor.getLocationOnScreen(position);
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        Rect bounds = BubblePlacement.place(frame,
                new Rect(position[0], position[1], position[0] + anchor.getWidth(), position[1] + anchor.getHeight()),
                width, content.getMeasuredHeight(), margin, Ui.dp(activity, 4));
        popup.setWidth(bounds.width());
        popup.setHeight(bounds.height());
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            popup.setIsLaidOutInScreen(true);
            popup.setIsClippedToScreen(true);
        } else {
            int[] screen = new int[2], window = new int[2];
            decor.getLocationOnScreen(screen);
            decor.getLocationInWindow(window);
            bounds.offset(window[0] - screen[0], window[1] - screen[1]);
        }
        showAtScreenPosition(popup, decor, bounds.left, bounds.top);
        return popup;
    }

    static LinearLayout header(Context c, Repository.App app, int width) {
        LinearLayout row = new LinearLayout(c);
        row.setTag("app-actions-header");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 32));
        row.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 6));
        ImageView icon = new ImageView(c);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        IconLoader.bind(c, app.pkg, icon);
        TextView name = Ui.text(c, app.label, 12, R.color.ink);
        name.setTag("app-actions-name");
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMaxWidth(Math.max(0, (width - Ui.dp(c, 74)) * 2 / 5));
        LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(-2, -2);
        label.setMarginStart(Ui.dp(c, 8));
        row.addView(name, label);
        TextView pkg = Ui.text(c, app.pkg, 10, R.color.muted);
        pkg.setTag("app-actions-package");
        pkg.setSingleLine(true);
        pkg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detail = new LinearLayout.LayoutParams(0, -2, 1);
        detail.setMarginStart(Ui.dp(c, 8));
        row.addView(pkg, detail);
        ImageButton copy = Ui.icon(c, R.drawable.ic_copy, "复制包名", v -> copyPackage(c, app));
        copy.setTag("app-actions-copy");
        copy.setImageTintList(android.content.res.ColorStateList.valueOf(c.getColor(R.color.muted)));
        copy.setPadding(Ui.dp(c, 7), Ui.dp(c, 7), Ui.dp(c, 7), Ui.dp(c, 7));
        row.addView(copy, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
        return row;
    }

    @android.annotation.SuppressLint("RtlHardcoded") // Coordinates are already physical screen coordinates in either
                                                     // layout direction.
    private static void showAtScreenPosition(PopupWindow popup, View decor, int x, int y) {
        popup.showAtLocation(decor, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private static void perform(Activity activity, Repository.App app, int which, Runnable onChanged) {
        if (which == 0) {
            Set<String> pins = Repository.selection(activity, "pinned");
            if (pins.contains(app.pkg))
                pins.remove(app.pkg);
            else {
                if (Repository.selection(activity, "hidden").contains(app.pkg)) {
                    Ui.toast(activity, "请先在「不推荐」中取消排除");
                    return;
                }
                pins.add(app.pkg);
            }
            Repository.prefs(activity).edit().putStringSet("pinned", pins).apply();
            Repository.selectionChanged(activity.getApplicationContext());
            onChanged.run();
        } else if (which == 1) {
            Repository.setExcluded(activity.getApplicationContext(), app.pkg, true);
            onChanged.run();
            Ui.toast(activity, "已不再推荐，可在「不推荐」中恢复");
        } else if (which == 2) {
            try {
                activity.startActivity(
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)));
            } catch (ActivityNotFoundException e) {
                Ui.toast(activity, "系统暂不支持打开应用信息");
            }
        } else if (which == 3)
            ApkShare.start(activity, app);
    }

    private static void copyPackage(Context context, Repository.App app) {
        context.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("应用包名", app.pkg));
        if (android.os.Build.VERSION.SDK_INT < 33)
            Ui.toast(context, "包名已复制");
    }
}
