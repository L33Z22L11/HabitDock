package dev.habitdock;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

// Constructed programmatically with its host callbacks; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class SettingsPage extends ScrollView {
    private final Activity activity;
    private final Runnable addWidget, openExclusions;
    private final LinearLayout items;
    private static final AtomicBoolean UPDATE_IN_FLIGHT = new AtomicBoolean();
    private Repository.Snapshot lastData;
    private AppUpdates.Release latestRelease;
    private boolean checkingUpdate, loadingStats;
    private TextView learningDescription;
    private LinearLayout aboutRow;
    private String updateStatus;
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
        if (data != null)
            lastData = data;
        items.removeAllViews();
        row(R.drawable.ic_block, "不推荐的应用", "已排除 " + Repository.selection(activity, "hidden").size() + " 个",
                openExclusions).setTag("open-exclusions");
        row(R.drawable.ic_pin, "固定应用", "已固定 " + Repository.selection(activity, "pinned").size() + " 个",
                () -> activity.startActivity(new Intent(activity, AppPickerActivity.class).putExtra("mode", "pinned")));
        row(R.drawable.ic_recommend, "推荐上限", RecommendationLimit.load(activity) + " 个 · 固定应用另计",
                this::chooseRecommendationLimit).setTag("recommendation-limit");
        row(R.drawable.ic_add_widget, "添加桌面小组件", "布局可按每个组件单独调整", addWidget);
        row(R.drawable.ic_contrast, "图标样式", "圆角与背景填色 · 所有应用图标",
                () -> activity.startActivity(new Intent(activity, IconStyleActivity.class)));
        row(R.drawable.ic_recommend, "组件布局", "行列数、图标大小与更多入口",
                () -> activity.startActivity(new Intent(activity, WidgetSettingsActivity.class)));
        row(R.drawable.ic_refresh, "刷新间隔", intervalLabel(RefreshPolicy.minutes(activity)), this::chooseRefreshInterval);
        Ui.divider(items);
        row(R.drawable.ic_clock, "使用情况访问", UsageStore.permitted(activity) ? "已开启" : "未开启", () -> permission(activity));
        row(R.drawable.ic_delete, "重新学习", "清除使用记录，保留固定和排除设置", this::reset);
        Ui.divider(items);
        row(R.drawable.ic_code, "开源仓库", "GitHub · L33Z22L11 / HabitDock",
                () -> openWeb(AppUpdates.REPOSITORY)).setTag("open-repository");
        LinearLayout about = row(R.drawable.ic_info, aboutTitle(), learningText(), this::showAbout);
        aboutRow = about;
        LinearLayout copy = (LinearLayout) about.getChildAt(1);
        TextView title = (TextView) copy.getChildAt(0);
        android.text.SpannableString versionedTitle = new android.text.SpannableString(aboutTitle());
        int versionStart = versionedTitle.toString().indexOf(" v");
        versionedTitle.setSpan(new android.text.style.RelativeSizeSpan(.75f), versionStart,
                versionedTitle.length(), 0);
        versionedTitle.setSpan(new android.text.style.ForegroundColorSpan(activity.getColor(R.color.muted)),
                versionStart, versionedTitle.length(), 0);
        title.setText(versionedTitle);
        title.setTag("about-title");
        learningDescription = (TextView) copy.getChildAt(copy.getChildCount() - 1);
        about.setTag("open-about");
        about.removeViewAt(about.getChildCount() - 1);
        Button update = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        update.setText(checkingUpdate ? "检查中…" : latestRelease != null ? "查看更新" : "检查更新");
        update.setTextSize(14);
        update.setTextColor(activity.getColor(R.color.accent));
        update.setTag("check-updates");
        update.setContentDescription(checkingUpdate
                ? "正在检查更新"
                : latestRelease != null
                        ? "查看新版本 " + latestRelease.tag
                        : updateStatus == null ? "检查更新" : "检查更新，" + updateStatus);
        update.setMinWidth(0);
        update.setMinimumWidth(0);
        update.setEnabled(!checkingUpdate);
        update.setAlpha(checkingUpdate ? 0.6f : 1f);
        update.setOnClickListener(v -> {
            if (latestRelease == null)
                checkForUpdates();
            else
                openWeb(latestRelease.url());
        });
        about.addView(update, new LinearLayout.LayoutParams(-2, Ui.dp(activity, 48)));
        loadLearningStats();
    }

    private String aboutTitle() {
        return "关于知时 v" + AppUpdates.installedVersion(activity);
    }

    private String learningText() {
        if (!UsageStore.permitted(activity))
            return "未开启使用情况访问";
        return lastData == null
                ? "正在读取学习记录…"
                : "已学习 " + lastData.days + " 天 · " + lastData.samples + " 条记录";
    }

    private void loadLearningStats() {
        if (loadingStats || !UsageStore.permitted(activity))
            return;
        loadingStats = true;
        Context app = activity.getApplicationContext();
        Repository.WORK.execute(() -> {
            Repository.Snapshot result = null;
            try {
                long stamp = RefreshPolicy.last(app);
                if (stamp == 0)
                    stamp = System.currentTimeMillis();
                // Read local records only; visiting settings never advances the clock.
                result = Repository.loadAt(app, stamp, stamp, false);
            } catch (RuntimeException ignored) {
            }
            Repository.Snapshot data = result;
            activity.runOnUiThread(() -> {
                loadingStats = false;
                if (activity.isDestroyed())
                    return;
                if (data != null)
                    lastData = data;
                String detail = lastData == null ? "暂时无法读取学习记录" : learningText();
                learningDescription.setText(detail);
                aboutRow.setContentDescription(aboutTitle() + "，" + detail);
            });
        });
    }

    private void showAbout() {
        LinearLayout content = Ui.column(activity);
        content.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 16), Ui.dp(activity, 24), Ui.dp(activity, 8));
        ImageView icon = new ImageView(activity);
        icon.setContentDescription("知时应用图标");
        icon.setImageBitmap(WidgetIcons.style(activity, WidgetIcons.source(activity.getDrawable(R.drawable.ic_app)),
                IconStyle.load(activity)));
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(Ui.dp(activity, 64), Ui.dp(activity, 64));
        iconSize.gravity = Gravity.CENTER_HORIZONTAL;
        iconSize.bottomMargin = Ui.dp(activity, 16);
        content.addView(icon, iconSize);
        content.addView(Ui.text(activity, "版本 " + AppUpdates.installedVersion(activity) + "\n" + learningText()
                + "\n\n根据时段、星期和近期使用频率推荐应用。最多保存 60 天本地记录，推荐完全在本机完成。"
                + "\n\n仅手动检查更新时连接 GitHub，不上传应用列表或使用记录。"
                + "\n\n组件按设置间隔更新，系统省电策略可能延迟；打开推荐页或点击更多时，到期才补刷。", 16, R.color.ink));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        new AlertDialog.Builder(activity).setTitle("知时 · HabitDock").setView(scroll)
                .setPositiveButton("关闭", null).show();
    }

    private void chooseRecommendationLimit() {
        LinearLayout content = Ui.column(activity);
        int padding = Ui.dp(activity, 24);
        content.setPadding(padding, Ui.dp(activity, 8), padding, 0);
        content.addView(Ui.text(activity, "智能推荐最多显示多少个应用？固定应用不占名额，组件仍按布局容量显示。", 14, R.color.muted));
        EditText count = new EditText(activity);
        count.setTag("recommendation-limit-input");
        count.setContentDescription("推荐上限，1 到 100 个");
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        count.setSingleLine(true);
        count.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        count.setHint("1–100，默认 20");
        count.setText(String.valueOf(RecommendationLimit.load(activity)));
        count.selectAll();
        content.addView(count, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("推荐上限").setView(content)
                .setNegativeButton("取消", null).setPositiveButton("确定", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int value;
            try {
                value = Integer.parseInt(count.getText().toString());
            } catch (NumberFormatException error) {
                value = 0;
            }
            if (value < 1 || value > RecommendationLimit.MAX) {
                count.setError("请输入 1–100");
                return;
            }
            if (value != RecommendationLimit.load(activity))
                RecommendationLimit.save(activity, value);
            dialog.dismiss();
            refresh(null);
        }));
        dialog.show();
    }

    private void checkForUpdates() {
        if (!UPDATE_IN_FLIGHT.compareAndSet(false, true)) {
            Ui.toast(activity, "正在检查更新，请稍候");
            return;
        }
        checkingUpdate = true;
        refresh(null);
        WeakReference<SettingsPage> page = new WeakReference<>(this);
        // Network waits must never delay local recommendations, privacy changes or
        // repainting.
        new Thread(() -> {
            AppUpdates.Release release = null;
            String error = null;
            try {
                release = AppUpdates.fetch();
            } catch (java.io.IOException | RuntimeException failure) {
                error = failure instanceof AppUpdates.Failure ? failure.getMessage() : "无法连接 GitHub，请检查网络后重试";
            } finally {
                UPDATE_IN_FLIGHT.set(false);
            }
            AppUpdates.Release result = release;
            String message = error;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                SettingsPage target = page.get();
                if (target != null && !target.activity.isDestroyed())
                    target.completeUpdate(result, message);
            });
        }, "habitdock-update-check").start();
    }

    void completeUpdate(AppUpdates.Release release, String error) {
        checkingUpdate = false;
        String installed = AppUpdates.installedVersion(activity);
        boolean newer = false;
        if (error == null) {
            try {
                newer = release.newerThan(installed);
            } catch (IllegalArgumentException exception) {
                error = "无法比较当前版本，请打开版本页面查看";
            }
        }
        if (error == null) {
            latestRelease = newer ? release : null;
            updateStatus = newer ? null : installed + " · 已是最新正式版";
        } else
            updateStatus = installed + " · 检查失败";
        refresh(null);
        if (!isShown() || !activity.hasWindowFocus() || activity.isFinishing())
            return;
        if (error != null) {
            new AlertDialog.Builder(activity).setTitle("检查更新失败").setMessage(error)
                    .setNegativeButton("关闭", null)
                    .setPositiveButton("版本页面", (dialog, which) -> openWeb(AppUpdates.RELEASES)).show();
        } else if (newer) {
            new AlertDialog.Builder(activity).setTitle("发现新版本 " + release.tag)
                    .setMessage("当前版本 " + installed + "\n\n前往 GitHub Release 页面查看更新说明与下载 APK。")
                    .setNegativeButton("稍后", null)
                    .setPositiveButton("前往下载", (dialog, which) -> openWeb(release.url())).show();
        } else
            Ui.toast(activity, "当前已是最新正式版（" + installed + "）");
    }

    private void openWeb(String url) {
        try {
            activity.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException error) {
            Ui.toast(activity, "未找到可打开链接的浏览器");
        }
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
                                lastData = null;
                                refresh(null);
                                Ui.toast(activity, "已清除，从现在重新学习");
                            }
                        });
                    });
                }).show();
    }
}
