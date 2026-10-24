package dev.habitdock;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends ThemedActivity {
    private RecommendationList recommendations;
    private TextView title, time, empty;
    private LinearLayout actions, permissionRow, recommendPage, bar, root;
    private String appliedMainAppearance = "";
    private ImageButton previewButton, refreshButton;
    private PopupWindow appMenu;
    private boolean resumed;
    private Repository.Snapshot lastData;
    private long previewTime;
    private String pendingMenuPackage;
    static final String APP_ACTIONS = "dev.habitdock.APP_ACTIONS";
    static Intent actionIntent(Context c, String pkg) {
        return actionIntent(c, pkg, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    static Intent actionIntent(Context c, String pkg, int widgetId) {
        return appIntent(c).setData(android.net.Uri.parse("habitdock://app-actions/" + widgetId + "/" + pkg))
                .putExtra(APP_ACTIONS, pkg);
    }

    static Intent widgetIntent(Context c, int widgetId) {
        return appIntent(c).setData(android.net.Uri.parse("habitdock://widget/" + widgetId));
    }
    private int generation;
    static Intent appIntent(Context c) {
        return Intent.makeMainActivity(new ComponentName(c, MainActivity.class));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        pendingMenuPackage = getIntent().getStringExtra(APP_ACTIONS);
        getIntent().removeExtra(APP_ACTIONS);
        if (state != null) {
            pendingMenuPackage = state.getString("pending-menu", pendingMenuPackage);
            previewTime = state.getLong("preview", 0);
        }
        root = Ui.screen(this);
        root.setFocusableInTouchMode(true);
        bar = Ui.toolbar(this, root, getString(R.string.app_name), false);
        bar.setTag("main-toolbar");
        title = (TextView) bar.getChildAt(0);
        time = Ui.text(this, "", 11, R.color.muted);
        time.setTag("recommend-time");
        bar.addView(time);
        actions = new LinearLayout(this);
        bar.addView(actions);
        recommendPage = Ui.column(this);
        recommendPage.setTag("recommend-page");
        root.addView(recommendPage, new LinearLayout.LayoutParams(-1, 0, 1));
        permissionRow = new LinearLayout(this);
        permissionRow.setGravity(Gravity.CENTER_VERTICAL);
        permissionRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 4), 0);
        Ui.touchBackground(permissionRow, R.color.soft, false);
        permissionRow.addView(Ui.text(this, "开启使用情况访问，开始学习", 14, R.color.accent),
                new LinearLayout.LayoutParams(0, -2, 1));
        permissionRow.addView(Ui.icon(this, R.drawable.ic_chevron, "开启使用情况访问", v -> SettingsPage.permission(this)));
        permissionRow.setOnClickListener(v -> SettingsPage.permission(this));
        permissionRow.setVisibility(View.GONE);
        recommendPage.addView(permissionRow, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        FrameLayout content = new FrameLayout(this);
        recommendPage.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        recommendations = new RecommendationList(this);
        recommendations.setTag("recommendations-list");
        recommendations.onMenu = this::showAppMenu;
        content.addView(recommendations, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.text(this, "正在读取…", 14, R.color.muted);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        previewButton = Ui.icon(this, R.drawable.ic_history, "预览其他时间", v -> chooseTime());
        previewButton.setTag("preview-time");
        actions.addView(previewButton);
        refreshButton = Ui.icon(this, R.drawable.ic_refresh, "刷新推荐", v -> {
            previewTime = 0;
            refresh(true);
        });
        refreshButton.setTag("return-now");
        actions.addView(refreshButton);
        ImageButton settingsButton = Ui.icon(this, R.drawable.ic_settings, "设置",
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        settingsButton.setTag("open-settings");
        actions.addView(settingsButton);
        applyMainAppearance();
        updatePreviewActions();
    }

    private void updatePreviewActions() {
        if (refreshButton == null)
            return;
        boolean active = previewTime != 0;
        MainStyle style = MainStyle.load(this);
        boolean wallpaper = style.hasBackground();
        int actionColor = wallpaper ? style.ink(this) : Ui.color(this, active ? R.color.accent : R.color.ink);
        previewButton.setSelected(active);
        refreshButton.setSelected(active);
        previewButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(actionColor));
        refreshButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(actionColor));
        if (active)
            Ui.touchBackground(refreshButton, R.color.soft, true);
        else
            Ui.nativeTouch(refreshButton);
        String description = active ? "退出预览，回到此刻" : "刷新推荐";
        refreshButton.setContentDescription(description);
        refreshButton.setTooltipText(description);
        time.setTextColor(wallpaper
                ? style.ink(this)
                : Ui.color(this, active ? R.color.accent : R.color.muted));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingMenuPackage = intent.getStringExtra(APP_ACTIONS);
        intent.removeExtra(APP_ACTIONS);
        previewTime = 0;
        updatePreviewActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        applyMainAppearance();
        ApkShare.cleanupAsync(this);
        RefreshJob.schedule(this);
        refresh(false);
    }

    @Override
    boolean wallpaperScene() {
        return MainStyle.load(this).background == MainStyle.WALLPAPER;
    }

    @Override
    void refreshPageContent() {
        applyMainAppearance();
        if (lastData != null)
            recommendations.show(lastData);
    }

    private void applyMainAppearance() {
        if (root == null)
            return;
        MainStyle style = MainStyle.load(this);
        String key = style.background + ":" + style.transparency + ":" + style.page(this) + ":" + style.ink(this)
                + ":" + style.blur + ":" + BackgroundImages.selected(this);
        if (key.equals(appliedMainAppearance))
            return;
        appliedMainAppearance = key;
        if (Build.VERSION.SDK_INT >= 33)
            setTaskDescription(new ActivityManager.TaskDescription.Builder()
                    .setLabel(getString(R.string.app_name))
                    .setBackgroundColor(style.hasBackground()
                            ? android.graphics.Color.BLACK
                            : style.page(this))
                    .build());
        recommendations.applyScrollbar(style);
        applySystemBars();
        bar.setBackgroundColor(style.toolbar(this));
        title.setTextColor(style.ink(this));
        time.setTextColor(style.ink(this));
        empty.setTextColor(style.ink(this));
        for (int i = 0; i < actions.getChildCount(); i++)
            if (actions.getChildAt(i) instanceof ImageButton)
                ((ImageButton) actions.getChildAt(i))
                        .setImageTintList(android.content.res.ColorStateList.valueOf(style.ink(this)));
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (appMenu != null) {
            appMenu.dismiss();
            appMenu = null;
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putString("pending-menu", pendingMenuPackage);
        out.putLong("preview", previewTime);
        super.onSaveInstanceState(out);
    }

    private void refresh(boolean sync) {
        updatePreviewActions();
        int request = ++generation;
        long preview = previewTime;
        if (sync || RefreshPolicy.due(this, System.currentTimeMillis()))
            time.setText("更新中");
        else if (lastData == null)
            time.setText("读取中");
        Context app = getApplicationContext();
        Repository.WORK.execute(() -> {
            try {
                Repository.Current current = Repository.current(app, sync);
                long target = preview == 0 ? current.stamp : preview;
                Repository.Snapshot data = preview == 0
                        ? current.data
                        : Repository.loadAt(app, current.stamp, preview, false);
                HabitWidget.refreshIfDue(app);
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation)
                        return;
                    lastData = data;
                    permissionRow.setVisibility(data.permitted ? View.GONE : View.VISIBLE);
                    recommendations.show(data);
                    empty.setVisibility(recommendations.getAdapter().isEmpty() ? View.VISIBLE : View.GONE);
                    empty.setText(
                            data.permitted ? "还在了解你的习惯\n使用几个应用后刷新，或在设置里固定常用应用。" : "开启使用情况访问后生成推荐\n也可以在设置里先固定常用应用。");
                    String stamp = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault()).format(DateTimeFormatter
                            .ofPattern(previewTime == 0 ? "HH:mm" : "EE HH:mm", Locale.SIMPLIFIED_CHINESE));
                    time.setText(previewTime == 0 ? stamp : getString(R.string.preview_time, stamp));
                    showPendingMenu(data);
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && request == generation) {
                        time.setText("更新失败");
                        Ui.toast(this, "请检查使用情况访问权限后重试");
                    }
                });
            }
        });
    }

    private void showPendingMenu(Repository.Snapshot data) {
        String pkg = pendingMenuPackage;
        pendingMenuPackage = null;
        if (pkg != null && data.apps.containsKey(pkg))
            recommendations.reveal(pkg, anchor -> {
                if (resumed)
                    showAppMenu(data.apps.get(pkg), anchor == null ? title : anchor);
            });
    }

    private void showAppMenu(Repository.App app, View anchor) {
        if (appMenu != null)
            appMenu.dismiss();
        appMenu = AppActions.show(this, app, anchor, () -> {
            recommendations.clear();
            refresh(false);
        });
    }

    private void chooseTime() {
        String[] days = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("预览什么时候？")
                .setItems(days, (dialog, which) -> {
                    ZonedDateTime current = Instant
                            .ofEpochMilli(previewTime == 0 ? System.currentTimeMillis() : previewTime)
                            .atZone(ZoneId.systemDefault());
                    new TimePickerDialog(this, (picker, hour, minute) -> {
                        LocalDate date = LocalDate.now()
                                .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(which + 1)));
                        previewTime = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()
                                .toEpochMilli();
                        refresh(false);
                    }, current.getHour(), current.getMinute(), true).show();
                }).show();
    }
}
