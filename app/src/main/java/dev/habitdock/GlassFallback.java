package dev.habitdock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import androidx.core.graphics.ColorUtils;

/**
 * Cached, translucent glass shell for devices without the OEM material
 * renderer.
 */
final class GlassFallback {
    private static final android.util.LruCache<String, Bitmap> EDGES = new android.util.LruCache<String, Bitmap>(
            4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final Context context;
    private final boolean topOnly, continuous, liquid;
    private final float opacity;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private Bitmap edge;
    private int edgeWidth, edgeHeight;
    private float edgeRadius;
    private boolean edgeDark;

    GlassFallback(Context context) {
        this(context, false);
    }

    GlassFallback(Context context, boolean topOnly) {
        this(context, topOnly, false);
    }

    GlassFallback(Context context, boolean topOnly, boolean continuous) {
        this(context, topOnly, continuous, .75f);
    }

    GlassFallback(Context context, boolean topOnly, boolean continuous, float opacity) {
        this.context = context;
        this.liquid = MainStyle.liquid(context);
        this.topOnly = topOnly;
        this.continuous = continuous;
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    static boolean dark(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static int tint(Context context, int alpha) {
        boolean dark = dark(context);
        int base = Ui.color(context, R.color.surface);
        if (dark)
            base = ColorUtils.blendARGB(base, Color.WHITE, .08f);
        base = ColorUtils.blendARGB(base, Ui.color(context, R.color.accent), dark ? .06f : .04f);
        return ColorUtils.setAlphaComponent(base, alpha);
    }

    void draw(Canvas canvas, RectF bounds, float radius) {
        boolean dark = dark(context);
        fill.setColor(liquid
                ? tint(context, Math.round(opacity * 255))
                : ColorUtils.setAlphaComponent(Ui.color(context, R.color.surface), 255));
        shape(clip, bounds, radius, topOnly, continuous);
        canvas.drawPath(clip, fill);
        if (!liquid)
            return;
        int width = Math.round(bounds.width()), height = Math.round(bounds.height());
        if (width < 1 || height < 1)
            return;
        if (edge == null || width != edgeWidth || height != edgeHeight || edgeRadius != radius || edgeDark != dark)
            createEdge(width, height, radius, dark);
        if (edge == null)
            return;
        int saved = canvas.save();
        canvas.clipPath(clip);
        RectF destination = new RectF(bounds.left, bounds.top, bounds.right, bounds.top + edge.getHeight());
        canvas.drawBitmap(edge, null, destination, bitmapPaint);
        canvas.restoreToCount(saved);
    }

    static void shape(Path path, RectF bounds, float radius, boolean topOnly) {
        shape(path, bounds, radius, topOnly, false);
    }

    static void shape(Path path, RectF bounds, float radius, boolean topOnly, boolean continuous) {
        path.rewind();
        if (continuous && !topOnly) {
            float r = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2f);
            float l = bounds.left, t = bounds.top, right = bounds.right, bottom = bounds.bottom;
            float c = r * .1f;
            path.moveTo(l + r, t);
            path.lineTo(right - r, t);
            path.cubicTo(right - c, t, right, t + c, right, t + r);
            path.lineTo(right, bottom - r);
            path.cubicTo(right, bottom - c, right - c, bottom, right - r, bottom);
            path.lineTo(l + r, bottom);
            path.cubicTo(l + c, bottom, l, bottom - c, l, bottom - r);
            path.lineTo(l, t + r);
            path.cubicTo(l, t + c, l + c, t, l + r, t);
            path.close();
        } else if (topOnly)
            path.addRoundRect(bounds, new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
        else
            path.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    }

    /**
     * Demo-style distance-to-contour highlight, cached so it is never recomputed
     * per frame.
     */
    private void createEdge(int width, int height, float radius, boolean dark) {
        // A previous bitmap may still be referenced by a hardware display list.
        edge = null;
        edgeWidth = width;
        edgeHeight = height;
        edgeRadius = radius;
        edgeDark = dark;
        String cacheKey = width + ":" + height + ":" + Float.floatToIntBits(radius) + ":" + dark + ":"
                + topOnly + ":" + continuous + ":" + context.getResources().getDisplayMetrics().densityDpi;
        edge = EDGES.get(cacheKey);
        if (edge != null)
            return;
        try {
            float rimWidth = Math.max(1, Ui.dp(context, 2f));
            float edgeOpacity = dark ? .38f : 1f;
            float r = Math.min(radius, Math.min(width, height) / 2f);
            float halfW = width / 2f, halfH = height / 2f;
            // Sample the same two Gaussian bands as the Demo shader in 1/8 px steps.
            int limit = (int) Math.ceil((rimWidth * 5 + 3) * 8);
            float[] band = new float[limit + 1];
            for (int i = 0; i < band.length; i++) {
                float inside = i / 8f - .5f;
                float core = (inside - rimWidth * .1f) / Math.max(rimWidth * .32f, .65f);
                float halo = (inside - rimWidth * .2f) / Math.max(rimWidth, .75f);
                band[i] = (float) Math.min(1, 1.25 * Math.exp(-core * core) + .2 * Math.exp(-halo * halo))
                        * smoothstep(-.5f, .35f, inside);
            }
            int bitmapHeight = topOnly ? Math.min(height, (int) Math.ceil(r + rimWidth * 5 + 3)) : height;
            int[] pixels = new int[width * bitmapHeight];
            for (int y = 0; y < (topOnly ? bitmapHeight : (height + 1) / 2); y++) {
                float py = y + .5f;
                float direction = topOnly
                        ? 1 - smoothstep(r * .5f, Math.max(r * 1.4f, 1), py)
                        : smoothstep(.28f, .87f, Math.abs(py - halfH) / Math.max(halfH, 1));
                if (direction == 0)
                    continue;
                float qy = Math.abs(py - halfH) - (halfH - r);
                for (int x = 0; x < width; x++) {
                    float qx = Math.abs(x + .5f - halfW) - (halfW - r);
                    float outsideX = Math.max(qx, 0), outsideY = Math.max(qy, 0);
                    float outside;
                    if (outsideX > 0 && outsideY > 0 && continuous) {
                        double x2 = outsideX * outsideX, y2 = outsideY * outsideY;
                        outside = (float) Math.sqrt(Math.sqrt(x2 * x2 + y2 * y2));
                    } else {
                        outside = outsideX > 0 && outsideY > 0
                                ? (float) Math.hypot(outsideX, outsideY)
                                : Math.max(outsideX, outsideY);
                    }
                    float inside = r - outside - Math.min(Math.max(qx, qy), 0);
                    int index = Math.round((inside + .5f) * 8);
                    if (index < 0 || index >= band.length)
                        continue;
                    int alpha = Math.round(255 * band[index] * direction * edgeOpacity);
                    pixels[y * width + x] = (alpha << 24) | 0x00ffffff;
                    if (!topOnly)
                        pixels[(height - 1 - y) * width + x] = pixels[y * width + x];
                }
            }
            edge = Bitmap.createBitmap(pixels, width, bitmapHeight, Bitmap.Config.ARGB_8888);
            EDGES.put(cacheKey, edge);
        } catch (OutOfMemoryError failure) {
            // Keep the translucent fill if an edge bitmap cannot be allocated.
            edge = null;
        }
    }

    private static float smoothstep(float low, float high, float value) {
        float t = Math.max(0, Math.min(1, (value - low) / (high - low)));
        return t * t * (3 - 2 * t);
    }
}
