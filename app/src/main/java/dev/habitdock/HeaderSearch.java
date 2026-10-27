package dev.habitdock;

import android.app.Activity;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/**
 * A search action expands into the current toolbar without moving the results.
 */
final class HeaderSearch {
    private final Activity activity;
    private final LinearLayout toolbar;
    private final EditText input;
    private final List<View> normal = new ArrayList<>();
    private final Runnable changed;
    private boolean expanded;
    HeaderSearch(Activity activity, LinearLayout toolbar, ViewGroup actions, EditText input, Runnable changed) {
        this.activity = activity;
        this.toolbar = toolbar;
        this.input = input;
        this.changed = changed;
        ImageButton search = Ui.icon(activity, R.drawable.ic_search, "搜索应用", v -> open(true));
        search.setTag("open-search");
        GlassSurface searchButton = new GlassSurface(activity);
        searchButton.addView(search, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams buttonSize = new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48));
        buttonSize.setMarginEnd(Ui.dp(activity, 12));
        actions.addView(searchButton, buttonSize);
        for (int i = 0; i < toolbar.getChildCount(); i++)
            normal.add(toolbar.getChildAt(i));
    }

    boolean expanded() {
        return expanded;
    }

    void open(boolean keyboard) {
        if (expanded)
            return;
        expanded = true;
        toolbar.removeAllViews();
        ImageButton back = Ui.icon(activity, R.drawable.ic_back, "关闭搜索", v -> close(true));
        back.setTag("search-back");
        Ui.roundedTouch(back, 24);
        toolbar.addView(back);
        if (input.getParent() != null)
            ((ViewGroup) input.getParent()).removeView(input);
        GlassSurface searchField = new GlassSurface(activity);
        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.addView(input, new LinearLayout.LayoutParams(0, -1, 1));
        ImageButton clear = Ui.icon(activity, R.drawable.ic_close, "清除或关闭搜索", v -> {
            if (input.length() > 0)
                input.setText("");
            else
                close(true);
        });
        clear.setTag("clear-search");
        Ui.roundedTouch(clear, 24);
        searchRow.addView(clear, new LinearLayout.LayoutParams(Ui.dp(activity, 48), -1));
        searchField.addView(searchRow, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams fieldSize = new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1);
        fieldSize.setMarginEnd(Ui.dp(activity, 12));
        toolbar.addView(searchField, fieldSize);
        input.setAlpha(0);
        input.setTranslationX(Ui.dp(activity, 24));
        input.animate().alpha(1).translationX(0).setDuration(180).start();
        if (keyboard) {
            input.requestFocus();
            input.post(() -> {
                if (expanded)
                    activity.getSystemService(InputMethodManager.class).showSoftInput(input,
                            InputMethodManager.SHOW_IMPLICIT);
            });
        }
        changed.run();
    }

    void close(boolean clear) {
        if (!expanded)
            return;
        input.animate().cancel();
        input.clearFocus();
        activity.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(), 0);
        if (clear)
            input.setText("");
        toolbar.removeAllViews();
        for (View view : normal)
            toolbar.addView(view);
        expanded = false;
        changed.run();
    }
}
