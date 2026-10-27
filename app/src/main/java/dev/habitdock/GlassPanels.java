package dev.habitdock;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Shared material for popup, dialog and sheet surfaces. */
final class GlassPanels {
    private GlassPanels() {
    }

    static void attach(View panel, float cornerDp, int source) {
        attach(panel, cornerDp, source, false);
    }

    static void attach(View panel, float cornerDp, int source, boolean topOnly) {
        if (panel.getTag(R.id.glass_panel_material) != null)
            return;
        panel.setBackground(new SurfaceDrawable(panel.getContext(), cornerDp, topOnly));
        panel.setTag(R.id.glass_panel_material, new ObricGlass(panel, cornerDp, source, topOnly));
        panel.setClipToOutline(true);
    }

    private static final class SurfaceDrawable extends Drawable {
        private final GlassFallback fallback;
        private final float radius;
        private final RectF bounds = new RectF();
        private final Paint layerPaint = new Paint();
        private final boolean topOnly;

        SurfaceDrawable(android.content.Context context, float cornerDp, boolean topOnly) {
            fallback = new GlassFallback(context, topOnly, false, topOnly ? .84f : .75f);
            radius = Ui.dp(context, cornerDp);
            this.topOnly = topOnly;
        }

        @Override
        public void draw(Canvas canvas) {
            bounds.set(getBounds());
            if (layerPaint.getAlpha() == 255 && layerPaint.getColorFilter() == null) {
                fallback.draw(canvas, bounds, radius);
            } else {
                int layer = canvas.saveLayer(bounds, layerPaint);
                fallback.draw(canvas, bounds, radius);
                canvas.restoreToCount(layer);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            layerPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            layerPaint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void getOutline(Outline outline) {
            Rect rect = getBounds();
            if (topOnly) {
                Path path = new Path();
                GlassFallback.shape(path, new RectF(rect), radius, true);
                outline.setConvexPath(path);
            } else {
                outline.setRoundRect(rect, radius);
            }
        }
    }
}
