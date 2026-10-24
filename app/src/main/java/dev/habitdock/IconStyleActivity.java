package dev.habitdock;
import com.google.android.material.slider.Slider;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/**
 * Global appearance editor with immediate persistence and a session restore
 * point.
 */
public final class IconStyleActivity extends ThemedActivity {
    @Override
    boolean nativeWallpaperPreview() {
        return true;
    }

    private Slider roundness, fallbackScale, exponent, saturation, brightness, contrast;
    private TextView saturationValue, brightnessValue, contrastValue;
    private LinearLayout fallbackSection;
    private SlidingChoice continuous, filling;
    private LinearLayout exponentControls;
    private TextView exponentValue;
    private LinearLayout fallbackControls;
    private TextView fallbackValue;
    private boolean fill;
    private TextView roundnessLabel;
    private int fillColor;
    private String pack;
    private TextView packValue;
    private ImageView packIcon;
    private WidgetSettingsActivity.Preview preview, secondPreview;
    private IconStyle original;
    private Button restore;
    private boolean binding;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        IconStyle style = IconStyle.load(this);
        original = state == null
                ? style
                : new IconStyle(state.getInt("original-roundness"),
                        state.getBoolean("original-fill"), state.getInt("original-color"),
                        state.getString("original-pack", ""), state.getInt("original-fallback-scale", 100),
                        state.getBoolean("original-continuous", false), state.getInt("original-exponent", 40),
                        state.getInt("original-saturation", 100), state.getInt("original-brightness", 100),
                        state.getInt("original-contrast", 100));
        fillColor = style.fillColor;
        pack = style.pack;
        fill = style.fillBackground;
        LinearLayout root = Ui.screen(this), bar = Ui.toolbar(this, root, "图标样式", true);
        bar.setBackgroundColor(Ui.pageSurface(this));
        restore = StyleRestore.button(this,
                () -> StyleRestore.show(this, "恢复图标样式", () -> bind(original), () -> bind(IconStyle.defaults())));
        restore.setTag("icon-style-save");
        bar.addView(restore);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = Ui.column(this);
        scroll.addView(content);
        LinearLayout hero = Ui.column(this);
        hero.setTag("wallpaper-preview-stage");
        hero.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16));
        content.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        preview = new WidgetSettingsActivity.Preview(this);
        preview.setTag("icon-preview");
        preview.layout = new WidgetPreferences(5, 2, 82, false);
        preview.setWidget(0);
        preview.theme = WidgetTheme.load(this, WidgetTheme.previewId(this));
        preview.setSamples(0);
        hero.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        Ui.space(hero, 16);
        secondPreview = new WidgetSettingsActivity.Preview(this);
        secondPreview.setTag("icon-preview-secondary");
        secondPreview.layout = WidgetPreferences.defaults();
        secondPreview.setWidget(0);
        secondPreview.theme = WidgetTheme.load(this, WidgetTheme.previewId(this));
        secondPreview.setSamples(preview.layout.capacity());
        hero.addView(secondPreview, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = Ui.column(this);
        body.setBackgroundColor(Ui.pageSurface(this));
        body.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 24));
        content.addView(body, new LinearLayout.LayoutParams(-1, -2, 1));
        LinearLayout packRow = new LinearLayout(this);
        packRow.setGravity(Gravity.CENTER_VERTICAL);
        packRow.setTag("icon-pack");
        packRow.setMinimumHeight(Ui.dp(this, 52));
        Ui.nativeTouch(packRow);
        packRow.addView(Ui.text(this, "图标包", 14, R.color.ink), new LinearLayout.LayoutParams(0, -2, 1));
        packIcon = new ImageView(this);
        LinearLayout selectedPack = new LinearLayout(this);
        selectedPack.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        selectedPack.addView(packIcon, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        packValue = Ui.text(this, "", 14, R.color.muted);
        packValue.setPadding(Ui.dp(this, 8), 0, 0, 0);
        packValue.setMaxLines(2);
        packValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        selectedPack.addView(packValue, new LinearLayout.LayoutParams(-2, -2));
        packRow.addView(selectedPack, new LinearLayout.LayoutParams(0, -2, 2));
        packValue.setGravity(Gravity.END);
        packRow.setOnClickListener(v -> choosePack());
        body.addView(packRow);
        filling = new SlidingChoice(this, new String[]{"关", "白色", "自适应"}, "icon-fill-", index -> {
            fill = index != 0;
            fillColor = index == 1 ? android.graphics.Color.WHITE : 0;
            changed();
        });
        filling.setTag("icon-fill-mode");
        filling.addRow(body, "图标填充", 0);
        Ui.space(body, 16);
        continuous = new SlidingChoice(this, new String[]{"普通圆角", "超椭圆"}, "icon-corner-", i -> changed());
        continuous.setTag("icon-corner-mode");
        continuous.select(style.continuous ? 1 : 0, false);
        continuous.addRow(body, "圆角模式", 0);
        Ui.space(body, 12);
        exponentControls = Ui.column(this);
        exponentValue = Ui.text(this, "", 14, R.color.ink);
        exponent = new Slider(this);
        exponent.setTag("icon-exponent");
        exponent.setContentDescription("超椭圆参数");
        exponent.setValueFrom(2);
        exponent.setValueTo(8);
        exponent.setStepSize(.1f);
        exponent.setTickVisible(false);
        exponent.setValue(style.exponent / 10f);
        Ui.sliderRow(exponentControls, "超椭圆参数", exponent, exponentValue, IconStyle.defaults().exponent / 10f);
        body.addView(exponentControls);
        roundnessLabel = Ui.text(this, "", 14, R.color.ink);
        roundness = new Slider(this);
        roundness.setTag("icon-roundness");
        roundness.setContentDescription("圆角比例");
        roundness.setValueTo(100);
        roundness.setStepSize(1);
        roundness.setTickVisible(false);
        roundness.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
        roundness.setValue(style.roundness);
        Ui.sliderRow(body, "圆角比例", roundness, roundnessLabel, IconStyle.defaults().roundness);
        Ui.space(body, 16);
        fallbackSection = Ui.column(this);
        body.addView(fallbackSection);
        TextView fallbackHeader = Ui.text(this, "未适配图标样式", 12, R.color.muted);
        fallbackHeader.setTag("icon-fallback-heading");
        fallbackHeader.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        fallbackSection.addView(fallbackHeader, new LinearLayout.LayoutParams(-1, -2));
        fallbackControls = Ui.column(this);
        fallbackSection.addView(fallbackControls);
        brightnessValue = Ui.text(this, "", 13, R.color.muted);
        brightnessValue.setTag("icon-brightness-value");
        brightness = new Slider(this);
        brightness.setTag("icon-brightness");
        brightness.setContentDescription("未适配图标明度");
        brightness.setValueTo(200);
        brightness.setStepSize(1);
        brightness.setValue(style.brightness);
        Ui.sliderRow(fallbackControls, "明度", brightness, brightnessValue, 100f);
        saturationValue = Ui.text(this, "", 13, R.color.muted);
        saturationValue.setTag("icon-saturation-value");
        saturation = new Slider(this);
        saturation.setTag("icon-saturation");
        saturation.setContentDescription("未适配图标饱和度");
        saturation.setValueTo(200);
        saturation.setStepSize(1);
        saturation.setValue(style.saturation);
        Ui.sliderRow(fallbackControls, "饱和度", saturation, saturationValue, 100f);

        contrastValue = Ui.text(this, "", 13, R.color.muted);
        contrastValue.setTag("icon-contrast-value");
        contrast = new Slider(this);
        contrast.setTag("icon-contrast");
        contrast.setContentDescription("未适配图标对比度");
        contrast.setValueTo(200);
        contrast.setStepSize(1);
        contrast.setValue(style.contrast);
        Ui.sliderRow(fallbackControls, "对比度", contrast, contrastValue, 100f);
        fallbackValue = Ui.text(this, "", 14, R.color.ink);
        fallbackScale = new Slider(this);
        fallbackScale.setTag("icon-fallback-scale");
        fallbackScale.setContentDescription("未适配图标缩放");
        fallbackScale.setValueFrom(50);
        fallbackScale.setValueTo(100);
        fallbackScale.setStepSize(1);
        fallbackScale.setTickVisible(false);
        fallbackScale.setValue(style.fallbackScale);
        Ui.sliderRow(fallbackControls, "缩放", fallbackScale, fallbackValue, 100f);
        Ui.space(fallbackControls, 16);
        contrast.addOnChangeListener((slider, value, user) -> changed());
        brightness.addOnChangeListener((slider, value, user) -> changed());
        saturation.addOnChangeListener((slider, value, user) -> changed());
        fallbackScale.addOnChangeListener((slider, value, user) -> changed());
        roundness.addOnChangeListener((slider, value, user) -> changed());
        exponent.addOnChangeListener((slider, value, user) -> changed());
        changed();
    }

    private void updateFallbackSection() {
        fallbackSection.setVisibility(pack.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void choosePack() {
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("图标包")
                .setMessage("正在读取已安装图标包…").setNegativeButton("取消", null).show();
        Repository.WORK.execute(() -> {
            java.util.List<IconPacks.Pack> packs = IconPacks.installed(getApplicationContext());
            packs.add(0, new IconPacks.Pack("", "应用原图标"));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !dialog.isShowing())
                    return;
                dialog.dismiss();
                final int selected;
                int match = 0;
                for (int i = 1; i < packs.size(); i++)
                    if (packs.get(i).pkg.equals(pack))
                        match = i;
                selected = match;
                BaseAdapter adapter = new BaseAdapter() {
                    public int getCount() {
                        return packs.size();
                    }

                    public Object getItem(int position) {
                        return packs.get(position).label;
                    }

                    public long getItemId(int position) {
                        return position;
                    }

                    public View getView(int position, View recycled, ViewGroup parent) {
                        LinearLayout row = new LinearLayout(IconStyleActivity.this);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(Ui.dp(IconStyleActivity.this, 20), Ui.dp(IconStyleActivity.this, 8),
                                Ui.dp(IconStyleActivity.this, 12), Ui.dp(IconStyleActivity.this, 8));
                        row.setMinimumHeight(Ui.dp(IconStyleActivity.this, 60));
                        ImageView icon = new ImageView(IconStyleActivity.this);
                        icon.setImageDrawable(packIcon(packs.get(position).pkg, false));
                        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(IconStyleActivity.this, 32),
                                Ui.dp(IconStyleActivity.this, 32)));
                        TextView label = Ui.text(IconStyleActivity.this, packs.get(position).label, 16, R.color.ink);
                        label.setPadding(Ui.dp(IconStyleActivity.this, 12), 0, Ui.dp(IconStyleActivity.this, 8), 0);
                        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                        RadioButton radio = new com.google.android.material.radiobutton.MaterialRadioButton(
                                IconStyleActivity.this);
                        radio.setChecked(position == selected);
                        radio.setClickable(false);
                        radio.setFocusable(false);
                        radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                        row.addView(radio);
                        return row;
                    }
                };
                AlertDialog.Builder picker = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("图标包")
                        .setAdapter(adapter, (d, which) -> {
                            binding = true;
                            pack = packs.get(which).pkg;
                            binding = false;
                            changed();
                            d.dismiss();
                        }).setNegativeButton("取消", null);
                picker.show();
            });
        });
    }

    private Drawable packIcon(String pkg, boolean filled) {
        if (!pkg.isEmpty()) {
            try {
                return getPackageManager().getApplicationIcon(pkg);
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            }
        }
        return new android.graphics.drawable.BitmapDrawable(getResources(),
                WidgetIcons.style(this,
                        WidgetIcons.source(
                                androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_app)),
                        new IconStyle(35, filled, fillColor)));
    }

    private IconStyle input() {
        return new IconStyle((int) roundness.getValue(), fill, fillColor, pack, Math.round(fallbackScale.getValue()),
                continuous.selection() == 1, Math.round(exponent.getValue() * 10), Math.round(saturation.getValue()),
                Math.round(brightness.getValue()), Math.round(contrast.getValue()));
    }

    private void changed() {
        if (binding)
            return;
        IconStyle style = input();
        if (!style.sameAs(IconStyle.load(this))) {
            style.save(this);
            WidgetAppearance.request(this);
        }
        StyleRestore.update(restore, !style.sameAs(original));
        updateFallbackSection();
        contrastValue.setText(getString(R.string.widget_scale, style.contrast));
        brightnessValue.setText(getString(R.string.widget_scale, style.brightness));
        saturationValue.setText(getString(R.string.widget_scale, style.saturation));
        fallbackValue.setText(getString(R.string.widget_scale, style.fallbackScale));
        ((View) roundness.getParent()).setVisibility(style.continuous ? View.GONE : View.VISIBLE);
        exponentControls.setVisibility(style.continuous ? View.VISIBLE : View.GONE);
        exponentValue.setText(String.format(java.util.Locale.getDefault(), "%.1f", style.exponent / 10f));
        roundnessLabel.setText(getString(R.string.widget_scale, style.roundness));
        filling.select(!style.fillBackground ? 0 : style.fillColor == android.graphics.Color.WHITE ? 1 : 2, true);
        ((View) filling.getParent()).setVisibility(pack.isEmpty() ? View.VISIBLE : View.GONE);
        packValue.setText(IconPacks.label(this, pack));
        packIcon.setImageDrawable(packIcon(pack, style.fillBackground));
        preview.setStyle(style);
        secondPreview.setStyle(style);
    }

    private void bind(IconStyle style) {
        binding = true;
        fillColor = style.fillColor;
        pack = style.pack;
        fill = style.fillBackground;
        continuous.select(style.continuous ? 1 : 0, true);
        contrast.setValue(style.contrast);
        brightness.setValue(style.brightness);
        saturation.setValue(style.saturation);
        exponent.setValue(style.exponent / 10f);
        roundness.setValue(style.roundness);
        fallbackScale.setValue(style.fallbackScale);
        binding = false;
        changed();
    }

    @Override
    protected void onStop() {
        WidgetAppearance.flush();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("original-roundness", original.roundness);
        out.putBoolean("original-fill", original.fillBackground);
        out.putInt("original-color", original.fillColor);
        out.putString("original-pack", original.pack);
        out.putInt("original-fallback-scale", original.fallbackScale);
        out.putBoolean("original-continuous", original.continuous);
        out.putInt("original-exponent", original.exponent);
        out.putInt("original-saturation", original.saturation);
        out.putInt("original-brightness", original.brightness);
        out.putInt("original-contrast", original.contrast);
        super.onSaveInstanceState(out);
    }
}
