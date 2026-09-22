package dev.habitdock;
import android.app.Application;
import android.content.res.Configuration;

public final class HabitApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        IconStyle.load(this);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        Repository.WORK.execute(() -> {
            try {
                HabitWidget.update(this, false);
            } catch (RuntimeException ignored) {
            }
        });
    }
}
