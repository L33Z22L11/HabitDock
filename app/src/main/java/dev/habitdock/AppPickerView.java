package dev.habitdock;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

// Constructed programmatically with its host callbacks; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class AppPickerView extends LinearLayout {
    private final Activity activity;
    private final Set<String> selected = new HashSet<>();
    private final Set<String> orderSelected = new HashSet<>();
    private List<Repository.App> apps = new ArrayList<>(), visible = new ArrayList<>();
    private final String mode;
    private final EditText search;
    private final TextView empty;
    private HeaderSearch headerSearch;
    private boolean restoreSearch;
    private java.util.function.IntConsumer countChanged = count -> {
    };
    private final BaseAdapter adapter;
    private boolean loaded;
    private int columns = 1;

    static int columnsForWidth(Context context, int width, int horizontalPadding) {
        int gap = Ui.dp(context, 4);
        float minimum = 170 * Math.max(1f, context.getResources().getConfiguration().fontScale)
                * context.getResources().getDisplayMetrics().density;
        int available = Math.max(0, width - horizontalPadding);
        // Rows share fractional pixels; rounding each cell separately can lose a
        // column.
        // Allow one pixel for separately rounded width and left/right padding.
        return Math.max(1, (int) ((available + gap + 1f) / (minimum + gap)));
    }

    AppPickerView(Activity host, String selectionMode, Bundle state) {
        super(host);
        activity = host;
        mode = selectionMode;
        setOrientation(VERTICAL);
        setFocusableInTouchMode(true);
        selected.addAll(Repository.selection(activity, mode));
        java.util.ArrayList<String> savedOrder = state == null ? null : state.getStringArrayList("order-selected");
        orderSelected.addAll(savedOrder == null ? selected : savedOrder);
        LinearLayout root = this;
        search = new EditText(activity);
        search.setTag("search");
        search.setTextColor(Ui.color(activity, R.color.ink));
        search.setHintTextColor(Ui.color(activity, R.color.muted));
        search.setHint(R.string.search_apps);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setPadding(Ui.dp(activity, 20), 0, 0, 0);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        FrameLayout content = new FrameLayout(activity);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        ListView list = new ListView(activity) {
            @Override
            protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                super.onSizeChanged(width, height, oldWidth, oldHeight);
                int next = columnsForWidth(activity, width, getPaddingLeft() + getPaddingRight());
                if (next == columns)
                    return;
                int firstApp = getFirstVisiblePosition() * columns;
                View firstRow = getChildAt(0);
                int top = firstRow == null ? 0 : firstRow.getTop() - getPaddingTop();
                columns = next;
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                    setSelectionFromTop(firstApp / columns, top);
                }
            }
        };
        list.setTag("picker-list");
        list.setDivider(null);
        list.setItemsCanFocus(true);
        list.setSelector(android.R.color.transparent);
        list.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), Ui.dp(activity, 8));
        list.setClipToPadding(false);
        list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        adapter = new BaseAdapter() {
            public int getCount() {
                return (visible.size() + columns - 1) / columns;
            }

            public Object getItem(int pos) {
                return visible.get(pos * columns);
            }

            public long getItemId(int pos) {
                return pos;
            }

            public boolean areAllItemsEnabled() {
                return false;
            }

            public boolean isEnabled(int pos) {
                return false;
            }

            public View getView(int position, View reused, ViewGroup parent) {
                LinearLayout row;
                if (reused instanceof LinearLayout && ((LinearLayout) reused).getChildCount() == columns)
                    row = (LinearLayout) reused;
                else {
                    row = new LinearLayout(activity);
                    for (int col = 0; col < columns; col++) {
                        PickerCell cell = new PickerCell(activity);
                        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
                        if (col < columns - 1)
                            p.setMarginEnd(Ui.dp(activity, 4));
                        row.addView(cell, p);
                    }
                }
                row.setLayoutParams(new AbsListView.LayoutParams(-1,
                        Ui.dp(activity, 48 * Math.max(1f, getResources().getConfiguration().fontScale))));
                for (int col = 0; col < columns; col++) {
                    PickerCell cell = (PickerCell) row.getChildAt(col);
                    int index = position * columns + col;
                    if (index >= visible.size()) {
                        cell.setVisibility(View.INVISIBLE);
                        continue;
                    }
                    cell.setVisibility(View.VISIBLE);
                    Repository.App app = visible.get(index);
                    cell.bind(app, selected.contains(app.pkg), (button, checked) -> change(app.pkg, checked));
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = Ui.text(activity, "正在加载…", 14, R.color.muted);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        if (state != null)
            search.setText(state.getString("query", ""));
        restoreSearch = state != null && state.getBoolean("search-open", search.length() > 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
                filter();
                list.setSelection(0);
            }

            public void afterTextChanged(Editable e) {
            }
        });
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // App discovery must not wait behind history imports on the recommendation
        // queue.
        new Thread(() -> {
            try {
                List<Repository.App> found = new ArrayList<>(
                        Repository.apps(activity.getApplicationContext()).values());
                if (mode.equals("pinned")) {
                    Set<String> hidden = Repository.selection(activity, "hidden");
                    found.removeIf(a -> hidden.contains(a.pkg));
                }
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) {
                        apps = found;
                        loaded = true;
                        filter();
                    }
                });
            } catch (RuntimeException e) {
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) {
                        empty.setText("应用列表读取失败，请返回后重试");
                    }
                });
            }
        }, "habitdock-picker").start();
    }

    private void change(String pkg, boolean checked) {
        if (checked)
            selected.add(pkg);
        else
            selected.remove(pkg);
        if (mode.equals("hidden"))
            Repository.setExcluded(activity.getApplicationContext(), pkg, checked);
        else {
            Repository.prefs(activity).edit().putStringSet(mode, new HashSet<>(selected)).apply();
            Repository.selectionChanged(activity.getApplicationContext());
        }
        updateCount();
    }

    private void filter() {
        if (!loaded)
            return;
        visible = AppCatalog.filter(apps, orderSelected, search.getText().toString());
        adapter.notifyDataSetChanged();
        updateCount();
        empty.setText(R.string.no_search_results);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    void saveState(Bundle out) {
        out.putStringArrayList("order-selected", new java.util.ArrayList<>(orderSelected));
        out.putString("query", search.getText().toString());
        out.putBoolean("search-open", searching());
    }

    void setCountListener(java.util.function.IntConsumer listener) {
        countChanged = listener;
        updateCount();
    }

    private void updateCount() {
        countChanged.accept(selected.size());
    }

    void attachSearch(LinearLayout toolbar, ViewGroup actions, Runnable changed) {
        headerSearch = new HeaderSearch(activity, toolbar, actions, search, changed);
        if (restoreSearch || search.length() > 0)
            headerSearch.open(false);
        restoreSearch = false;
    }

    boolean searching() {
        return headerSearch != null && headerSearch.expanded();
    }

    boolean closeSearch() {
        if (!searching())
            return false;
        headerSearch.close(true);
        return true;
    }

}
