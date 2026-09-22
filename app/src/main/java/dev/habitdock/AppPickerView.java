package dev.habitdock;

import android.app.*;
import android.content.SharedPreferences;
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
    private List<Repository.App> apps = new ArrayList<>(), visible = new ArrayList<>();
    private String mode;
    private EditText search;
    private TextView count, empty;
    private BaseAdapter adapter;
    private boolean loaded;
    private int columns;
    AppPickerView(Activity host, String selectionMode, Bundle state) {
        super(host);
        activity = host;
        mode = selectionMode;
        setOrientation(VERTICAL);
        setFocusableInTouchMode(true);
        selected.addAll(Repository.selection(activity, mode));
        columns = getResources().getConfiguration().fontScale > 1.35f ? 1 : 2;
        LinearLayout root = this;
        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(Ui.shape(activity, R.color.surface, 8));
        ImageView magnifier = new ImageView(activity);
        magnifier.setImageResource(R.drawable.ic_search);
        magnifier.setImageTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.muted)));
        magnifier.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 8), 0);
        searchRow.addView(magnifier, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 24)));
        search = new EditText(activity);
        search.setTag("search");
        search.setTextColor(activity.getColor(R.color.ink));
        search.setHintTextColor(activity.getColor(R.color.muted));
        search.setHint(R.string.search_apps);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setPadding(0, 0, 0, 0);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1));
        ImageButton clear = Ui.icon(activity, R.drawable.ic_close, "清除搜索", v -> search.setText(""));
        clear.setVisibility(View.INVISIBLE);
        searchRow.addView(clear);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48));
        searchParams.setMargins(Ui.dp(activity, 12), Ui.dp(activity, 2), Ui.dp(activity, 12), Ui.dp(activity, 4));
        root.addView(searchRow, searchParams);
        count = Ui.text(activity, "正在加载…", 12, R.color.muted);
        count.setTag("count");
        count.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 6), Ui.dp(activity, 16), Ui.dp(activity, 8));
        root.addView(count);
        FrameLayout content = new FrameLayout(activity);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        ListView list = new ListView(activity);
        list.setTag("picker-list");
        list.setDivider(null);
        list.setItemsCanFocus(true);
        list.setSelector(android.R.color.transparent);
        list.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), Ui.dp(activity, 8));
        list.setClipToPadding(false);
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
                if (reused instanceof LinearLayout)
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
        clear.setVisibility(search.length() == 0 ? View.INVISIBLE : View.VISIBLE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
                clear.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
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
                        count.setText("应用列表读取失败");
                        empty.setText("请返回后重新打开");
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
        filter();
    }

    private void filter() {
        if (!loaded)
            return;
        visible = AppCatalog.filter(apps, selected, search.getText().toString());
        adapter.notifyDataSetChanged();
        count.setText(search.length() > 0
                ? activity.getString(R.string.picker_search_count, selected.size(), visible.size())
                : activity.getString(R.string.picker_count, selected.size()));
        empty.setText(R.string.no_search_results);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    void saveState(Bundle out) {
        out.putString("query", search.getText().toString());
    }

    void refreshSelection() {
        selected.clear();
        selected.addAll(Repository.selection(activity, mode));
        filter();
    }

    void showInfo() {
        new AlertDialog.Builder(activity).setTitle(mode.equals("hidden") ? "不推荐的应用" : "固定应用")
                .setMessage(mode.equals("hidden")
                        ? "勾选立即生效：不推荐、不学习，并清除已有本地使用记录。\n\n桌面已有或不想展示的应用都可以排除。取消勾选后恢复学习。"
                        : "固定数量不限。不推荐的应用不会出现在这里。修改立即生效。")
                .setPositiveButton("知道了", null).show();
    }
}
