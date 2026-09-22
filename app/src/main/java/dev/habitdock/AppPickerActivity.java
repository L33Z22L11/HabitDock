package dev.habitdock;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

/**
 * Secondary picker host. The exclusion settings page uses the same
 * AppPickerView.
 */
public final class AppPickerActivity extends Activity {
    private AppPickerView picker;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = "pinned".equals(getIntent().getStringExtra("mode")) ? "pinned" : "hidden";
        LinearLayout root = Ui.screen(this);
        LinearLayout bar = Ui.toolbar(this, root, mode.equals("hidden") ? "不推荐的应用" : "固定应用", true);
        picker = new AppPickerView(this, mode, state);
        root.addView(picker, new LinearLayout.LayoutParams(-1, 0, 1));
        bar.addView(Ui.icon(this, R.drawable.ic_info, "说明", v -> picker.showInfo()));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        picker.saveState(out);
        super.onSaveInstanceState(out);
    }
}
