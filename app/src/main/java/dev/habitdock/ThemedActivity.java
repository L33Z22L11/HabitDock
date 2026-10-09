package dev.habitdock;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public abstract class ThemedActivity extends AppCompatActivity {
    private String appearance;
    private boolean recreating;
    boolean wallpaperContent;
    WallpaperBack wallpaperBack;
    WallpaperBlur wallpaperBlur;
    @Override
    protected void onCreate(Bundle state) {
        Appearance.apply(this);
        super.onCreate(state);
        // AppCompat may restore the base theme while applying the night configuration.
        Appearance.apply(this);
        getWindow().setFormat(WallpaperStage.usesImage(this) && !nativeWallpaperPreview()
                ? android.graphics.PixelFormat.OPAQUE
                : android.graphics.PixelFormat.TRANSLUCENT);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        if (nativeWallpaperPreview() || MainStyle.load(this).background == MainStyle.WALLPAPER)
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
        syncWallpaperWindow();
        applySystemBars();
        WallpaperStage.refresh(this);
        wallpaperBack.resumed();
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
        return 0;
    }

    void blurAvailabilityChanged() {
    }

    private String visualKey() {
        return Appearance.key(this) + ":" + WallpaperStage.usesImage(this);
    }

    void prepareReturnPreview() {
        if (!recreateForAppearance()) {
            syncWallpaperWindow();
            WallpaperStage.refresh(this);
        }
    }

    private void syncWallpaperWindow() {
        MainStyle style = MainStyle.load(this);
        boolean nativeWallpaper = nativeWallpaperPreview() || !MainStyle.imageMode(style.background)
                && (style.background == MainStyle.WALLPAPER || wallpaperContent);
        if (nativeWallpaper) {
            getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        } else {
            getWindow().setFormat(android.graphics.PixelFormat.OPAQUE);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
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
        wallpaperBlur.stop();
        wallpaperBack.destroy();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        recreateForAppearance();
        syncWallpaperWindow();
        wallpaperBlur.start();
    }

    @Override
    protected void onStop() {
        wallpaperBlur.stop();
        super.onStop();
    }
}
