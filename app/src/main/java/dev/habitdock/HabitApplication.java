package dev.habitdock;
import android.app.Application;
import android.content.res.Configuration;

public final class HabitApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(Appearance.mode(this));
        IconStyle.load(this);
        android.content.IntentFilter packages = new android.content.IntentFilter();
        packages.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(android.content.Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED);
        packages.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(new IconPackReceiver(), packages, RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(new IconPackReceiver(), packages);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        Repository.WORK.execute(() -> {
            try {
                HabitWidget.repaint(this);
            } catch (RuntimeException ignored) {
            }
        });
    }
}
