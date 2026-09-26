package dev.habitdock;

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

public final class WidgetSettingsActivity extends Activity {
    private final Map<Integer, WidgetPreferences> originals = new HashMap<>();
    private int selectedId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner target;
    private int[] targetIds;
    private TextView nameValue;
    private LinearLayout nameRow;
    private SeekBar columns, rows, scale;
    private Switch more, moreTime, actions;
    private TextView columnValue, rowValue, scaleLabel, capacity;
    private Preview preview;
    private ImageButton restore;
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
        if (state != null) {
            int[] saved = state.getIntArray("original-layouts");
            if (saved != null)
                for (int i = 0; i + 6 < saved.length; i += 7)
                    originals.put(saved[i], new WidgetPreferences(saved[i + 1], saved[i + 2], saved[i + 3],
                            saved[i + 4] != 0, saved[i + 5] != 0, saved[i + 6] != 0));
        }
        int[] active = HabitWidget.ids(this);
        Arrays.sort(active);
        int[] ids = configuring ? new int[]{requested} : Arrays.copyOf(active, active.length + 2);
        if (!configuring) {
            ids[active.length] = AppWidgetManager.INVALID_APPWIDGET_ID;
            ids[active.length + 1] = WidgetPreferences.COMPACT_DEFAULT;
        }
        targetIds = ids;
        // Freeze existing widgets before changing defaults for future widgets.
        for (int id : active)
            WidgetPreferences.ensure(this, id);
        for (int id : ids)
            originals.putIfAbsent(id, WidgetPreferences.load(this, id));
        selectedId = state == null ? ids[0] : state.getInt("selected", ids[0]);
        boolean validSelection = false;
        for (int id : ids)
            validSelection |= id == selectedId;
        if (!validSelection)
            selectedId = ids[0];
        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.toolbar(this, root, "组件布局", true);
        restore = Ui.icon(this, R.drawable.ic_undo, "撤销更改",
                v -> StyleRestore.show(this, "恢复当前组件布局", () -> bind(originals.get(selectedId)),
                        () -> bind(WidgetPreferences.defaults(this, selectedId))));
        restore.setTag("widget-save");
        bar.addView(restore);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 20));
        scroll.addView(body);
        if (!configuring) {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < active.length; i++)
                labels.add(WidgetNames.label(this, active[i]));
            labels.add(WidgetNames.label(this, AppWidgetManager.INVALID_APPWIDGET_ID));
            labels.add(WidgetNames.label(this, WidgetPreferences.COMPACT_DEFAULT));
            target = spinner(labels.toArray(new String[0]));
            target.setTag("widget-target");
            body.addView(target, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
            int index = 0;
            for (int i = 0; i < ids.length; i++)
                if (ids[i] == selectedId)
                    index = i;
            target.setSelection(index);
            target.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                    if (columns != null) {
                        selectedId = ids[position];
                        load();
                    }
                }

                public void onNothingSelected(AdapterView<?> p) {
                }
            });
        }
        nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.setMinimumHeight(Ui.dp(this, 52));
        nameRow.setTag("widget-name");
        Ui.nativeTouch(nameRow);
        LinearLayout nameCopy = Ui.column(this);
        nameCopy.addView(Ui.text(this, "组件名称", 13, R.color.muted));
        nameValue = Ui.text(this, "", 16, R.color.ink);
        nameValue.setSingleLine(true);
        nameValue.setEllipsize(TextUtils.TruncateAt.END);
        nameValue.setPadding(0, Ui.dp(this, 4), 0, 0);
        nameCopy.addView(nameValue);
        nameRow.addView(nameCopy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView edit = new ImageView(this);
        edit.setImageResource(R.drawable.ic_edit);
        edit.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.muted)));
        nameRow.addView(edit, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        nameRow.setOnClickListener(v -> rename());
        body.addView(nameRow);
        preview = new Preview(this);
        body.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        capacity = Ui.text(this, "", 12, R.color.muted);
        capacity.setGravity(Gravity.CENTER);
        body.addView(capacity);
        body.addView(Ui.text(this, "调整立即生效，返回即可。撤销仅恢复当前选中的布局。", 12, R.color.muted));
        Ui.space(body, 16);
        columnValue = Ui.text(this, "", 14, R.color.muted);
        rowValue = Ui.text(this, "", 14, R.color.muted);
        scaleLabel = Ui.text(this, "", 14, R.color.muted);
        columns = slider(body, "应用列数", "widget-columns", 2, 6, columnValue);
        rows = slider(body, "应用行数", "widget-rows", 1, 5, rowValue);
        scale = slider(body, "图标大小", "widget-scale", 50, 100, scaleLabel);
        body.addView(Ui.text(this, "按格子空间缩放，拖大或缩小组件时自动适配。", 12, R.color.muted));
        Ui.space(body, 12);
        more = new Switch(this);
        more.setTag("widget-more");
        more.setText("末格显示更多");
        more.setTextSize(14);
        more.setTextColor(getColor(R.color.ink));
        body.addView(more, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        Ui.space(body, 12);
        moreTime = new Switch(this);
        moreTime.setTag("widget-more-time");
        moreTime.setText("更多下方显示更新时间");
        moreTime.setTextSize(14);
        moreTime.setTextColor(getColor(R.color.ink));
        body.addView(moreTime, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        Ui.space(body, 12);
        actions = new Switch(this);
        actions.setTag("widget-actions");
        actions.setText("点按图标显示操作菜单");
        actions.setTextSize(14);
        actions.setTextColor(getColor(R.color.ink));
        body.addView(actions, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        body.addView(Ui.text(this, "可固定、不推荐、查看应用信息、分享安装包。关闭时直接打开应用。", 12, R.color.muted));
        Ui.space(body, 12);
        body.addView(Ui.text(this, "桌面占位与内部行列独立。添加时先选择桌面占格；支持缩放的桌面还可长按调整占位。这里的行列数只调整组件内部。", 12, R.color.muted));
        more.setOnCheckedChangeListener((v, checked) -> changed());
        moreTime.setOnCheckedChangeListener((v, checked) -> changed());
        actions.setOnCheckedChangeListener((v, checked) -> changed());
        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean user) {
                changed();
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        };
        columns.setOnSeekBarChangeListener(seekListener);
        rows.setOnSeekBarChangeListener(seekListener);
        scale.setOnSeekBarChangeListener(seekListener);
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
        if (preview != null) {
            preview.setStyle(IconStyle.load(this));
            preview.setWidget(selectedId);
        }
    }

    private SeekBar slider(LinearLayout body, String label, String tag, int min, int max, TextView value) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(Ui.text(this, label, 14, R.color.ink), new LinearLayout.LayoutParams(Ui.dp(this, 72), -2));
        SeekBar slider = new SeekBar(this);
        slider.setTag(tag);
        slider.setContentDescription(label);
        slider.setMax(max);
        slider.setMin(min);
        line.addView(slider, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        value.setGravity(Gravity.END);
        value.setTag(tag + "-value");
        line.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 44), -2));
        body.addView(line);
        return slider;
    }

    private Spinner spinner(String[] labels) {
        Spinner view = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new ArrayList<>(Arrays.asList(labels)));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        view.setAdapter(adapter);
        return view;
    }

    private WidgetPreferences input() {
        return new WidgetPreferences(columns.getProgress(), rows.getProgress(),
                scale.getProgress(), more.isChecked(), actions.isChecked(), moreTime.isChecked());
    }

    private void load() {
        nameRow.setVisibility(
                selectedId <= 0 ? View.GONE : View.VISIBLE);
        updateName();
        preview.setWidget(selectedId);
        bind(WidgetPreferences.load(this, selectedId));
    }

    private void updateName() {
        nameValue.setText(WidgetNames.label(this, selectedId));
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
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("组件名称").setView(content)
                .setNegativeButton("取消", null).setPositiveButton("保存", (d, which) -> {
                    WidgetNames.save(this, id, input.getText().toString());
                    updateName();
                    if (target != null) {
                        @SuppressWarnings("unchecked")
                        ArrayAdapter<String> adapter = (ArrayAdapter<String>) target.getAdapter();
                        adapter.setNotifyOnChange(false);
                        adapter.clear();
                        for (int targetId : targetIds)
                            adapter.add(WidgetNames.label(this, targetId));
                        adapter.notifyDataSetChanged();
                        for (int i = 0; i < targetIds.length; i++)
                            if (targetIds[i] == id)
                                target.setSelection(i);
                    }
                }).create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void bind(WidgetPreferences p) {
        binding = true;
        columns.setProgress(p.columns);
        rows.setProgress(p.rows);
        scale.setProgress(p.percent);
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
        StyleRestore.update(restore, !p.sameAs(originals.get(selectedId)));
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
        android.content.res.Configuration config = new android.content.res.Configuration(
                context.getResources().getConfiguration());
        config.densityDpi = 160;
        Preview preview = new Preview(context.createConfigurationContext(config));
        preview.layout = layout;
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

    private static final class Preview extends View {
        WidgetPreferences layout = new WidgetPreferences(5, 2, 82, true);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Bitmap[] sources = new Bitmap[3], icons = new Bitmap[3];
        final RectF destination = new RectF();
        float desktopWidth = 320, desktopHeight = 160;
        boolean desktopSizing;

        void setWidget(int id) {
            AppWidgetManager manager = AppWidgetManager.getInstance(getContext());
            AppWidgetProviderInfo info = id > 0 ? manager.getAppWidgetInfo(id) : null;
            boolean compact = id == WidgetPreferences.COMPACT_DEFAULT
                    || (info != null && info.provider.equals(HabitWidget.provider(getContext(), true)));
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

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            if (!desktopSizing) {
                super.onMeasure(widthSpec, heightSpec);
                return;
            }
            int available = MeasureSpec.getSize(widthSpec);
            float width = Math.min(available, Ui.dp(getContext(), desktopWidth));
            int height = Math.round(width * desktopHeight / desktopWidth);
            setMeasuredDimension(available, resolveSize(height, heightSpec));
        }

        Preview(Context c) {
            super(c);
            setContentDescription("组件布局预览");
            setTag("widget-preview");
            int[] colors = {0xff3a8872, 0xff5681c7, 0xffcf8653};
            for (int i = 0; i < sources.length; i++) {
                sources[i] = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(sources[i]);
                paint.setColor(colors[i]);
                canvas.drawCircle(64, 64, 54, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(64, 64, 18, paint);
            }
            setStyle(IconStyle.load(c));
        }

        void setStyle(IconStyle style) {
            for (int i = 0; i < sources.length; i++)
                icons[i] = WidgetIcons.style(getContext(), sources[i], style);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = desktopSizing ? Math.min(getWidth(), Ui.dp(getContext(), desktopWidth)) : getWidth();
            float h = getHeight(), x = (getWidth() - w) / 2f, y = 0;
            float factor = desktopSizing ? w / Ui.dp(getContext(), desktopWidth) : 1;
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(factor, factor);
            x = 0;
            y = 0;
            w /= factor;
            h /= factor;
            paint.setColor(getContext().getColor(R.color.surface));
            canvas.drawRoundRect(x, y, x + w, y + h, Ui.dp(getContext(), 24), Ui.dp(getContext(), 24), paint);
            x += Ui.dp(getContext(), 8);
            y += Ui.dp(getContext(), 8);
            w -= 2 * x;
            h -= 2 * y;
            float cw = w / layout.columns, ch = h / layout.rows, side = Math.min(cw, ch) * layout.percent / 100f;
            for (int index = 0; index < layout.rows * layout.columns; index++) {
                float cx = x + cw * (index % layout.columns + .5f), cy = y + ch * (index / layout.columns + .5f);
                paint.setColor(getContext().getColor(R.color.accent));
                if (layout.more && index == layout.rows * layout.columns - 1) {
                    float moreSide = Math.min(Ui.dp(getContext(), 28), side * .5f);
                    for (int dot = -1; dot <= 1; dot++)
                        canvas.drawCircle(cx + dot * moreSide / 3, cy, moreSide / 16, paint);
                    if (layout.moreTime && ch >= moreSide + 2 * Ui.dp(getContext(), 18)) {
                        paint.setTextSize(Ui.dp(getContext(), 9));
                        paint.setTextAlign(Paint.Align.CENTER);
                        paint.setAlpha(89);
                        canvas.drawText("12:34", cx, cy + moreSide / 2 + Ui.dp(getContext(), 9), paint);
                        paint.setAlpha(255);
                    }
                } else {
                    destination.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
                    canvas.drawBitmap(icons[index % icons.length], null, destination, paint);
                }
            }
            canvas.restore();
        }
    }
}
