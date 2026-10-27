package dev.habitdock;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.util.*;

/** Main-only presentation settings with a read-only preview. */
public final class MainStyleActivity extends ThemedActivity {
    @Override
    boolean nativeWallpaperPreview() {
        return MainStyle.load(this).background == MainStyle.WALLPAPER;
    }

    private MainStyle original, style;
    private boolean originalLiquid;
    private int originalStartTab;
    private int originalSeed, originalColorMode, originalScheme;
    private Button restore;
    private SlidingChoice backgroundMode, startPage;
    private String originalWallpaperImage, originalCustomImage;
    private Button imageAction, transparencyAction, blurAction;
    private AnchoredGlassPopup adjustment;
    private Slider activeAdjustment;
    private TextView imageStatus;
    private LinearLayout root;
    private boolean importing;
    private static final int PICK_IMAGE = 41, WALLPAPER_ACCESS = 42;

    private LinearLayout transparencyControls;
    private TextView blurStatus;
    private MaterialSwitch liquid;
    private LinearLayout preview;
    private Repository.Snapshot sample;
    private boolean binding;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        style = MainStyle.load(this);
        originalLiquid = state == null ? MainStyle.liquid(this) : state.getBoolean("original-liquid", true);
        originalStartTab = state == null ? MainStyle.startTab(this) : state.getInt("original-start-tab", 0);
        originalColorMode = state == null ? Appearance.colorMode(this) : state.getInt("original-color-mode");
        originalScheme = state == null ? Appearance.scheme(this) : state.getInt("original-scheme", 1);
        originalSeed = state == null ? Appearance.seed(this) : state.getInt("original-seed");
        originalWallpaperImage = state == null
                ? BackgroundImages.selected(this, MainStyle.IMAGE)
                : state.getString("original-wallpaper-image", "");
        originalCustomImage = state == null
                ? BackgroundImages.selected(this, MainStyle.CUSTOM_IMAGE)
                : state.getString("original-custom-image", "");
        int[] saved = state == null ? null : state.getIntArray("original");
        original = saved == null
                ? style
                : new MainStyle(saved[0], saved[1], saved[2], saved[3], saved[4] != 0,
                        saved.length > 5 ? saved[5] : MainStyle.defaults().transparency,
                        saved.length > 6 ? saved[6] : 0);
        root = Ui.screen(this);
        LinearLayout bar = Ui.toolbar(this, root, "主界面样式", true);
        bar.setBackgroundColor(Ui.pageSurface(this));
        restore = StyleRestore.button(this, () -> StyleRestore.show(this, "重置主界面样式",
                () -> restoreStyle(original, originalColorMode, originalScheme, originalSeed, originalWallpaperImage,
                        originalCustomImage, originalLiquid, originalStartTab),
                () -> restoreStyle(MainStyle.defaults(), 0, 1, 0xff246e5c, "", "", true, 0)));
        restore.setTag("main-style-reset");
        bar.addView(restore);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = Ui.column(this);
        scroll.addView(content);
        preview = Ui.column(this);
        preview.setTag("main-style-preview");
        preview.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft)
                preview.post(this::renderPreview);
        });
        preview.setPadding(Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16));
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 24));
        body.setBackgroundColor(Ui.pageSurface(this));
        content.addView(body, new LinearLayout.LayoutParams(-1, -2, 1));
        body.addView(new AppearanceControls(this), new LinearLayout.LayoutParams(-1, -2));
        startPage = new SlidingChoice(this, new String[]{"推荐", "设置"}, "main-start-page-", selected -> {
            if (binding)
                return;
            Repository.prefs(this).edit().putInt("main.start_tab", selected == 0 ? 0 : 2).apply();
            bind(style);
        });
        startPage.setTag("main-start-page");
        startPage.addRow(body, "默认打开", 0);
        liquid = Ui.switchRow(body, "液态材质", "main-liquid");
        liquid.setOnCheckedChangeListener((button, checked) -> {
            if (binding)
                return;
            Repository.prefs(this).edit().putBoolean("main.liquid", checked).apply();
            bind(style);
            recreateForAppearance();
        });
        backgroundMode = new SlidingChoice(this, new String[]{"关", "原生", "兼容", "自定义"}, "background-mode-",
                index -> {
                    if (binding)
                        return;
                    int mode = index == 0
                            ? MainStyle.APP
                            : index == 1
                                    ? MainStyle.WALLPAPER
                                    : index == 2 ? MainStyle.IMAGE : MainStyle.CUSTOM_IMAGE;
                    update(new MainStyle(style.iconDp, style.labelSp, mode, style.reasons, style.showHidden,
                            style.transparency, style.blur));
                });
        backgroundMode.setTag("background-mode");
        backgroundMode.setContentDescription("壁纸背景");
        backgroundMode.addRow(body, "壁纸背景", 0);
        transparencyControls = Ui.column(this);
        body.addView(transparencyControls);
        Ui.space(transparencyControls, 8);
        LinearLayout actions = new LinearLayout(this);
        actions.setTag("background-actions");
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (getResources().getConfiguration().fontScale > 1.35f)
            actions.setOrientation(LinearLayout.VERTICAL);
        transparencyControls.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        imageAction = compactAction(actions, "background-image-action", v -> {
            if (style.background == MainStyle.IMAGE)
                readWallpaper();
            else
                chooseImage();
        });
        transparencyAction = compactAction(actions, "main-transparency-action",
                v -> showAdjustment(transparencyAction, "背景透明度", "main-transparency", style.transparency,
                        value -> update(new MainStyle(style.iconDp, style.labelSp, style.background, style.reasons,
                                style.showHidden, value, style.blur))));
        blurAction = compactAction(actions, "main-blur-action",
                v -> showAdjustment(blurAction, "壁纸模糊", "main-blur", style.blur,
                        value -> update(new MainStyle(style.iconDp, style.labelSp, style.background, style.reasons,
                                style.showHidden, style.transparency, value))));
        imageStatus = Ui.text(this, "", 12, R.color.muted);
        imageStatus.setTag("background-image-status");
        transparencyControls.addView(imageStatus);
        blurStatus = Ui.text(this, "", 12, R.color.muted);
        blurStatus.setTag("main-blur-status");
        transparencyControls.addView(blurStatus);
        bind(style);
        Context app = getApplicationContext();
        Repository.WORK.execute(() -> {
            long stamp = RefreshPolicy.last(app);
            if (stamp == 0)
                stamp = System.currentTimeMillis();
            Repository.Snapshot data = Repository.loadAt(app, stamp, stamp, false);
            runOnUiThread(() -> {
                if (isDestroyed())
                    return;
                sample = data;
                renderPreview();
            });
        });
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_IMAGE);
        } catch (ActivityNotFoundException error) {
            Ui.toast(this, "此设备没有图片选择器");
        }
    }

    private boolean wallpaperAccess() {
        return android.os.Build.VERSION.SDK_INT >= 33
                ? android.os.Environment.isExternalStorageManager()
                : checkSelfPermission(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void readWallpaper() {
        if (wallpaperAccess()) {
            importWallpaper();
            return;
        }
        new GlassDialogBuilder(this)
                .setTitle("读取当前壁纸")
                .setMessage(android.os.Build.VERSION.SDK_INT >= 33
                        ? "当前 Android 版本需要授予所有文件访问权限，才能读取桌面壁纸。仅在点击读取时获取并缓存壁纸；也可改用自定义图片，无需此权限。"
                        : "读取桌面壁纸需要存储读取权限；也可改用自定义图片。")
                .setNegativeButton("取消", null).setPositiveButton("去授权", (dialog, which) -> {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        try {
                            startActivityForResult(
                                    new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            android.net.Uri.parse("package:" + getPackageName())),
                                    WALLPAPER_ACCESS);
                        } catch (ActivityNotFoundException error) {
                            Ui.toast(this, "此设备无法打开授权页面，请使用自定义图片");
                        }
                    } else
                        requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                                WALLPAPER_ACCESS);
                }).show();
    }

    private void importWallpaper() {
        importing = true;
        bind(style);
        BackgroundImages.importWallpaper(this, id -> imported(MainStyle.IMAGE, id), this::importFailed);
    }

    private void imported(int mode, String id) {
        if (isDestroyed() || isFinishing())
            return;
        importing = false;
        BackgroundImages.select(this, mode, id);
        WidgetAppearance.request(this);
        bind(style);
        recreateForAppearance();
    }

    private void importFailed(String message) {
        if (isDestroyed() || isFinishing())
            return;
        importing = false;
        bind(style);
        Ui.toast(this, message);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            importing = true;
            bind(style);
            BackgroundImages.importImage(this, data.getData(), id -> imported(MainStyle.CUSTOM_IMAGE, id),
                    this::importFailed);
        } else if (request == WALLPAPER_ACCESS) {
            if (wallpaperAccess())
                importWallpaper();
            else
                Ui.toast(this, "未授权读取壁纸，可使用自定义图片");
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == WALLPAPER_ACCESS) {
            if (wallpaperAccess())
                importWallpaper();
            else
                Ui.toast(this, "未授权读取壁纸，可使用自定义图片");
        }
    }

    private void update(MainStyle value) {
        if (style.background != value.background)
            WidgetAppearance.request(this);
        style = value;
        if (!value.sameAs(MainStyle.load(this)))
            value.save(this);
        bind(value);
        recreateForAppearance();
    }

    private void restoreStyle(MainStyle value, int mode, int scheme, int seed, String wallpaperImage,
            String customImage, boolean liquidEnabled, int startTab) {
        BackgroundImages.select(this, MainStyle.IMAGE, wallpaperImage);
        BackgroundImages.select(this, MainStyle.CUSTOM_IMAGE, customImage);
        Repository.prefs(this).edit().putBoolean("main.liquid", liquidEnabled).putInt("main.start_tab", startTab)
                .apply();
        bind(value);
        boolean seedChanged = seed != Appearance.seed(this);
        Repository.prefs(this).edit().putInt("appearance.seed", seed).apply();
        if (Appearance.colorMode(this) != mode || Appearance.scheme(this) != scheme)
            AppearanceControls.configure(this, mode, scheme);
        else if (seedChanged)
            recreate();
        else
            recreateForAppearance();
    }

    private void bind(MainStyle value) {
        binding = true;
        style = value;
        if (!value.sameAs(MainStyle.load(this)))
            value.save(this);
        transparencyAction.setText(getString(R.string.main_transparency_action, value.transparency));
        blurAction.setText(getString(R.string.main_blur_action, value.blur));
        blurAvailabilityChanged();
        transparencyControls.setVisibility(value.hasBackground() ? View.VISIBLE : View.GONE);
        backgroundMode.select(
                value.background == MainStyle.WALLPAPER
                        ? 1
                        : value.background == MainStyle.IMAGE
                                ? 2
                                : value.background == MainStyle.CUSTOM_IMAGE ? 3 : 0,
                true);
        imageAction.setVisibility(MainStyle.imageMode(value.background) ? View.VISIBLE : View.GONE);
        imageAction.setText(value.background == MainStyle.IMAGE
                ? BackgroundImages.exists(this) ? "重新获取" : "获取壁纸"
                : BackgroundImages.exists(this) ? "更换图片" : "选择图片");
        imageAction.setEnabled(!importing);
        imageStatus.setText(value.background == MainStyle.WALLPAPER
                ? "随系统壁纸实时变化"
                : importing
                        ? "正在准备背景图片…"
                        : BackgroundImages.exists(this)
                                ? value.background == MainStyle.IMAGE ? "已缓存当前壁纸并应用到整个应用；更换桌面壁纸后可重新获取" : "自定义图片已应用到整个应用"
                                : "请先" + (value.background == MainStyle.IMAGE ? "读取当前壁纸" : "选择一张背景图片") + "，之后用于整个应用");
        imageStatus.setVisibility(importing || (MainStyle.imageMode(value.background) && !BackgroundImages.exists(this))
                ? View.VISIBLE
                : View.GONE);
        liquid.setChecked(MainStyle.liquid(this));
        startPage.select(MainStyle.startTab(this) == 2 ? 1 : 0, true);
        StyleRestore.update(restore,
                !value.sameAs(original) || MainStyle.liquid(this) != originalLiquid
                        || MainStyle.startTab(this) != originalStartTab
                        || Appearance.colorMode(this) != originalColorMode
                        || Appearance.scheme(this) != originalScheme
                        || Appearance.seed(this) != originalSeed
                        || !BackgroundImages.selected(this, MainStyle.IMAGE).equals(originalWallpaperImage)
                        || !BackgroundImages.selected(this, MainStyle.CUSTOM_IMAGE).equals(originalCustomImage));
        binding = false;
        renderPreview();
    }

    @Override
    void blurAvailabilityChanged() {
        if (blurAction == null || blurStatus == null)
            return;
        if (MainStyle.imageMode(style.background)) {
            blurAction.setEnabled(true);
            blurStatus.setText("图片模糊应用到整个应用，图标和文字保持清晰");
        } else if (android.os.Build.VERSION.SDK_INT < 31) {
            blurAction.setEnabled(false);
            blurStatus.setText(R.string.wallpaper_blur_requires_android12);
        } else {
            boolean available = wallpaperBlur.available();
            blurAction.setEnabled(available);
            blurStatus.setText(available
                    ? "仅模糊壁纸，图标和文字保持清晰"
                    : "当前系统未启用原生壁纸模糊，可改用图片模式；已保留设置");
        }
        blurStatus.setVisibility(blurAction.isEnabled() ? View.GONE : View.VISIBLE);
        if (activeAdjustment != null && "main-blur".equals(activeAdjustment.getTag()))
            activeAdjustment.setEnabled(blurAction.isEnabled());
    }

    private Button compactAction(LinearLayout row, String tag, View.OnClickListener clicked) {
        Button action = Ui.textButton(this);
        action.setTag(tag);
        action.setTextSize(13);
        action.setMinWidth(0);
        action.setMinimumWidth(0);
        action.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        action.setOnClickListener(clicked);
        row.addView(action, new LinearLayout.LayoutParams(-2, Ui.dp(this, 48)));
        return action;
    }

    private void showAdjustment(View anchor, String title, String tag, int value,
            java.util.function.IntConsumer changed) {
        if (adjustment != null)
            adjustment.dismiss();
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        TextView number = Ui.text(this, getString(R.string.widget_scale, value), 13, R.color.muted);
        Slider slider = new Slider(this);
        slider.setTag(tag);
        slider.setContentDescription(title);
        slider.setValueFrom(0);
        slider.setValueTo(100);
        slider.setStepSize(1);
        slider.setValue(value);
        slider.setTickVisible(false);
        Ui.styleSlider(slider);
        slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
        slider.addOnChangeListener((control, selected, fromUser) -> {
            number.setText(getString(R.string.widget_scale, Math.round(selected)));
            changed.accept(Math.round(selected));
        });
        Ui.sliderRow(content, title, slider, number,
                tag.equals("main-blur") ? MainStyle.defaults().blur : MainStyle.defaults().transparency);
        android.graphics.Rect frame = AnchoredGlassPopup.visibleFrame(this, anchor);
        int margin = Ui.dp(this, 12);
        int width = Math.min(Ui.dp(this, 280), Math.max(1, frame.width() - 2 * margin));
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        android.graphics.Rect bounds = BubblePlacement.place(frame, AnchoredGlassPopup.anchorBounds(anchor),
                width, content.getMeasuredHeight(), margin, Ui.dp(this, 4));
        AnchoredGlassPopup popup = new AnchoredGlassPopup(this, 18, 8, false, false);
        popup.surface.addView(content, new FrameLayout.LayoutParams(-1, -2));
        popup.setOnDismissListener(() -> {
            if (adjustment == popup) {
                adjustment = null;
                activeAdjustment = null;
            }
        });
        adjustment = popup;
        activeAdjustment = slider;
        popup.show(bounds);
    }

    @Override
    protected void onStop() {
        if (adjustment != null)
            adjustment.dismiss();
        super.onStop();
    }

    private void renderPreview() {
        preview.removeAllViews();
        preview.setBackgroundColor(style.hasBackground() ? style.wallpaperVeil(this) : style.page(this));
        if (sample == null) {
            preview.setMinimumHeight(Ui.dp(this, 212));
            return;
        }
        List<Predictor.Prediction> examples = style.visiblePredictions(this, sample);
        Set<String> seen = new HashSet<>();
        for (Predictor.Prediction prediction : examples)
            seen.add(prediction.pkg);
        int available = (preview.getWidth() > 0 ? preview.getWidth() : getResources().getDisplayMetrics().widthPixels)
                - preview.getPaddingLeft() - preview.getPaddingRight();
        int columns = style.columns(this, available);
        int sampleCount = Math.max(4, columns * 2);
        Set<String> hidden = Repository.selection(this, "hidden"), pinned = Repository.selection(this, "pinned");
        for (Repository.App app : sample.apps.values())
            if (examples.size() < sampleCount && !hidden.contains(app.pkg) && seen.add(app.pkg))
                examples.add(new Predictor.Prediction(app.pkg, 0, "已安装的应用"));
        for (int index = 0; index < Math.min(sampleCount, examples.size()); index += columns) {
            LinearLayout row = new LinearLayout(this);
            for (int column = 0; column < columns; column++) {
                int item = index + column;
                if (item >= examples.size()) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1));
                    continue;
                }
                Predictor.Prediction prediction = examples.get(item);
                AppTile tile = new AppTile(this);
                tile.bind(sample.apps.get(prediction.pkg), prediction, IconStyle.load(this), style,
                        pinned.contains(prediction.pkg));
                tile.setFocusable(false);
                tile.setClickable(false);
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
                if (column + 1 < columns)
                    cell.setMarginEnd(Ui.dp(this, 4));
                row.addView(tile, cell);
            }
            preview.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, style.rowDp(this))));
            Ui.space(preview, 4);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("original-wallpaper-image", originalWallpaperImage);
        state.putString("original-custom-image", originalCustomImage);
        state.putInt("original-color-mode", originalColorMode);
        state.putInt("original-scheme", originalScheme);
        state.putInt("original-seed", originalSeed);
        state.putBoolean("original-liquid", originalLiquid);
        state.putInt("original-start-tab", originalStartTab);
        state.putIntArray("original", new int[]{original.iconDp, original.labelSp, original.background,
                original.reasons, original.showHidden ? 1 : 0, original.transparency, original.blur});
        super.onSaveInstanceState(state);
    }
}
