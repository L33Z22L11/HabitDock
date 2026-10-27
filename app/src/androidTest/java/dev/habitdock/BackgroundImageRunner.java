package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * Disposable emulator: cached images, whole-app windows and native return
 * eligibility.
 */
public final class BackgroundImageRunner extends Instrumentation {
    private Activity current;
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

    private void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }

    private void open(Class<?> type) {
        ActivityMonitor monitor = addMonitor(type.getName(), null, false);
        runOnMainSync(() -> current.startActivity(new Intent(current, type)));
        Activity next = waitForMonitorWithTimeout(monitor, 8000);
        removeMonitor(monitor);
        check(next != null, "destination starts: " + type.getSimpleName());
        waitForIdleSync();
        SystemClock.sleep(400);
        current = next;
    }

    private Bitmap load(Context c, String id, int blur) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Bitmap[] bitmap = {null};
        BackgroundImages.load(c, id, blur, value -> {
            bitmap[0] = value;
            latch.countDown();
        });
        check(latch.await(10, TimeUnit.SECONDS) && bitmap[0] != null, "private background decodes at blur " + blur);
        return bitmap[0];
    }

    private Bitmap background(Activity activity) {
        ViewGroup host = activity.findViewById(android.R.id.content);
        View root = host.getChildAt(0);
        Bitmap result = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.getBackground().draw(new Canvas(result));
        return result;
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
            File image = new File(c.getCacheDir(), "background-source.png");
            Bitmap original = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(original);
            canvas.drawColor(Color.BLUE);
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            canvas.drawRect(0, 0, 40, 60, paint);
            try (OutputStream out = new FileOutputStream(image)) {
                original.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            original.recycle();
            CountDownLatch imported = new CountDownLatch(1);
            String[] id = {null};
            BackgroundImages.importImage(c, Uri.fromFile(image), value -> {
                id[0] = value;
                imported.countDown();
            }, error -> imported.countDown());
            check(imported.await(10, TimeUnit.SECONDS) && id[0] != null,
                    "picked image is copied privately without broad storage access");
            check(BackgroundImages.paletteSeed(c, id[0]) != null, "import caches automatic palette seed");
            BackgroundImages.select(c, MainStyle.CUSTOM_IMAGE, id[0]);
            BackgroundImages.select(c, MainStyle.IMAGE, "saved-wallpaper.png");
            check(BackgroundImages.selected(c, MainStyle.IMAGE).equals("saved-wallpaper.png")
                    && BackgroundImages.selected(c, MainStyle.CUSTOM_IMAGE).equals(id[0]),
                    "wallpaper and custom images are independent");
            Bitmap sharp = load(c, id[0], 0), blurred = load(c, id[0], 60);
            check(sharp.getPixel(39, 30) != blurred.getPixel(39, 30), "blur softens the image boundary");
            check(sharp.getPixel(10, 30) == Color.RED, "blur keeps original image intact");
            MainStyle style = new MainStyle(44, 16, MainStyle.CUSTOM_IMAGE, 1, false, 80, 60);
            style.save(c);
            Integer imageSeed = Appearance.automaticSeed(c);
            BackgroundImages.select(c, MainStyle.IMAGE, id[0]);
            new MainStyle(44, 16, MainStyle.IMAGE, 1, false, 80, 60).save(c);
            check(imageSeed != null && imageSeed.equals(Appearance.automaticSeed(c)),
                    "compatibility wallpaper uses its cached image palette");
            int widgetColor = new WidgetTheme(WidgetTheme.DEVICE, 60, 0).surface(c);
            check(widgetColor != android.graphics.Color.TRANSPARENT, "widget resolves cached wallpaper palette");
            style.save(c);
            check(MainStyle.load(c).sameAs(style) && BackgroundImages.exists(c), "image mode and blur survive reload");
            current = startActivitySync(new Intent(c, AppPickerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            SystemClock.sleep(500);
            runOnMainSync(() -> {
                ViewGroup host = current.findViewById(android.R.id.content);
                android.widget.LinearLayout root = (android.widget.LinearLayout) host.getChildAt(0);
                WallpaperStage.prepareImage(current, root, style.toolbar(current), style.wallpaperVeil(current));
                Bitmap first = background(current), second = background(current);
                check(first.sameAs(second), "status bar paint cannot change image opacity on the next frame");
                WallpaperStage.prepareImage(current, root, Ui.color(current, R.color.page),
                        Ui.color(current, R.color.page));
                WallpaperStage.refresh(current);
                Bitmap returned = background(current);
                check(first.sameAs(returned),
                        "returning background uses configured transparency instead of a theme veil");
                first.recycle();
                second.recycle();
                returned.recycle();
            });
            for (Class<?> type : new Class<?>[]{AppPickerActivity.class, MainStyleActivity.class}) {
                open(type);
                ThemedActivity screen = (ThemedActivity) current;
                check(!screen.wallpaperScene(),
                        "image background is not a native wallpaper scene: " + type.getSimpleName());
                check((screen.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) == 0,
                        "image page has no wallpaper window flag: " + type.getSimpleName());
                check(screen.getWindow().getAttributes().format == PixelFormat.OPAQUE, "image window is opaque");
                check(!screen.wallpaperBack.callback.isEnabled(), "image page leaves predictive back to the platform");
                if (screen instanceof AppPickerActivity)
                    runOnMainSync(() -> {
                        Bitmap pixels = background(screen);
                        ViewGroup frame = screen.findViewById(android.R.id.content);
                        int top = frame.getChildAt(0).getPaddingTop();
                        if (top > 1)
                            check(pixels.getPixel(pixels.getWidth() / 4, 1) == pixels.getPixel(pixels.getWidth() / 4,
                                    top + 1),
                                    "status area does not add a second theme veil above the toolbar");
                        pixels.recycle();
                    });
                ViewGroup host = screen.findViewById(android.R.id.content);
                check(host.getChildAt(0).getBackground().getOpacity() == PixelFormat.OPAQUE,
                        "all application pages paint a complete background");
            }
            View blurAction = current.getWindow().getDecorView().findViewWithTag("main-blur-action");
            runOnMainSync(blurAction::performClick);
            waitForIdleSync();
            View blur = null;
            if (Build.VERSION.SDK_INT >= 29)
                for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    View found = window.findViewWithTag("main-blur");
                    if (found != null)
                        blur = found;
                }
            check(blur != null && blur.isEnabled(), "image blur does not depend on cross-window blur support");
            View selectedBlur = blur;
            runOnMainSync(() -> ((com.google.android.material.slider.Slider) selectedBlur).setValue(25));
            check(MainStyle.load(c).blur == 25, "image blur slider saves immediately");
            for (Class<?> type : new Class<?>[]{IconStyleActivity.class, WidgetSettingsActivity.class}) {
                open(type);
                ThemedActivity screen = (ThemedActivity) current;
                check(!screen.wallpaperScene(), "image previews share the selected image background");
                check((screen.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) == 0,
                        "image previews do not expose the native wallpaper");
                check(screen.getWindow().getAttributes().format == PixelFormat.OPAQUE,
                        "image preview window remains opaque after resume");
                runOnMainSync(() -> {
                    Bitmap pixels = background(screen);
                    check(Color.alpha(pixels.getPixel(pixels.getWidth() / 2, pixels.getHeight() / 2)) == 255,
                            "image preview paints its selected background");
                    pixels.recycle();
                    screen.prepareReturnPreview();
                });
            }
            for (int mode : new int[]{MainStyle.IMAGE, MainStyle.CUSTOM_IMAGE}) {
                for (Class<?> type : new Class<?>[]{AppPickerActivity.class}) {
                    new MainStyle(44, 16, mode, 1, false, 100, 60).save(c);
                    open(type);
                    runOnMainSync(() -> {
                        int[] pixels = new int[3];
                        int index = 0;
                        for (int transparency : new int[]{0, 50, 100}) {
                            new MainStyle(44, 16, mode, 1, false, transparency, 60).save(current);
                            WallpaperStage.refresh(current);
                            Bitmap frame = background(current);
                            pixels[index++] = frame.getPixel(frame.getWidth() / 2, frame.getHeight() * 3 / 4);
                            frame.recycle();
                        }
                        int surface = Ui.color(current, R.color.page);
                        check(Math.abs(Color.red(pixels[0]) - Color.red(surface)) <= 1
                                && Math.abs(Color.green(pixels[0]) - Color.green(surface)) <= 1
                                && Math.abs(Color.blue(pixels[0]) - Color.blue(surface)) <= 1,
                                "zero global transparency fully covers image, allowing 8-bit blend rounding");
                        check(pixels[0] != pixels[2], "image control background still responds to global transparency");
                        int expected = androidx.core.graphics.ColorUtils.blendARGB(pixels[0], pixels[2], .5f);
                        check(Math.abs(Color.red(pixels[1]) - Color.red(expected)) <= 2
                                && Math.abs(Color.green(pixels[1]) - Color.green(expected)) <= 2
                                && Math.abs(Color.blue(pixels[1]) - Color.blue(expected)) <= 2,
                                "image opacity composes with additional theme veil instead of replacing global value");
                    });
                }
            }
            for (int mode : new int[]{MainStyle.IMAGE, MainStyle.CUSTOM_IMAGE}) {
                for (Class<?> type : new Class<?>[]{IconStyleActivity.class, WidgetSettingsActivity.class,
                        MainStyleActivity.class}) {
                    runOnMainSync(() -> current.finish());
                    new MainStyle(44, 16, mode, 1, false, 100, 60).save(c);
                    current = startActivitySync(new Intent(c, type)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    waitForIdleSync();
                    SystemClock.sleep(400);
                    runOnMainSync(() -> {
                        ViewGroup host = current.findViewById(android.R.id.content);
                        android.widget.LinearLayout root = (android.widget.LinearLayout) host.getChildAt(0);
                        View preview = WallpaperStage.previewStage(root);
                        check(preview != null, "all three style pages register a preview stage");
                        Rect bounds = new Rect();
                        check(preview.getLocalVisibleRect(bounds),
                                "image preview has visible bounds: " + type.getSimpleName());
                        root.offsetDescendantRectToMyCoords(preview, bounds);
                        Bitmap clear = background(current);
                        int exposed = clear.getPixel(bounds.centerX(), bounds.centerY());
                        clear.recycle();
                        new MainStyle(44, 16, mode, 1, false, 0, 60).save(current);
                        WallpaperStage.refresh(current);
                        Bitmap covered = background(current);
                        check(covered.getPixel(bounds.centerX(), bounds.centerY()) == exposed,
                                "preview exposes selected image through both page veils");
                        covered.recycle();
                        if (current instanceof MainStyleActivity)
                            check(preview.getBackground() != null,
                                    "main preview paints its own list opacity above exposed image");
                    });
                }
            }
            result.putString("stream", "\nPASS: " + checks + " image background checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
        }
        finish(status, result);
    }
}
