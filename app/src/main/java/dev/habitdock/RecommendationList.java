package dev.habitdock;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;

final class RecommendationList extends ListView {
    private final Context context;
    private int columns = 1;
    private boolean grid;
    private int scrollbarTop;

    void scrollbarTop(int top) {
        scrollbarTop = Math.max(0, top);
        invalidate();
    }

    @Override
    public void onDrawForeground(android.graphics.Canvas canvas) {
        if (scrollbarTop <= 0 || scrollbarTop >= getHeight()) {
            super.onDrawForeground(canvas);
            return;
        }
        int save = canvas.save();
        canvas.translate(0, scrollbarTop);
        canvas.scale(1, (getHeight() - scrollbarTop) / (float) getHeight());
        super.onDrawForeground(canvas);
        canvas.restoreToCount(save);
    }

    void grid(boolean value) {
        if (grid == value)
            return;
        grid = value;
        adapter.notifyDataSetChanged();
        requestLayout();
    }

    private int rowHeight() {
        if (!grid)
            return mainStyle.rowDp(context);
        float scale = Math.max(1, getResources().getConfiguration().fontScale);
        return Math.round(mainStyle.iconDp + 24 + (mainStyle.labelSp * 1.3f
                + (mainStyle.reasons == 0 ? 0 : 34)) * scale);
    }
    private IconStyle iconStyle;
    private String renderedStyle = "";
    private MainStyle mainStyle;
    private Set<String> pins = Collections.emptySet();
    private List<Predictor.Prediction> predictions = Collections.emptyList();
    private Map<String, Repository.App> apps = Collections.emptyMap();
    private final BaseAdapter adapter;
    private android.graphics.drawable.Drawable normalScrollbar;
    private Set<String> hidden = Collections.emptySet();
    java.util.function.BiConsumer<Repository.App, View> onMenu = (app, anchor) -> {
    };
    RecommendationList(Context a) {
        super(a);
        context = a;
        iconStyle = IconStyle.load(a);
        mainStyle = MainStyle.load(a);
        setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        setDividerHeight(Ui.dp(a, 4));
        setSelector(android.R.color.transparent);
        setItemsCanFocus(true);
        setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        setClipToPadding(false);
        setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        adapter = new BaseAdapter() {
            public int getCount() {
                return (predictions.size() + columns - 1) / columns;
            }

            public Object getItem(int p) {
                return predictions.get(p * columns);
            }

            public long getItemId(int p) {
                return p;
            }

            public boolean areAllItemsEnabled() {
                return false;
            }

            public boolean isEnabled(int p) {
                return false;
            }

            public View getView(int pos, View reused, ViewGroup parent) {
                LinearLayout row;
                if (reused instanceof LinearLayout && ((LinearLayout) reused).getChildCount() == columns)
                    row = (LinearLayout) reused;
                else {
                    row = new LinearLayout(context);
                    for (int i = 0; i < columns; i++) {
                        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
                        if (i + 1 < columns)
                            cell.setMarginEnd(Ui.dp(context, 4));
                        row.addView(new AppTile(context), cell);
                    }
                }
                row.setLayoutParams(new AbsListView.LayoutParams(-1,
                        Ui.dp(context, rowHeight())));
                for (int i = 0; i < columns; i++) {
                    AppTile tile = (AppTile) row.getChildAt(i);
                    int index = pos * columns + i;
                    if (index >= predictions.size()) {
                        tile.setVisibility(View.INVISIBLE);
                        continue;
                    }
                    tile.setVisibility(View.VISIBLE);
                    Predictor.Prediction p = predictions.get(index);
                    Repository.App app = apps.get(p.pkg);
                    tile.grid(grid);
                    tile.bind(app, p, iconStyle, mainStyle, pins.contains(p.pkg));
                    tile.setOnClickListener(v -> Ui.launch(context, p.pkg));
                    tile.setOnLongClickListener(v -> {
                        onMenu.accept(app, tile);
                        return true;
                    });
                }
                return row;
            }
        };
        setAdapter(adapter);
        if (android.os.Build.VERSION.SDK_INT >= 29)
            normalScrollbar = getVerticalScrollbarThumbDrawable();
        applyScrollbar(mainStyle);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int nextColumns = grid
                ? Math.max(1, (width + Ui.dp(context, 4)) / Ui.dp(context,
                        Math.max(78, mainStyle.iconDp + 20) * Math.max(1, getResources().getConfiguration().fontScale)))
                : mainStyle.columns(context, width);
        if (columns != nextColumns) {
            int firstItem = getFirstVisiblePosition() * columns;
            int top = getChildCount() == 0 ? getPaddingTop() : getChildAt(0).getTop();
            columns = nextColumns;
            adapter.notifyDataSetChanged();
            setSelectionFromTop(firstItem / columns, top - getPaddingTop());
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    void applyScrollbar(MainStyle style) {
        if (android.os.Build.VERSION.SDK_INT < 29)
            return;
        if (style.hasBackground()) {
            android.graphics.drawable.GradientDrawable thumb = new android.graphics.drawable.GradientDrawable();
            thumb.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(style.ink(context), 102));
            thumb.setCornerRadius(Ui.dp(context, 2));
            thumb.setSize(Ui.dp(context, 3), Ui.dp(context, 32));
            setVerticalScrollbarThumbDrawable(thumb);
        } else
            setVerticalScrollbarThumbDrawable(normalScrollbar);
    }

    void reveal(String pkg, java.util.function.Consumer<View> ready) {
        int index = -1;
        for (int i = 0; i < predictions.size(); i++)
            if (predictions.get(i).pkg.equals(pkg)) {
                index = i;
                break;
            }
        if (index < 0) {
            ready.accept(null);
            return;
        }
        int row = index / columns;
        if (row < getFirstVisiblePosition() || row > getLastVisiblePosition())
            setSelection(row);
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                ready.accept(findViewWithTag(pkg));
            }
        });
        requestLayout();
    }

    void show(Repository.Snapshot data) {
        IconStyle nextStyle = IconStyle.load(context);
        MainStyle nextMainStyle = MainStyle.load(context);
        Set<String> nextPins = Repository.selection(context, "pinned");
        List<Predictor.Prediction> visible = nextMainStyle.visiblePredictions(context, data);
        Set<String> nextHidden = Repository.selection(context, "hidden");
        String nextKey = nextStyle.cacheKey(context) + ":" + nextMainStyle.key();
        boolean same = nextHidden.equals(hidden) && nextKey.equals(renderedStyle) && nextPins.equals(pins)
                && predictions.size() == visible.size();
        for (int i = 0; same && i < predictions.size(); i++) {
            Predictor.Prediction before = predictions.get(i), after = visible.get(i);
            Repository.App oldApp = apps.get(before.pkg), newApp = data.apps.get(after.pkg);
            same = before.pkg.equals(after.pkg) && Objects.equals(mainStyle.reason(before), nextMainStyle.reason(after))
                    && oldApp != null && newApp != null && Objects.equals(oldApp.label, newApp.label);
        }
        iconStyle = nextStyle;
        renderedStyle = nextKey;
        mainStyle = nextMainStyle;
        pins = nextPins;
        hidden = nextHidden;
        apps = data.apps;
        predictions = visible;
        // Returning from settings must preserve the rendered rows and scroll position.
        if (!same)
            adapter.notifyDataSetChanged();
    }

    void clear() {
        predictions = Collections.emptyList();
        adapter.notifyDataSetChanged();
    }
}
