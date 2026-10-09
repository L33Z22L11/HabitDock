package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;

/**
 * Disposable emulator: actual edge gestures and transparent scene composition.
 */
public final class WallpaperBackRunner extends Instrumentation {
    private volatile Activity current;
    private int checks, preservedCommits;
    private boolean exposedOldFrame;
    private long down;
    private int width, height;
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

    @Override
    public void callActivityOnDestroy(Activity activity) {
        View scene = activity.getWindow().getDecorView().findViewWithTag("wallpaper-back-scene");
        boolean committed = activity.isFinishing() && scene != null;
        super.callActivityOnDestroy(activity);
        if (committed) {
            ViewGroup host = activity.findViewById(android.R.id.content);
            boolean retained = activity.getWindow().getDecorView().findViewWithTag("wallpaper-back-scene") == scene
                    && host.getChildAt(0).getVisibility() == View.INVISIBLE;
            exposedOldFrame |= !retained;
            preservedCommits++;
        }
    }

    private void check(boolean ok, String label) {
        if (!ok)
            throw new AssertionError(label);
        checks++;
    }

    private View view(String tag) {
        return current.getWindow().getDecorView().findViewWithTag(tag);
    }

    private void tap(String tag) {
        runOnMainSync(() -> view(tag).performClick());
        SystemClock.sleep(1000);
        waitForIdleSync();
    }

    private void screenshot(String name) throws Exception {
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(getTargetContext().getCacheDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    private void inject(int action, float x) {
        inject(action, x, true);
    }

    private void inject(int action, float x, boolean sync) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, height * .55f, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        if (!TouchInput.inject(getUiAutomation(), event, sync))
            throw new AssertionError("edge input rejected");
        event.recycle();
    }

    private void startGesture() {
        for (int attempt = 0; attempt < 100 && !current.hasWindowFocus(); attempt++)
            SystemClock.sleep(50);
        if (!current.hasWindowFocus())
            throw new AssertionError("gesture target has no window focus: " + current.getClass().getSimpleName());
        SystemClock.sleep(300);
        width = current.getResources().getDisplayMetrics().widthPixels;
        height = current.getResources().getDisplayMetrics().heightPixels;
        TouchInput.prepare(getUiAutomation());
        down = SystemClock.uptimeMillis();
        // Queue the initial crossing without waiting on a rendered frame per event.
        // A loaded emulator can otherwise stretch this into an edge long-press.
        inject(MotionEvent.ACTION_DOWN, 1, false);
        for (int step = 1; step <= 3; step++) {
            SystemClock.sleep(12);
            inject(MotionEvent.ACTION_MOVE, width * .12f * step / 3, false);
        }
        for (int step = 0; step < 10; step++) {
            SystemClock.sleep(80);
            inject(MotionEvent.ACTION_MOVE, width * .12f);
        }
        for (int step = 1; step <= 12; step++) {
            SystemClock.sleep(60);
            inject(MotionEvent.ACTION_MOVE, width * (.12f + .43f * step / 12));
        }
        SystemClock.sleep(200);
    }

    private void endGesture(boolean commit) {
        if (!commit) {
            for (int step = 11; step >= 0; step--) {
                SystemClock.sleep(22);
                inject(MotionEvent.ACTION_MOVE, Math.max(1, width * .55f * step / 12));
            }
            SystemClock.sleep(80);
        }
        inject(MotionEvent.ACTION_UP, commit ? width * .55f : 1);
        // Native shell animations finish asynchronously after the activity resumes.
        SystemClock.sleep(1500);
        waitForIdleSync();
    }

    private boolean wallpaper(Activity activity) {
        return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0;
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
            current = startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            SystemClock.sleep(500);
            Activity home = current;
            tap("open-settings");
            Activity settings = current;
            check(settings instanceof SettingsActivity && !wallpaper(settings) && !wallpaper(home),
                    "ordinary pages without wallpaper keep ordinary windows");
            check(!((ThemedActivity) settings).getOnBackPressedDispatcher().hasEnabledCallbacks(),
                    "ordinary settings retains system predictive back");
            screenshot("back-native-before");
            startGesture();
            screenshot("back-native-mid");
            endGesture(false);
            check(current == settings, "actual native edge cancel keeps settings");
            screenshot("back-native-canceled");
            startGesture();
            endGesture(true);
            check(current == home, "actual native edge commit returns to original home");
            tap("open-settings");
            Activity pickerParent = current;
            for (String destination : new String[]{"open-exclusions", "open-pinned"}) {
                tap(destination);
                Activity picker = current;
                check(picker instanceof AppPickerActivity && picker.getTaskId() == pickerParent.getTaskId(),
                        "both pickers are sibling activity destinations");
                check(!((ThemedActivity) picker).getOnBackPressedDispatcher().hasEnabledCallbacks()
                        && !wallpaper(picker),
                        "both pickers use native predictive return when search is closed");
                startGesture();
                endGesture(false);
                check(current == picker, "picker native gesture cancellation preserves its page");
                startGesture();
                endGesture(true);
                check(current == pickerParent, "picker native gesture returns directly to the same settings");
            }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(1500);
            waitForIdleSync();
            check(current == home, "settings still returns to the original home");
            runOnMainSync(() -> new MainStyle(44, 16, MainStyle.WALLPAPER, 1, false).save(context));
            tap("open-settings");
            settings = current;
            check(((ThemedActivity) settings).wallpaperBack.callback.isEnabled(),
                    "wallpaper destination uses scene back only on that edge");
            check(wallpaper(settings), "settings retains its prepared wallpaper surface after entry");
            startGesture();
            WallpaperBack.Scene scene = (WallpaperBack.Scene) view("wallpaper-back-scene");
            check(scene != null && scene.progress > .15f,
                    "actual edge gesture drives visible scene progress: " + (scene == null ? -1 : scene.progress));
            screenshot("back-to-wallpaper-mid");
            endGesture(false);
            check(current == settings && view("wallpaper-back-scene") == null && wallpaper(settings),
                    "scene cancel restores content without dropping the wallpaper surface");
            tap("open-main-style");
            Activity editor = current;
            Map<String, ?> prefs = new HashMap<>(Repository.prefs(context).getAll());
            screenshot("back-wallpaper-editor-before");
            startGesture();
            WallpaperBack.Scene editorScene = (WallpaperBack.Scene) view("wallpaper-back-scene");
            check(editorScene != null && editorScene.progress > .15f,
                    "wallpaper editor has visible actual gesture progress: "
                            + (editorScene == null ? -1 : editorScene.progress));
            screenshot("back-wallpaper-editor-mid");
            endGesture(false);
            check(current == editor && view("wallpaper-back-scene") == null && wallpaper(editor),
                    "editor cancel restores its wallpaper scene");
            check(prefs.equals(Repository.prefs(context).getAll()), "gesture cancellation does not change preferences");
            startGesture();
            endGesture(true);
            check(current == settings, "wallpaper editor commit returns to original settings");
            screenshot("back-wallpaper-committed");
            check(preservedCommits > 0 && !exposedOldFrame,
                    "destroyed committing window retains its final scene without exposing the old page");
            startGesture();
            endGesture(true);
            check(current == home && preservedCommits >= 2 && !exposedOldFrame,
                    "wallpaper settings-to-home commit retains the final frame through destruction");
            check(wallpaper(home), "returning home preserves its wallpaper window");
            Activity nativeHome = home;
            runOnMainSync(() -> {
                android.widget.FrameLayout frame = nativeHome.findViewById(android.R.id.content);
                View root = frame.getChildAt(0);
                Bitmap stale = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                stale.eraseColor(Color.MAGENTA);
                root.getBackground().draw(new Canvas(stale));
                int pixel = stale.getPixel(stale.getWidth() / 2, stale.getHeight() / 2);
                check(pixel == MainStyle.load(context).wallpaperVeil(),
                        "native wallpaper repaint replaces old text pixels and preserves only the configured veil");
                stale.recycle();
            });
            runOnMainSync(() -> {
                Bitmap under = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
                under.eraseColor(Color.RED);
                Bitmap front = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
                Canvas frontCanvas = new Canvas(front);
                Paint blue = new Paint();
                blue.setColor(Color.BLUE);
                frontCanvas.drawRect(0, 0, 200, 25, blue);
                WallpaperBack.Scene test = new WallpaperBack.Scene(current, under, front, 1);
                test.layout(0, 0, 200, 200);
                test.progress = .6f;
                Bitmap composed = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
                test.draw(new Canvas(composed));
                check(composed.getPixel(0, 100) == Color.RED, "previous page appears outside moving page boundary");
                check(Color.alpha(composed.getPixel(100, 100)) == 0,
                        "previous page never leaks into transparent wallpaper holes");
                composed.eraseColor(Color.TRANSPARENT);
                test.progress = 0;
                test.exit = .2f;
                test.draw(new Canvas(composed));
                check(composed.getPixel(0, 100) == Color.RED && Color.alpha(composed.getPixel(175, 100)) == 0,
                        "entry animation also clips the previous page out of wallpaper holes");
                test.exit = 0;
                test.progress = .6f;
                WallpaperBack.Scene reverse = new WallpaperBack.Scene(current, under, front, -1);
                reverse.layout(0, 0, 200, 200);
                reverse.progress = .6f;
                composed.eraseColor(Color.TRANSPARENT);
                reverse.draw(new Canvas(composed));
                check(composed.getPixel(199, 100) == Color.RED && Color.alpha(composed.getPixel(80, 100)) == 0,
                        "right-edge scene uses the same complete page mask");
                check(MainStyle.defaults().rowDp(current) == 68,
                        "default row spacing is between former dense and loose layouts");
            });
            result.putString("stream", "\nPASS: " + checks + " actual predictive-back/input/composition checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            try {
                screenshot("back-failure");
            } catch (Exception ignored) {
            }
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error) + "\n");
        }
        finish(status, result);
    }
}
