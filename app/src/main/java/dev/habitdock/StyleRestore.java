package dev.habitdock;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.Button;

/** Shared toolbar state for immediately applied appearance settings. */
final class StyleRestore {
    static void update(View button, boolean changed) {
        button.setVisibility(changed ? View.VISIBLE : View.GONE);
    }

    static Button button(Activity activity, Runnable action) {
        Button button = Ui.textButton(activity);
        button.setText("重置");
        button.setTextSize(14);
        button.setContentDescription("重置");
        button.setOnClickListener(v -> action.run());
        return button;
    }

    static void show(Activity activity, String title, Runnable previous, Runnable defaults) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(title)
                .setItems(new String[]{"恢复上次", "恢复默认"}, (dialog, which) -> {
                    if (which == 0)
                        previous.run();
                    else
                        defaults.run();
                }).setNegativeButton("取消", null).show();
    }
}
