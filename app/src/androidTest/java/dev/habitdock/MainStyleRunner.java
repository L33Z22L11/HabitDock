package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.slider.Slider;
import java.util.*;

/** Disposable emulator: main-only appearance and privacy boundaries. */
public final class MainStyleRunner extends Instrumentation {
    private Activity current;
    private int checks;
    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    private void check(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
        checks++;
    }

    private View view(String tag) {
        View value = current.getWindow().getDecorView().findViewWithTag(tag);
        if (value == null && android.os.Build.VERSION.SDK_INT >= 29)
            for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                value = root.findViewWithTag(tag);
                if (value != null)
                    return value;
            }
        return value;
    }

    private void screenshot(String name) throws Exception {
        SystemClock.sleep(600);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(getTargetContext().getCacheDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    private void dragChoice(String tag, int from, int to, boolean cancel) {
        check(view(tag).getWidth() < ((View) view(tag).getParent()).getWidth(),
                "segmented control leaves room for its label instead of stretching across the row");
        runOnMainSync(() -> view(tag).requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                view(tag).getWidth(), view(tag).getHeight()), true));
        SystemClock.sleep(350);
        final float[] points = new float[3];
        runOnMainSync(() -> {
            View control = view(tag);
            int[] xy = new int[2];
            control.getLocationOnScreen(xy);
            float segment = (control.getWidth() - Ui.dp(current, 8)) / 3f;
            points[0] = xy[0] + Ui.dp(current, 4) + segment * (from + .5f);
            points[1] = xy[0] + Ui.dp(current, 4) + segment * (to + .5f);
            points[2] = xy[1] + control.getHeight() / 2f;
        });
        long down = SystemClock.uptimeMillis();
        for (int step = 0; step <= 13; step++) {
            int action = step == 0
                    ? MotionEvent.ACTION_DOWN
                    : step == 13
                            ? cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP
                            : MotionEvent.ACTION_MOVE;
            float x = points[0] + (points[1] - points[0]) * Math.min(1, step / 12f);
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, points[2], 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            if (!getUiAutomation().injectInputEvent(event, true))
                throw new AssertionError("segmented control touch rejected");
            event.recycle();
            SystemClock.sleep(20);
        }
        waitForIdleSync();
        SystemClock.sleep(200);
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("emulator only");
            Context context = getTargetContext();
            Repository.prefs(context).edit().clear().commit();
            MainStyle.defaults().save(context);
            WidgetPreferences widget = WidgetPreferences.load(context,
                    android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
            WidgetTheme theme = WidgetTheme.load(context, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
            current = startActivitySync(new Intent(context, MainStyleActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            SystemClock.sleep(1500);
            screenshot("main-style-default");
            runOnMainSync(() -> {
                ViewGroup control = (ViewGroup) view("main-reasons");
                View thumb = control.getChildAt(0);
                check(thumb.getWidth() > 0 && thumb.getHeight() > 0, "segmented thumb has size on its first frame");
                Bitmap painted = Bitmap.createBitmap(control.getWidth(), control.getHeight(), Bitmap.Config.ARGB_8888);
                control.draw(new android.graphics.Canvas(painted));
                int selectedColor = ((android.graphics.drawable.GradientDrawable) thumb.getBackground())
                        .getColor().getDefaultColor();
                int containerColor = ((android.graphics.drawable.GradientDrawable) control.getBackground())
                        .getColor().getDefaultColor();
                int sampleX = Math.round(thumb.getX() + thumb.getWidth() / 2f);
                int sampleY = thumb.getBottom() - Ui.dp(current, 4);
                check(selectedColor != containerColor && painted.getPixel(sampleX, sampleY) == selectedColor,
                        "selected segment paints a visible theme-token thumb before interaction");
                painted.recycle();
                check(((Slider) view("main-icon-size")).getValue() == 44, "larger default icons");
                check(((Slider) view("main-label-size")).getValue() == 16, "larger default labels");
                ((Slider) view("main-icon-size")).setValue(60);
                ((Slider) view("main-label-size")).setValue(20);
                view("background-mode-1").performClick();
                view("main-show-hidden").performClick();
                view("main-transparency-action").performClick();
            });
            waitForIdleSync();
            runOnMainSync(() -> {
                ((Slider) view("main-transparency")).setValue(65);
            });
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            dragChoice("main-reasons", 1, 2, false);
            check(MainStyle.load(context).reasons == 2 && view("main-reason-2").isSelected(),
                    "dragging reasons selects and saves detailed mode");
            dragChoice("main-reasons", 2, 0, true);
            check(MainStyle.load(context).reasons == 2 && view("main-reason-2").isSelected(),
                    "cancelled reason drag preserves the selected mode");
            runOnMainSync(() -> view("background-mode-0").performClick());
            check(MainStyle.load(context).background == MainStyle.APP,
                    "wallpaper off inherits the app appearance");
            check(new MainStyle(44, 16, 1, 1, false).background == MainStyle.APP,
                    "legacy wallpaper palette migrates to app appearance");
            runOnMainSync(() -> view("background-mode-1").performClick());
            screenshot("main-style-segmented");
            MainStyle edited = MainStyle.load(context);
            check(edited.iconDp == 60 && edited.labelSp == 20 && edited.background == 2 && edited.showHidden,
                    "controls save immediately");
            check(edited.transparency == 65, "wallpaper transparency saved with other controls");
            check(android.graphics.Color.alpha(edited.wallpaperVeil()) == 89, "veil matches transparency");
            check(edited.ink(context) == 0xebffffff, "wallpaper labels use translucent white");
            check(new MainStyle(44, 16, 2, 1, false, 100).wallpaperVeil() == 0,
                    "fully transparent wallpaper has no veil");
            check(widget
                    .sameAs(WidgetPreferences.load(context, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID))
                    && theme.sameAs(WidgetTheme.load(context, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)),
                    "main controls preserve widget appearance");
            ActivityMonitor monitor = addMonitor(MainStyleActivity.class.getName(), null, false);
            runOnMainSync(() -> current.recreate());
            current = waitForMonitorWithTimeout(monitor, 8000);
            removeMonitor(monitor);
            check(current != null, "recreation completes");
            waitForIdleSync();
            check(MainStyle.load(context).sameAs(edited), "appearance survives recreation");
            screenshot("main-style-wallpaper");
            runOnMainSync(() -> {
                Map<String, Repository.App> apps = new HashMap<>();
                apps.put("visible", new Repository.App("visible", "Visible"));
                apps.put("hidden", new Repository.App("hidden", "Hidden"));
                Repository.prefs(context).edit().putStringSet("hidden", new HashSet<>(Collections.singleton("hidden")))
                        .commit();
                Predictor.Prediction visible = new Predictor.Prediction("visible", 1, "常用", "近7天打开12次");
                Predictor.Prediction hidden = new Predictor.Prediction("hidden", 2, "此时常用", "近7天打开24次");
                Repository.Snapshot data = new Repository.Snapshot(apps, List.of(visible),
                        List.of(hidden, visible), 2, 1, true);
                check(MainStyle.defaults().visiblePredictions(context, data).size() == 1, "hidden omitted by default");
                check(edited.visiblePredictions(context, data).size() == 2 && data.predictions.size() == 1,
                        "show hidden leaves recommendation input untouched");
                Predictor.Prediction p = data.predictions.get(0);
                AppTile tile = new AppTile(current);
                tile.bind(apps.get("visible"), p, IconStyle.defaults(), new MainStyle(60, 20, 2, 0, false), true);
                check(tile.findViewWithTag("pin-badge").getVisibility() == View.VISIBLE, "pinned badge visible");
                check(tile.findViewWithTag("app-reason").getVisibility() == View.GONE, "reason can be hidden");
                check(tile.getChildAt(0).getLayoutParams().width == Ui.dp(current, 60), "icon size applied to tile");
                tile.bind(apps.get("visible"), p, IconStyle.defaults(), MainStyle.defaults(), false);
                check(tile.findViewWithTag("pin-badge").getVisibility() == View.GONE,
                        "recycled tile clears pinned badge");
                check(((TextView) tile.findViewWithTag("app-reason")).getText().toString().equals("常用"),
                        "brief reason shown");
                tile.bind(apps.get("visible"), p, IconStyle.defaults(), new MainStyle(44, 16, 0, 2, false), false);
                check(((TextView) tile.findViewWithTag("app-reason")).getText().toString().equals("近7天打开12次"),
                        "detailed reason shown");
                Repository.prefs(context).edit().putStringSet("hidden", Set.of("visible")).commit();
                check(MainStyle.defaults().visiblePredictions(context, data).get(0) == hidden,
                        "return preview applies the current hidden selection to cached scores");
                Repository.prefs(context).edit().putStringSet("hidden", Set.of("hidden")).commit();
                check(edited.visiblePredictions(context, data).get(0) == hidden,
                        "hidden app retains its real rank and score instead of being appended");
                tile.bind(apps.get("hidden"), hidden, IconStyle.defaults(), new MainStyle(44, 16, 0, 0, true), false);
                check(tile.findViewWithTag("hidden-badge").getVisibility() == View.VISIBLE
                        && tile.findViewWithTag("app-label").getAlpha() == .5f
                        && ((android.view.ViewGroup) tile.getChildAt(0)).getChildAt(0).getAlpha() == .5f,
                        "hidden app has translucent text and icon with an independent badge");
                check(tile.findViewWithTag("app-reason").getVisibility() == View.GONE,
                        "hidden badge does not override the reason switch");
                tile.bind(apps.get("visible"), visible, IconStyle.defaults(), MainStyle.defaults(), false);
                check(tile.findViewWithTag("hidden-badge").getVisibility() == View.GONE
                        && tile.findViewWithTag("app-label").getAlpha() == 1f
                        && ((android.view.ViewGroup) tile.getChildAt(0)).getChildAt(0).getAlpha() == 1f,
                        "recycled visible app clears hidden opacity and badge");
                MainStyle.defaults().save(context);
                current.finish();
            });
            current = startActivitySync(
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            screenshot("main-personalization-default");
            Activity home = current;
            for (int mode : new int[]{2, 0, 2}) {
                ActivityMonitor editorMonitor = addMonitor(MainStyleActivity.class.getName(), null, false);
                runOnMainSync(() -> home.startActivity(new Intent(home, MainStyleActivity.class)));
                current = waitForMonitorWithTimeout(editorMonitor, 8000);
                removeMonitor(editorMonitor);
                check(current != null, "style editor opens above existing home");
                waitForIdleSync();
                runOnMainSync(() -> view("background-mode-" + (mode == 2 ? 1 : 0)).performClick());
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                SystemClock.sleep(500);
                waitForIdleSync();
                current = home;
                runOnMainSync(() -> {
                    int color = ((android.graphics.drawable.ColorDrawable) view("main-toolbar").getBackground())
                            .getColor();
                    check(android.graphics.Color.alpha(color) == (mode == 2 ? 18 : 255),
                            "toolbar follows wallpaper mode without restarting");
                    check(!home.isFinishing(), "same home survives live background changes");
                });
            }
            check(MainStyle.defaults().rowDp(context) == 68, "default rows use moderate even spacing");
            runOnMainSync(() -> {
                AppTile bare = new AppTile(home);
                bare.bind(new Repository.App("visible", "Visible"), new Predictor.Prediction("visible", 1, "常用"),
                        IconStyle.defaults(), MainStyle.load(context), false);
                android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(100, 100,
                        android.graphics.Bitmap.Config.ARGB_8888);
                bare.getBackground().setBounds(0, 0, 100, 100);
                bare.getBackground().draw(new android.graphics.Canvas(pixels));
                check(android.graphics.Color.alpha(pixels.getPixel(50, 50)) == 0,
                        "application tile has no resting card fill");
                pixels.recycle();
                ColorSettingRow adaptive = new ColorSettingRow(home, "图标填充", () -> {
                });
                adaptive.bind(0, true);
                check(adaptive.value.getText().toString().equals("自适应"), "adaptive color has a readable value");
                adaptive.bind(0xff123456, false);
                check(adaptive.value.getText().toString().equals("#123456"), "custom color shows exact hex value");
            });
            screenshot("main-wallpaper-live");
            runOnMainSync(() -> current.finish());
            current = startActivitySync(
                    new Intent(context, IconStyleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            ActivityMonitor iconMonitor = addMonitor(IconStyleActivity.class.getName(), null, false);
            runOnMainSync(() -> current
                    .setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            current = waitForMonitorWithTimeout(iconMonitor, 8000);
            removeMonitor(iconMonitor);
            check(current != null, "landscape icon editor recreated");
            waitForIdleSync();
            SystemClock.sleep(600);
            final ScrollView[] scrolling = new ScrollView[1];
            runOnMainSync(() -> {
                ViewParent parent = view("icon-preview").getParent();
                while (!(parent instanceof ScrollView))
                    parent = parent.getParent();
                scrolling[0] = (ScrollView) parent;
                check(scrolling[0].canScrollVertically(1), "whole icon body scrolls in constrained height");
                check((current.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0,
                        "wallpaper anchored to window, not scrolling content");
                scrolling[0].smoothScrollBy(0, Ui.dp(current, 160));
            });
            SystemClock.sleep(500);
            check(scrolling[0].getScrollY() > 0, "preview and controls scroll together");
            screenshot("icon-body-scrolled");
            runOnMainSync(() -> current.finish());
            result.putString("stream", "\nPASS: " + checks + " main appearance/privacy checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error) + "\n");
        }
        finish(status, result);
    }
}
