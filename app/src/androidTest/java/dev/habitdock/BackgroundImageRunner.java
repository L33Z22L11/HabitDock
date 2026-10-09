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
            check(MainStyle.load(c).sameAs(style) && BackgroundImages.exists(c), "image mode and blur survive reload");
            current = startActivitySync(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            SystemClock.sleep(500);
            runOnMainSync(() -> {
                ViewGroup host = current.findViewById(android.R.id.content);
                android.widget.LinearLayout root = (android.widget.LinearLayout) host.getChildAt(0);
                WallpaperStage.prepareImage(current, root, style.toolbar(current), style.wallpaperVeil());
                Bitmap first = background(current), second = background(current);
                check(first.sameAs(second), "status bar paint cannot change image opacity on the next frame");
                WallpaperStage.prepareImage(current, root, Ui.color(current, R.color.page),
                        Ui.color(current, R.color.page));
                WallpaperStage.refresh(current);
                Bitmap returned = background(current);
                check(first.sameAs(returned),
                        "returning main background uses configured transparency instead of a theme veil");
                first.recycle();
                second.recycle();
                returned.recycle();
            });
            for (Class<?> type : new Class<?>[]{SettingsActivity.class, AppPickerActivity.class,
                    MainStyleActivity.class}) {
                open(type);
                ThemedActivity screen = (ThemedActivity) current;
                check(!screen.wallpaperScene(),
                        "image background is not a native wallpaper scene: " + type.getSimpleName());
                check((screen.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) == 0,
                        "image page has no wallpaper window flag: " + type.getSimpleName());
                check(screen.getWindow().getAttributes().format == PixelFormat.OPAQUE, "image window is opaque");
                check(!screen.wallpaperBack.callback.isEnabled(), "image page leaves predictive back to the platform");
                if (screen instanceof SettingsActivity || screen instanceof AppPickerActivity)
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
                check(screen.wallpaperScene() && screen.nativeWallpaperPreview(),
                        "desktop style preview always uses native wallpaper: " + type.getSimpleName());
                check((screen.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0,
                        "native preview keeps wallpaper flag in image mode");
                check(screen.getWindow().getAttributes().format == PixelFormat.TRANSLUCENT,
                        "native preview window remains translucent after resume");
                check(Color.alpha(Ui.color(screen, R.color.page)) == 218,
                        "native cutout page keeps app image theme surfaces");
                ViewGroup host = screen.findViewById(android.R.id.content);
                check(host.getChildAt(0).getBackground().getOpacity() == PixelFormat.TRANSLUCENT,
                        "native preview has transparent wallpaper holes");
                runOnMainSync(() -> {
                    View stage = screen.findViewById(android.R.id.content).findViewWithTag("wallpaper-preview-stage");
                    Rect visible = new Rect();
                    check(stage.getLocalVisibleRect(visible), "preview stage is visible");
                    ViewGroup frame = screen.findViewById(android.R.id.content);
                    ViewGroup root = (ViewGroup) frame.getChildAt(0);
                    root.offsetDescendantRectToMyCoords(stage, visible);
                    Bitmap pixels = background(screen);
                    check(Color.alpha(pixels.getPixel(visible.centerX(), visible.centerY())) == 0,
                            "only preview hole exposes real wallpaper");
                    check(Color.alpha(pixels.getPixel(pixels.getWidth() / 2, 0)) > 0,
                            "outside preview keeps application background");
                    pixels.recycle();
                    screen.prepareReturnPreview();
                });
                check((screen.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0,
                        "return preparation preserves native wallpaper");
            }
            result.putString("stream", "\nPASS: " + checks + " image background checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
        }
        finish(status, result);
    }
}
