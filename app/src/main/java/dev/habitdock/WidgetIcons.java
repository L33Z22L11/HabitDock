package dev.habitdock;

import android.graphics.*;
import android.graphics.drawable.*;

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

    static Bitmap clip(Bitmap source, int roundness) {
        return style(source, roundness, Color.TRANSPARENT);
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
