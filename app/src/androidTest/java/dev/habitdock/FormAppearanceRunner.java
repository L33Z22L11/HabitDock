package dev.habitdock;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.android.material.slider.Slider;

/** Isolated emulator regression for shared surfaces, rows and live blur. */
public final class FormAppearanceRunner extends Instrumentation {
    private int checks;
    private Activity current;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    private void check(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
        checks++;
    }

    private Activity open(Class<?> type) {
        if (current != null)
            runOnMainSync(() -> current.finish());
        current = startActivitySync(new Intent(getTargetContext(), type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        return current;
    }

    private void verifyReset(Activity screen, String tag, float changed, float expected) {
        runOnMainSync(() -> {
            Slider slider = screen.findViewById(android.R.id.content).findViewWithTag(tag);
            slider.setValue(changed);
            View reset = ((View) slider.getParent()).findViewWithTag(tag + "-reset");
            check(reset.getVisibility() == View.VISIBLE, tag + " exposes reset after editing");
            reset.performClick();
            check(slider.getValue() == expected && reset.getVisibility() == View.INVISIBLE,
                    tag + " restores its actual page default");
        });
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int status = Activity.RESULT_OK;
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("disposable emulator only");
            Context context = getTargetContext();
            Repository.prefs(context).edit().clear().commit();
            MainStyle wallpaper = new MainStyle(44, 16, MainStyle.WALLPAPER, 1, false, 65, 30);
            wallpaper.save(context);
            for (boolean dark : new boolean[]{false, true}) {
                Configuration config = new Configuration(context.getResources().getConfiguration());
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                Context themed = context.createConfigurationContext(config);
                int veil = wallpaper.wallpaperVeil(themed);
                check(Color.alpha(veil) == 89, "veil preserves configured opacity");
                check((veil | 0xff000000) == (wallpaper.page(themed) | 0xff000000),
                        "veil uses the themed surface instead of pure black or white");
            }
            for (Class<?> type : new Class<?>[]{MainActivity.class, SettingsActivity.class, AppPickerActivity.class,
                    MainStyleActivity.class, IconStyleActivity.class, WidgetSettingsActivity.class}) {
                ThemedActivity screen = (ThemedActivity) open(type);
                for (int transparency : new int[]{0, 65, 100}) {
                    MainStyle value = new MainStyle(44, 16, MainStyle.WALLPAPER, 1, false, transparency, 30);
                    runOnMainSync(() -> value.save(screen));
                    waitForIdleSync();
                    runOnMainSync(() -> {
                        check(screen.wallpaperBlurRadius() == 30, "every page inherits native blur");
                        check(screen.wallpaperContent, "every page uses the native background");
                        ViewGroup host = screen.findViewById(android.R.id.content);
                        View root = host.getChildAt(0);
                        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                        root.getBackground().draw(new Canvas(bitmap));
                        int expected = screen instanceof MainActivity
                                ? value.wallpaperVeil(screen)
                                : androidx.core.graphics.ColorUtils.compositeColors(value.extraWallpaperVeil(screen),
                                        value.wallpaperVeil(screen));
                        int actual = bitmap.getPixel(bitmap.getWidth() / 2,
                                bitmap.getHeight() - root.getPaddingBottom() - Ui.dp(screen, 10));
                        check(Color.alpha(actual) == Color.alpha(expected),
                                "controls compose the extra veil over global opacity, including both endpoints");
                        if (Color.alpha(expected) > 0)
                            check(Math.abs(Color.red(actual) - Color.red(expected)) <= 1
                                    && Math.abs(Color.green(actual) - Color.green(expected)) <= 1
                                    && Math.abs(Color.blue(actual) - Color.blue(expected)) <= 1,
                                    "surface retains theme color");
                        check(Color.alpha(bitmap.getPixel(bitmap.getWidth() / 2,
                                root.getPaddingTop() + Ui.dp(screen, 20))) == Color.alpha(
                                        androidx.core.graphics.ColorUtils.compositeColors(
                                                value.extraWallpaperVeil(screen), value.wallpaperVeil(screen))),
                                "toolbar adds its veil on top of global opacity");
                        View preview = WallpaperStage.previewStage((LinearLayout) root);
                        if (preview != null) {
                            Rect bounds = new Rect();
                            check(preview.getLocalVisibleRect(bounds), "native preview is visible");
                            ((ViewGroup) root).offsetDescendantRectToMyCoords(preview, bounds);
                            check(Color.alpha(bitmap.getPixel(bounds.centerX(), bounds.centerY())) == 0,
                                    "native preview cuts through both theme veils");
                        }
                        bitmap.recycle();
                    });
                }
            }

            Activity icons = open(IconStyleActivity.class);
            verifyReset(icons, "icon-roundness", 80, 40);
            verifyReset(icons, "icon-exponent", 6, 4);
            verifyReset(icons, "icon-brightness", 160, 100);
            check(IconStyle.load(context).sameAs(IconStyle.defaults()), "icon resets persist defaults");
            Activity widgets = open(WidgetSettingsActivity.class);
            verifyReset(widgets, "widget-scale", 60, 82);
            verifyReset(widgets, "widget-transparency", 80, 60);
            MainStyleActivity editor = (MainStyleActivity) open(MainStyleActivity.class);
            verifyReset(editor, "main-icon-size", 64, 44);
            verifyReset(editor, "main-label-size", 20, 16);
            runOnMainSync(() -> {
                editor.findViewById(android.R.id.content).findViewWithTag("main-blur-action").performClick();
            });
            waitForIdleSync();
            runOnMainSync(() -> {
                Slider blur = null;
                for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    Slider found = window.findViewWithTag("main-blur");
                    if (found != null)
                        blur = found;
                }
                check(blur != null, "blur popup exists");
                LinearLayout body = Ui.column(editor);
                Slider slider = new Slider(editor);
                slider.setTag("test-slider");
                slider.setValueTo(200);
                slider.setValue(100);
                Ui.sliderRow(body, "饱和度", slider, Ui.text(editor, "100%", 13, R.color.muted), 100f);
                FormRow row = (FormRow) slider.getParent();
                View reset = row.findViewWithTag("test-slider-reset");
                check(reset.getVisibility() == View.INVISIBLE, "default slider hides reset");
                slider.setValue(160);
                check(reset.getVisibility() == View.VISIBLE, "changed slider exposes reset");
                reset.performClick();
                check(slider.getValue() == 100 && reset.getVisibility() == View.INVISIBLE,
                        "slider reset restores and hides itself");
                check(reset.getPaddingLeft() == Ui.dp(editor, 16), "reset keeps its 16dp icon and full touch target");
                int alignedStart = -1;
                for (String title : new String[]{"明度", "饱和度", "圆角比例", "背景透明度"}) {
                    Slider aligned = new Slider(editor);
                    aligned.setValueTo(200);
                    aligned.setValue(100);
                    aligned.setTag("alignment-" + title);
                    Ui.sliderRow(body, title, aligned, Ui.text(editor, "100%", 13, R.color.muted), 100f);
                    FormRow alignedRow = (FormRow) aligned.getParent();
                    alignedRow.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(editor, 320), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    alignedRow.layout(0, 0, alignedRow.getMeasuredWidth(), alignedRow.getMeasuredHeight());
                    check(alignedRow.getOrientation() == LinearLayout.HORIZONTAL, "normal width keeps slider inline");
                    check(alignedStart < 0 || aligned.getLeft() == alignedStart,
                            "two to five character labels align sliders");
                    alignedStart = aligned.getLeft();
                    aligned.setValue(160);
                    check(alignedRow.findViewWithTag("alignment-" + title + "-reset").getWidth() == Ui.dp(editor, 48),
                            "compact spacing preserves reset touch width");
                    check(aligned.getRight() <= alignedRow.getWidth(), "slider leaves room for trailing value");
                }
                MainStyle brief = new MainStyle(44, 16, MainStyle.APP, 1, false);
                MainStyle detailed = new MainStyle(44, 16, MainStyle.APP, 2, false);
                check(brief.rowDp(editor) == detailed.rowDp(editor), "brief and detailed reasons share row height");
                AppTile tile = new AppTile(editor);
                Repository.App example = new Repository.App(editor.getPackageName(), "示例应用");
                Predictor.Prediction prediction = new Predictor.Prediction(example.pkg, 1, "此时常用");
                int reasonHeight = -1;
                for (MainStyle variant : new MainStyle[]{brief, detailed, brief}) {
                    tile.bind(example, prediction, IconStyle.defaults(), variant, false);
                    tile.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(editor, 180), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(Ui.dp(editor, variant.rowDp(editor)),
                                    View.MeasureSpec.EXACTLY));
                    tile.layout(0, 0, tile.getMeasuredWidth(), tile.getMeasuredHeight());
                    TextView reason = tile.findViewWithTag("app-reason");
                    check(reasonHeight < 0 || reason.getHeight() == reasonHeight,
                            "reason area keeps its height when switching detail");
                    check(reason.getMaxLines() == (variant.reasons == 2 ? 2 : 1), "brief reason still uses one line");
                    reasonHeight = reason.getHeight();
                }
                int[] selected = {-1};
                SlidingChoice choice = new SlidingChoice(editor, new String[]{"关", "原生", "兼容", "自定义"},
                        "test-choice-", value -> selected[0] = value);
                choice.setTag("test-choice");
                choice.select(2, false);
                choice.addRow(body, "壁纸背景", 0);
                FormRow choiceRow = (FormRow) choice.getParent();
                choiceRow.findViewWithTag("test-choice-reset").performClick();
                check(choice.selection() == 0 && selected[0] == 0, "segment reset uses the normal save callback");
                for (int width : new int[]{280, 420}) {
                    choiceRow.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(editor, width), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    choiceRow.layout(0, 0, choiceRow.getMeasuredWidth(), choiceRow.getMeasuredHeight());
                    check(choice.getRight() <= choiceRow.getWidth(), "segment fits narrow and wide rows");
                }
            });
            Bitmap unblurred = null;
            for (int radius : new int[]{0, 35, 0, 60}) {
                runOnMainSync(() -> {
                    Slider blur = null;
                    for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        Slider found = window.findViewWithTag("main-blur");
                        if (found != null)
                            blur = found;
                    }
                    check(blur != null && blur.isAttachedToWindow(), "popup remains open across zero");
                    blur.setValue(radius);
                    check(MainStyle.load(editor).blur == radius, "popup persists blur changes");

                });
                waitForIdleSync();
                check(editor.wallpaperBlur.appliedRadius() == (editor.wallpaperBlur.available() ? radius : 0),
                        "global observer updates blur before popup dismissal");
                SystemClock.sleep(350);
                Bitmap frame = getUiAutomation().takeScreenshot();
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                        new java.io.File(context.getCacheDir(), "native-blur-" + radius + ".png"))) {
                    frame.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                if (radius == 0) {
                    if (unblurred != null)
                        unblurred.recycle();
                    unblurred = frame;
                } else {
                    if (radius == 60 && editor.wallpaperBlur.available()) {
                        int changed = 0;
                        for (int y = Ui.dp(editor, 110); y < Ui.dp(editor, 160); y += 2)
                            for (int x = Ui.dp(editor, 20); x < frame.getWidth() - Ui.dp(editor, 20); x += 2)
                                if (frame.getPixel(x, y) != unblurred.getPixel(x, y))
                                    changed++;
                        check(changed > 100, "wallpaper pixels update from zero blur while popup stays open");
                    }
                    frame.recycle();
                }
            }
            if (unblurred != null)
                unblurred.recycle();
            result.putString("stream", "\nPASS: " + checks + " shared appearance and form checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
        }
        finish(status, result);
    }
}
