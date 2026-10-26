package dev.habitdock;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public abstract class ThemedActivity extends AppCompatActivity {
    private String appearance;
    private boolean recreating;
    boolean wallpaperContent;
    WallpaperBack wallpaperBack;
    WallpaperBlur wallpaperBlur;
    private final android.os.Handler appearanceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshAppearance = () -> {
        if (!isFinishing() && !isDestroyed() && !recreateForAppearance())
            applyPageAppearance();
    };
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener appearanceChanges = (prefs,
            key) -> {
        if (key == null || key.startsWith("appearance.") || key.startsWith("background.")
                || key.equals("main.background") || key.equals("main.transparency") || key.equals("main.blur")) {
            appearanceHandler.removeCallbacks(refreshAppearance);
            appearanceHandler.post(refreshAppearance);
        }
    };
    @Override
    protected void onCreate(Bundle state) {
        Appearance.apply(this);
        boolean nativeWindow = WallpaperStage.nativePreview(this)
                || MainStyle.load(this).background == MainStyle.WALLPAPER;
        if (nativeWindow)
            getTheme().applyStyle(R.style.NativeWallpaperWindow, true);
        super.onCreate(state);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // AppCompat may restore the base theme while applying the night configuration.
        Appearance.apply(this);
        if (nativeWindow)
            getTheme().applyStyle(R.style.NativeWallpaperWindow, true);
        getWindow().setFormat(WallpaperStage.usesImage(this) && !WallpaperStage.nativePreview(this)
                ? android.graphics.PixelFormat.OPAQUE
                : android.graphics.PixelFormat.TRANSLUCENT);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        if (WallpaperStage.nativePreview(this) || MainStyle.load(this).background == MainStyle.WALLPAPER)
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        else
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        appearance = visualKey();
        wallpaperBlur = new WallpaperBlur(this);
        wallpaperBack = new WallpaperBack(this, state);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        applyPageAppearance();
        wallpaperBack.resumed();
    }

    private void applyPageAppearance() {
        WallpaperStage.refresh(this);
        syncWallpaperWindow();
        wallpaperBlur.set(wallpaperBlurRadius());
        applySystemBars();
        blurAvailabilityChanged();
    }

    void applySystemBars() {
        Ui.systemBars(this);
    }

    boolean nativeWallpaperPreview() {
        return false;
    }

    boolean wallpaperScene() {
        return wallpaperContent;
    }

    int wallpaperBlurRadius() {
        MainStyle style = MainStyle.load(this);
        return style.background == MainStyle.WALLPAPER ? style.blur : 0;
    }

    void blurAvailabilityChanged() {
    }

    private String visualKey() {
        return Appearance.key(this) + ":" + MainStyle.load(this).background;
    }

    final void prepareReturnPreview() {
        if (!recreateForAppearance()) {
            WallpaperStage.refresh(this);
            refreshPageContent();
        }
    }

    void refreshPageContent() {
    }

    final void syncWallpaperWindow() {
        MainStyle style = MainStyle.load(this);
        boolean nativeWallpaper = WallpaperStage.nativePreview(this) || !MainStyle.imageMode(style.background)
                && (style.background == MainStyle.WALLPAPER || wallpaperContent);
        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        int format = nativeWallpaper ? android.graphics.PixelFormat.TRANSLUCENT : android.graphics.PixelFormat.OPAQUE;
        int flags = nativeWallpaper
                ? params.flags | android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
                : params.flags & ~android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (format != params.format || flags != params.flags) {
            params.format = format;
            params.flags = flags;
            getWindow().setAttributes(params);
        }
    }

    @Override
    public void startActivity(android.content.Intent intent, Bundle options) {
        super.startActivity(intent, options);
        if (wallpaperScene())
            overridePendingTransition(0, 0);
    }

    final boolean recreateForAppearance() {
        if (!recreating && !appearance.equals(visualKey())) {
            recreating = true;
            recreate();
        }
        return recreating;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        wallpaperBack.save(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        wallpaperBack.paused();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Repository.prefs(this).unregisterOnSharedPreferenceChangeListener(appearanceChanges);
        appearanceHandler.removeCallbacks(refreshAppearance);
        wallpaperBlur.stop();
        wallpaperBack.destroy();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Repository.prefs(this).registerOnSharedPreferenceChangeListener(appearanceChanges);
        recreateForAppearance();
        syncWallpaperWindow();
        wallpaperBlur.start();
    }

    @Override
    protected void onStop() {
        Repository.prefs(this).unregisterOnSharedPreferenceChangeListener(appearanceChanges);
        appearanceHandler.removeCallbacks(refreshAppearance);
        wallpaperBlur.stop();
        super.onStop();
    }
}
