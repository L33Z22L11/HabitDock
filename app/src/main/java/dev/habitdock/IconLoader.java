package dev.habitdock;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.concurrent.*;

final class IconLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(80);
    private static final ExecutorService WORK = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static void bind(Context context, String pkg, ImageView view) {
        bind(context, pkg, view, IconStyle.load(context));
    }

    static void bind(Context context, String pkg, ImageView view, IconStyle style) {
        int background = style.backgroundColor(context);
        String key = pkg + ":" + style.cacheKey(context);
        view.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.ic_app);
        Context app = context.getApplicationContext();
        WORK.execute(() -> {
            try {
                Drawable icon = app.getPackageManager().getApplicationIcon(pkg);
                Bitmap bitmap = WidgetIcons.style(WidgetIcons.source(icon), style.roundness, background);
                CACHE.put(key, bitmap);
                MAIN.post(() -> {
                    if (key.equals(view.getTag()))
                        view.setImageBitmap(bitmap);
                });
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            }
        });
    }
}
