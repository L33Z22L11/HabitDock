package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Disposable-emulator window, dialog and translucent item regressions. */
public final class WallpaperWindowRunner extends Instrumentation {
    private volatile Activity current;
    private int checks;
    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        current = activity;
    }

    private int backgroundPixel(android.graphics.drawable.Drawable background, int width, int height) {
        Bitmap pixels = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        background.setBounds(0, 0, width, height);
        background.draw(new Canvas(pixels));
        int pixel = pixels.getPixel(width / 2, height / 2);
        pixels.recycle();
        return pixel;
    }

    private void stableFrames(Bitmap reference, String phase) throws Exception {
        for (int frame = 0; frame < 8; frame++) {
            Bitmap pixels = getUiAutomation().takeScreenshot();
            int dark = 0, samples = 0;
            long delta = 0;
            int x = 4;
            for (int y = pixels.getHeight() / 5; y < pixels.getHeight() * 4 / 5; y += 8) {
                int before = reference.getPixel(x, y), after = pixels.getPixel(x, y);
                if (Color.red(after) + Color.green(after) + Color.blue(after) < 45)
                    dark++;
                delta += Math.abs(Color.red(before) - Color.red(after))
                        + Math.abs(Color.green(before) - Color.green(after))
                        + Math.abs(Color.blue(before) - Color.blue(after));
                samples++;
            }
            if (dark > samples / 2 || delta > samples * 45) {
                try (OutputStream out = new FileOutputStream(
                        new File(getTargetContext().getCacheDir(), phase + "-failure.png"))) {
                    pixels.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            }
            check(dark < samples / 2, phase + " has no black background frame");
            check(delta < samples * 45, phase + " keeps wallpaper blur stable: " + delta / samples);
            pixels.recycle();
        }
    }

    private void check(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
        checks++;
    }

    private void open(Class<?> type) {
        ActivityMonitor monitor = addMonitor(type.getName(), null, false);
        runOnMainSync(() -> current.startActivity(new Intent(current, type)));
        current = waitForMonitorWithTimeout(monitor, 5000);
        removeMonitor(monitor);
        check(current != null, "destination opens");
        waitForIdleSync();
        SystemClock.sleep(400);
    }

    private void capture(String name) throws Exception {
        Bitmap pixels = getUiAutomation().takeScreenshot();
        try (OutputStream out = new FileOutputStream(new File(getTargetContext().getCacheDir(), name + ".png"))) {
            pixels.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        pixels.recycle();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("disposable emulator only");
            Context c = getTargetContext();
            Repository.prefs(c).edit().clear().commit();
            new MainStyle(44, 16, MainStyle.WALLPAPER, 1, false, 80, 60).save(c);
            current = startActivitySync(new Intent(c, GlassHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            HomeNavigation.select(this, current, 2);
            Activity settings = current;
            runOnMainSync(() -> settings.getWindow().getDecorView().findViewWithTag("refresh-interval").performClick());
            waitForIdleSync();
            for (int i = 0; i < 3; i++) {
                SystemClock.sleep(400);
                runOnMainSync(() -> {
                    boolean found = false;
                    for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if (root == settings.getWindow().getDecorView())
                            continue;
                        if (root.isShown() && root.hasWindowFocus()) {
                            WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
                            check((p.flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) == 0,
                                    "dialog does not claim wallpaper");
                            found = true;
                        }
                    }
                    check(found, "frequency dialog remains shown and focused with native blur");
                });
                capture("frequency-blur-" + i);
            }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            runOnMainSync(() -> {
                PickerCell cell = new PickerCell(settings);
                Repository.App sample = new Repository.App(c.getPackageName(), "Sample");
                cell.bind(sample, false, (button, selected) -> {
                });
                check(Color.alpha(backgroundPixel(cell.getBackground(), 120, 80)) == 0,
                        "unselected app row exposes wallpaper");
                cell.bind(sample, true, (button, selected) -> {
                });
                int selectedAlpha = Color.alpha(backgroundPixel(cell.getBackground(), 120, 80));
                check(selectedAlpha > 0 && selectedAlpha < 255, "selected app row remains translucent");
            });
            open(WidgetSettingsActivity.class);
            runOnMainSync(() -> {
                Spinner spinner = current.getWindow().getDecorView().findViewWithTag("widget-target");
                check(Color.alpha(backgroundPixel(spinner.getPopupBackground(), 160, 100)) < 255,
                        "component menu background is translucent");
                View selected = spinner.getAdapter().getDropDownView(spinner.getSelectedItemPosition(), null, spinner);
                check(selected.getBackground() != null
                        && Color.alpha(backgroundPixel(selected.getBackground(), 160, 100)) < 255,
                        "selected component row remains translucent");
                spinner.performClick();
            });
            waitForIdleSync();
            capture("component-selector");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            runOnMainSync(() -> current.finish());
            waitForIdleSync();
            for (int radius : new int[]{0, 60}) {
                new MainStyle(44, 16, MainStyle.WALLPAPER, 1, false, 80, radius).save(c);
                waitForIdleSync();
                SystemClock.sleep(400);
                for (Class<?> destination : new Class<?>[]{AppPickerActivity.class, WidgetSettingsActivity.class,
                        MainStyleActivity.class}) {
                    Activity parent = current;
                    Bitmap reference = getUiAutomation().takeScreenshot();
                    capture("reference-" + radius + "-" + destination.getSimpleName());
                    ActivityMonitor monitor = addMonitor(destination.getName(), null, false);
                    runOnMainSync(() -> parent.startActivity(new Intent(parent, destination)));
                    stableFrames(reference, "forward-" + radius + "-" + destination.getSimpleName());
                    Activity child = waitForMonitorWithTimeout(monitor, 5000);
                    removeMonitor(monitor);
                    check(child != null, "transition reaches destination");
                    current = child;
                    runOnMainSync(() -> ((ThemedActivity) child).getOnBackPressedDispatcher().onBackPressed());
                    stableFrames(reference, "back-" + radius + "-" + destination.getSimpleName());
                    waitForIdleSync();
                    check(current == parent, "returns to existing settings page");
                    reference.recycle();
                }
            }
            result.putString("stream", "\nPASS: " + checks + " wallpaper window checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
        }
        finish(status, result);
    }
}
