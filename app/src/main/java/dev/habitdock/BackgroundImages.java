package dev.habitdock;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Private, immutable background snapshots. No file permission is needed for a
 * picked image.
 */
final class BackgroundImages {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };

    static String selected(Context context) {
        return selected(context, MainStyle.load(context).background);
    }

    static String selected(Context context, int mode) {
        return Repository.prefs(context).getString(mode == MainStyle.CUSTOM_IMAGE
                ? "background.custom_image"
                : "background.wallpaper_image", "");
    }

    static void select(Context context, int mode, String id) {
        Repository.prefs(context).edit().putString(mode == MainStyle.CUSTOM_IMAGE
                ? "background.custom_image"
                : "background.wallpaper_image", id).apply();
    }

    private static File file(Context context, String id) {
        if (!id.matches("[a-f0-9-]+\\.png"))
            return null;
        return new File(new File(context.getFilesDir(), "backgrounds"), id);
    }

    static boolean exists(Context context) {
        File image = file(context, selected(context));
        return image != null && image.isFile();
    }

    static void importImage(Context context, Uri uri, Consumer<String> done, Consumer<String> failed) {
        Context app = context.getApplicationContext();
        WORK.execute(() -> {
            try {
                Bitmap bitmap;
                if (Build.VERSION.SDK_INT >= 28) {
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.getContentResolver(), uri),
                            (decoder, info, source) -> {
                                float factor = Math.min(1f,
                                        2048f / Math.max(info.getSize().getWidth(), info.getSize().getHeight()));
                                decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * factor)),
                                        Math.max(1, Math.round(info.getSize().getHeight() * factor)));
                                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            });
                } else {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                        BitmapFactory.decodeStream(input, null, bounds);
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                        throw new IOException("无法读取这张图片");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    options.inSampleSize = 1;
                    while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048)
                        options.inSampleSize *= 2;
                    try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                        bitmap = BitmapFactory.decodeStream(input, null, options);
                    }
                }
                if (bitmap == null)
                    throw new IOException("无法读取这张图片");
                String id;
                try {
                    id = save(app, bitmap);
                } finally {
                    bitmap.recycle();
                }
                MAIN.post(() -> done.accept(id));
            } catch (IOException | SecurityException | IllegalArgumentException error) {
                MAIN.post(() -> failed.accept("图片读取失败，请重新选择"));
            }
        });
    }

    @android.annotation.SuppressLint("MissingPermission") // Caller requests access; revocation is caught below.
    static void importWallpaper(Context context, Consumer<String> done, Consumer<String> failed) {
        Context app = context.getApplicationContext();
        WORK.execute(() -> {
            WallpaperManager manager = WallpaperManager.getInstance(app);
            try {
                if (manager.getWallpaperInfo() != null)
                    throw new IOException("动态壁纸无法读取，请选择图片");
                Drawable drawable = manager.getDrawable();
                if (drawable == null)
                    throw new IOException("没有可读取的壁纸，请选择图片");
                int width = Math.max(1, drawable.getIntrinsicWidth()),
                        height = Math.max(1, drawable.getIntrinsicHeight());
                float scale = Math.min(1f, 2048f / Math.max(width, height));
                Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                drawable.draw(new Canvas(bitmap));
                String id;
                try {
                    id = save(app, bitmap);
                } finally {
                    bitmap.recycle();
                }
                MAIN.post(() -> done.accept(id));
            } catch (SecurityException error) {
                MAIN.post(() -> failed.accept("系统未允许读取当前壁纸，可授权后重试或选择图片"));
            } catch (IOException | IllegalArgumentException error) {
                MAIN.post(() -> failed.accept(error.getMessage() == null ? "壁纸读取失败，请选择图片" : error.getMessage()));
            } finally {
                manager.forgetLoadedWallpaper();
            }
        });
    }

    private static String save(Context context, Bitmap bitmap) throws IOException {
        File directory = new File(context.getFilesDir(), "backgrounds");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("无法保存背景图片");
        String id = UUID.randomUUID() + ".png";
        File destination = new File(directory, id);
        try (OutputStream output = new FileOutputStream(destination)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                throw new IOException("无法保存背景图片");
        } catch (IOException error) {
            destination.delete();
            throw error;
        }
        storeSeed(context, id, bitmap);
        return id;
    }

    private static void storeSeed(Context context, String id, Bitmap bitmap) {
        if (Build.VERSION.SDK_INT >= 27) {
            int seed = android.app.WallpaperColors.fromBitmap(bitmap).getPrimaryColor().toArgb();
            Repository.prefs(context).edit().putInt("background.seed." + id, seed).apply();
        }
    }

    static Integer paletteSeed(Context context, String id) {
        File image = file(context, id);
        if (image == null || !image.isFile() || Build.VERSION.SDK_INT < 27)
            return null;
        String key = "background.seed." + id;
        if (!Repository.prefs(context).contains(key)) {
            // One-time migration for existing images; decode only a small thumbnail.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 16;
            Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), options);
            if (bitmap == null)
                return null;
            storeSeed(context, id, bitmap);
            bitmap.recycle();
        }
        return Repository.prefs(context).getInt(key, 0xff246e5c);
    }

    static Bitmap cached(String id, int blur) {
        return CACHE.get(id + ":" + blur);
    }

    static Future<?> load(Context context, String id, int blur, Consumer<Bitmap> done) {
        Context app = context.getApplicationContext();
        return WORK.submit(() -> {
            String key = id + ":" + blur;
            Bitmap bitmap = CACHE.get(key);
            if (bitmap == null) {
                File image = file(app, id);
                if (image == null)
                    return;
                bitmap = CACHE.get(id + ":0");
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeFile(image.getAbsolutePath());
                    if (bitmap == null)
                        return;
                    CACHE.put(id + ":0", bitmap);
                }
                if (blur > 0) {
                    float factor = Math.min(1f, 720f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
                    Bitmap small = Bitmap.createScaledBitmap(bitmap,
                            Math.max(1, Math.round(bitmap.getWidth() * factor)),
                            Math.max(1, Math.round(bitmap.getHeight() * factor)), true);
                    int w = small.getWidth(), h = small.getHeight();
                    int[] pixels = new int[w * h];
                    small.getPixels(pixels, 0, w, 0, 0, w, h);
                    int radius = Math.max(1, Math.round(blur * .32f));
                    int[] blurred = BackgroundBlur.apply(pixels, w, h, radius);
                    if (small != bitmap)
                        small.recycle();
                    bitmap = Bitmap.createBitmap(blurred, w, h, Bitmap.Config.ARGB_8888);
                    CACHE.put(key, bitmap);
                }
            }
            Bitmap result = bitmap;
            MAIN.post(() -> done.accept(result));
        });
    }
}
