package dev.habitdock;

import android.app.Activity;
import android.content.Context;
import android.view.*;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Recommendation display controls share the existing MainStyle preferences. */
final class DisplaySettings {
    static int catalogIconSize(Context context) {
        int saved = Repository.prefs(context).getInt("catalog.icon_dp", 64);
        int clamped = Math.max(32, Math.min(96, saved));
        return 32 + Math.round((clamped - 32) / 8f) * 8;
    }

    static void show(GlassHomeActivity activity, boolean catalog, Runnable changed) {
        String prefix = catalog ? "catalog." : "main.";
        LinearLayout content = Ui.column(activity);
        content.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 12), Ui.dp(activity, 20), Ui.dp(activity, 24));
        View handle = new View(activity);
        handle.setBackground(Ui.shape(activity, R.color.muted, 3));
        LinearLayout.LayoutParams grip = new LinearLayout.LayoutParams(Ui.dp(activity, 32), Ui.dp(activity, 4));
        grip.gravity = Gravity.CENTER_HORIZONTAL;
        grip.bottomMargin = Ui.dp(activity, 16);
        content.addView(handle, grip);
        TextView title = Ui.text(activity, "显示", 20, R.color.ink);
        content.addView(title);
        if (!catalog) {
            Ui.space(content, 12);
            TextView status = Ui.text(activity, "读取中", 13, R.color.muted);
            status.setTag("recommend-time");
            content.addView(status);
            LinearLayout actions = new LinearLayout(activity);
            Button preview = new com.google.android.material.button.MaterialButton(activity, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            preview.setText("预览其他时间");
            preview.setTag("preview-time");
            preview.setOnClickListener(v -> activity.chooseTime());
            Button refresh = new com.google.android.material.button.MaterialButton(activity, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            refresh.setTag("return-now");
            refresh.setOnClickListener(v -> activity.refreshRecommendations());
            actions.addView(preview, new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
            content.addView(actions);
            activity.bindDisplayStatus(status, refresh);
        }
        Ui.space(content, 16);
        SlidingChoice layout = new SlidingChoice(activity, new String[]{"平铺", "宫格"}, "display-layout-", index -> {
            Repository.prefs(activity).edit().putBoolean(prefix + "grid", index == 1).apply();
            changed.run();
        });
        layout.addRow(content, "布局", 0);
        layout.select(Repository.prefs(activity).getBoolean(prefix + "grid", catalog) ? 1 : 0, false);
        MainStyle current = MainStyle.load(activity);
        slider(activity, content, "图标大小", 32, catalog ? 96 : 64, catalog ? 8 : 2,
                catalog ? catalogIconSize(activity) : current.iconDp,
                catalog ? 64 : MainStyle.defaults().iconDp,
                value -> {
                    Repository.prefs(activity).edit().putInt(prefix + "icon_dp", value).apply();
                    changed.run();
                });
        slider(activity, content, "应用名大小", catalog ? 10 : 14, 20, 1,
                catalog ? Repository.prefs(activity).getInt("catalog.label_sp", 12) : current.labelSp,
                catalog ? 12 : MainStyle.defaults().labelSp,
                value -> {
                    Repository.prefs(activity).edit().putInt(prefix + "label_sp", value).apply();
                    changed.run();
                });
        if (!catalog) {
            SlidingChoice reasons = new SlidingChoice(activity, MainStyle.REASONS, "display-reasons-", index -> {
                Repository.prefs(activity).edit().putInt("main.reasons", index).apply();
                changed.run();
            });
            reasons.setTag("display-reasons");
            reasons.addRow(content, "推荐理由", 1);
            reasons.select(current.reasons, false);
            Ui.space(content, 12);
            MaterialSwitch hidden = Ui.switchRow(content, "显示隐藏应用", "display-show-hidden");
            hidden.setChecked(current.showHidden);
            hidden.setOnCheckedChangeListener((button, value) -> {
                Repository.prefs(activity).edit().putBoolean("main.show_hidden", value).apply();
                changed.run();
            });
        }
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(activity);
        scroll.addView(content);
        if (ObricGlass.available(activity)) {
            showNativeSheet(activity, scroll);
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        sheet.setContentView(scroll);
        sheet.getBehavior().setDraggable(true);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.setDismissWithAnimation(true);
        if (!MainStyle.liquid(activity)) {
            sheet.show();
            return;
        }
        if (sheet.getWindow() != null)
            sheet.getWindow().setWindowAnimations(0);
        // The Material sheet window fills the screen. A separate, non-interactive
        // blur window follows just the sheet, so the rest of the page stays sharp.
        ScopedWindowBlur backdrop = ScopedWindowBlur.create(activity, 28);
        if (backdrop != null)
            sheet.setOnDismissListener(dialog -> backdrop.dismiss());
        sheet.setOnShowListener(dialog -> {
            View panel = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (panel != null) {
                GlassPanels.attach(panel, 28, ObricGlass.WINDOW_BACKGROUND, true);
                // Animate the panel itself so the blur follows it from the first frame.
                panel.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (panel.getHeight() <= 0)
                            return true;
                        panel.getViewTreeObserver().removeOnPreDrawListener(this);
                        panel.setTranslationY(panel.getHeight());
                        android.animation.ValueAnimator entrance = android.animation.ValueAnimator.ofFloat(
                                panel.getHeight(), 0);
                        entrance.setDuration(280);
                        entrance.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
                        entrance.addUpdateListener(
                                animation -> panel.setTranslationY((float) animation.getAnimatedValue()));
                        entrance.start();
                        return true;
                    }
                });
                if (backdrop != null)
                    backdrop.track(panel);
            }
        });
        try {
            sheet.show();
        } catch (RuntimeException failure) {
            if (backdrop != null)
                backdrop.dismiss();
            throw failure;
        }
    }

    private static void showNativeSheet(Activity activity, View content) {
        ViewGroup root = activity.findViewById(android.R.id.content);
        androidx.coordinatorlayout.widget.CoordinatorLayout overlay = new androidx.coordinatorlayout.widget.CoordinatorLayout(
                activity) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        View panel = findViewWithTag("display-sheet-panel");
                        if (panel != null)
                            BottomSheetBehavior.from(panel).setState(BottomSheetBehavior.STATE_HIDDEN);
                    }
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        overlay.setFocusableInTouchMode(true);
        View scrim = new View(activity);
        scrim.setBackgroundColor(0x33000000);
        overlay.addView(scrim, new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(-1, -1));
        FrameLayout panel = new FrameLayout(activity);
        panel.setTag("display-sheet-panel");
        panel.addView(content, new FrameLayout.LayoutParams(-1, -2));
        GlassPanels.attach(panel, 28, ObricGlass.VIEW_BACKGROUND, true);
        BottomSheetBehavior<FrameLayout> behavior = new BottomSheetBehavior<>();
        behavior.setHideable(true);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params = new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                -1, -2);
        params.gravity = Gravity.BOTTOM;
        params.setBehavior(behavior);
        overlay.addView(panel, params);
        scrim.setOnClickListener(v -> behavior.setState(BottomSheetBehavior.STATE_HIDDEN));
        androidx.activity.OnBackPressedCallback back = new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        };
        if (activity instanceof androidx.activity.ComponentActivity)
            ((androidx.activity.ComponentActivity) activity).getOnBackPressedDispatcher().addCallback(
                    (androidx.activity.ComponentActivity) activity, back);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int state) {
                if (state == BottomSheetBehavior.STATE_HIDDEN) {
                    back.remove();
                    if (overlay.getParent() instanceof ViewGroup)
                        ((ViewGroup) overlay.getParent()).removeView(overlay);
                }
            }

            @Override
            public void onSlide(View bottomSheet, float slideOffset) {
            }
        });
        root.addView(overlay, new ViewGroup.LayoutParams(-1, -1));
        overlay.requestFocus();
        panel.post(() -> {
            if (overlay.isAttachedToWindow())
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
    }

    private static void slider(Activity activity, LinearLayout content, String label, int min, int max,
            int step, int value, int reset, java.util.function.IntConsumer changed) {
        Ui.space(content, 12);
        Slider slider = new Slider(activity);
        slider.setContentDescription(label);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(step);
        slider.setValue(value);
        Ui.styleSlider(slider);
        TextView indicator = Ui.text(activity, String.valueOf(value), 13, R.color.muted);
        slider.addOnChangeListener((control, number, fromUser) -> {
            indicator.setText(String.valueOf(Math.round(number)));
            changed.accept(Math.round(number));
        });
        Ui.sliderRow(content, label, slider, indicator, reset);
        Ui.space(content, 12);
    }
}
