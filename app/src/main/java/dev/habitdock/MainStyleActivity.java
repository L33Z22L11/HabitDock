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
    private MainStyle original, style;
    private int originalSeed, originalColorMode, originalScheme;
    private Button restore;
    private SlidingChoice reasons, backgroundMode;
    private String originalWallpaperImage, originalCustomImage;
    private Button imageAction, transparencyAction, blurAction;
    private PopupWindow adjustment;
    private Slider activeAdjustment;
    private TextView imageStatus;
    private LinearLayout root;
    private boolean importing;
    private static final int PICK_IMAGE = 41, WALLPAPER_ACCESS = 42;

    private Slider icons, labels;
    private LinearLayout transparencyControls;
    private TextView blurStatus;
    private TextView iconValue, labelValue;
    private MaterialSwitch hidden;
    private LinearLayout preview;
    private Repository.Snapshot sample;
    private boolean binding;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        style = MainStyle.load(this);
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
                        saved.length > 5 ? saved[5] : 80, saved.length > 6 ? saved[6] : 0);
        root = Ui.screen(this);
        WallpaperStage.prepare(this, root);
        LinearLayout bar = Ui.toolbar(this, root, "主界面样式", true);
        bar.setBackgroundColor(Ui.color(this, R.color.page));
        restore = StyleRestore.button(this, () -> StyleRestore.show(this, "重置主界面样式",
                () -> restoreStyle(original, originalColorMode, originalScheme, originalSeed, originalWallpaperImage,
                        originalCustomImage),
                () -> restoreStyle(MainStyle.defaults(), 0, 1, 0xff246e5c, "", "")));
        restore.setTag("main-style-reset");
        bar.addView(restore);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = Ui.column(this);
        scroll.addView(content);
        preview = Ui.column(this);
        preview.setTag("main-style-preview");
        preview.setPadding(Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16));
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 24));
        body.setBackgroundColor(Ui.color(this, R.color.page));
        content.addView(body, new LinearLayout.LayoutParams(-1, -2, 1));
        body.addView(new AppearanceControls(this), new LinearLayout.LayoutParams(-1, -2));
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
        backgroundMode.addRow(body, "壁纸背景");
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
        Ui.space(body, 20);
        iconValue = Ui.text(this, "", 13, R.color.muted);
        icons = slider(body, "图标大小", "main-icon-size", 32, 64, 2, iconValue);
        labelValue = Ui.text(this, "", 13, R.color.muted);
        labels = slider(body, "应用名大小", "main-label-size", 14, 20, 1, labelValue);
        Ui.space(body, 8);
        reasons = new SlidingChoice(this, MainStyle.REASONS, "main-reason-", index -> update(new MainStyle(style.iconDp,
                style.labelSp, style.background, index, style.showHidden, style.transparency, style.blur)));
        reasons.setTag("main-reasons");
        reasons.addRow(body, "推荐理由");
        Ui.space(body, 12);
        hidden = new MaterialSwitch(this);
        hidden.setTag("main-show-hidden");
        hidden.setText("显示隐藏应用");
        hidden.setTextSize(16);
        hidden.setTextColor(Ui.color(this, R.color.ink));
        body.addView(hidden, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        body.addView(Ui.text(this, "仅在主界面显示，桌面组件仍不推荐", 12, R.color.muted));
        icons.addOnChangeListener((slider, value, fromUser) -> fromControls());
        labels.addOnChangeListener((slider, value, fromUser) -> fromControls());
        hidden.setOnCheckedChangeListener((button, checked) -> fromControls());
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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

    private Slider slider(LinearLayout body, String label, String tag, int min, int max, int step, TextView value) {
        Slider slider = new Slider(this);
        slider.setTag(tag);
        slider.setContentDescription(label);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(step);
        slider.setValue(min);
        slider.setTickVisible(false);
        Ui.styleSlider(slider);
        Ui.sliderRow(body, label, slider, value);
        Ui.space(body, 12);
        return slider;
    }

    private void fromControls() {
        if (!binding)
            update(new MainStyle(Math.round(icons.getValue()), Math.round(labels.getValue()), style.background,
                    style.reasons, hidden.isChecked(), style.transparency, style.blur));
    }

    private void update(MainStyle value) {
        style = value;
        if (!value.sameAs(MainStyle.load(this)))
            value.save(this);
        bind(value);
        recreateForAppearance();
    }

    private void restoreStyle(MainStyle value, int mode, int scheme, int seed, String wallpaperImage,
            String customImage) {
        BackgroundImages.select(this, MainStyle.IMAGE, wallpaperImage);
        BackgroundImages.select(this, MainStyle.CUSTOM_IMAGE, customImage);
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
        wallpaperBlur.set(wallpaperBlurRadius());
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
        WallpaperStage.prepare(this, root);
        icons.setValue(value.iconDp);
        labels.setValue(value.labelSp);
        hidden.setChecked(value.showHidden);
        iconValue.setText(getString(R.string.widget_scale, Math.round(value.iconDp * 100 / 44f)));
        labelValue.setText(String.valueOf(value.labelSp));
        reasons.select(value.reasons, true);
        StyleRestore.update(restore,
                !value.sameAs(original) || Appearance.colorMode(this) != originalColorMode
                        || Appearance.scheme(this) != originalScheme
                        || Appearance.seed(this) != originalSeed
                        || !BackgroundImages.selected(this, MainStyle.IMAGE).equals(originalWallpaperImage)
                        || !BackgroundImages.selected(this, MainStyle.CUSTOM_IMAGE).equals(originalCustomImage));
        binding = false;
        renderPreview();
    }

    @Override
    int wallpaperBlurRadius() {
        return style != null && style.background == MainStyle.WALLPAPER ? style.blur : 0;
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
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 4));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(Ui.text(this, title, 14, R.color.ink), new LinearLayout.LayoutParams(0, -2, 1));
        TextView number = Ui.text(this, getString(R.string.widget_scale, value), 13, R.color.muted);
        heading.addView(number);
        content.addView(heading);
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
        content.addView(slider, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        int width = Math.min(Ui.dp(this, 280), getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32));
        PopupWindow popup = new PopupWindow(content, width, -2, true);
        popup.setBackgroundDrawable(Ui.shape(this, R.color.surface, 18));
        popup.setElevation(Ui.dp(this, 8));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOnDismissListener(() -> {
            if (adjustment == popup) {
                adjustment = null;
                activeAdjustment = null;
            }
        });
        adjustment = popup;
        activeAdjustment = slider;
        popup.showAsDropDown(anchor, (anchor.getWidth() - width) / 2, Ui.dp(this, 4));
        slider.requestFocus();
    }

    @Override
    protected void onStop() {
        if (adjustment != null)
            adjustment.dismiss();
        super.onStop();
    }

    private void renderPreview() {
        preview.removeAllViews();
        preview.setBackgroundColor(
                style.hasBackground() ? style.wallpaperVeil() : style.page(this));
        TextView heading = Ui.text(this, "预览", 12, R.color.muted);
        heading.setTextColor(style.ink(this));
        heading.setGravity(Gravity.CENTER);
        preview.addView(heading);
        Ui.space(preview, 12);
        if (sample == null) {
            preview.setMinimumHeight(Ui.dp(this, 212));
            return;
        }
        List<Predictor.Prediction> examples = style.visiblePredictions(this, sample);
        Set<String> seen = new HashSet<>();
        for (Predictor.Prediction prediction : examples)
            seen.add(prediction.pkg);
        Set<String> hidden = Repository.selection(this, "hidden"), pinned = Repository.selection(this, "pinned");
        for (Repository.App app : sample.apps.values())
            if (examples.size() < 4 && !hidden.contains(app.pkg) && seen.add(app.pkg))
                examples.add(new Predictor.Prediction(app.pkg, 0, "已安装的应用"));
        int columns = getResources().getConfiguration().fontScale > 1.35f ? 1 : 2;
        for (int index = 0; index < Math.min(4, examples.size()); index += columns) {
            LinearLayout row = new LinearLayout(this);
            for (int column = 0; column < columns; column++) {
                int item = index + column;
                if (item >= examples.size())
                    break;
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
        state.putIntArray("original", new int[]{original.iconDp, original.labelSp, original.background,
                original.reasons, original.showHidden ? 1 : 0, original.transparency, original.blur});
        super.onSaveInstanceState(state);
    }
}
