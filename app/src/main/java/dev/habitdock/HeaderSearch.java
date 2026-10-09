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
        actions.addView(search);
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
        toolbar.addView(back);
        if (input.getParent() != null)
            ((ViewGroup) input.getParent()).removeView(input);
        toolbar.addView(input, new LinearLayout.LayoutParams(0, -1, 1));
        ImageButton clear = Ui.icon(activity, R.drawable.ic_close, "清除搜索", v -> input.setText(""));
        clear.setTag("clear-search");
        toolbar.addView(clear);
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
