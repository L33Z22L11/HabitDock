package dev.habitdock;
import com.google.android.material.slider.Slider;
import androidx.appcompat.app.AlertDialog;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class WidgetSettingsActivity extends ThemedActivity {
    @Override
    boolean nativeWallpaperPreview() {
        return true;
    }

    private final Map<Integer, WidgetPreferences> originals = new HashMap<>();
    private final Map<Integer, WidgetTheme> originalThemes = new HashMap<>();
    private WidgetThemeControls themeControls;
    private Button addButton;
    private LinearLayout layoutControls;
    private int selectedId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner target;
    private int[] targetIds, bindingsAtOpen;
    private TextView nameValue;
    private Button renameButton;
    private int[] activeAtOpen;
    private static final int DEFAULT_HEADER = -10, ACTIVE_HEADER = -11, INACTIVE_HEADER = -12;
    private Slider columns, rows, scale;
    private com.google.android.material.materialswitch.MaterialSwitch more, moreTime, actions;
    private TextView columnValue, rowValue, scaleLabel, capacity;
    private Preview preview;
    private Button restore;
    private boolean binding, configuring;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        configuring = getIntent().hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID);
        int requested = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        if (configuring) {
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(requested);
            if (info == null || !HabitWidget.owns(this, info.provider)) {
                finish();
                return;
            }
        }
        if (configuring) {
            WidgetInstances.confirm(this, requested);
            WidgetInstances.rememberHost(this, requested, getCallingPackage());
        }
        if (state != null) {
            int[] themes = state.getIntArray("original-themes");
            if (themes != null)
                for (int i = 0; i + 3 < themes.length; i += 4)
                    originalThemes.put(themes[i], new WidgetTheme(themes[i + 1], themes[i + 2], themes[i + 3]));
            int[] saved = state.getIntArray("original-layouts");
            if (saved != null)
                for (int i = 0; i + 6 < saved.length; i += 7)
                    originals.put(saved[i], new WidgetPreferences(saved[i + 1], saved[i + 2], saved[i + 3],
                            saved[i + 4] != 0, saved[i + 5] != 0, saved[i + 6] != 0));
        }
        bindingsAtOpen = HabitWidget.boundIds(this);
        int[] active = HabitWidget.ids(this);
        Arrays.sort(active);
        activeAtOpen = active;
        List<Integer> entries = new ArrayList<>();
        if (configuring)
            entries.add(requested);
        else {
            entries.add(DEFAULT_HEADER);
            entries.add(AppWidgetManager.INVALID_APPWIDGET_ID);
            entries.add(WidgetPreferences.COMPACT_DEFAULT);
            if (active.length > 0) {
                entries.add(ACTIVE_HEADER);
                for (int id : active)
                    entries.add(id);
            }
            boolean header = false;
            for (int id : bindingsAtOpen) {
                if (WidgetInstances.active(this, id))
                    continue;
                if (!header) {
                    entries.add(INACTIVE_HEADER);
                    header = true;
                }
                entries.add(id);
            }
        }
        int[] ids = entries.stream().mapToInt(Integer::intValue).toArray();
        targetIds = ids;
        // Freeze existing widgets before changing defaults for future widgets.
        for (int id : active)
            WidgetPreferences.ensure(this, id);
        for (int id : ids)
            if (id > DEFAULT_HEADER) {
                originals.putIfAbsent(id, WidgetPreferences.load(this, id));
                originalThemes.putIfAbsent(id, WidgetTheme.load(this, id));
            }
        int initial = configuring
                ? requested
                : getIntent().hasExtra("default-columns")
                        ? WidgetPreferences.defaultId(getIntent().getIntExtra("default-columns", 4))
                        : active.length > 0 ? active[0] : 0;
        selectedId = state == null ? initial : state.getInt("selected", initial);
        boolean validSelection = false;
        for (int id : ids)
            validSelection |= id == selectedId;
        if (!validSelection)
            selectedId = initial;
        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.toolbar(this, root, "组件样式", true);
        bar.setBackgroundColor(Ui.pageSurface(this));
        restore = StyleRestore.button(this,
                () -> StyleRestore.show(this, "恢复当前组件外观",
                        () -> restoreAppearance(originals.get(selectedId), originalThemes.get(selectedId)),
                        () -> restoreAppearance(WidgetPreferences.defaults(this, selectedId), WidgetTheme.defaults())));
        restore.setTag("widget-save");
        bar.addView(restore);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = Ui.column(this);
        scroll.addView(body);
        LinearLayout selector = new LinearLayout(this);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        selector.setBackgroundColor(Ui.pageSurface(this));
        body.addView(selector, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
        if (!configuring) {
            target = spinner();
            target.setTag("widget-target");
            selector.addView(target, new LinearLayout.LayoutParams(0, -1, 1));
            for (int i = 0; i < ids.length; i++)
                if (ids[i] == selectedId)
                    target.setSelection(i);
            target.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                    if (columns != null && ids[position] > DEFAULT_HEADER) {
                        selectedId = ids[position];
                        load();
                    }
                }

                public void onNothingSelected(AdapterView<?> p) {
                }
            });
        } else {
            nameValue = Ui.text(this, "", 16, R.color.ink);
            selector.addView(nameValue, new LinearLayout.LayoutParams(0, -2, 1));
        }
        renameButton = Ui.textButton(this);
        renameButton.setText("重命名");
        renameButton.setTextColor(Ui.color(this, R.color.accent));
        renameButton.setTag("widget-name");
        renameButton.setOnClickListener(v -> rename());
        selector.addView(renameButton, new LinearLayout.LayoutParams(-2, -1));
        FrameLayout hero = new FrameLayout(this);
        hero.setTag("wallpaper-preview-stage");
        hero.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16));
        body.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        preview = new Preview(this);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        hero.addView(preview, previewParams);
        LinearLayout controls = Ui.column(this);
        controls.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 20));
        controls.setBackgroundColor(Ui.pageSurface(this));
        body.addView(controls, new LinearLayout.LayoutParams(-1, -2, 1));
        themeControls = new WidgetThemeControls(this, this::applyTheme);
        controls.addView(themeControls, new LinearLayout.LayoutParams(-1, -2));
        Ui.space(controls, 16);
        LinearLayout layoutCard = Ui.column(this);
        if (!MainStyle.load(this).hasBackground())
            layoutCard.setBackground(Ui.shape(this, R.color.surface, 24));
        layoutCard.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        controls.addView(layoutCard, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout expand = new LinearLayout(this);
        expand.setTag("widget-layout-toggle");
        expand.setGravity(Gravity.CENTER_VERTICAL);
        expand.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        Ui.nativeTouch(expand);
        LinearLayout copy = Ui.column(this);
        copy.addView(Ui.text(this, "排列与入口", 16, R.color.ink));
        capacity = Ui.text(this, "", 12, R.color.muted);
        capacity.setPadding(0, Ui.dp(this, 6), 0, 0);
        copy.addView(capacity);
        expand.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.muted)));
        expand.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        layoutCard.addView(expand);
        layoutControls = Ui.column(this);
        layoutControls.setVisibility(state != null && state.getBoolean("layout-expanded") ? View.VISIBLE : View.GONE);
        chevron.setRotation(layoutControls.getVisibility() == View.VISIBLE ? 90 : 0);
        expand.setOnClickListener(v -> {
            boolean expanded = layoutControls.getVisibility() != View.VISIBLE;
            layoutControls.setVisibility(expanded ? View.VISIBLE : View.GONE);
            chevron.setRotation(expanded ? 90 : 0);
            expand.setContentDescription("排列与入口，" + (expanded ? "已展开" : "已收起"));
        });
        layoutCard.addView(layoutControls);
        body = layoutControls;
        columnValue = Ui.text(this, "", 14, R.color.muted);
        rowValue = Ui.text(this, "", 14, R.color.muted);
        scaleLabel = Ui.text(this, "", 14, R.color.muted);
        columns = slider(body, "应用列数", "widget-columns", 2, 6, columnValue);
        rows = slider(body, "应用行数", "widget-rows", 1, 5, rowValue);
        scale = slider(body, "图标大小", "widget-scale", 50, 100, scaleLabel);
        Ui.space(body, 12);
        more = new com.google.android.material.materialswitch.MaterialSwitch(this);
        more.setTag("widget-more");
        more.setText("显示更多入口");
        more.setTextSize(14);
        more.setTextColor(Ui.color(this, R.color.ink));
        body.addView(more, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        Ui.space(body, 12);
        moreTime = new com.google.android.material.materialswitch.MaterialSwitch(this);
        moreTime.setTag("widget-more-time");
        moreTime.setText("显示更新时间");
        moreTime.setTextSize(14);
        moreTime.setTextColor(Ui.color(this, R.color.ink));
        body.addView(moreTime, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        Ui.space(body, 12);
        actions = new com.google.android.material.materialswitch.MaterialSwitch(this);
        actions.setTag("widget-actions");
        actions.setText("点按图标显示操作菜单");
        actions.setTextSize(14);
        actions.setTextColor(Ui.color(this, R.color.ink));
        body.addView(actions, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        Ui.space(body, 12);
        more.setOnCheckedChangeListener((v, checked) -> changed());
        moreTime.setOnCheckedChangeListener((v, checked) -> changed());
        actions.setOnCheckedChangeListener((v, checked) -> changed());
        Slider.OnChangeListener seekListener = (slider, value, user) -> changed();
        columns.addOnChangeListener(seekListener);
        rows.addOnChangeListener(seekListener);
        scale.addOnChangeListener(seekListener);
        if (!configuring) {
            LinearLayout footer = Ui.column(this);
            footer.setBackgroundColor(Ui.pageSurface(this));
            footer.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 12));
            addButton = new com.google.android.material.button.MaterialButton(this);
            addButton.setTag("widget-add");
            addButton.setOnClickListener(v -> addSelectedWidget());
            footer.addView(addButton, new LinearLayout.LayoutParams(-1, Ui.dp(this, 56)));
            root.addView(footer);
        }
        load();
        if (configuring) {
            // No separate save step: returning also completes launcher configuration.
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, selectedId));
            WidgetAppearance.request(this);
            RefreshJob.schedule(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!configuring && targetIds != null
                && (!Arrays.equals(bindingsAtOpen, HabitWidget.boundIds(this))
                        || !Arrays.equals(activeAtOpen, HabitWidget.ids(this)))) {
            recreate();
            return;
        }
        if (preview != null) {
            preview.setStyle(IconStyle.load(this));
            preview.setWidget(selectedId);
        }
    }

    private Slider slider(LinearLayout body, String label, String tag, int min, int max, TextView value) {
        Slider slider = new Slider(this);
        slider.setTag(tag);
        slider.setContentDescription(label);
        slider.setValueTo(max);
        slider.setValueFrom(min);
        slider.setValue(min);
        slider.setStepSize(1);
        slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
        value.setTag(tag + "-value");
        Ui.sliderRow(body, label, slider, value, () -> {
            WidgetPreferences defaults = WidgetPreferences.defaults(this, selectedId);
            return tag.equals("widget-columns")
                    ? defaults.columns
                    : tag.equals("widget-rows") ? defaults.rows : defaults.percent;
        });
        return slider;
    }

    private Spinner spinner() {
        Spinner view = new Spinner(this, null, 0, Spinner.MODE_DROPDOWN);
        Ui.nativeTouch(view);
        view.setPopupBackgroundDrawable(Ui.panel(this, 16));
        BaseAdapter adapter = new BaseAdapter() {
            public int getCount() {
                return targetIds.length;
            }

            public Object getItem(int position) {
                return targetLabel(targetIds[position]);
            }

            public long getItemId(int position) {
                return targetIds[position];
            }

            public boolean areAllItemsEnabled() {
                return false;
            }

            public boolean isEnabled(int position) {
                return targetIds[position] > DEFAULT_HEADER;
            }

            public View getView(int position, View convertView, ViewGroup parent) {
                return targetRow(position, false);
            }

            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return targetRow(position, true);
            }
        };
        view.setAdapter(adapter);
        return view;
    }

    private String targetLabel(int id) {
        if (id == DEFAULT_HEADER)
            return "默认布局";
        if (id == ACTIVE_HEADER)
            return "已启用";
        if (id == INACTIVE_HEADER)
            return "未完成添加或已隐藏";
        return WidgetNames.label(this, id);
    }

    private View targetRow(int position, boolean dropdown) {
        int id = targetIds[position];
        if (id <= DEFAULT_HEADER) {
            TextView header = Ui.text(this, targetLabel(id), 12, R.color.muted);
            header.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 6));
            return header;
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 12), 0);
        row.setMinimumHeight(Ui.dp(this, 48));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(id > 0
                ? WidgetInstances.hostIcon(this, id)
                : androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_add_widget));
        icon.setContentDescription(id > 0 ? WidgetInstances.hostLabel(this, id) : "默认布局");
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView text = Ui.text(this, WidgetNames.label(this, id), 16, R.color.ink);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setPadding(Ui.dp(this, 10), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (dropdown && id == selectedId)
            row.setBackgroundColor(Ui.selectionColor(this));
        if (!dropdown) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron);
            arrow.setRotation(90);
            arrow.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.muted)));
            row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        }
        return row;
    }

    private void hideSelected() {
        if (selectedId <= 0)
            return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("隐藏此组件？")
                .setMessage("从布局列表和推荐分配中隐藏，可在未完成添加或已隐藏中恢复。不会删除宿主中的绑定。")
                .setNegativeButton("取消", null).setPositiveButton("隐藏", (d, which) -> {
                    WidgetInstances.setHidden(this, selectedId, true);
                    if (configuring)
                        finish();
                    else
                        recreate();
                }).show();
    }

    private WidgetPreferences input() {
        return new WidgetPreferences((int) columns.getValue(), (int) rows.getValue(),
                (int) scale.getValue(), more.isChecked(), actions.isChecked(), moreTime.isChecked());
    }

    private void load() {
        renameButton.setVisibility(
                selectedId <= 0 ? View.GONE : View.VISIBLE);
        updateName();
        preview.setWidget(selectedId);
        themeControls.bind(WidgetTheme.load(this, selectedId));
        if (addButton != null)
            addButton.setText(WidgetPreferences.templateId(this, selectedId) == WidgetPreferences.COMPACT_DEFAULT
                    ? "添加 2×2 组件"
                    : "添加 4×2 组件");
        bind(WidgetPreferences.load(this, selectedId));
    }

    private void updateName() {
        if (nameValue != null)
            nameValue.setText(WidgetNames.label(this, selectedId));
        if (target != null)
            ((BaseAdapter) target.getAdapter()).notifyDataSetChanged();
    }

    private void rename() {
        int id = selectedId;
        LinearLayout content = Ui.column(this);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        input.setText(WidgetNames.get(this, id));
        input.setHint("留空使用默认名称");
        input.setContentDescription("组件名称输入");
        input.selectAll();
        content.addView(input, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        content.addView(Ui.text(this, "用于设置中区分各个组件。桌面自带的下方标题由桌面管理。", 12, R.color.muted));
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("组件名称")
                .setView(content)
                .setNeutralButton(WidgetInstances.active(this, id) ? "隐藏组件" : "启用组件", (d, which) -> {
                    if (WidgetInstances.active(this, id))
                        hideSelected();
                    else {
                        WidgetInstances.setHidden(this, id, false);
                        recreate();
                    }
                })
                .setNegativeButton("取消", null).setPositiveButton("保存", (d, which) -> {
                    WidgetNames.save(this, id, input.getText().toString());
                    updateName();

                }).create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void applyTheme(WidgetTheme theme) {
        Repository.prefs(this).edit().putInt("widget.preview_id", selectedId).apply();
        if (!theme.sameAs(WidgetTheme.load(this, selectedId))) {
            theme.save(this, selectedId);
            WidgetAppearance.request(this);
        }
        preview.theme = theme;
        changed();
    }

    private void restoreAppearance(WidgetPreferences layout, WidgetTheme theme) {
        themeControls.bind(theme);
        applyTheme(theme);
        bind(layout);
    }

    private void addSelectedWidget() {
        int columns = WidgetPreferences.templateId(this, selectedId) == WidgetPreferences.COMPACT_DEFAULT ? 2 : 4;
        try {
            if (WidgetPinRequest.request(this, columns, 2, input(), "", WidgetTheme.load(this, selectedId)))
                return;
        } catch (RuntimeException error) {
            Ui.toast(this, "暂时无法添加组件，请重试");
            return;
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("请从桌面添加")
                .setMessage("长按桌面空白处，在小组件列表选择知时。")
                .setPositiveButton("知道了", null).show();
    }

    private void bind(WidgetPreferences p) {
        binding = true;
        columns.setValue(p.columns);
        rows.setValue(p.rows);
        scale.setValue(p.percent);
        more.setChecked(p.more);
        moreTime.setChecked(p.moreTime);
        actions.setChecked(p.actions);
        binding = false;
        changed();
    }

    private void changed() {
        if (binding || actions == null)
            return;
        WidgetPreferences p = input();
        if (!p.sameAs(WidgetPreferences.load(this, selectedId))) {
            p.save(this, selectedId);
            WidgetAppearance.request(this);
        }
        StyleRestore.update(restore, !p.sameAs(originals.get(selectedId))
                || !WidgetTheme.load(this, selectedId).sameAs(originalThemes.get(selectedId)));
        columnValue.setText(String.valueOf(p.columns));
        rowValue.setText(String.valueOf(p.rows));
        scaleLabel.setText(getString(R.string.widget_scale, p.percent));
        capacity.setText(getString(R.string.widget_capacity, p.columns, p.rows, p.capacity(), p.more ? " + 更多" : ""));
        moreTime.setEnabled(p.more);
        moreTime.setAlpha(p.more ? 1f : .45f);
        preview.layout = p;
        preview.invalidate();
    }

    @Override
    protected void onStop() {
        WidgetAppearance.flush();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("selected", selectedId);
        out.putBoolean("layout-expanded", layoutControls.getVisibility() == View.VISIBLE);
        int[] themes = new int[originalThemes.size() * 4];
        int themeIndex = 0;
        for (Map.Entry<Integer, WidgetTheme> entry : originalThemes.entrySet()) {
            themes[themeIndex++] = entry.getKey();
            themes[themeIndex++] = entry.getValue().mode;
            themes[themeIndex++] = entry.getValue().transparency;
            themes[themeIndex++] = entry.getValue().color;
        }
        out.putIntArray("original-themes", themes);
        int[] saved = new int[originals.size() * 7];
        int i = 0;
        for (Map.Entry<Integer, WidgetPreferences> e : originals.entrySet()) {
            saved[i++] = e.getKey();
            saved[i++] = e.getValue().columns;
            saved[i++] = e.getValue().rows;
            saved[i++] = e.getValue().percent;
            saved[i++] = e.getValue().more ? 1 : 0;
            saved[i++] = e.getValue().actions ? 1 : 0;
            saved[i++] = e.getValue().moreTime ? 1 : 0;
        }
        out.putIntArray("original-layouts", saved);
        super.onSaveInstanceState(out);
    }

    static RemoteViews pinPreview(Context context, WidgetPreferences layout, int columns, int rows) {
        return pinPreview(context, layout, columns, rows,
                WidgetTheme.load(context, WidgetPreferences.defaultId(columns)));
    }

    static RemoteViews pinPreview(Context context, WidgetPreferences layout, int columns, int rows, WidgetTheme theme) {
        android.content.res.Configuration config = new android.content.res.Configuration(
                context.getResources().getConfiguration());
        config.densityDpi = 160;
        Preview preview = new Preview(context.createConfigurationContext(config), false);
        preview.data = PreviewContent.load(context, IconStyle.load(context).pack, 0, 30, false);
        preview.renderIcons();
        preview.layout = layout;
        preview.theme = theme;
        int width = 320, height = width * rows / columns;
        preview.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        preview.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        preview.draw(new Canvas(bitmap));
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_preview);
        views.setImageViewBitmap(R.id.widget_preview, bitmap);
        return views;
    }

    static final class Preview extends View {
        WidgetPreferences layout = new WidgetPreferences(5, 2, 82, true);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        PreviewContent data = new PreviewContent();
        final List<Bitmap> icons = new ArrayList<>();
        IconStyle style;
        WidgetTheme theme;
        String loadedKey = "";
        int widgetId, generation, sampleOffset;
        boolean fillSamples;
        final boolean asynchronous;
        final RectF destination = new RectF();
        float desktopWidth = 320, desktopHeight = 160;
        boolean desktopSizing, widePreview;

        void setSamples(int offset) {
            sampleOffset = Math.max(0, offset);
            fillSamples = true;
            setStyle(IconStyle.load(getContext()));
        }

        void setWidget(int id) {
            widgetId = id;
            theme = WidgetTheme.load(getContext(), id);
            setStyle(IconStyle.load(getContext()));
            AppWidgetManager manager = AppWidgetManager.getInstance(getContext());
            AppWidgetProviderInfo info = id > 0 ? manager.getAppWidgetInfo(id) : null;
            boolean compact = id == WidgetPreferences.COMPACT_DEFAULT
                    || (info != null && info.provider.equals(HabitWidget.provider(getContext(), true)));
            widePreview = !compact;
            desktopWidth = compact ? 160 : 320;
            desktopHeight = 160;
            // Defaults use the most recently added matching instance as the host's size
            // hint.
            int reference = id;
            if (id <= 0) {
                int[] active = manager.getAppWidgetIds(HabitWidget.provider(getContext(), compact));
                for (int candidate : active)
                    reference = Math.max(reference, candidate);
            }
            if (reference > 0) {
                Bundle options = manager.getAppWidgetOptions(reference);
                desktopWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (int) desktopWidth);
                desktopHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (int) desktopHeight);
            }
            desktopWidth = Math.max(1, desktopWidth);
            desktopHeight = Math.max(1, desktopHeight);
            desktopSizing = true;
            requestLayout();
            invalidate();
        }

        private float previewWidth(int available) {
            return desktopSizing
                    ? WidgetSizing.previewWidth(available, getResources().getDisplayMetrics().density, widePreview)
                    : available;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            if (!desktopSizing) {
                super.onMeasure(widthSpec, heightSpec);
                return;
            }
            int available = MeasureSpec.getSize(widthSpec);
            float width = previewWidth(available);
            int height = Math.round(width * desktopHeight / desktopWidth);
            setMeasuredDimension(available, resolveSize(height, heightSpec));
        }

        Preview(Context c) {
            this(c, true);
        }

        Preview(Context c, boolean asynchronous) {
            super(c);
            this.asynchronous = asynchronous;
            theme = WidgetTheme.load(c, 0);
            setContentDescription("组件布局预览");
            setTag("widget-preview");
            setStyle(IconStyle.load(c));
        }

        void setStyle(IconStyle style) {
            this.style = style;
            renderIcons();
            if (!asynchronous)
                return;
            String key = style.pack + ":" + widgetId + ":" + RefreshPolicy.last(getContext())
                    + ":" + fillSamples + ":" + sampleOffset;
            if (key.equals(loadedKey))
                return;
            loadedKey = key;
            int request = ++generation, id = widgetId, offset = sampleOffset;
            boolean fallback = fillSamples;
            int limit = fallback ? layout.capacity() : 30;
            Context context = getContext().getApplicationContext().createConfigurationContext(
                    new android.content.res.Configuration(getResources().getConfiguration()));
            Repository.WORK.execute(() -> {
                PreviewContent content = PreviewContent.load(context, style.pack, id, limit, fallback, offset);
                post(() -> {
                    if (generation != request)
                        return;
                    data = content;
                    setContentDescription("组件布局预览：" + String.join("、", data.labels)
                            + (data.time().isEmpty() ? "" : "，更新于 " + data.time()));
                    renderIcons();
                });
            });
        }

        void renderIcons() {
            icons.clear();
            for (int i = 0; i < data.sources.size(); i++)
                icons.add(WidgetIcons.style(getContext(), data.sources.get(i), style,
                        i < data.themed.size() && data.themed.get(i)));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = previewWidth(getWidth());
            if (w <= 0 || getHeight() <= 0)
                return;
            float h = getHeight(), x = (getWidth() - w) / 2f, y = 0;
            float factor = desktopSizing ? w / Ui.dp(getContext(), desktopWidth) : 1;
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(factor, factor);
            x = 0;
            y = 0;
            w /= factor;
            h /= factor;
            paint.setColor(theme.background(getContext()));
            canvas.drawRoundRect(x, y, x + w, y + h, Ui.dp(getContext(), 24), Ui.dp(getContext(), 24), paint);
            x += Ui.dp(getContext(), 8);
            y += Ui.dp(getContext(), 8);
            w -= 2 * x;
            h -= 2 * y;
            float cw = w / layout.columns, ch = h / layout.rows, side = Math.min(cw, ch) * layout.percent / 100f;
            for (int index = 0; index < layout.rows * layout.columns; index++) {
                float cx = x + cw * (index % layout.columns + .5f), cy = y + ch * (index / layout.columns + .5f);
                paint.setColor(theme.foreground(getContext()));
                if (layout.more && index == layout.rows * layout.columns - 1) {
                    float moreSide = Math.min(Ui.dp(getContext(), 28), side * .5f);
                    for (int dot = -1; dot <= 1; dot++)
                        canvas.drawCircle(cx + dot * moreSide / 3, cy, moreSide / 16, paint);
                    if (layout.moreTime && ch >= moreSide + 2 * Ui.dp(getContext(), 18)) {
                        paint.setTextSize(Ui.dp(getContext(), 9));
                        paint.setTextAlign(Paint.Align.CENTER);
                        paint.setAlpha(89);
                        canvas.drawText(data.time(), cx, cy + moreSide / 2 + Ui.dp(getContext(), 9), paint);
                        paint.setAlpha(255);
                    }
                } else if (index < icons.size()) {
                    destination.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
                    canvas.drawBitmap(icons.get(index), null, destination, paint);
                }
            }
            canvas.restore();
        }
    }
}
