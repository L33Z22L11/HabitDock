package dev.habitdock;

import android.app.*;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.slider.Slider;

/**
 * Disposable emulator: app palettes, wallpaper controls, and pack fallback
 * rendering.
 */
public final class AppearanceRunner extends Instrumentation {
    static int[] previewPalette(Context context, int style) {
        android.content.res.Configuration config = new android.content.res.Configuration(
                context.getResources().getConfiguration());
        int night = style == Appearance.LIGHT
                ? android.content.res.Configuration.UI_MODE_NIGHT_NO
                : style == Appearance.DARK
                        ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                        : android.content.res.Resources.getSystem().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        config.uiMode = (config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | night;
        Context preview = new android.view.ContextThemeWrapper(
                context.getApplicationContext().createConfigurationContext(config), R.style.AppTheme);
        if (style == Appearance.DEVICE)
            preview = DynamicColors.wrapContextIfAvailable(preview);
        else if (style == Appearance.CUSTOM)
            preview = DynamicColors.wrapContextIfAvailable(preview,
                    new DynamicColorsOptions.Builder().setContentBasedSource(Appearance.seed(context)).build());
        return new int[]{
                com.google.android.material.color.MaterialColors.getColor(preview,
                        com.google.android.material.R.attr.colorSurfaceContainer, 0xfff5f7f5),
                com.google.android.material.color.MaterialColors.getColor(preview,
                        com.google.android.material.R.attr.colorPrimary, 0xff246e5c),
                com.google.android.material.color.MaterialColors.getColor(preview,
                        com.google.android.material.R.attr.colorOutlineVariant, 0xff737873)};
    }

    private volatile Activity current;
    private int checks;
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        current = activity;
    }

    private void check(boolean ok, String label) {
        if (!ok)
            throw new AssertionError(label);
        checks++;
    }

    private View view(String tag) {
        return current.getWindow().getDecorView().findViewWithTag(tag);
    }

    private void select(int style) {
        int scheme = style == Appearance.DEVICE ? 1 : style == Appearance.CUSTOM ? 2 : 0;
        int mode = style == Appearance.LIGHT ? 1 : style == Appearance.DARK ? 2 : 0;
        chooseAppearance("app-scheme-", scheme);
        chooseAppearance("app-color-mode-", mode);
    }

    private void chooseAppearance(String prefix, int selected) {
        if (view(prefix + selected).isSelected())
            return;
        ActivityMonitor monitor = addMonitor(MainStyleActivity.class.getName(), null, false);
        runOnMainSync(() -> view(prefix + selected).performClick());
        Activity next = waitForMonitorWithTimeout(monitor, 8000);
        removeMonitor(monitor);
        check(next != null, "appearance selection recreates");
        current = next;
        waitForIdleSync();
        check(view(prefix + selected).isSelected(), "appearance selection persists");
    }

    private void choose(String label) {
        for (int attempt = 0; attempt < 60; attempt++) {
            android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null)
                for (android.view.accessibility.AccessibilityNodeInfo node : root
                        .findAccessibilityNodeInfosByText(label))
                    if (label.contentEquals(node.getText() == null ? "" : node.getText())) {
                        Rect bounds = new Rect();
                        node.getBoundsInScreen(bounds);
                        long down = SystemClock.uptimeMillis();
                        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                                    bounds.centerX(), bounds.centerY(), 0);
                            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                            getUiAutomation().injectInputEvent(event, true);
                            event.recycle();
                        }
                        SystemClock.sleep(300);
                        waitForIdleSync();
                        return;
                    }
            SystemClock.sleep(50);
        }
        throw new AssertionError("missing dialog choice " + label);
    }

    private int attr(Context c, int id) {
        return com.google.android.material.color.MaterialColors.getColor(c, id, 0);
    }

    private void defaultSurfaces() {
        check(attr(current, com.google.android.material.R.attr.colorSurfaceContainerHigh) == current
                .getColor(R.color.surface),
                "default dialog surface does not inherit device colors");
        check(attr(current, com.google.android.material.R.attr.colorSurfaceContainerHighest) == current
                .getColor(R.color.soft),
                "default switch surface does not inherit device colors");
        runOnMainSync(() -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(current).setTitle("配色验证")
                    .setSingleChoiceItems(new String[]{"选项一", "选项二"}, 0, (d, w) -> {
                    }).setNegativeButton("取消", null);
            Drawable background = builder.getBackground();
            check(background instanceof MaterialShapeDrawable
                    && ((MaterialShapeDrawable) background).getFillColor().getDefaultColor() == current
                            .getColor(R.color.surface),
                    "actual Material modal background uses application surface");
            AlertDialog dialog = builder.show();
            check(attr(dialog.getContext(), com.google.android.material.R.attr.colorPrimary) == current
                    .getColor(R.color.accent),
                    "modal selected controls use application accent");
            dialog.dismiss();
        });
    }

    private void screenshot(String name) throws Exception {
        SystemClock.sleep(350);
        Bitmap b = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(getTargetContext().getCacheDir(), name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        b.recycle();
    }

    public void onStart() {
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("emulator only");
            Context c = getTargetContext();
            Repository.prefs(c).edit().clear().putBoolean("appearance.dynamic", true)
                    .putInt("appearance.mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES).commit();
            check(Appearance.style(c) == Appearance.DEVICE
                    && Appearance.mode(c) == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                    "legacy device palette becomes one mode following system brightness");
            Repository.prefs(c).edit().putBoolean("appearance.dynamic", false).commit();
            check(Appearance.style(c) == Appearance.DARK
                    && Appearance.mode(c) == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES,
                    "legacy static dark choice preserves the dark appearance");
            Repository.prefs(c).edit().clear().commit();
            current = startActivitySync(new Intent(c, MainStyleActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            check(Appearance.style(c) == Appearance.DEVICE, "legacy default device preference preserved");
            select(Appearance.LIGHT);
            defaultSurfaces();
            check((current.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO, "light resources");
            select(Appearance.DARK);
            defaultSurfaces();
            check((current.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES, "dark resources");
            select(Appearance.DEVICE);
            int device = Ui.color(current, R.color.accent);
            check(device != current.getColor(R.color.accent), "device palette active");
            int[] palette = previewPalette(current, Appearance.DEVICE);
            check(attr(current, com.google.android.material.R.attr.colorSurfaceContainer) == palette[0],
                    "activity surfaces match independently resolved device palette");
            check(device == palette[1], "activity accent matches device swatch after AppCompat initialization");
            check(attr(current, android.R.attr.colorControlActivated) == device,
                    "framework picker divider follows device primary");
            check(attr(current, android.R.attr.colorControlNormal) == Ui.color(current, R.color.muted),
                    "framework controls follow device on-surface color");
            Bitmap source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            source.eraseColor(0xffa09080);
            source.setPixel(0, 0, Color.TRANSPARENT);
            IconStyle normal = new IconStyle(0, false, 0, "test.pack", 100, false, 40, 100);
            IconStyle vivid = new IconStyle(0, false, 0, "test.pack", 100, false, 40, 200);
            IconStyle gray = new IconStyle(0, false, 0, "test.pack", 100, false, 40, 0);
            check(WidgetIcons.style(current, source, normal, true) == source,
                    "default saturation preserves original bitmap");
            check(WidgetIcons.style(current, source, vivid, true) == source,
                    "adapted pack artwork keeps original color despite fallback adjustments");
            Bitmap boosted = WidgetIcons.style(current, source, vivid, false);
            Bitmap desaturated = WidgetIcons.style(current, source, gray, false);
            int boostedColor = boosted.getPixel(1, 1), grayColor = desaturated.getPixel(1, 1);
            check(Color.red(boostedColor) - Color.blue(boostedColor) > 32,
                    "saturation increases color separation only for fallback artwork");
            check(Math.abs(Color.red(grayColor) - Color.blue(grayColor)) <= 1,
                    "zero saturation renders grayscale fallback artwork");
            check(Color.alpha(boosted.getPixel(0, 0)) == 0 && Color.alpha(desaturated.getPixel(0, 0)) == 0,
                    "saturation preserves transparent edges");
            check(!normal.sameAs(vivid) && !normal.cacheKey(current).equals(vivid.cacheKey(current)),
                    "saturation invalidates style and icon cache");
            IconStyle dim = new IconStyle(0, false, 0, "test.pack", 100, false, 40, 100, 50);
            Bitmap dimmed = WidgetIcons.style(current, source, dim, false);
            check(Color.red(dimmed.getPixel(1, 1)) < Color.red(source.getPixel(1, 1)),
                    "fallback brightness dims artwork");
            check(WidgetIcons.style(current, source, dim, true) == source,
                    "fallback brightness leaves adapted artwork untouched");
            Bitmap tones = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            tones.eraseColor(Color.TRANSPARENT);
            tones.setPixel(0, 0, 0xff404040);
            tones.setPixel(1, 0, 0xffc0c0c0);
            IconStyle contrast = new IconStyle(0, false, 0, "test.pack", 100, false, 40, 100, 100, 200);
            Bitmap contrasted = WidgetIcons.style(current, tones, contrast, false);
            check(Color.red(contrasted.getPixel(0, 0)) < 64 && Color.red(contrasted.getPixel(1, 0)) > 192,
                    "contrast separates dark and light fallback tones around midgray");
            check(Color.alpha(contrasted.getPixel(0, 1)) == 0,
                    "contrast preserves transparent pixels");
            check(WidgetIcons.style(current, tones, contrast, true) == tones,
                    "contrast does not affect adapted pack artwork");
            contrasted.recycle();
            tones.recycle();
            runOnMainSync(() -> {
                Slider control = new Slider(current);
                LinearLayout form = Ui.column(current);
                Ui.sliderRow(form, "Contrast", control, Ui.text(current, "100%", 13, R.color.muted));
                check(control.getTrackHeight() == Ui.dp(current, 16)
                        && control.getThumbHeight() == Ui.dp(current, 32), "thick track and shorter handle");
                check(control.getLayoutParams().height - control.getThumbHeight() >= Ui.dp(current, 24),
                        "adjacent handles have vertical breathing room");
            });
            dimmed.recycle();
            boosted.recycle();
            desaturated.recycle();
            source.recycle();
            Repository.prefs(c).edit().putInt("appearance.seed", 0xffd13925).commit();
            select(Appearance.CUSTOM);
            check(Ui.color(current, R.color.accent) != device, "custom seed replaces device palette");
            screenshot("app-style-custom");
            select(Appearance.SYSTEM);
            defaultSurfaces();
            check(!Appearance.dynamic(c), "system mode uses app palette");
            select(Appearance.LIGHT);
            defaultSurfaces();
            runOnMainSync(() -> current.finish());
            new MainStyle(44, 16, MainStyle.WALLPAPER, 1, false).save(c);
            current = startActivitySync(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            SystemClock.sleep(1200);
            runOnMainSync(() -> {
                try {
                    java.lang.reflect.Field stamp = MainActivity.class.getDeclaredField("previewTime");
                    stamp.setAccessible(true);
                    java.lang.reflect.Method update = MainActivity.class.getDeclaredMethod("updatePreviewActions");
                    update.setAccessible(true);
                    for (long value : new long[]{0, 123456789L}) {
                        stamp.setLong(current, value);
                        update.invoke(current);
                        check(((TextView) view("recommend-time")).getCurrentTextColor() == Color.WHITE,
                                "timestamp stays white after state update");
                        for (String tag : new String[]{"preview-time", "return-now", "open-settings"})
                            check(((ImageButton) view(tag)).getImageTintList().getDefaultColor() == 0xebffffff,
                                    "wallpaper toolbar stays white: " + tag);
                    }
                    stamp.setLong(current, 0);
                    update.invoke(current);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                AppTile tile = new AppTile(current);
                tile.bind(new Repository.App(c.getPackageName(), "样例"),
                        new Predictor.Prediction(c.getPackageName(), 1, "常用"), IconStyle.defaults(), MainStyle.load(c),
                        false);
                check(((TextView) tile.findViewWithTag("app-label")).getShadowRadius() == 0
                        && ((TextView) tile.findViewWithTag("app-reason")).getShadowRadius() == 0,
                        "wallpaper text has no shadow");
            });
            screenshot("app-style-wallpaper-white");
            IconStyle packed = new IconStyle(20, false, 0, "dev.habitdock.test", 75);
            check(IconPacks.loadSource(c, c.getPackageName(), packed.pack).themed, "test pack covers main app");
            check(!IconPacks.loadSource(c, "com.android.settings", packed.pack).themed,
                    "missing pack drawable counts as fallback");
            check(IconPacks.styled(c, c.getPackageName(), packed).getWidth() == 128,
                    "covered pack icon remains original size");
            check(IconPacks.styled(c, "com.android.settings", packed).getWidth() == 171,
                    "fallback icon gets centered 75 percent inset");
            check(!packed.cacheKey(c).equals(new IconStyle(20, false, 0, packed.pack, 100).cacheKey(c)),
                    "fallback scaling invalidates icon cache");
            packed.save(c);
            check(IconStyle.load(c).sameAs(packed), "fallback scaling persists");
            new WidgetTheme(WidgetTheme.DEVICE, 40, 0).save(c, 0);
            Repository.prefs(c).edit().putInt("widget.preview_id", 0).commit();
            runOnMainSync(() -> current.finish());
            current = startActivitySync(new Intent(c, IconStyleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check(((WidgetSettingsActivity.Preview) view("icon-preview")).theme.mode == WidgetTheme.DEVICE,
                    "icon preview uses selected widget palette");
            runOnMainSync(() -> ((Slider) view("icon-fallback-scale")).setValue(65));
            check(IconStyle.load(c).fallbackScale == 65, "fallback slider saves immediately");
            screenshot("icon-fallback-scale");
            ActivityMonitor iconMonitor = addMonitor(IconStyleActivity.class.getName(), null, false);
            runOnMainSync(() -> current.recreate());
            current = waitForMonitorWithTimeout(iconMonitor, 8000);
            removeMonitor(iconMonitor);
            check(current != null && IconStyle.load(c).fallbackScale == 65, "fallback scale survives recreation");
            waitForIdleSync();
            runOnMainSync(() -> ((Slider) view("icon-roundness")).setValue(30));
            check(IconStyle.load(c).fallbackScale == 65, "roundness edits preserve fallback scale");
            runOnMainSync(() -> view("icon-style-save").performClick());
            choose("恢复上次");
            check(IconStyle.load(c).sameAs(packed), "restore preserves original fallback scale across recreation");

            runOnMainSync(() -> {
                view("icon-corner-1").performClick();
                ((Slider) view("icon-exponent")).setValue(5.2f);
            });
            IconStyle curved = IconStyle.load(c);
            check(curved.continuous && curved.exponent == 52 && curved.fallbackScale == packed.fallbackScale,
                    "continuous shape and parameter save without changing fallback scale");
            check(view("icon-roundness").getVisibility() == View.GONE && view("icon-exponent").isShown(),
                    "continuous shape shows its own parameter");
            check(!curved.cacheKey(c).equals(packed.cacheKey(c)), "continuous shape invalidates icon cache");
            Bitmap square = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            square.eraseColor(Color.RED);
            Bitmap circle = WidgetIcons.style(c, square, new IconStyle(0, false, 0, "", 100, true, 20));
            Bitmap squircle = WidgetIcons.style(c, square, new IconStyle(0, false, 0, "", 100, true, 40));
            Bitmap boxy = WidgetIcons.style(c, square, new IconStyle(0, false, 0, "", 100, true, 80));
            check(Color.alpha(circle.getPixel(14, 14)) == 0 && squircle.getPixel(14, 14) == Color.RED,
                    "continuous curve parameter changes the visible corner contour");
            check(Color.alpha(squircle.getPixel(4, 4)) == 0 && boxy.getPixel(8, 8) == Color.RED,
                    "larger exponent makes the contour squarer");
            check(squircle.getPixel(64, 64) == Color.RED && Color.alpha(squircle.getPixel(0, 0)) == 0,
                    "continuous shape preserves artwork and clips corner pixels");
            IconStyle filledCurve = new IconStyle(0, true, Color.GREEN, "", 100, true, 40);
            Bitmap transparent = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            Bitmap filled = WidgetIcons.style(c, transparent, filledCurve);
            check(filled.getPixel(64, 64) == Color.GREEN && Color.alpha(filled.getPixel(0, 0)) == 0,
                    "custom background fill obeys the same continuous contour");
            screenshot("icon-continuous");
            ActivityMonitor curveMonitor = addMonitor(IconStyleActivity.class.getName(), null, false);
            runOnMainSync(() -> current.recreate());
            current = waitForMonitorWithTimeout(curveMonitor, 8000);
            removeMonitor(curveMonitor);
            check(current != null && IconStyle.load(c).sameAs(curved), "continuous shape survives recreation");
            waitForIdleSync();
            runOnMainSync(() -> view("icon-style-save").performClick());
            choose("恢复上次");
            check(IconStyle.load(c).sameAs(packed), "original shape restores after recreation");

            for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                Configuration config = new Configuration(c.getResources().getConfiguration());
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
                Context host = c.createConfigurationContext(config);
                runOnMainSync(() -> {
                    RemoteViews remote = new RemoteViews(c.getPackageName(), R.layout.widget_more);
                    WidgetTheme theme = new WidgetTheme(WidgetTheme.DEVICE, 40, 0);
                    theme.applyForeground(host, remote, R.id.more_icon, "setColorFilter");
                    theme.applyForeground(host, remote, R.id.more_time, "setTextColor");
                    View rendered = remote.apply(host, new FrameLayout(host));
                    int expected = host.getColor(mode == Configuration.UI_MODE_NIGHT_NO
                            ? android.R.color.system_accent1_800
                            : android.R.color.system_accent1_100);
                    check(((TextView) rendered.findViewById(R.id.more_time)).getCurrentTextColor() == expected,
                            "widget time uses host device tonal color");
                    android.graphics.PorterDuffColorFilter filter = (android.graphics.PorterDuffColorFilter) ((ImageView) rendered
                            .findViewById(R.id.more_icon)).getColorFilter();
                    check(filter != null && filter.equals(new android.graphics.PorterDuffColorFilter(expected,
                            android.graphics.PorterDuff.Mode.SRC_ATOP)), "widget dots use host device tonal color");
                });
            }
            result.putString("stream", "\nPASS: " + checks + " application palette/wallpaper/fallback checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
        } finally {
            if (current != null)
                runOnMainSync(() -> current.finish());
        }
        finish(status, result);
    }
}
