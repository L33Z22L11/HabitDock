package dev.habitdock;

import android.os.Bundle;
import android.widget.LinearLayout;

/**
 * Sibling settings destinations for both excluded and pinned applications.
 */
public final class AppPickerActivity extends ThemedActivity {
    private AppPickerView picker;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = "pinned".equals(getIntent().getStringExtra("mode")) ? "pinned" : "hidden";
        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.toolbar(this, root, mode.equals("hidden") ? "排除应用" : "置顶应用", true);
        picker = new AppPickerView(this, mode, state);
        picker.setTag(mode.equals("hidden") ? "exclusions-page" : "pinned-page");
        root.addView(picker, new LinearLayout.LayoutParams(-1, 0, 1));
        android.widget.TextView title = (android.widget.TextView) bar.getChildAt(1);
        Ui.headerTitle(title);
        picker.setCountListener(count -> title
                .setText(getString(R.string.picker_title, mode.equals("hidden") ? "排除应用" : "置顶应用", count)));
        androidx.activity.OnBackPressedCallback searchBack = new androidx.activity.OnBackPressedCallback(false) {
            public void handleOnBackPressed() {
                picker.closeSearch();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, searchBack);
        picker.attachSearch(bar, bar, () -> searchBack.setEnabled(picker.searching()));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        picker.saveState(out);
        super.onSaveInstanceState(out);
    }
}
