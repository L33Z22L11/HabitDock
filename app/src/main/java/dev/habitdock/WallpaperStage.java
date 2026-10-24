package dev.habitdock;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;

/**
 * Draw native wallpaper previews or an opaque cached image across the
 * application.
 */
final class WallpaperStage {
    static boolean nativePreview(android.content.Context context) {
        if (usesImage(context))
            return false;
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
        MainStyle style = MainStyle.load(activity);
        prepare(activity, root, style.toolbar(activity),
                style.hasBackground() ? style.wallpaperVeil(activity) : Color.TRANSPARENT);
    }

    static void prepare(Activity activity, LinearLayout root, int barsColor, int veilColor) {
        if (usesImage(activity)) {
            prepareImage(activity, root, barsColor, veilColor);
            return;
        }
        if (!changed(activity, root, barsColor, veilColor))
            return;
        if (MainStyle.load(activity).background == MainStyle.WALLPAPER) {
            prepareNative(activity, root, barsColor, veilColor);
            if (nativePreview(activity))
                prepareCutout(activity, root, root.getBackground());
        } else if (nativePreview(activity)) {
            prepareCutout(activity, root,
                    new android.graphics.drawable.ColorDrawable(Ui.color(activity, R.color.page)));
        } else {
            if (root.getBackground() instanceof ImageBackground)
                ((ImageBackground) root.getBackground()).cancel();
            if (activity instanceof ThemedActivity)
                ((ThemedActivity) activity).wallpaperContent = false;
            root.setBackgroundColor(Ui.color(activity, R.color.page));
        }
    }

    private static boolean changed(Activity activity, LinearLayout root, int bars, int veil) {
        MainStyle style = MainStyle.load(activity);
        String key = style.background + ":" + style.blur + ":" + BackgroundImages.selected(activity)
                + ":" + bars + ":" + veil + ":" + Ui.color(activity, R.color.page);
        if (key.equals(root.getTag(R.id.wallpaper_stage_key)))
            return false;
        root.setTag(R.id.wallpaper_stage_key, key);
        return true;
    }

    static android.view.View previewStage(LinearLayout root) {
        android.view.View stage = root.findViewWithTag("wallpaper-preview-stage");
        return stage != null ? stage : root.findViewWithTag("main-style-preview");
    }

    private static void watchPreview(LinearLayout root) {
        if (root.getTag(R.id.wallpaper_cutout_listener) == null) {
            root.setTag(R.id.wallpaper_cutout_listener, true);
            root.getViewTreeObserver().addOnScrollChangedListener(root::invalidate);
        }
    }

    private static void clipPreview(Canvas canvas, LinearLayout root) {
        android.view.View stage = previewStage(root);
        Rect bounds = new Rect();
        if (stage != null && stage.getLocalVisibleRect(bounds)) {
            root.offsetDescendantRectToMyCoords(stage, bounds);
            canvas.clipOutRect(bounds);
        }
    }

    /**
     * Both wallpaper renderers use the same base veil and additional control-area
     * veil.
     */
    private static void drawVeil(Canvas canvas, LinearLayout root, int base) {
        int previewSave = canvas.save();
        clipPreview(canvas, root);
        canvas.drawColor(base);
        int save = canvas.save();
        android.view.View list = root.findViewWithTag("recommendations-list");
        if (list != null && list.getWidth() > 0) {
            Rect bounds = new Rect(0, 0, list.getWidth(), list.getHeight());
            root.offsetDescendantRectToMyCoords(list, bounds);
            canvas.clipOutRect(bounds);
        }
        canvas.drawColor(MainStyle.load(root.getContext()).extraWallpaperVeil(root.getContext()));
        canvas.restoreToCount(save);
        canvas.restoreToCount(previewSave);
    }

    private static void prepareNative(Activity activity, LinearLayout root, int barsColor, int veilColor) {
        if (root.getBackground() instanceof ImageBackground)
            ((ImageBackground) root.getBackground()).cancel();
        if (activity instanceof ThemedActivity)
            ((ThemedActivity) activity).wallpaperContent = true;
        root.setBackground(new Drawable() {
            private final Paint paint = new Paint();
            @Override
            public void draw(Canvas canvas) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC);
                drawVeil(canvas, root, veilColor);
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
        if (!changed(activity, root, barsColor, veilColor))
            return;
        if (activity instanceof ThemedActivity)
            ((ThemedActivity) activity).wallpaperContent = false;
        Drawable old = root.getBackground();
        if (old instanceof ImageBackground)
            ((ImageBackground) old).cancel();
        MainStyle style = MainStyle.load(activity);
        watchPreview(root);
        root.setBackground(new ImageBackground(activity, root, barsColor, veilColor, style.blur,
                old instanceof ImageBackground ? ((ImageBackground) old).bitmap : null));
    }

    static void refresh(Activity activity) {
        android.widget.FrameLayout host = activity.findViewById(android.R.id.content);
        if (host == null || host.getChildCount() == 0 || !(host.getChildAt(0) instanceof LinearLayout))
            return;
        LinearLayout root = (LinearLayout) host.getChildAt(0);
        prepare(activity, root);
    }

    private static void prepareCutout(Activity activity, LinearLayout root, Drawable background) {
        root.setBackground(new CutoutBackground(root, background));
        if (activity instanceof ThemedActivity)
            ((ThemedActivity) activity).wallpaperContent = true;
        watchPreview(root);
    }

    /**
     * Paint the app background everywhere except the visible native preview stage.
     */
    private static final class CutoutBackground extends Drawable {
        private final LinearLayout root;
        private final Drawable background;
        CutoutBackground(LinearLayout root, Drawable background) {
            this.root = root;
            this.background = background;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC);
            int save = canvas.save();
            clipPreview(canvas, root);
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
            drawVeil(canvas, root, veil);
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
