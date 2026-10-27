package dev.habitdock;

import android.content.Context;
import android.icu.text.AlphabeticIndex;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Searchable app grid with a drag index; indexing dims rather than filters
 * apps.
 */
@android.annotation.SuppressLint("ViewConstructor")
final class GlassCatalog extends LinearLayout {
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private final AlphabeticIndex.ImmutableIndex<Repository.App> alphabet = new AlphabeticIndex<Repository.App>(
            Locale.SIMPLIFIED_CHINESE).addLabels(Locale.ENGLISH).buildImmutableIndex();
    private final List<Repository.App> apps = new ArrayList<>(), visible = new ArrayList<>();
    private final Map<String, Integer> positions = new HashMap<>();
    private final Map<String, String> buckets = new HashMap<>();
    private final GridView grid;
    private final EditText search;
    private final TextView empty, bubble;
    private final List<TextView> indexLabels = new ArrayList<>();
    private final BaseAdapter adapter;
    private final BiConsumer<Repository.App, View> menu;
    private String activeLetter = "";
    private Set<String> systemApps = Collections.emptySet();
    private int appType;
    private ImageButton clear, filter, display;
    private LinearLayout searchBar;
    private FloatingTitleBar titleBar;
    private FadingListFrame listContent;
    private boolean gridLayout;
    private int iconDp, labelSp;
    private IconStyle iconStyle;
    private int indexScrollTarget;
    private int indexScrollDuration;
    private long indexScrollFrameTime;
    private boolean indexScrollPosted;
    private final int[] indexLocation = new int[2], gridLocation = new int[2];
    private final Runnable indexScrollStep = this::advanceIndexScroll;
    private Runnable pendingRestore;
    Runnable onDisplay = () -> {
    };

    void refreshDisplay() {
        stopIndexScroll();
        gridLayout = Repository.prefs(getContext()).getBoolean("catalog.grid", true);
        iconDp = DisplaySettings.catalogIconSize(getContext());
        labelSp = Repository.prefs(getContext()).getInt("catalog.label_sp", 12);
        iconStyle = IconStyle.load(getContext());
        display.setImageResource(gridLayout ? R.drawable.ic_grid : R.drawable.ic_tiles);
        display.setContentDescription((gridLayout ? "宫格" : "平铺") + "，全部应用显示设置");
        adapter.notifyDataSetChanged();
        grid.requestLayout();
    }

    void topInset(int inset) {
        stopIndexScroll();
        titleBar.topInset(inset);
        listContent.topInset(inset);
        grid.setPadding(grid.getPaddingLeft(), inset + Ui.dp(getContext(), FloatingTitleBar.CONTENT_TOP_DP),
                grid.getPaddingRight(), grid.getPaddingBottom());
    }

    GlassCatalog(Context context, BiConsumer<Repository.App, View> menu) {
        super(context);
        this.menu = menu;
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, 0);
        gridLayout = Repository.prefs(context).getBoolean("catalog.grid", true);
        iconDp = DisplaySettings.catalogIconSize(context);
        labelSp = Repository.prefs(context).getInt("catalog.label_sp", 12);
        iconStyle = IconStyle.load(context);
        boolean landscape = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        search = new EditText(context);
        search.setSingleLine(true);
        search.setTag("catalog-search");
        search.setTextSize(16);
        search.setTextColor(Ui.color(context, R.color.ink));
        search.setHintTextColor(Ui.color(context, R.color.muted));
        search.setHint("搜索应用");
        search.setContentDescription("搜索应用名称或包名");
        search.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 16), 0);
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBar = new LinearLayout(context);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);

        searchBar.addView(search, new LayoutParams(0, -1, 1));
        clear = Ui.icon(context, R.drawable.ic_close, "清空搜索", v -> search.setText(""));
        clear.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(context, R.color.ink)));
        clear.setVisibility(GONE);
        clear.setTag("catalog-clear");
        searchBar.addView(clear, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 48)));
        filter = Ui.icon(context, R.drawable.ic_filter, "筛选应用类型",
                v -> GlassFormDialog.choose(context, "应用类型", new String[]{"全部应用", "非系统应用", "系统应用"},
                        appType, false, which -> {
                            appType = which;
                            filter();
                        }));
        filter.setTag("catalog-filter");

        display = Ui.icon(context, gridLayout ? R.drawable.ic_grid : R.drawable.ic_tiles,
                "全部应用显示设置", v -> onDisplay.run());
        display.setTag("catalog-display");

        FrameLayout host = new FrameLayout(context);
        addView(host, new LayoutParams(-1, 0, 1));
        grid = new GridView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int columns = Math.max(1,
                        (MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight() + Ui.dp(context, 4))
                                / Math.max(1, Ui.dp(context,
                                        gridLayout ? Math.max(78, iconDp + 14) : Math.max(170, iconDp + 110))));
                if (getNumColumns() != columns)
                    setNumColumns(columns);
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        grid.setNumColumns(4);
        grid.setTag("catalog-grid");
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(context, 4));
        grid.setHorizontalSpacing(Ui.dp(context, 4));
        // Old vendor renderers can retain a recycled cell's cached display while
        // the index dims it during an animated scroll.
        grid.setScrollingCacheEnabled(false);
        grid.setAnimationCacheEnabled(false);
        grid.setSelector(android.R.color.transparent);
        grid.setClipToPadding(false);
        grid.setPadding(Ui.dp(context, 12), Ui.dp(context, FloatingTitleBar.CONTENT_TOP_DP), Ui.dp(context, 12),
                Ui.dp(context, landscape ? 12 : 80));
        grid.setVerticalScrollBarEnabled(false);
        grid.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        grid.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                stopIndexScroll();
            return false;
        });
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(-1, -1);
        gridParams.rightMargin = 0;
        listContent = new FadingListFrame(context);
        listContent.addView(grid, gridParams);
        host.addView(listContent, new FrameLayout.LayoutParams(-1, -1));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int state) {
            }

            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                updateVisibleAlpha();
            }
        });
        empty = Ui.text(context, "正在读取应用…", 15, R.color.muted);
        empty.setGravity(Gravity.CENTER);
        host.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        bubble = Ui.text(context, "", 48, R.color.ink);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(Ui.card(context, 50));
        bubble.setVisibility(GONE);
        bubble.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout searchRow = FloatingTitleBar.row(context);
        GlassSurface searchShell = new GlassSurface(context);
        searchShell.addView(searchBar, new FrameLayout.LayoutParams(-1, -1));
        searchRow.addView(searchShell, new LayoutParams(0, Ui.dp(context, 48), 1));
        FloatingTitleBar.addButton(searchRow, filter);
        FloatingTitleBar.addButton(searchRow, display);
        titleBar = new FloatingTitleBar(context, searchRow);
        search.setOnFocusChangeListener((v, focused) -> titleBar.blurred(!focused));
        host.addView(titleBar,
                new FrameLayout.LayoutParams(-1, Ui.dp(context, FloatingTitleBar.MASK_HEIGHT_DP), Gravity.TOP));
        host.addView(bubble, new FrameLayout.LayoutParams(Ui.dp(context, 100), Ui.dp(context, 100), Gravity.CENTER));
        LinearLayout rail = new LinearLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                return true;
            }

            @Override
            public boolean performClick() {
                return super.performClick();
            }
        };
        rail.setOrientation(VERTICAL);
        rail.setTag("catalog-index");
        rail.setGravity(Gravity.CENTER);
        for (int i = 0; i < LETTERS.length(); i++) {
            String value = LETTERS.substring(i, i + 1);
            TextView key = Ui.text(context, value, 10, R.color.muted);
            key.setGravity(Gravity.CENTER);
            key.setIncludeFontPadding(false);
            key.setContentDescription("跳到 " + value);
            key.setOnClickListener(v -> {
                jump(value, false);
                endIndex();
            });
            LayoutParams letter = new LayoutParams(Ui.dp(context, 12), 0, 1);
            letter.gravity = Gravity.END;
            rail.addView(key, letter);
            indexLabels.add(key);
        }
        rail.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                    || e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                int index = Math.max(0, Math.min(26, (int) (e.getY() * 27 / Math.max(1, v.getHeight()))));
                v.getParent().requestDisallowInterceptTouchEvent(true);
                jump(LETTERS.substring(index, index + 1), e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE);
            } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                endIndex();
                v.getParent().requestDisallowInterceptTouchEvent(false);
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP)
                    v.performClick();
            }
            return true;
        });
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 378),
                Gravity.END | Gravity.CENTER_VERTICAL);
        railParams.bottomMargin = Ui.dp(context, landscape ? 12 : 80);
        host.addView(rail, railParams);
        rail.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (android.os.Build.VERSION.SDK_INT >= 29)
                rail.setSystemGestureExclusionRects(Collections.singletonList(
                        new android.graphics.Rect(0, 0, r - l, b - t)));
        });
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int height = Math.min(Ui.dp(context, 378), Math.max(0, b - t - railParams.bottomMargin));
            if (railParams.height != height) {
                railParams.height = height;
                rail.setLayoutParams(railParams);
            }
        });
        adapter = new BaseAdapter() {
            public int getCount() {
                return visible.size();
            }

            public Object getItem(int position) {
                return visible.get(position);
            }

            public long getItemId(int position) {
                return position;
            }

            public View getView(int position, View reused, android.view.ViewGroup parent) {
                AppCell cell = reused instanceof AppCell ? (AppCell) reused : new AppCell(context);
                Repository.App app = visible.get(position);
                cell.configure(gridLayout, iconDp, labelSp);
                cell.bucket = buckets.get(app.pkg);
                cell.label.setText(app.label);
                cell.setContentDescription(app.label);
                IconLoader.bind(context, app.pkg, cell.icon, iconStyle);
                cell.dim(!activeLetter.isEmpty() && !activeLetter.equals(cell.bucket));
                cell.setOnClickListener(v -> Ui.launch(context, app.pkg));
                cell.setOnLongClickListener(v -> {
                    menu.accept(app, v);
                    return true;
                });
                return cell;
            }
        };
        grid.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter();
            }

            public void afterTextChanged(Editable s) {
            }
        });
    }

    void show(Map<String, Repository.App> catalog) {
        show(catalog, Collections.emptySet());
    }

    void show(Map<String, Repository.App> catalog, Set<String> systemApps) {
        iconStyle = IconStyle.load(getContext());
        this.systemApps = new HashSet<>(systemApps);
        apps.clear();
        buckets.clear();
        apps.addAll(catalog.values());
        for (Repository.App app : apps) {
            String value = alphabet.getBucket(alphabet.getBucketIndex(app.label)).getLabel();
            buckets.put(app.pkg, value.length() == 1 && value.charAt(0) >= 'A' && value.charAt(0) <= 'Z' ? value : "#");
        }
        java.text.Collator collator = java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        apps.sort(Comparator
                .comparing((Repository.App app) -> "#".equals(buckets.get(app.pkg)) ? "ZZZ" : buckets.get(app.pkg))
                .thenComparing(app -> app.label, collator).thenComparing(app -> app.pkg));
        filter();
    }

    private void filter() {
        stopIndexScroll();
        grid.smoothScrollBy(0, 0);
        visible.clear();
        positions.clear();
        activeLetter = "";
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (Repository.App app : apps) {
            if (appType == 1 && systemApps.contains(app.pkg) || appType == 2 && !systemApps.contains(app.pkg))
                continue;
            if (!(app.label + " " + app.pkg).toLowerCase(Locale.ROOT).contains(query))
                continue;
            positions.putIfAbsent(buckets.get(app.pkg), visible.size());
            visible.add(app);
        }
        adapter.notifyDataSetChanged();
        clear.setVisibility(query.isEmpty() ? GONE : VISIBLE);
        int filterColor = Ui.color(getContext(), R.color.ink);
        filter.setImageTintList(android.content.res.ColorStateList.valueOf(filterColor));
        filter.setActivated(appType != 0);
        filter.setContentDescription("筛选：" + new String[]{"全部应用", "非系统应用", "系统应用"}[appType]);
        empty.setText("没有匹配的应用");
        empty.setVisibility(visible.isEmpty() ? VISIBLE : GONE);
        paintIndex();
    }

    private void jump(String value, boolean fast) {
        cancelRestore();
        if (value.equals(activeLetter))
            return;
        activeLetter = value;
        Integer position = positions.get(value);
        if (position != null) {
            int letterIndex = LETTERS.indexOf(value);
            View label = indexLabels.get(letterIndex);
            label.getLocationInWindow(indexLocation);
            grid.getLocationInWindow(gridLocation);
            int anchorY = indexLocation[1] + label.getHeight() / 2 - gridLocation[1];
            scrollToIndex(position, fast ? 90 : 240, anchorY);
        }
        updateVisibleAlpha();
        performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 34
                ? HapticFeedbackConstants.SEGMENT_TICK
                : HapticFeedbackConstants.CLOCK_TICK);
        paintIndex();
        bubble.animate().cancel();
        bubble.setText(value);
        bubble.setAlpha(1);
        bubble.setVisibility(VISIBLE);
    }

    private void scrollToIndex(int position, int duration, int anchorY) {
        int columns = Math.max(1, grid.getNumColumns());
        View first = grid.getChildAt(0);
        if (first == null)
            return;
        int stride = first.getHeight() + grid.getVerticalSpacing();
        if (stride <= 0)
            return;
        int start = grid.getFirstVisiblePosition() / columns * stride + grid.getPaddingTop() - first.getTop();
        int end = indexScrollOffset(position, columns, visible.size(), first.getHeight(), grid.getVerticalSpacing(),
                grid.getHeight(), grid.getPaddingTop(), grid.getPaddingBottom(), anchorY);
        if (indexScrollPosted && end == indexScrollTarget) {
            indexScrollDuration = duration;
            return;
        }
        indexScrollTarget = end;
        indexScrollDuration = duration;
        if (indexScrollPosted || Math.abs(end - start) <= 1)
            return;
        // Stop a previous fling only once; later finger movements retarget the
        // running animation without cancelling or translating the whole grid.
        grid.smoothScrollBy(0, 0);
        indexScrollFrameTime = 0;
        indexScrollPosted = true;
        grid.postOnAnimation(indexScrollStep);
    }

    private void advanceIndexScroll() {
        indexScrollPosted = false;
        View first = grid.getChildAt(0);
        if (first == null)
            return;
        int stride = first.getHeight() + grid.getVerticalSpacing();
        if (stride <= 0)
            return;
        int columns = Math.max(1, grid.getNumColumns());
        int start = grid.getFirstVisiblePosition() / columns * stride + grid.getPaddingTop() - first.getTop();
        int distance = indexScrollTarget - start;
        if (Math.abs(distance) <= 1)
            return;
        long now = android.os.SystemClock.uptimeMillis();
        long elapsed = indexScrollFrameTime == 0 ? 16 : Math.max(1, Math.min(48, now - indexScrollFrameTime));
        indexScrollFrameTime = now;
        float fraction = 1f - (float) Math.exp(-3f * elapsed / Math.max(1, indexScrollDuration));
        int limit = Math.max(1, grid.getHeight() / 2);
        int step = Math.max(1, Math.min(limit, Math.round(Math.abs(distance) * fraction)));
        grid.scrollListBy(distance > 0 ? step : -step);
        View after = grid.getChildAt(0);
        if (after == null)
            return;
        int actual = grid.getFirstVisiblePosition() / columns * stride + grid.getPaddingTop() - after.getTop();
        // At a real list boundary there is no point posting another frame.
        if (actual == start)
            return;
        indexScrollPosted = true;
        grid.postOnAnimation(indexScrollStep);
    }

    private void stopIndexScroll() {
        cancelRestore();
        if (indexScrollPosted)
            grid.removeCallbacks(indexScrollStep);
        indexScrollPosted = false;
        indexScrollFrameTime = 0;
    }

    private void cancelRestore() {
        if (pendingRestore != null) {
            grid.removeCallbacks(pendingRestore);
            pendingRestore = null;
        }
    }

    private static int indexScrollOffset(int position, int columns, int count, int rowHeight, int spacing,
            int height, int paddingTop, int paddingBottom, int anchorY) {
        int stride = rowHeight + spacing;
        int halfRow = rowHeight / 2;
        int minY = paddingTop + halfRow;
        int maxY = Math.max(minY, height - paddingBottom - halfRow);
        int targetY = Math.max(minY, Math.min(maxY, anchorY));
        int rows = (count + columns - 1) / columns;
        int max = Math.max(0, rows * stride - spacing - height + paddingTop + paddingBottom);
        return Math.max(0, Math.min(max, position / columns * stride + paddingTop + halfRow - targetY));
    }

    @Override
    protected void onDetachedFromWindow() {
        stopIndexScroll();
        grid.smoothScrollBy(0, 0);
        super.onDetachedFromWindow();
    }

    private void paintIndex() {
        for (TextView key : indexLabels) {
            String letter = key.getText().toString();
            key.setTextColor(Ui.color(getContext(), letter.equals(activeLetter) ? R.color.accent : R.color.muted));
            key.setAlpha(positions.containsKey(letter) ? 1 : .35f);
        }
    }

    private void updateVisibleAlpha() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof AppCell) {
                AppCell cell = (AppCell) child;
                cell.dim(!activeLetter.isEmpty() && !activeLetter.equals(cell.bucket));
            }
        }
    }

    private void endIndex() {
        activeLetter = "";
        updateVisibleAlpha();
        paintIndex();
        bubble.animate().alpha(0).setDuration(180).withEndAction(() -> bubble.setVisibility(GONE)).start();
    }

    void save(android.os.Bundle state, String prefix) {
        state.putInt(prefix + ".type", appType);
        state.putString(prefix + ".query", search.getText().toString());
        state.putInt(prefix + ".position", grid.getFirstVisiblePosition());
    }

    void restore(android.os.Bundle state, String prefix) {
        if (state == null)
            return;
        appType = state.getInt(prefix + ".type", 0);
        search.setText(state.getString(prefix + ".query", ""));
        cancelRestore();
        int position = state.getInt(prefix + ".position", 0);
        pendingRestore = () -> {
            pendingRestore = null;
            grid.setSelection(position);
        };
        grid.post(pendingRestore);
    }

    private static final class AppCell extends LinearLayout {
        final ImageView icon;
        final TextView label;
        private boolean gridLayout = true;
        private int iconDp = 64;
        private int labelSp;
        private boolean configured;
        private String bucket;
        AppCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            setPadding(Ui.dp(context, 2), Ui.dp(context, 4), Ui.dp(context, 2), Ui.dp(context, 2));
            setLayoutParams(new AbsListView.LayoutParams(-1, Ui.dp(context, 112)));
            Ui.pressScale(this);
            icon = new ImageView(context);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LayoutParams(Ui.dp(context, 60), Ui.dp(context, 60)));
            label = Ui.text(context, "", 12, R.color.ink);
            label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(0, Ui.dp(context, 7), 0, 0);
            addView(label, new LayoutParams(-1, -2));
            setFocusable(true);
        }

        void configure(boolean grid, int iconSize, int labelSize) {
            if (configured && gridLayout == grid && iconDp == iconSize && labelSp == labelSize)
                return;
            configured = true;
            gridLayout = grid;
            iconDp = iconSize;
            labelSp = labelSize;
            setOrientation(grid ? VERTICAL : HORIZONTAL);
            setGravity(grid ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
            label.setTextSize(labelSize);
            label.setGravity(grid ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
            label.setPadding(grid ? 0 : Ui.dp(getContext(), 10), grid ? Ui.dp(getContext(), 7) : 0, 0, 0);
            label.setLayoutParams(grid ? new LayoutParams(-1, -2) : new LayoutParams(0, -2, 1));
            float scale = Math.max(1, getResources().getConfiguration().fontScale);
            int height = grid
                    ? Math.round(iconSize + 13 + 2 * labelSize * 1.3f * scale)
                    : Math.max(iconSize + 20, Math.round(labelSize * 2.6f * scale + 16));
            setLayoutParams(new AbsListView.LayoutParams(-1, Ui.dp(getContext(), height)));
        }

        void dim(boolean dimmed) {
            // Older vendor hardware renderers may retain a recycled translucent
            // image layer. Limit the workaround to dimmed icons on those devices.
            if (android.os.Build.VERSION.SDK_INT <= 27) {
                int layer = dimmed ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_NONE;
                if (icon.getLayerType() != layer)
                    icon.setLayerType(layer, null);
            }
            icon.setImageAlpha(dimmed ? 59 : 255);
            label.setAlpha(dimmed ? .23f : 1f);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int size = gridLayout
                    ? Math.max(Ui.dp(getContext(), 32),
                            Math.min(Ui.dp(getContext(), iconDp), width - Ui.dp(getContext(), 14)))
                    : Ui.dp(getContext(), iconDp);
            icon.getLayoutParams().width = size;
            icon.getLayoutParams().height = size;
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
