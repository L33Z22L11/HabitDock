package dev.habitdock;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;

final class RecommendationList extends ListView {
    private final Context context;
    private final int columns;
    private IconStyle iconStyle;
    private List<Predictor.Prediction> predictions = Collections.emptyList();
    private Map<String, Repository.App> apps = Collections.emptyMap();
    private final BaseAdapter adapter;
    java.util.function.BiConsumer<Repository.App, View> onMenu = (app, anchor) -> {
    };
    RecommendationList(Context a) {
        super(a);
        context = a;
        iconStyle = IconStyle.load(a);
        columns = getResources().getConfiguration().fontScale > 1.35f ? 1 : 2;
        setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        setDividerHeight(Ui.dp(a, 4));
        setSelector(android.R.color.transparent);
        setItemsCanFocus(true);
        setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        setClipToPadding(false);
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
                if (reused instanceof LinearLayout)
                    row = (LinearLayout) reused;
                else {
                    row = new LinearLayout(context);
                    for (int i = 0; i < columns; i++)
                        row.addView(new AppTile(context), new LinearLayout.LayoutParams(0, -1, 1));
                }
                row.setLayoutParams(new AbsListView.LayoutParams(-1,
                        Ui.dp(context, 72 * Math.max(1f, getResources().getConfiguration().fontScale))));
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
                    tile.bind(app, p.reason, iconStyle);
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
        iconStyle = IconStyle.load(context);
        apps = data.apps;
        predictions = data.predictions;
        adapter.notifyDataSetChanged();
    }

    void clear() {
        predictions = Collections.emptyList();
        adapter.notifyDataSetChanged();
    }
}
