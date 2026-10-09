package dev.habitdock;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Draw native wallpaper previews or an opaque cached image across the
 * application.
 */
final class WallpaperStage {
    static boolean nativePreview(android.content.Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof ThemedActivity)
                return ((ThemedActivity) context).nativeWallpaperPreview();
            android.content.Context next = ((android.content.ContextWrapper) context).getBaseContext();
            if (next == context)
                break;
            context = next;
        }
        return false;
    }

    static boolean usesImage(android.content.Context context) {
        return MainStyle.imageMode(MainStyle.load(context).background);
    }

    static void prepare(Activity activity, LinearLayout root) {
        prepare(activity, root, Ui.color(activity, R.color.page));
    }

    static void prepare(Activity activity, LinearLayout root, int barsColor) {
        prepare(activity, root, barsColor, Color.TRANSPARENT);
    }

    static void prepare(Activity activity, LinearLayout root, int barsColor, int veilColor) {
        if (nativePreview(activity)) {
            prepareCutout(activity, root);
            return;
        }
        if (usesImage(activity)) {
            prepareImage(activity, root, barsColor, veilColor);
            return;
        }
        prepareNative(activity, root, barsColor, veilColor);
    }

    private static void prepareNative(Activity activity, LinearLayout root, int barsColor, int veilColor) {
        if (root.getBackground() instanceof ImageBackground)
            ((ImageBackground) root.getBackground()).cancel();
        if (activity instanceof ThemedActivity)
            ((ThemedActivity) activity).wallpaperContent = true;
        activity.getWindow().setFormat(PixelFormat.TRANSLUCENT);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        root.setBackground(new Drawable() {
            private final Paint paint = new Paint();
            @Override
            public void draw(Canvas canvas) {
                canvas.drawColor(veilColor, PorterDuff.Mode.SRC);
                paint.setColor(barsColor);
                canvas.drawRect(0, 0, root.getWidth(), root.getPaddingTop(), paint);
                canvas.drawRect(0, 0, root.getPaddingLeft(), root.getHeight(), paint);
                canvas.drawRect(root.getWidth() - root.getPaddingRight(), 0, root.getWidth(), root.getHeight(), paint);
                canvas.drawRect(0, root.getHeight() - root.getPaddingBottom(), root.getWidth(), root.getHeight(),
                        paint);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter filter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        });
    }

    static void prepareImage(Activity activity, LinearLayout root, int barsColor, int veilColor) {
        if (nativePreview(activity)) {
            prepareCutout(activity, root);
            return;
        }
        if (activity instanceof ThemedActivity)
            ((ThemedActivity) activity).wallpaperContent = false;
        activity.getWindow().setFormat(PixelFormat.OPAQUE);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        Drawable old = root.getBackground();
        if (old instanceof ImageBackground)
            ((ImageBackground) old).cancel();
        MainStyle style = MainStyle.load(activity);
        root.setBackground(new ImageBackground(activity, root, barsColor, veilColor, style.blur,
                old instanceof ImageBackground ? ((ImageBackground) old).bitmap : null));
    }

    static void refresh(Activity activity) {
        android.widget.FrameLayout host = activity.findViewById(android.R.id.content);
        if (host == null || host.getChildCount() == 0 || !(host.getChildAt(0) instanceof LinearLayout))
            return;
        LinearLayout root = (LinearLayout) host.getChildAt(0);
        if (nativePreview(activity)) {
            prepareCutout(activity, root);
            return;
        }
        if (usesImage(activity)
                && root.getBackground() instanceof ImageBackground) {
            ImageBackground current = (ImageBackground) root.getBackground();
            MainStyle style = MainStyle.load(activity);
            prepareImage(activity, root, activity instanceof MainActivity ? style.toolbar(activity) : current.bars,
                    activity instanceof MainActivity ? style.wallpaperVeil() : current.veil);
        }
    }

    private static void prepareCutout(Activity activity, LinearLayout root) {
        Drawable old = root.getBackground();
        Drawable previous = old instanceof CutoutBackground ? ((CutoutBackground) old).background : old;
        Bitmap bitmap = previous instanceof ImageBackground ? ((ImageBackground) previous).bitmap : null;
        if (previous instanceof ImageBackground)
            ((ImageBackground) previous).cancel();
        MainStyle style = MainStyle.load(activity);
        Drawable background = usesImage(activity)
                ? new ImageBackground(activity, root, Ui.color(activity, R.color.page), Color.TRANSPARENT,
                        style.blur, bitmap)
                : new android.graphics.drawable.ColorDrawable(style.background == MainStyle.WALLPAPER
                        ? style.wallpaperVeil()
                        : Ui.color(activity, R.color.page));
        root.setBackground(new CutoutBackground(root, background));
        if (activity instanceof ThemedActivity)
            ((ThemedActivity) activity).wallpaperContent = true;
        activity.getWindow().setFormat(PixelFormat.TRANSLUCENT);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        if (root.getTag(R.id.wallpaper_cutout_listener) == null) {
            root.setTag(R.id.wallpaper_cutout_listener, true);
            root.getViewTreeObserver().addOnScrollChangedListener(root::invalidate);
        }
    }

    /**
     * Paint the app background everywhere except the visible native preview stage.
     */
    private static final class CutoutBackground extends Drawable {
        private final LinearLayout root;
        private final Drawable background;
        private final Rect hole = new Rect();
        CutoutBackground(LinearLayout root, Drawable background) {
            this.root = root;
            this.background = background;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC);
            int save = canvas.save();
            android.view.View stage = root.findViewWithTag("wallpaper-preview-stage");
            if (stage != null && stage.getLocalVisibleRect(hole)) {
                root.offsetDescendantRectToMyCoords(stage, hole);
                canvas.clipOutRect(hole);
            }
            background.setBounds(getBounds());
            background.draw(canvas);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class ImageBackground extends Drawable {
        private final LinearLayout root;
        private final int bars, veil;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap bitmap;
        private final java.util.concurrent.Future<?> request;
        ImageBackground(Activity activity, LinearLayout root, int bars, int veil, int blur, Bitmap previous) {
            this.root = root;
            this.bars = bars;
            this.veil = veil;
            String id = BackgroundImages.selected(activity);
            Bitmap cached = BackgroundImages.cached(id, blur);
            bitmap = cached == null ? previous : cached;
            request = cached == null ? BackgroundImages.load(activity, id, blur, value -> {
                bitmap = value;
                root.invalidate();
            }) : null;
        }

        void cancel() {
            if (request != null)
                request.cancel(false);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            if (bitmap != null) {
                float scale = Math.max(root.getWidth() / (float) bitmap.getWidth(),
                        root.getHeight() / (float) bitmap.getHeight());
                float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
                float left = (root.getWidth() - width) / 2f, top = (root.getHeight() - height) / 2f;
                canvas.drawBitmap(bitmap, null, new RectF(left, top, left + width, top + height), bitmapPaint);
            }
            canvas.drawColor(veil);
            paint.setColor(bars);
            canvas.drawRect(0, 0, root.getWidth(), root.getPaddingTop(), paint);
            canvas.drawRect(0, 0, root.getPaddingLeft(), root.getHeight(), paint);
            canvas.drawRect(root.getWidth() - root.getPaddingRight(), 0, root.getWidth(), root.getHeight(), paint);
            canvas.drawRect(0, root.getHeight() - root.getPaddingBottom(), root.getWidth(), root.getHeight(), paint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
