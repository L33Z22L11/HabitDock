package dev.habitdock;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.ImageButton;

/** Shared toolbar state for immediately applied appearance settings. */
final class StyleRestore {
    static void update(ImageButton button, boolean changed) {
        button.setVisibility(changed ? View.VISIBLE : View.GONE);
    }

    static void show(Activity activity, String title, Runnable previous, Runnable defaults) {
        new AlertDialog.Builder(activity).setTitle(title)
                .setItems(new String[]{"恢复上次", "恢复默认"}, (dialog, which) -> {
                    if (which == 0)
                        previous.run();
                    else
                        defaults.run();
                }).setNegativeButton("取消", null).show();
    }
}
