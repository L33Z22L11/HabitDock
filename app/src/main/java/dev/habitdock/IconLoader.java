package dev.habitdock;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

final class IconLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(80);
    // Recent visible cells take precedence over cells recycled during a fast
    // scroll.
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<Runnable>() {
                @Override
                public boolean offer(Runnable task) {
                    return offerFirst(task);
                }
            });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static void bind(Context context, String pkg, ImageView view) {
        bind(context, pkg, view, IconStyle.load(context));
    }

    static void bind(Context context, String pkg, ImageView view, IconStyle style) {
        String key = pkg + ":" + style.cacheKey(context);
        Request previous = (Request) view.getTag(R.id.icon_load_request);
        if (previous != null && !previous.cancelled && key.equals(previous.key))
            return;
        if (previous != null) {
            previous.cancelled = true;
            WORK.remove(previous);
        }
        view.setTag(R.id.icon_load_request, null);
        view.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            if (!(view.getDrawable() instanceof BitmapDrawable)
                    || ((BitmapDrawable) view.getDrawable()).getBitmap() != cached)
                view.setImageDrawable(new BitmapDrawable(view.getResources(), cached));
            return;
        }
        // Some older ImageView implementations retain their last resource while
        // reusing a BitmapDrawable. Clear it before installing the placeholder.
        view.setImageDrawable(null);
        view.setImageResource(R.drawable.ic_app);
        // Freeze the theme matching this cache key while the worker renders.
        Context app = context.getApplicationContext().createConfigurationContext(
                new android.content.res.Configuration(context.getResources().getConfiguration()));
        Request request = new Request(app, pkg, key, style, view);
        view.setTag(R.id.icon_load_request, request);
        WORK.execute(request);
    }

    private static final class Request implements Runnable {
        private final Context context;
        private final String pkg, key;
        private final IconStyle style;
        private final WeakReference<ImageView> target;
        private volatile boolean cancelled;

        Request(Context context, String pkg, String key, IconStyle style, ImageView target) {
            this.context = context;
            this.pkg = pkg;
            this.key = key;
            this.style = style;
            this.target = new WeakReference<>(target);
        }

        @Override
        public void run() {
            if (cancelled || target.get() == null)
                return;
            try {
                Bitmap bitmap = IconPacks.styled(context, pkg, style);
                CACHE.put(key, bitmap);
                MAIN.post(() -> {
                    ImageView view = target.get();
                    if (!cancelled && view != null && view.getTag(R.id.icon_load_request) == this
                            && key.equals(view.getTag())) {
                        // ImageView.setImageBitmap can reuse its previous BitmapDrawable.
                        // Give recycled views a fresh drawable so their alpha/display
                        // state cannot be carried over from the previous application.
                        view.setImageDrawable(new BitmapDrawable(view.getResources(), bitmap));
                        view.setTag(R.id.icon_load_request, null);
                    }
                });
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                MAIN.post(() -> {
                    ImageView view = target.get();
                    if (view != null && view.getTag(R.id.icon_load_request) == this)
                        view.setTag(R.id.icon_load_request, null);
                });
            }
        }
    }
}
