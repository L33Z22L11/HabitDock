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

    static Bitmap style(android.content.Context context, Bitmap source, IconStyle style) {
        if (!style.fillBackground || style.fillColor != 0)
            return style(source, style.roundness, style.fillBackground ? style.fillColor : Color.TRANSPARENT);
        int width = source.getWidth(), height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        IconBackground.Model model = IconBackground.analyze(pixels, width, height);
        if (model.kind == IconBackground.Kind.EMPTY)
            return clip(source, style.roundness);
        int fallback = context.getColor(R.color.widget_icon_background);
        if (model.kind == IconBackground.Kind.SOLID || model.kind == IconBackground.Kind.FALLBACK)
            return style(source, style.roundness, model.colorAt(0, 0, fallback));
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                pixels[y * width + x] = model.colorAt(x, y, fallback);
        Bitmap background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        background.setPixels(pixels, 0, width, 0, 0, width, height);
        // Composite before clipping. Opaque artwork stays byte-identical; only
        // transparent/antialiased edges blend with the inferred background.
        new Canvas(background).drawBitmap(source, 0, 0, null);
        Bitmap result = clip(background, style.roundness);
        if (result != background)
            background.recycle();
        return result;
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
