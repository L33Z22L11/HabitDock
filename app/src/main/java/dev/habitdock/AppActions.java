package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
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
    private static final int CORNER_DP = 16;
    static AnchoredGlassPopup show(Activity activity, Repository.App app, View anchor, Runnable onChanged) {
        return show(activity, app, anchor, onChanged, null);
    }

    static AnchoredGlassPopup show(Activity activity, Repository.App app, View anchor, Runnable onChanged,
            Intent nativeDetails) {
        boolean pinned = Repository.selection(activity, "pinned").contains(app.pkg);
        boolean hidden = Repository.selection(activity, "hidden").contains(app.pkg);
        List<Action> actions = new ArrayList<>();
        actions.add(new Action(pinned ? "取消固定" : "固定推荐", R.drawable.ic_pin,
                () -> perform(activity, app, 0, onChanged)));
        actions.add(new Action(hidden ? "恢复推荐" : "不推荐", R.drawable.ic_block,
                () -> perform(activity, app, 1, onChanged)));
        actions.add(new Action("应用信息", R.drawable.ic_info, () -> perform(activity, app, 2, onChanged)));
        actions.add(new Action("分享 APK", R.drawable.ic_share, () -> perform(activity, app, 3, onChanged)));
        if (nativeDetails != null)
            actions.add(new Action("原生信息", R.drawable.ic_settings, () -> {
                try {
                    activity.startActivity(nativeDetails);
                } catch (ActivityNotFoundException | SecurityException error) {
                    Ui.toast(activity, "系统未开放原生应用信息，已打开应用信息");
                    perform(activity, app, 2, onChanged);
                }
            }));
        Intent libChecker = LibCheckerDetails.find(activity, app.pkg);
        if (libChecker != null)
            actions.add(new Action("LibChecker", R.drawable.ic_library_chip, () -> {
                try {
                    activity.startActivity(libChecker);
                } catch (ActivityNotFoundException | SecurityException error) {
                    Ui.toast(activity, "无法打开 LibChecker");
                }
            }));
        Rect frame = AnchoredGlassPopup.visibleFrame(activity, anchor);
        int margin = Ui.dp(activity, 12);
        float fontScale = activity.getResources().getConfiguration().fontScale;
        int cellWidth = Ui.dp(activity, Math.max(64, 52 * fontScale + 12));
        android.text.TextPaint labelPaint = Ui.text(activity, "", 12, R.color.ink).getPaint();
        for (Action action : actions)
            cellWidth = Math.max(cellWidth, (int) Math.ceil(labelPaint.measureText(action.label)) + Ui.dp(activity, 8));
        int available = Math.max(cellWidth, frame.width() - 2 * margin);
        int columns = Math.max(1, Math.min(actions.size(), available / cellWidth));
        int width = Math.min(frame.width() - 2 * margin, columns * cellWidth);
        LinearLayout content = Ui.column(activity);
        content.setTag("app-actions-menu");
        if (android.os.Build.VERSION.SDK_INT >= 28)
            content.setAccessibilityPaneTitle(app.label + "的操作");
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(activity, CORNER_DP));
            }
        });
        scroll.setClipToOutline(true);
        scroll.addView(content);
        AnchoredGlassPopup popup = new AnchoredGlassPopup(activity, CORNER_DP, 4);
        popup.surface.addView(scroll, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout heading = header(activity, app, width);
        heading.findViewWithTag("app-actions-copy").setOnClickListener(v -> {
            copyPackage(activity, app);
            popup.dismiss();
        });
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        for (int start = 0; start < actions.size(); start += columns) {
            LinearLayout row = new LinearLayout(activity);
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            for (int col = 0; col < columns; col++) {
                int index = start + col;
                if (index >= actions.size()) {
                    row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
                    continue;
                }
                Action action = actions.get(index);
                LinearLayout item = Ui.column(activity);
                // The popup has no outer inset; spacing belongs inside each full-size click
                // target.
                item.setGravity(Gravity.CENTER);
                item.setPadding(Ui.dp(activity, 2), Ui.dp(activity, 8), Ui.dp(activity, 2), Ui.dp(activity, 8));
                item.setMinimumHeight(Ui.dp(activity, 64));
                item.setFocusable(true);
                item.setContentDescription(action.label);
                item.setTag("app-action:" + index);
                Ui.nativeTouch(item);
                ImageView icon = new ImageView(activity);
                icon.setImageResource(action.icon);
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(activity, R.color.ink)));
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22)));
                Ui.space(item, 6);
                TextView text = Ui.text(activity, action.label, 12, R.color.ink);
                text.setGravity(Gravity.CENTER);
                text.setSingleLine(true);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(text, new LinearLayout.LayoutParams(-1, -2));
                item.setOnClickListener(v -> {
                    popup.dismiss();
                    action.run.run();
                });
                row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
            }
        }
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        Rect bounds = BubblePlacement.place(frame, AnchoredGlassPopup.anchorBounds(anchor),
                width, content.getMeasuredHeight(), margin, Ui.dp(activity, 4));
        popup.show(bounds);
        return popup;
    }

    private static final class Action {
        final String label;
        final int icon;
        final Runnable run;

        Action(String label, int icon, Runnable run) {
            this.label = label;
            this.icon = icon;
            this.run = run;
        }
    }

    static LinearLayout header(Context c, Repository.App app, int width) {
        LinearLayout row = new LinearLayout(c);
        row.setTag("app-actions-header");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 32));
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 6));
        ImageView icon = new ImageView(c);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
        IconLoader.bind(c, app.pkg, icon);
        TextView name = Ui.text(c, app.label, 12, R.color.ink);
        name.setTag("app-actions-name");
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMaxWidth(Math.max(0, (width - row.getPaddingLeft() - row.getPaddingRight() - Ui.dp(c, 66)) * 2 / 5));
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
        copy.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.muted)));
        copy.setPadding(Ui.dp(c, 7), Ui.dp(c, 7), Ui.dp(c, 7), Ui.dp(c, 7));
        row.addView(copy, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
        return row;
    }

    private static void perform(Activity activity, Repository.App app, int which, Runnable onChanged) {
        if (which == 0) {
            Set<String> pins = Repository.selection(activity, "pinned");
            if (pins.contains(app.pkg))
                pins.remove(app.pkg);
            else {
                if (Repository.selection(activity, "hidden").contains(app.pkg)) {
                    Ui.toast(activity, "请先恢复推荐，再置顶");
                    return;
                }
                pins.add(app.pkg);
            }
            Repository.prefs(activity).edit().putStringSet("pinned", pins).apply();
            Repository.selectionChanged(activity.getApplicationContext());
            onChanged.run();
        } else if (which == 1) {
            boolean hidden = Repository.selection(activity, "hidden").contains(app.pkg);
            Repository.setExcluded(activity.getApplicationContext(), app.pkg, !hidden);
            onChanged.run();
            Ui.toast(activity, hidden ? "已恢复推荐" : "已隐藏，可在「排除应用」中恢复");
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
