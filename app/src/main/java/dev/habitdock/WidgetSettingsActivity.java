package dev.habitdock;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class WidgetSettingsActivity extends Activity {
    private final Map<Integer, WidgetPreferences> drafts = new HashMap<>();
    private int selectedId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner columns, rows;
    private SeekBar scale;
    private Switch more, moreTime, actions;
    private TextView scaleLabel, capacity;
    private Preview preview;
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
            if (info == null || !info.provider.equals(new ComponentName(this, HabitWidget.class))) {
                finish();
                return;
            }
        }
        if (state != null) {
            int[] saved = state.getIntArray("drafts-v5");
            if (saved != null)
                for (int i = 0; i + 6 < saved.length; i += 7)
                    drafts.put(saved[i], new WidgetPreferences(saved[i + 1], saved[i + 2], saved[i + 3],
                            saved[i + 4] != 0, saved[i + 5] != 0, saved[i + 6] != 0));
        }
        int[] active = manager.getAppWidgetIds(new ComponentName(this, HabitWidget.class));
        Arrays.sort(active);
        int[] ids = configuring ? new int[]{requested} : Arrays.copyOf(active, active.length + 1);
        if (!configuring)
            ids[active.length] = AppWidgetManager.INVALID_APPWIDGET_ID;
        selectedId = state == null ? ids[0] : state.getInt("selected", ids[0]);
        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.toolbar(this, root, "组件布局", true);
        ImageButton done = Ui.icon(this, R.drawable.ic_check, "完成", v -> save());
        done.setTag("widget-save");
        bar.addView(done);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 20));
        scroll.addView(body);
        if (!configuring) {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < active.length; i++)
                labels.add("桌面组件 " + (i + 1));
            labels.add("新组件默认布局");
            Spinner target = spinner(labels.toArray(new String[0]));
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
                        capture();
                        selectedId = ids[position];
                        load();
                    }
                }

                public void onNothingSelected(AdapterView<?> p) {
                }
            });
        }
        preview = new Preview(this);
        body.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 160)));
        capacity = Ui.text(this, "", 12, R.color.muted);
        capacity.setGravity(Gravity.CENTER);
        body.addView(capacity);
        Ui.space(body, 16);
        LinearLayout grid = new LinearLayout(this);
        LinearLayout col = Ui.column(this), row = Ui.column(this);
        col.addView(Ui.text(this, "列数", 13, R.color.muted));
        row.addView(Ui.text(this, "行数", 13, R.color.muted));
        columns = spinner(new String[]{"2 列", "3 列", "4 列", "5 列", "6 列"});
        columns.setTag("widget-columns");
        rows = spinner(new String[]{"1 行", "2 行", "3 行", "4 行", "5 行"});
        rows.setTag("widget-rows");
        col.addView(columns, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        row.addView(rows, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        grid.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        grid.addView(row, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(grid);
        Ui.space(body, 12);
        scaleLabel = Ui.text(this, "", 14, R.color.ink);
        body.addView(scaleLabel);
        scale = new SeekBar(this);
        scale.setTag("widget-scale");
        scale.setMax(100);
        scale.setMin(50);
        body.addView(scale, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
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
        body.addView(Ui.text(this, "桌面占位与内部行列独立。长按桌面组件调整占位；例如 2×2 的桌面占位，也可以选择 3 列、3 行。", 12, R.color.muted));
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                changed();
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        };
        columns.setOnItemSelectedListener(listener);
        rows.setOnItemSelectedListener(listener);
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
        scale.setOnSeekBarChangeListener(seekListener);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preview != null) {
            preview.style = IconStyle.load(this);
            preview.invalidate();
        }
    }

    private Spinner spinner(String[] labels) {
        Spinner view = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        view.setAdapter(adapter);
        return view;
    }

    private WidgetPreferences input() {
        return new WidgetPreferences(columns.getSelectedItemPosition() + 2, rows.getSelectedItemPosition() + 1,
                scale.getProgress(), more.isChecked(), actions.isChecked(), moreTime.isChecked());
    }

    private void capture() {
        if (columns != null && !binding)
            drafts.put(selectedId, input());
    }

    private void load() {
        binding = true;
        WidgetPreferences p = drafts.containsKey(selectedId)
                ? drafts.get(selectedId)
                : WidgetPreferences.load(this, selectedId);
        columns.setSelection(p.columns - 2);
        rows.setSelection(p.rows - 1);
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
        capture();
        WidgetPreferences p = input();
        scaleLabel.setText(getString(R.string.widget_scale, p.percent));
        capacity.setText(getString(R.string.widget_capacity, p.columns, p.rows, p.capacity(), p.more ? " + 更多" : ""));
        moreTime.setEnabled(p.more);
        moreTime.setAlpha(p.more ? 1f : .45f);
        preview.layout = p;
        preview.invalidate();
    }

    private void save() {
        capture();
        for (int id : getSystemService(AppWidgetManager.class)
                .getAppWidgetIds(new ComponentName(this, HabitWidget.class)))
            WidgetPreferences.ensure(this, id);
        for (Map.Entry<Integer, WidgetPreferences> draft : drafts.entrySet())
            draft.getValue().save(this, draft.getKey());
        View done = getWindow().getDecorView().findViewWithTag("widget-save");
        done.setEnabled(false);
        Context app = getApplicationContext();
        Repository.WORK.execute(() -> {
            try {
                HabitWidget.update(app, false);
                RefreshJob.schedule(app);
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        if (configuring)
                            setResult(RESULT_OK,
                                    new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, selectedId));
                        finish();
                    }
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        done.setEnabled(true);
                        Ui.toast(this, "更新失败，请重试");
                    }
                });
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        capture();
        out.putInt("selected", selectedId);
        int[] saved = new int[drafts.size() * 7];
        int i = 0;
        for (Map.Entry<Integer, WidgetPreferences> e : drafts.entrySet()) {
            saved[i++] = e.getKey();
            saved[i++] = e.getValue().columns;
            saved[i++] = e.getValue().rows;
            saved[i++] = e.getValue().percent;
            saved[i++] = e.getValue().more ? 1 : 0;
            saved[i++] = e.getValue().actions ? 1 : 0;
            saved[i++] = e.getValue().moreTime ? 1 : 0;
        }
        out.putIntArray("drafts-v5", saved);
        super.onSaveInstanceState(out);
    }
    private static final class Preview extends View {
        WidgetPreferences layout = new WidgetPreferences(5, 2, 82, true);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconStyle style;
        Preview(Context c) {
            super(c);
            style = IconStyle.load(c);
            setContentDescription("组件布局预览");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = Ui.dp(getContext(), 16), y = Ui.dp(getContext(), 12), w = getWidth() - 2 * x,
                    h = getHeight() - 2 * y;
            paint.setColor(getContext().getColor(R.color.surface));
            canvas.drawRoundRect(x, y, x + w, y + h, Ui.dp(getContext(), 16), Ui.dp(getContext(), 16), paint);
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
                    paint.setColor(style.fillBackground
                            ? style.backgroundColor(getContext())
                            : getContext().getColor(R.color.accent));
                    if (!style.fillBackground)
                        paint.setAlpha(index % 2 == 0 ? 210 : 135);
                    canvas.drawRoundRect(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2,
                            side * style.roundness / 200f, side * style.roundness / 200f, paint);
                    paint.setColor(getContext().getColor(style.fillBackground ? R.color.accent : R.color.surface));
                    canvas.drawCircle(cx, cy, side * .18f, paint);
                }
            }
        }
    }
}
