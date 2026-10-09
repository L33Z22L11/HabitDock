package dev.habitdock;

import android.os.Bundle;
import android.widget.LinearLayout;

/**
 * Settings and both application pickers use the platform Activity hierarchy.
 */
public final class SettingsActivity extends ThemedActivity {
    private SettingsPage page;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.screen(this);
        Ui.toolbar(this, root, "设置", true);
        page = new SettingsPage(this);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        page.refresh(null);
    }

}
