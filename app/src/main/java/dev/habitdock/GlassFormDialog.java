package dev.habitdock;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Opt-in host for simple forms, choices and messages with no Dialog API
 * dependencies.
 */
final class GlassFormDialog {
    private static final Object ACTIVE_TAG = new Object();
    private final ComponentActivity activity;
    private final ViewGroup root;
    private final View content;
    private final View previousFocus;
    private final ArrayList<View> obscured = new ArrayList<>();
    private final ArrayList<Integer> accessibility = new ArrayList<>();
    private FrameLayout overlay;
    private OnBackPressedCallback back;
    private LifecycleEventObserver lifecycle;
    private boolean closed;

    static void show(Activity activity, String title, View content, Runnable confirm) {
        if (activity.isFinishing() || activity.isDestroyed())
            return;
        if (tryNative(activity, title, content, "取消", "确定", confirm, null))
            return;
        new GlassDialogBuilder(activity)
                .setTitle(title).setView(content).setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> confirm.run()).show();
    }

    /**
     * A negative selected index represents a plain list rather than radio choices.
     */
    static void choose(Context context, String title, String[] items, int selected, boolean cancelButton,
            IntConsumer action) {
        if (ObricGlass.available(context)
                && chooseNative(context, title, items, selected, cancelButton, action))
            return;
        GlassDialogBuilder builder = new GlassDialogBuilder(context);
        builder.setTitle(title);
        if (selected >= 0)
            builder.setSingleChoiceItems(items, selected, (dialog, which) -> {
                action.accept(which);
                dialog.dismiss();
            });
        else
            builder.setItems(items, (dialog, which) -> action.accept(which));
        if (cancelButton)
            builder.setNegativeButton("取消", null);
        builder.show();
    }

    private static boolean chooseNative(Context context, String title, String[] items, int selected,
            boolean cancelButton, IntConsumer action) {
        LinearLayout content = selected >= 0 ? new RadioGroup(context) : Ui.column(context);
        ArrayList<View> rows = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            View row;
            if (selected >= 0) {
                RadioButton radio = new RadioButton(context);
                radio.setText(items[i]);
                radio.setChecked(i == selected);
                radio.setTextSize(16);
                radio.setTextColor(Ui.color(context, R.color.ink));
                radio.setPadding(Ui.dp(context, 24), 0, Ui.dp(context, 24), 0);
                row = radio;
            } else {
                Button button = Ui.textButton(context);
                button.setText(items[i]);
                button.setTextSize(16);
                button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                button.setPadding(Ui.dp(context, 24), 0, Ui.dp(context, 24), 0);
                row = button;
            }
            row.setMinimumHeight(Ui.dp(context, 48));
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            rows.add(row);
        }
        return tryNative(context, title, content, cancelButton ? "取消" : null, null, null, host -> {
            for (int i = 0; i < rows.size(); i++) {
                int index = i;
                rows.get(i).setOnClickListener(v -> {
                    try {
                        action.accept(index);
                    } finally {
                        host.close();
                    }
                });
            }
        });
    }

    static void message(Activity activity, String title, String message, String negative, String positive,
            Runnable action) {
        if (activity.isFinishing() || activity.isDestroyed())
            return;
        TextView content = Ui.text(activity, message, 16, R.color.ink);
        content.setPadding(Ui.dp(activity, 24), 0, Ui.dp(activity, 24), Ui.dp(activity, 8));
        if (tryNative(activity, title, content, negative, positive, action, null))
            return;
        GlassDialogBuilder builder = new GlassDialogBuilder(activity);
        builder.setTitle(title).setMessage(message);
        if (negative != null)
            builder.setNegativeButton(negative, null);
        if (positive != null)
            builder.setPositiveButton(positive, (dialog, which) -> {
                if (action != null)
                    action.run();
            });
        builder.show();
    }

    static void info(Activity activity, String title, View content, String closeLabel) {
        if (activity.isFinishing() || activity.isDestroyed())
            return;
        if (tryNative(activity, title, content, null, closeLabel, null, null))
            return;
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        new GlassDialogBuilder(activity).setTitle(title)
                .setView(scroll).setPositiveButton(closeLabel, null).show();
    }

    private static boolean tryNative(Context context, String title, View content, String negative, String positive,
            Runnable confirm, Consumer<GlassFormDialog> configure) {
        Context ownerContext = context;
        while (ownerContext instanceof ContextWrapper && !(ownerContext instanceof Activity)) {
            Context next = ((ContextWrapper) ownerContext).getBaseContext();
            if (next == ownerContext)
                break;
            ownerContext = next;
        }
        if (ObricGlass.available(context) && ownerContext instanceof ComponentActivity) {
            ComponentActivity owner = (ComponentActivity) ownerContext;
            ViewGroup root = owner.findViewById(android.R.id.content);
            if (!owner.isFinishing() && !owner.isDestroyed() && root != null && root.isAttachedToWindow()
                    && owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                GlassFormDialog host = new GlassFormDialog(owner, root, content);
                try {
                    if (configure != null)
                        configure.accept(host);
                    host.showNative(title, negative, positive, confirm);
                    return true;
                } catch (RuntimeException error) {
                    host.close();
                    if (content.getParent() instanceof ViewGroup)
                        ((ViewGroup) content.getParent()).removeView(content);
                    android.util.Log.w("GlassFormDialog", "Unable to show same-window form", error);
                }
            }
        }
        return false;
    }

    static boolean isShowing(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        return root != null && root.findViewWithTag(ACTIVE_TAG) != null;
    }

    private GlassFormDialog(ComponentActivity activity, ViewGroup root, View content) {
        this.activity = activity;
        this.root = root;
        this.content = content;
        previousFocus = activity.getCurrentFocus();
    }

    private void showNative(String title, String negative, String positive, Runnable confirm) {
        overlay = new FrameLayout(activity) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP)
                        back();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        overlay.setTag(ACTIVE_TAG);
        overlay.setFocusableInTouchMode(true);
        overlay.setOnClickListener(v -> close());
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            androidx.core.graphics.Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        if (android.os.Build.VERSION.SDK_INT >= 28)
            overlay.setAccessibilityPaneTitle(title);

        FrameLayout surface = new FrameLayout(activity);
        GlassPanels.attach(surface, 28, ObricGlass.VIEW_BACKGROUND);
        surface.setClickable(true); // Empty space inside the form is not an outside click.
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout body = Ui.column(activity);
        TextView heading = Ui.text(activity, title, 20, R.color.ink);
        heading.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 16));
        ViewCompat.setAccessibilityHeading(heading, true);
        body.addView(heading);
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));
        if (negative != null) {
            Button cancel = Ui.textButton(activity);
            cancel.setText(negative);
            cancel.setOnClickListener(v -> close());
            buttons.addView(cancel);
        }
        if (positive != null) {
            Button accept = Ui.textButton(activity);
            accept.setText(positive);
            accept.setOnClickListener(v -> {
                try {
                    if (confirm != null)
                        confirm.run();
                } finally {
                    close();
                }
            });
            buttons.addView(accept);
        }
        if (negative != null || positive != null)
            body.addView(buttons);
        scroll.addView(body);
        surface.addView(scroll, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams panel = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        int margin = Ui.dp(activity, 24);
        panel.leftMargin = panel.rightMargin = margin;
        panel.topMargin = panel.bottomMargin = margin;
        // Keep the form readable on tablets without constraining the content on phones.
        int width = root.getWidth() - margin * 2;
        if (width > Ui.dp(activity, 560))
            panel.width = Ui.dp(activity, 560);
        overlay.addView(surface, panel);

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            obscured.add(child);
            accessibility.add(child.getImportantForAccessibility());
            child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        root.addView(overlay, new ViewGroup.LayoutParams(-1, -1));
        back = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                back();
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, back);
        lifecycle = (source, event) -> {
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY)
                close();
        };
        activity.getLifecycle().addObserver(lifecycle);
        ViewCompat.requestApplyInsets(overlay);
        overlay.requestFocus();
    }

    private void back() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(overlay);
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null)
                keyboard.hideSoftInputFromWindow(overlay.getWindowToken(), 0);
        } else {
            close();
        }
    }

    private void close() {
        if (closed)
            return;
        closed = true;
        if (back != null)
            back.remove();
        if (lifecycle != null)
            activity.getLifecycle().removeObserver(lifecycle);
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null)
                keyboard.hideSoftInputFromWindow(overlay.getWindowToken(), 0);
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        for (int i = 0; i < obscured.size(); i++)
            obscured.get(i).setImportantForAccessibility(accessibility.get(i));
        if (previousFocus != null && previousFocus.isAttachedToWindow())
            previousFocus.requestFocus();
    }
}
