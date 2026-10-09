package dev.habitdock;

import android.graphics.*;
import android.graphics.drawable.*;
import java.util.*;

final class WidgetIcons {
    static Bitmap source(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, 128, 128);
        // Draw adaptive layers before the OS mask, so the chosen shape can be square or
        // round.
        if (drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) drawable;
            if (adaptive.getBackground() != null)
                adaptive.getBackground().draw(canvas);
            if (adaptive.getForeground() != null)
                adaptive.getForeground().draw(canvas);
        } else
            drawable.draw(canvas);
        return bitmap;
    }

    static Map<String, Bitmap> inset(Map<String, Bitmap> icons, int percent) {
        Map<String, Bitmap> result = new HashMap<>();
        for (Map.Entry<String, Bitmap> icon : icons.entrySet())
            result.put(icon.getKey(), inset(icon.getValue(), percent));
        return result;
    }

    static Bitmap inset(Bitmap source, int percent) {
        int edge = Math.max(source.getWidth(), source.getHeight());
        int size = Math.round(edge * 100f / Math.max(50, Math.min(100, percent)));
        if (size == source.getWidth() && size == source.getHeight())
            return source;
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        result.setDensity(source.getDensity());
        new Canvas(result).drawBitmap(source, (size - source.getWidth()) / 2f,
                (size - source.getHeight()) / 2f, new Paint(Paint.FILTER_BITMAP_FLAG));
        return result;
    }

    static Bitmap clip(Bitmap source, int roundness) {
        return style(source, roundness, Color.TRANSPARENT);
    }

    static Bitmap style(android.content.Context context, Bitmap source, IconStyle style, boolean themed) {
        Bitmap shaped = themed ? shape(source, style, Color.TRANSPARENT) : style(context, source, style);
        return !style.pack.isEmpty() && !themed ? inset(shaped, style.fallbackScale) : shaped;
    }

    static Bitmap style(android.content.Context context, Bitmap source, IconStyle style) {
        Bitmap shaped = fallbackShape(context, source, style);
        if (style.pack.isEmpty() || style.saturation == 100 && style.brightness == 100 && style.contrast == 100)
            return shaped;
        Bitmap result = Bitmap.createBitmap(shaped.getWidth(), shaped.getHeight(), Bitmap.Config.ARGB_8888);
        result.setDensity(shaped.getDensity());
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(style.saturation / 100f);
        float contrast = style.contrast / 100f;
        float offset = 128f * (1 - contrast);
        matrix.postConcat(new ColorMatrix(new float[]{
                contrast, 0, 0, 0, offset,
                0, contrast, 0, 0, offset,
                0, 0, contrast, 0, offset,
                0, 0, 0, 1, 0}));
        ColorMatrix light = new ColorMatrix();
        float gain = style.brightness / 100f;
        light.setScale(gain, gain, gain, 1);
        matrix.postConcat(light);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        new Canvas(result).drawBitmap(shaped, 0, 0, paint);
        if (shaped != source)
            shaped.recycle();
        return result;
    }

    private static Bitmap fallbackShape(android.content.Context context, Bitmap source, IconStyle style) {
        if (!style.fillBackground || style.fillColor != 0)
            return shape(source, style, style.fillBackground ? style.fillColor : Color.TRANSPARENT);
        int width = source.getWidth(), height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        IconBackground.Model model = IconBackground.analyze(pixels, width, height);
        if (model.kind == IconBackground.Kind.EMPTY)
            return shape(source, style, Color.TRANSPARENT);
        int fallback = context.getColor(R.color.widget_icon_background);
        if (model.kind == IconBackground.Kind.SOLID || model.kind == IconBackground.Kind.FALLBACK)
            return shape(source, style, model.colorAt(0, 0, fallback));
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                pixels[y * width + x] = model.colorAt(x, y, fallback);
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        background.setPixels(pixels, 0, width, 0, 0, width, height);
        // Composite before clipping. Opaque artwork stays byte-identical; only
        // transparent/antialiased edges blend with the inferred background.
        new Canvas(background).drawBitmap(source, 0, 0, null);
        Bitmap result = shape(background, style, Color.TRANSPARENT);
        if (result != background)
            background.recycle();
        return result;
    }

    private static Bitmap shape(Bitmap source, IconStyle style, int background) {
        if (!style.continuous)
            return style(source, style.roundness, background);
        float halfWidth = source.getWidth() / 2f, halfHeight = source.getHeight() / 2f;
        double power = 20.0 / style.exponent;
        Path shape = new Path();
        // Superellipse approximation: continuous curvature along each corner.
        for (int i = 0; i < 256; i++) {
            double angle = i * Math.PI * 2 / 256;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            float x = halfWidth * (1 + (float) Math.copySign(Math.pow(Math.abs(cos), power), cos));
            float y = halfHeight * (1 + (float) Math.copySign(Math.pow(Math.abs(sin), power), sin));
            if (i == 0)
                shape.moveTo(x, y);
            else
                shape.lineTo(x, y);
        }
        shape.close();
        Bitmap bitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(source.getDensity());
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (background != Color.TRANSPARENT) {
            paint.setColor(background);
            canvas.drawPath(shape, paint);
        }
        paint.setAlpha(255);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.drawPath(shape, paint);
        return bitmap;
    }

    static Bitmap style(Bitmap source, int roundness, int background) {
        if (roundness <= 0 && background == Color.TRANSPARENT)
            return source;
        Bitmap bitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Canvas canvas = new Canvas(bitmap);
        float radius = Math.min(source.getWidth(), source.getHeight()) * Math.max(0, Math.min(roundness, 100)) / 200f;
        if (background != Color.TRANSPARENT) {
            paint.setColor(background);
            canvas.drawRoundRect(0, 0, source.getWidth(), source.getHeight(), radius, radius, paint);
        }
        paint.setAlpha(255);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0, 0, source.getWidth(), source.getHeight(), radius, radius, paint);
        return bitmap;
    }
}
