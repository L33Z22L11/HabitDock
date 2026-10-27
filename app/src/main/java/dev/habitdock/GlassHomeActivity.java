package dev.habitdock;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Recommendation, app catalog and settings home. */
public final class GlassHomeActivity extends ThemedActivity {
    static final String APP_ACTIONS = "dev.habitdock.APP_ACTIONS";

    static Intent appIntent(Context context) {
        return Intent.makeMainActivity(new ComponentName(context, "dev.habitdock.MainActivity"));
    }

    static Intent widgetIntent(Context context, int widgetId) {
        return appIntent(context).setData(android.net.Uri.parse("habitdock://widget/" + widgetId));
    }

    static Intent actionIntent(Context context, String pkg, int widgetId) {
        return appIntent(context).setData(android.net.Uri.parse("habitdock://app-actions/" + widgetId + "/" + pkg))
                .putExtra(APP_ACTIONS, pkg);
    }

    static Intent actionIntent(Context context, String pkg) {
        return actionIntent(context, pkg, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
    }
    // App rows have their own internal vertical padding in addition to this gap.
    private static final int RECOMMENDATION_CONTENT_TOP_DP = FloatingTitleBar.CONTROL_HEIGHT_DP + 8;
    private LinearLayout root;
    private FrameLayout recommendation;
    private FloatingTitleBar recommendationTitle, settingsTitle;
    private int topInset;
    private FadingListFrame recommendationContent, settingsContent;
    private FrameLayout stage;
    private NavigationPager pages;
    private View[] tabs;
    private GlassNavigation navigation;
    private RecommendationList recommendations;
    private GlassCatalog catalog;
    private SettingsPage settings;
    private TextView empty, previewIndicator;
    private ImageButton displayMode;
    private LinearLayout permission;
    private Repository.Snapshot snapshot;
    private AnchoredGlassPopup menu;
    private int selected, request;
    private Bundle restored;
    private String pendingPackage;
    private long previewTime, updateTime;
    private String loadStatus = "读取中";
    private WeakReference<TextView> displayStatus = new WeakReference<>(null);
    private WeakReference<Button> displayRefresh = new WeakReference<>(null);
    private final androidx.activity.result.ActivityResultLauncher<Intent> homeRequest = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (android.os.Build.VERSION.SDK_INT < 29)
                    return;
                boolean held = isDefaultHome();
                settings.setDefaultHome(held);
                if (held) {
                    Ui.toast(this, "当前已是默认桌面");
                } else {
                    // A cancelled result can also mean that the ROM did not present a role UI.
                    // Do not change settings without another explicit user action.
                    new GlassDialogBuilder(this).setMessage("未更改默认桌面。也可以在系统设置中选择桌面。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("打开设置", (dialog, which) -> openHomeSettings()).show();
                }
            });

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        restored = state;
        selected = entryTab(getIntent(), state, Repository.prefs(this).getInt("main.start_tab", 0));
        pendingPackage = state == null
                ? getIntent().getStringExtra(APP_ACTIONS)
                : state.getString("glass.pending", getIntent().getStringExtra(APP_ACTIONS));
        previewTime = state == null ? 0 : state.getLong("glass.preview", 0);
        getIntent().removeExtra(APP_ACTIONS);
        root = Ui.screen(this);
        root.setFocusableInTouchMode(true);
        boolean landscape = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        pages = new NavigationPager(this);
        pages.setId(View.generateViewId());
        pages.setOffscreenPageLimit(2);
        // In landscape the pager leaves a rail for navigation. Exclude the whole
        // content stage from the extra wallpaper veil, including that rail, so
        // the capsule is not surrounded by a darker rectangular strip.
        (landscape ? stage : pages).setTag("recommendations-list");
        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(-1, -1);
        pageParams.leftMargin = landscape ? Ui.dp(this, 80) : 0;
        stage.addView(pages, pageParams);
        recommendation = new FrameLayout(this);
        displayMode = Ui.icon(this, R.drawable.ic_tiles, "推荐显示设置",
                v -> DisplaySettings.show(this, false, this::refreshDisplay));
        LinearLayout recommendationHeader = FloatingTitleBar.title(this, getString(R.string.app_name), displayMode);
        previewIndicator = Ui.text(this, "", 12, R.color.accent);
        previewIndicator.setTag("preview-indicator");
        previewIndicator.setGravity(Gravity.CENTER_VERTICAL);
        previewIndicator.setMinHeight(Ui.dp(this, 48));
        previewIndicator.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        previewIndicator.setOnClickListener(v -> refreshRecommendations());
        LinearLayout.LayoutParams indicator = new LinearLayout.LayoutParams(-2, -2);
        indicator.setMarginEnd(Ui.dp(this, 4));
        recommendationHeader.addView(previewIndicator, recommendationHeader.getChildCount() - 1, indicator);
        recommendationTitle = new FloatingTitleBar(this, recommendationHeader);
        updateDisplayStatus();
        permission = Ui.permissionBanner(this);
        FrameLayout listHost = new FrameLayout(this);
        recommendation.addView(listHost, new FrameLayout.LayoutParams(-1, -1));
        recommendations = new RecommendationList(this);
        recommendations.setPadding(Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, landscape ? 12 : 80));
        recommendations.onMenu = this::appMenu;
        refreshDisplay();
        recommendationContent = new FadingListFrame(this);
        recommendationContent.addView(recommendations, new FrameLayout.LayoutParams(-1, -1));
        listHost.addView(recommendationContent, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.text(this, "正在读取推荐…", 16, R.color.muted);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), Ui.dp(this, 100));
        listHost.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        recommendation.addView(permission, new FrameLayout.LayoutParams(-1, Ui.dp(this, 48), Gravity.TOP));
        recommendation.addView(recommendationTitle,
                new FrameLayout.LayoutParams(-1, Ui.dp(this, FloatingTitleBar.MASK_HEIGHT_DP), Gravity.TOP));
        catalog = new GlassCatalog(this, this::appMenu);
        catalog.onDisplay = () -> DisplaySettings.show(this, true, catalog::refreshDisplay);
        settings = new SettingsPage(this, this::chooseHome);
        settings.setClipToPadding(false);
        settings.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 80));
        FrameLayout settingsHost = new FrameLayout(this);
        settingsContent = new FadingListFrame(this);
        settingsContent.addView(settings, new FrameLayout.LayoutParams(-1, -1));
        settingsHost.addView(settingsContent, new FrameLayout.LayoutParams(-1, -1));
        settingsTitle = new FloatingTitleBar(this, FloatingTitleBar.title(this, "设置"));
        settingsHost.addView(settingsTitle,
                new FrameLayout.LayoutParams(-1, Ui.dp(this, FloatingTitleBar.MASK_HEIGHT_DP), Gravity.TOP));
        tabs = new View[]{recommendation, catalog, settingsHost};
        pages.setAdapter(new androidx.viewpager.widget.PagerAdapter() {
            public int getCount() {
                return tabs.length;
            }

            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }

            public Object instantiateItem(ViewGroup container, int position) {
                container.addView(tabs[position]);
                return tabs[position];
            }

            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }
        });
        navigation = new GlassNavigation(this, this::showTab);
        FrameLayout.LayoutParams nav = new FrameLayout.LayoutParams(
                landscape ? Ui.dp(this, 64) : -1, landscape ? -1 : Ui.dp(this, 60),
                landscape ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nav.leftMargin = nav.rightMargin = Ui.dp(this, landscape ? 8 : 12);
        nav.topMargin = Ui.dp(this, 4);
        nav.bottomMargin = Ui.dp(this, 4);
        stage.addView(navigation, nav);
        stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int length = landscape
                    ? Math.min(Ui.dp(this, 216), b - t - Ui.dp(this, 8))
                    : Math.min(Ui.dp(this, 420), r - l - Ui.dp(this, 24));
            if ((landscape ? nav.height : nav.width) != length) {
                if (landscape)
                    nav.height = length;
                else
                    nav.width = length;
                navigation.setLayoutParams(nav);
            }
        });
        pages.addOnPageChangeListener(new androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float offset, int pixels) {
                navigation.followPage(position + offset);
                navigation.invalidate();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING)
                    navigation.beginPageDrag();
            }

            @Override
            public void onPageSelected(int position) {
                selected = position;
                navigation.markSelected(position);
                tabActivated();
            }
        });
        navigation.onPressStart = () -> navigation.startFromPage(pages.prepareNavigation());
        navigation.onDrag = pages::dragNavigationTo;
        navigation.onDragEnd = () -> {
            if (pages.isFakeDragging())
                pages.endFakeDrag();
        };
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets
                    .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            updateTitleInsets(bars.top);
            navigation.setVisibility(
                    insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            return insets;
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            public void handleOnBackPressed() {
                if (selected != 0) {
                    navigation.select(0, true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
        pages.setCurrentItem(selected, false);
        navigation.select(selected, false);
        tabActivated();
    }

    private void updateTitleInsets(int inset) {
        topInset = inset;
        recommendationContent.topInset(inset);
        settingsContent.topInset(inset);
        recommendationTitle.topInset(inset);
        settingsTitle.topInset(inset);
        catalog.topInset(inset);
        int banner = permission.getVisibility() == View.GONE ? 0 : Ui.dp(this, 48);
        recommendations.setPadding(recommendations.getPaddingLeft(),
                inset + Ui.dp(this, RECOMMENDATION_CONTENT_TOP_DP) + banner,
                recommendations.getPaddingRight(), recommendations.getPaddingBottom());
        recommendations.scrollbarTop(inset + Ui.dp(this, FloatingTitleBar.CONTENT_TOP_DP));
        FrameLayout.LayoutParams bannerParams = (FrameLayout.LayoutParams) permission.getLayoutParams();
        bannerParams.topMargin = inset + Ui.dp(this, 48);
        permission.setLayoutParams(bannerParams);
        settings.setPadding(0, inset + Ui.dp(this, FloatingTitleBar.CONTENT_TOP_DP), 0, settings.getPaddingBottom());
    }

    private void refreshDisplay() {
        boolean grid = Repository.prefs(this).getBoolean("main.grid", false);
        displayMode.setImageResource(grid ? R.drawable.ic_grid : R.drawable.ic_tiles);
        displayMode.setContentDescription((grid ? "宫格" : "平铺") + "，显示设置");
        recommendations.grid(grid);
        if (snapshot != null) {
            recommendations.show(snapshot);
            empty.setVisibility(recommendations.getAdapter().isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showTab(int index) {
        if (pages.isFakeDragging())
            pages.endFakeDrag();
        pages.setCurrentItem(Math.max(0, Math.min(2, index)), true);
        tabActivated();
    }

    private void tabActivated() {
        root.requestFocus();
        android.view.inputmethod.InputMethodManager keyboard = getSystemService(
                android.view.inputmethod.InputMethodManager.class);
        if (keyboard != null)
            keyboard.hideSoftInputFromWindow(root.getWindowToken(), 0);
        if (selected == 2) {
            settings.setDefaultHome(isDefaultHome());
            settings.refresh(snapshot);
        }
        if (navigation != null)
            navigation.postDelayed(navigation::invalidate, 100);
    }

    private void appMenu(Repository.App app, View anchor) {
        if (menu != null)
            menu.dismiss();
        menu = AppActions.show(this, app, anchor, () -> load(false), NativeAppDetails.find(this, app.pkg));
    }

    private void chooseHome() {
        boolean held = isDefaultHome();
        settings.setDefaultHome(held);
        if (held) {
            openHomeSettings();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
                if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    homeRequest.launch(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME));
                    return;
                }
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                // Fall through to the system's home settings when the ROM has no role UI.
            }
        }
        openHomeSettings();
    }

    private boolean isDefaultHome() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
                if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME))
                    return roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
            }
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo home = getPackageManager().resolveActivity(intent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return home != null && home.activityInfo != null && getPackageName().equals(home.activityInfo.packageName);
        } catch (SecurityException unavailable) {
            return false;
        }
    }

    private void openHomeSettings() {
        String[] actions = {android.provider.Settings.ACTION_HOME_SETTINGS,
                android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS};
        for (String action : actions) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                // Some older devices omit the dedicated launcher settings page.
            }
        }
        Ui.toast(this, "请在系统默认应用中选择桌面");
    }

    static int entryTab(Intent intent, Bundle state, int preference) {
        if (state != null)
            return Math.max(0, Math.min(2, state.getInt("glass.tab", 0)));
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasExtra(APP_ACTIONS))
            return 0;
        return preference == 2 ? 2 : 0;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int tab = entryTab(intent, null, Repository.prefs(this).getInt("main.start_tab", 0));
        pendingPackage = intent.getStringExtra(APP_ACTIONS);
        intent.removeExtra(APP_ACTIONS);
        previewTime = 0;
        navigation.select(tab, true);
        load(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApkShare.cleanupAsync(this);
        settings.setDefaultHome(isDefaultHome());
        load(false);
        RefreshJob.schedule(this);
    }

    private void load(boolean force) {
        int generation = ++request;
        long preview = previewTime;
        loadStatus = force || RefreshPolicy.due(this, System.currentTimeMillis()) ? "更新中" : "读取中";
        updateDisplayStatus();
        Context app = getApplicationContext();
        Repository.WORK.execute(() -> {
            try {
                Repository.Current current = Repository.current(app, force);
                Repository.Snapshot data = preview == 0
                        ? current.data
                        : Repository.loadAt(app, current.stamp, preview, false);
                HabitWidget.refreshIfDue(app);
                Set<String> systemApps = new HashSet<>();
                Map<String, Repository.App> allApps = new LinkedHashMap<>();
                Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (android.content.pm.ResolveInfo info : app.getPackageManager().queryIntentActivities(launcher, 0)) {
                    String pkg = info.activityInfo.packageName;
                    allApps.putIfAbsent(pkg,
                            new Repository.App(pkg, info.loadLabel(app.getPackageManager()).toString()));
                    int flags = info.activityInfo.applicationInfo.flags;
                    if ((flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM
                            | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0)
                        systemApps.add(pkg);
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation)
                        return;
                    snapshot = data;
                    updateTime = current.stamp;
                    loadStatus = "";
                    updateDisplayStatus();
                    recommendations.show(data);
                    catalog.show(allApps, systemApps);
                    catalog.restore(restored, "all");
                    restored = null;
                    permission.setVisibility(data.permitted ? View.GONE : View.VISIBLE);
                    updateTitleInsets(topInset);
                    empty.setVisibility(recommendations.getAdapter().isEmpty() ? View.VISIBLE : View.GONE);
                    empty.setText(data.permitted ? "使用几个应用后，推荐会出现在这里" : "开启使用情况访问，或在设置里置顶应用");
                    if (selected == 2)
                        settings.refresh(data);
                    navigation.postDelayed(navigation::invalidate, 250);
                    showPendingMenu();
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && request == generation) {
                        loadStatus = "更新失败";
                        updateDisplayStatus();
                        empty.setText("读取失败，请稍后重试");
                        Ui.toast(this, "应用读取失败");
                    }
                });
            }
        });
    }

    private void showPendingMenu() {
        if (pendingPackage == null || snapshot == null
                || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            return;
        Repository.App target = snapshot.apps.get(pendingPackage);
        if (target == null) {
            pendingPackage = null;
            return;
        }
        navigation.post(() -> {
            if (target.pkg.equals(pendingPackage)
                    && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                pendingPackage = null;
                appMenu(target, navigation);
            }
        });
    }

    void bindDisplayStatus(TextView status, Button refresh) {
        displayStatus = new WeakReference<>(status);
        displayRefresh = new WeakReference<>(refresh);
        updateDisplayStatus();
    }

    void refreshRecommendations() {
        previewTime = 0;
        load(true);
    }

    private void updateDisplayStatus() {
        if (previewIndicator != null) {
            previewIndicator.setVisibility(previewTime == 0 ? View.GONE : View.VISIBLE);
            if (previewTime != 0) {
                String label = "预览 " + formatTime(previewTime, true);
                previewIndicator.setText(label);
                previewIndicator.setContentDescription(label + "，点按回到实时并刷新");
            }
        }
        TextView status = displayStatus.get();
        if (status != null) {
            String text = updateTime == 0 ? "更新时间：暂无" : "更新时间：" + formatTime(updateTime, false);
            if (previewTime != 0)
                text += "\n预览时间：" + formatTime(previewTime, true);
            if (!loadStatus.isEmpty())
                text += "\n" + loadStatus;
            status.setText(text);
        }
        Button refresh = displayRefresh.get();
        if (refresh != null)
            refresh.setText(previewTime == 0 ? "立即刷新" : "回到实时并刷新");
    }

    private String formatTime(long time, boolean preview) {
        return Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter
                .ofPattern(preview ? "EE HH:mm" : "HH:mm", Locale.SIMPLIFIED_CHINESE));
    }

    void chooseTime() {
        String[] days = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        new GlassDialogBuilder(this).setTitle("预览什么时候？")
                .setItems(days, (dialog, which) -> {
                    ZonedDateTime current = Instant.ofEpochMilli(previewTime == 0
                            ? System.currentTimeMillis()
                            : previewTime).atZone(ZoneId.systemDefault());
                    new android.app.TimePickerDialog(this, (picker, hour, minute) -> {
                        LocalDate date = LocalDate.now().with(
                                java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(which + 1)));
                        previewTime = date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()
                                .toEpochMilli();
                        load(false);
                    }, current.getHour(), current.getMinute(), true).show();
                }).show();
    }

    @Override
    protected void onPause() {
        if (menu != null)
            menu.dismiss();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("glass.tab", selected);
        state.putString("glass.pending", pendingPackage);
        state.putLong("glass.preview", previewTime);
        catalog.save(state, "all");
        super.onSaveInstanceState(state);
    }
}
