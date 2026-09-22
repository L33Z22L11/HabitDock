package dev.habitdock;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

// Constructed programmatically with its host callbacks; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class SettingsPage extends ScrollView {
    private final Activity activity;
    private final Runnable addWidget, openExclusions;
    private final LinearLayout items;
    SettingsPage(Activity activity, Runnable addWidget, Runnable openExclusions) {
        super(activity);
        this.activity = activity;
        this.addWidget = addWidget;
        this.openExclusions = openExclusions;
        setTag("settings-page");
        items = Ui.column(activity);
        addView(items);
    }

    void refresh(Repository.Snapshot data) {
        items.removeAllViews();
        row(R.drawable.ic_block, "不推荐的应用", "已排除 " + Repository.selection(activity, "hidden").size() + " 个",
                openExclusions).setTag("open-exclusions");
        row(R.drawable.ic_pin, "固定应用", "已固定 " + Repository.selection(activity, "pinned").size() + " 个",
                () -> activity.startActivity(new Intent(activity, AppPickerActivity.class).putExtra("mode", "pinned")));
        row(R.drawable.ic_add_widget, "添加桌面小组件", "布局可按每个组件单独调整", addWidget);
        row(R.drawable.ic_contrast, "图标样式", "圆角与背景填色 · 所有应用图标",
                () -> activity.startActivity(new Intent(activity, IconStyleActivity.class)));
        row(R.drawable.ic_recommend, "组件布局", "行列数、图标大小与更多入口",
                () -> activity.startActivity(new Intent(activity, WidgetSettingsActivity.class)));
        row(R.drawable.ic_refresh, "刷新间隔", intervalLabel(RefreshPolicy.minutes(activity)), this::chooseRefreshInterval);
        Ui.divider(items);
        row(R.drawable.ic_clock, "使用情况访问", UsageStore.permitted(activity) ? "已开启" : "未开启", () -> permission(activity));
        row(R.drawable.ic_delete, "重新学习", "清除使用记录，保留固定和排除设置", this::reset);
        row(R.drawable.ic_info, "关于知时",
                data != null && data.permitted
                        ? "本机学习 · " + data.days + " 天 · " + data.samples + " 次记录"
                        : "本机学习 · 无网络权限",
                () -> new AlertDialog.Builder(activity).setTitle("知时")
                        .setMessage(
                                "按时段、星期和近期使用频率推荐应用。最多保存 60 天本地记录，不联网。\n\n组件按设置间隔批量更新，系统省电策略可能延迟。打开推荐页或点击更多时，到期才补刷；手动刷新立即生效。移除全部组件后停止定期任务。\n\n组件行列与图标比例可调整，图标样式全局通用，界面跟随系统深浅色。")
                        .setPositiveButton("知道了", null).show());
    }

    private String intervalLabel(int minutes) {
        return minutes < 60 ? minutes + " 分钟" : minutes % 60 == 0 ? (minutes / 60) + " 小时" : minutes + " 分钟";
    }

    private void chooseRefreshInterval() {
        int[] values = {15, 30, 60, 120, 240, 480, 720, 1440};
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = intervalLabel(values[i]);
            if (values[i] == RefreshPolicy.minutes(activity))
                selected = i;
        }
        new AlertDialog.Builder(activity).setTitle("自动刷新间隔")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    Repository.prefs(activity).edit().putInt("refresh_minutes", values[which]).apply();
                    Context app = activity.getApplicationContext();
                    RefreshJob.schedule(app);
                    Repository.WORK.execute(() -> {
                        try {
                            HabitWidget.refreshIfDue(app);
                        } catch (RuntimeException ignored) {
                        }
                    });
                    dialog.dismiss();
                    refresh(null);
                }).setNegativeButton("取消", null).show();
    }

    private LinearLayout row(int drawable, String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(activity, 64));
        row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));
        Ui.nativeTouch(row);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(drawable);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.muted)));
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 24), Ui.dp(activity, 24)));
        LinearLayout copy = Ui.column(activity);
        copy.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 8), 0);
        copy.addView(Ui.text(activity, title, 16, R.color.ink));
        Ui.space(copy, 4);
        copy.addView(Ui.text(activity, detail, 12, R.color.muted));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(activity);
        arrow.setImageResource(R.drawable.ic_chevron);
        arrow.setImageTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.muted)));
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18)));
        row.setFocusable(true);
        row.setContentDescription(title + "，" + detail);
        row.setOnClickListener(v -> action.run());
        items.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    static void permission(Activity a) {
        try {
            a.startActivity(
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
        } catch (ActivityNotFoundException e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                Ui.toast(a, "请在系统设置中搜索使用情况访问权限");
            }
        }
    }

    private void reset() {
        new AlertDialog.Builder(activity).setTitle("重新学习你的习惯？").setMessage("删除已有本机记录，从现在重新开始，不再导入更早的系统历史。固定和排除设置保留。")
                .setNegativeButton("取消", null).setPositiveButton("重新学习", (d, w) -> {
                    Context app = activity.getApplicationContext();
                    Repository.WORK.execute(() -> {
                        try (UsageStore store = new UsageStore(app)) {
                            store.reset(System.currentTimeMillis());
                        }
                        RefreshPolicy.invalidate(app);
                        HabitWidget.update(app, false);
                        activity.runOnUiThread(() -> {
                            if (!activity.isDestroyed()) {
                                refresh(null);
                                Ui.toast(activity, "已清除，从现在重新学习");
                            }
                        });
                    });
                }).show();
    }
}
