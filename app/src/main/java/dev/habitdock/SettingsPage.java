package dev.habitdock;
import androidx.appcompat.app.AlertDialog;

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
    private final Runnable chooseHome;
    private final LinearLayout items;
    private static final AtomicBoolean UPDATE_IN_FLIGHT = new AtomicBoolean();
    private Repository.Snapshot lastData;
    private AppUpdates.Release latestRelease;
    private boolean checkingUpdate, loadingStats;
    private TextView learningDescription;
    private LinearLayout aboutRow;
    private LinearLayout defaultHomeRow;
    private TextView defaultHomeTitle;
    private boolean defaultHome;
    private String updateStatus;
    SettingsPage(Activity activity) {
        this(activity, null);
    }

    SettingsPage(Activity activity, Runnable chooseHome) {
        super(activity);
        this.chooseHome = chooseHome;
        this.activity = activity;
        setTag("settings-page");
        setId(R.id.settings_list);
        items = Ui.column(activity);
        addView(items);
    }

    void refresh(Repository.Snapshot data) {
        if (data != null)
            lastData = data;
        items.removeAllViews();
        defaultHomeRow = null;
        defaultHomeTitle = null;
        section("推荐机制");
        row(R.drawable.ic_block, "排除应用", "已排除 " + Repository.selection(activity, "hidden").size() + " 个",
                () -> activity.startActivity(new Intent(activity, AppPickerActivity.class).putExtra("mode", "hidden")))
                .setTag("open-exclusions");
        row(R.drawable.ic_pin, "置顶应用", "已置顶 " + Repository.selection(activity, "pinned").size() + " 个",
                () -> activity.startActivity(new Intent(activity, AppPickerActivity.class).putExtra("mode", "pinned")))
                .setTag("open-pinned");
        row(R.drawable.ic_count, "推荐数量", RecommendationLimit.load(activity) + " 个（置顶另计）",
                this::chooseRecommendationLimit).setTag("recommendation-limit");
        row(R.drawable.ic_refresh, "更新频率", intervalLabel(RefreshPolicy.minutes(activity)),
                this::chooseRefreshInterval).setTag("refresh-interval");
        section("样式设置");
        row(R.drawable.ic_shapes, "图标样式", "",
                () -> activity.startActivity(new Intent(activity, IconStyleActivity.class))).setTag("open-icon-style");
        widgetStyleRow();
        row(R.drawable.ic_layout, "主界面样式", "",
                () -> activity.startActivity(new Intent(activity, MainStyleActivity.class))).setTag("open-main-style");
        section("权限与数据");
        if (chooseHome != null) {
            defaultHomeRow = row(R.drawable.ic_home, homeTitle(), "", chooseHome);
            defaultHomeRow.setTag("default-home");
            defaultHomeTitle = (TextView) ((LinearLayout) defaultHomeRow.getChildAt(1)).getChildAt(0);
        }
        row(R.drawable.ic_clock, "使用情况访问", UsageStore.permitted(activity) ? "已开启" : "未开启", () -> permission(activity));
        row(R.drawable.ic_delete, "重新学习", "清除使用记录", this::reset);
        section("关于");
        row(R.drawable.ic_code, "开源仓库", "L33Z22L11/HabitDock",
                () -> openWeb(AppUpdates.REPOSITORY)).setTag("open-repository");
        LinearLayout about = row(R.drawable.ic_info, aboutTitle(), learningText(), this::showAbout);
        aboutRow = about;
        LinearLayout copy = (LinearLayout) about.getChildAt(1);
        TextView title = (TextView) copy.getChildAt(0);
        android.text.SpannableString versionedTitle = new android.text.SpannableString(aboutTitle());
        int versionStart = versionedTitle.toString().indexOf(" v");
        versionedTitle.setSpan(new android.text.style.RelativeSizeSpan(.75f), versionStart,
                versionedTitle.length(), 0);
        versionedTitle.setSpan(new android.text.style.ForegroundColorSpan(Ui.color(activity, R.color.muted)),
                versionStart, versionedTitle.length(), 0);
        title.setText(versionedTitle);
        title.setTag("about-title");
        learningDescription = (TextView) copy.getChildAt(copy.getChildCount() - 1);
        about.setTag("open-about");
        about.removeViewAt(about.getChildCount() - 1);
        Button update = Ui.textButton(activity);
        update.setText(checkingUpdate ? "检查中…" : latestRelease != null ? "查看更新" : "检查更新");
        update.setTextSize(14);
        update.setTextColor(Ui.color(activity, R.color.accent));
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

    void setDefaultHome(boolean value) {
        defaultHome = value;
        if (defaultHomeTitle != null) {
            defaultHomeTitle.setText(homeTitle());
            defaultHomeRow.setContentDescription(homeTitle());
        }
    }

    private String homeTitle() {
        return defaultHome ? "切换默认桌面" : "设为默认桌面";
    }

    private void section(String label) {
        TextView heading = Ui.text(activity, label, 12, R.color.accent);
        // An embedded floating header already supplies the first section's top gap.
        int top = chooseHome != null && items.getChildCount() == 0 ? 0 : 20;
        heading.setPadding(Ui.dp(activity, 16), Ui.dp(activity, top), Ui.dp(activity, 16), Ui.dp(activity, 4));
        items.addView(heading);
    }

    private void widgetStyleRow() {
        LinearLayout row = row(R.drawable.ic_recommend, "组件样式", "",
                () -> activity.startActivity(new Intent(activity, WidgetSettingsActivity.class)));
        row.setTag("open-widget-appearance");
        LinearLayout copy = (LinearLayout) row.getChildAt(1);
        while (copy.getChildCount() > 1)
            copy.removeViewAt(1);
        row.removeViewAt(2);
        for (int columns : new int[]{4, 2}) {
            Button button = Ui.textButton(activity);
            button.setText(columns == 4 ? "4×2" : "2×2");
            button.setTextSize(14);
            button.setTextColor(Ui.color(activity, R.color.accent));
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setPadding(0, 0, 0, 0);
            button.setTag("open-widget-" + columns + "x2");
            button.setContentDescription("编辑 " + columns + "×2 默认组件样式");
            button.setOnClickListener(v -> activity.startActivity(new Intent(activity, WidgetSettingsActivity.class)
                    .putExtra("default-columns", columns)));
            row.addView(button, new LinearLayout.LayoutParams(Ui.dp(activity, 52), Ui.dp(activity, 48)));
        }
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
        icon.setImageBitmap(WidgetIcons.style(activity,
                WidgetIcons.source(
                        androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, R.drawable.ic_app)),
                IconStyle.load(activity)));
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(Ui.dp(activity, 64), Ui.dp(activity, 64));
        iconSize.gravity = Gravity.CENTER_HORIZONTAL;
        iconSize.bottomMargin = Ui.dp(activity, 16);
        content.addView(icon, iconSize);
        content.addView(Ui.text(activity, "版本 " + AppUpdates.installedVersion(activity) + "\n" + learningText()
                + "\n\n根据时段、星期和近期使用频率推荐应用。最多保存 60 天本地记录，推荐完全在本机完成。"
                + "\n\n仅手动检查更新时连接 GitHub，不上传应用列表或使用记录。"
                + "\n\n组件按设置间隔更新，系统省电策略可能延迟；打开推荐页或点击更多时，到期才补刷。", 16, R.color.ink));
        GlassFormDialog.info(activity, activity.getString(R.string.app_name), content, "关闭");
    }

    private void chooseRecommendationLimit() {
        LinearLayout content = Ui.column(activity);
        content.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 8), Ui.dp(activity, 24), 0);
        com.google.android.material.slider.Slider slider = new com.google.android.material.slider.Slider(activity);
        Ui.styleSlider(slider);
        slider.setContentDescription("推荐数量");
        slider.setTag("recommendation-limit-input");
        slider.setValueFrom(1);
        slider.setValueTo(RecommendationLimit.MAX);
        slider.setStepSize(1);
        slider.setValue(RecommendationLimit.load(activity));
        slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
        LinearLayout counter = new LinearLayout(activity);
        counter.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = Ui.text(activity, "", 24, R.color.ink);
        value.setGravity(Gravity.CENTER);
        Button decrease = Ui.textButton(activity);
        decrease.setText("−");
        decrease.setContentDescription("减少推荐数量");
        Button increase = Ui.textButton(activity);
        increase.setText("+");
        increase.setContentDescription("增加推荐数量");
        Runnable update = () -> {
            value.setText(activity.getString(R.string.app_count, (int) slider.getValue()));
            decrease.setEnabled(slider.getValue() > 1);
            increase.setEnabled(slider.getValue() < RecommendationLimit.MAX);
        };
        decrease.setOnClickListener(v -> slider.setValue(Math.max(1, slider.getValue() - 1)));
        increase.setOnClickListener(v -> slider.setValue(Math.min(RecommendationLimit.MAX, slider.getValue() + 1)));
        counter.addView(decrease, new LinearLayout.LayoutParams(Ui.dp(activity, 56), Ui.dp(activity, 48)));
        counter.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        counter.addView(increase, new LinearLayout.LayoutParams(Ui.dp(activity, 56), Ui.dp(activity, 48)));
        content.addView(counter);
        content.addView(slider, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48)));
        slider.addOnChangeListener((view, count, user) -> update.run());
        update.run();
        GlassFormDialog.show(activity, "推荐数量", content, () -> {
            int count = (int) slider.getValue();
            if (count != RecommendationLimit.load(activity))
                RecommendationLimit.save(activity, count);
            refresh(null);
        });
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
        if (!isShown() || !activity.hasWindowFocus() || activity.isFinishing() || GlassFormDialog.isShowing(activity))
            return;
        if (error != null) {
            new GlassDialogBuilder(activity).setTitle("检查更新失败")
                    .setMessage(error)
                    .setNegativeButton("关闭", null)
                    .setPositiveButton("版本页面", (dialog, which) -> openWeb(AppUpdates.RELEASES)).show();
        } else if (newer) {
            new GlassDialogBuilder(activity).setTitle("发现新版本 " + release.tag)
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
        return minutes < 60
                ? minutes + " 分钟"
                : minutes % 60 == 0
                        ? (minutes / 60) + " 小时"
                        : (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }

    private void chooseRefreshInterval() {
        LinearLayout content = new LinearLayout(activity);
        content.setGravity(Gravity.CENTER);
        content.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        NumberPicker hours = new NumberPicker(activity), minutes = new NumberPicker(activity);
        hours.setContentDescription("刷新间隔小时");
        minutes.setContentDescription("刷新间隔分钟");
        hours.setMinValue(0);
        hours.setMaxValue(24);
        hours.setWrapSelectorWheel(false);
        hours.setValue(RefreshPolicy.minutes(activity) / 60);
        minutes.setMinValue(0);
        minutes.setMaxValue(59);
        minutes.setWrapSelectorWheel(false);
        minutes.setValue(RefreshPolicy.minutes(activity) % 60);
        Runnable bounds = () -> {
            int value = minutes.getValue(), min = hours.getValue() == 0 ? 15 : 0, max = hours.getValue() == 24 ? 0 : 59;
            minutes.setMinValue(0);
            minutes.setMaxValue(max);
            minutes.setMinValue(min);
            minutes.setValue(Math.max(min, Math.min(max, value)));
            minutes.setEnabled(hours.getValue() != 24);
        };
        hours.setOnValueChangedListener((picker, before, after) -> bounds.run());
        bounds.run();
        content.addView(hours, new LinearLayout.LayoutParams(Ui.dp(activity, 80), Ui.dp(activity, 168)));
        content.addView(Ui.text(activity, "小时", 14, R.color.ink));
        content.addView(minutes, new LinearLayout.LayoutParams(Ui.dp(activity, 80), Ui.dp(activity, 168)));
        content.addView(Ui.text(activity, "分钟", 14, R.color.ink));
        GlassFormDialog.show(activity, "更新频率", content, () -> {
            hours.clearFocus();
            minutes.clearFocus();
            int interval = Math.max(15, Math.min(1440, hours.getValue() * 60 + minutes.getValue()));
            if (interval != RefreshPolicy.minutes(activity)) {
                Repository.prefs(activity).edit().putInt("refresh_minutes", interval).apply();
                Context app = activity.getApplicationContext();
                RefreshJob.schedule(app);
                Repository.WORK.execute(() -> {
                    try {
                        HabitWidget.refreshIfDue(app);
                    } catch (RuntimeException ignored) {
                    }
                });
            }
            refresh(null);
        });
    }

    private LinearLayout row(int drawable, String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(activity, 64));
        row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));
        Ui.nativeTouch(row);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(drawable);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(activity, R.color.muted)));
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 24), Ui.dp(activity, 24)));
        LinearLayout copy = Ui.column(activity);
        copy.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 8), 0);
        copy.addView(Ui.text(activity, title, 16, R.color.ink));
        if (!detail.isEmpty()) {
            Ui.space(copy, 4);
            copy.addView(Ui.text(activity, detail, 12, R.color.muted));
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(activity);
        arrow.setImageResource(R.drawable.ic_chevron);
        arrow.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(activity, R.color.muted)));
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18)));
        row.setFocusable(true);
        row.setContentDescription(detail.isEmpty() ? title : title + "，" + detail);
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
        new GlassDialogBuilder(activity).setTitle("重新学习你的习惯？")
                .setMessage("删除已有本机记录，从现在重新开始，不再导入更早的系统历史。固定和排除设置保留。")
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
