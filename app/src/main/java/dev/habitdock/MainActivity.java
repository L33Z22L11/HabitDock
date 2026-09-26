package dev.habitdock;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    private RecommendationList recommendations;
    private TextView title, time, empty;
    private LinearLayout actions, permissionRow, recommendPage;
    private FrameLayout pages;
    private AppPickerView exclusions;
    private SettingsPage settings;
    private ImageButton back, previewButton, refreshButton;
    private PopupWindow appMenu;
    private boolean resumed;
    private Repository.Snapshot lastData;
    private Bundle pickerState;
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
    private int currentPage = -1, generation;
    private android.window.OnBackInvokedCallback backCallback;
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
            pickerState = state.getBundle("picker");
        }
        LinearLayout root = Ui.screen(this);
        root.setFocusableInTouchMode(true);
        LinearLayout bar = new LinearLayout(this);
        bar.setTag("main-toolbar");
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        back = Ui.icon(this, R.drawable.ic_back, "返回", v -> navigateBack());
        back.setTag("screen-back");
        bar.addView(back);
        title = Ui.text(this, "", 20, R.color.ink);
        Ui.title(title);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        time = Ui.text(this, "", 11, R.color.muted);
        time.setTag("recommend-time");
        bar.addView(time);
        actions = new LinearLayout(this);
        bar.addView(actions);
        root.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 56)));
        pages = new FrameLayout(this);
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        recommendPage = Ui.column(this);
        recommendPage.setTag("recommend-page");
        pages.addView(recommendPage, new FrameLayout.LayoutParams(-1, -1));
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
        recommendations.onMenu = this::showAppMenu;
        content.addView(recommendations, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.text(this, "正在读取…", 14, R.color.muted);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        showPage(state == null ? entryPage(getIntent()) : state.getInt("page", 0), false);
    }

    private int entryPage(Intent intent) {
        return intent.getComponent() != null && intent.getComponent().getClassName().endsWith(".SettingsActivity")
                ? 2
                : 0;
    }

    private void showPage(int page, boolean reload) {
        if (page < 0 || page > 2)
            page = 0;
        if (currentPage == page)
            return;
        currentPage = page;
        ++generation;
        View focus = getCurrentFocus();
        if (focus != null) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
        if (page == 1 && exclusions == null) {
            exclusions = new AppPickerView(this, "hidden", pickerState);
            exclusions.setTag("exclusions-page");
            pages.addView(exclusions, new FrameLayout.LayoutParams(-1, -1));
        }
        if (page == 2 && settings == null) {
            settings = new SettingsPage(this, this::addWidget, () -> showPage(1, false));
            pages.addView(settings, new FrameLayout.LayoutParams(-1, -1));
        }
        recommendPage.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        if (exclusions != null)
            exclusions.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        if (settings != null)
            settings.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
        back.setVisibility(page == 0 ? View.GONE : View.VISIBLE);
        title.setPadding(Ui.dp(this, page == 0 ? 12 : 0), 0, 0, 0);
        title.setText(page == 0 ? getString(R.string.app_name) : page == 1 ? "不推荐的应用" : "设置");
        time.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        actions.removeAllViews();
        if (page == 0) {
            previewButton = Ui.icon(this, R.drawable.ic_history, "预览其他时间", v -> chooseTime());
            previewButton.setTag("preview-time");
            actions.addView(previewButton);
            refreshButton = Ui.icon(this, R.drawable.ic_refresh, "刷新推荐", v -> {
                previewTime = 0;
                refresh(true);
            });
            refreshButton.setTag("return-now");
            actions.addView(refreshButton);
            updatePreviewActions();
            ImageButton settingsButton = Ui.icon(this, R.drawable.ic_settings, "设置", v -> showPage(2, false));
            settingsButton.setTag("open-settings");
            actions.addView(settingsButton);
            if (reload) {
                recommendations.clear();
                refresh(false);
            }
        } else if (page == 1) {
            actions.addView(Ui.icon(this, R.drawable.ic_info, "说明", v -> exclusions.showInfo()));
            exclusions.refreshSelection();
        } else
            settings.refresh(lastData);
        updateBackHandler();
    }

    private void updatePreviewActions() {
        if (currentPage != 0 || refreshButton == null)
            return;
        boolean active = previewTime != 0;
        previewButton.setSelected(active);
        refreshButton.setSelected(active);
        previewButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(getColor(active ? R.color.accent : R.color.ink)));
        refreshButton.setImageTintList(
                android.content.res.ColorStateList.valueOf(getColor(active ? R.color.accent : R.color.ink)));
        if (active)
            Ui.touchBackground(refreshButton, R.color.soft, true);
        else
            Ui.nativeTouch(refreshButton);
        String description = active ? "退出预览，回到此刻" : "刷新推荐";
        refreshButton.setContentDescription(description);
        refreshButton.setTooltipText(description);
        time.setTextColor(getColor(active ? R.color.accent : R.color.muted));
    }

    private void updateBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (backCallback != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
                backCallback = null;
            }
            if (currentPage != 0) {
                backCallback = this::navigateBack;
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            }
        }
    }

    private void navigateBack() {
        showPage(currentPage == 1 ? 2 : 0, currentPage == 2);
    }

    @Override
    public void onBackPressed() {
        if (currentPage != 0)
            navigateBack();
        else
            super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingMenuPackage = intent.getStringExtra(APP_ACTIONS);
        intent.removeExtra(APP_ACTIONS);
        previewTime = 0;
        showPage(entryPage(intent), false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        ApkShare.cleanupAsync(this);
        RefreshJob.schedule(this);
        if (currentPage == 0) {
            refresh(false);
        } else if (currentPage == 1)
            exclusions.refreshSelection();
        else
            settings.refresh(lastData);
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
        out.putInt("page", currentPage);
        if (exclusions != null) {
            Bundle b = new Bundle();
            exclusions.saveState(b);
            out.putBundle("picker", b);
        }
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
                    if (isDestroyed())
                        return;
                    lastData = data;
                    if (currentPage == 2 && settings != null)
                        settings.refresh(data);
                    if (request != generation || currentPage != 0)
                        return;
                    permissionRow.setVisibility(data.permitted ? View.GONE : View.VISIBLE);
                    recommendations.show(data);
                    empty.setVisibility(data.predictions.isEmpty() ? View.VISIBLE : View.GONE);
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
                if (resumed && currentPage == 0)
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

    private void addWidget(int columns) {
        try {
            if (WidgetPinRequest.request(this, columns, 2,
                    WidgetPreferences.load(this, WidgetPreferences.defaultId(columns)), ""))
                return;
        } catch (RuntimeException error) {
            Ui.toast(this, "暂时无法添加组件，请重试");
            return;
        }
        new AlertDialog.Builder(this).setTitle("请从桌面添加")
                .setMessage("当前桌面不支持应用内添加。请长按桌面空白处，在小组件列表选择知时。")
                .setPositiveButton("知道了", null).show();
    }

    private void chooseTime() {
        String[] days = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        new AlertDialog.Builder(this).setTitle("预览什么时候？").setItems(days, (dialog, which) -> {
            ZonedDateTime current = Instant.ofEpochMilli(previewTime == 0 ? System.currentTimeMillis() : previewTime)
                    .atZone(ZoneId.systemDefault());
            new TimePickerDialog(this, (picker, hour, minute) -> {
                LocalDate date = LocalDate.now()
                        .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(which + 1)));
                previewTime = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                refresh(false);
            }, current.getHour(), current.getMinute(), true).show();
        }).show();
    }
}
