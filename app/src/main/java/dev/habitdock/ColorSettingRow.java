package dev.habitdock;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Shared color picker row: label, swatch and readable value. */
// Created with required data and callbacks; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class ColorSettingRow extends LinearLayout {
    final ImageView swatch;
    final TextView value;
    private final String label;
    ColorSettingRow(Context context, String label, Runnable action) {
        super(context);
        this.label = label;
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.dp(context, 56));
        Ui.nativeTouch(this);
        addView(Ui.text(context, label, 14, R.color.ink), new LayoutParams(0, -2, 1));
        swatch = new ImageView(context);
        swatch.setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10));
        addView(swatch, new LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));
        value = Ui.text(context, "", 14, R.color.muted);
        value.setGravity(Gravity.END);
        addView(value, new LayoutParams(-2, -2));
        setOnClickListener(v -> action.run());
        swatch.setOnClickListener(v -> action.run());
    }

    void bind(int color, boolean adaptive) {
        String text = adaptive ? "自适应" : String.format(Locale.ROOT, "#%06X", color & 0xffffff);
        value.setText(text);
        swatch.setImageDrawable(
                new ColorChip(adaptive ? 0 : color, Ui.color(getContext(), R.color.muted), Ui.dp(getContext(), 24)));
        setContentDescription(label + "，" + text);
        swatch.setContentDescription(label + "，" + text);
    }
    private static final class ColorChip extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int color, stroke, size;
        ColorChip(int color, int stroke, int size) {
            this.color = color;
            this.stroke = stroke;
            this.size = size;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), radius = size * .44f, cx = b.exactCenterX(),
                    cy = b.exactCenterY();
            RectF circle = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.FILL);
            if (color == 0) {
                int[] colors = {0xff5681c7, 0xff3a8872, 0xffcf8653};
                for (int i = 0; i < colors.length; i++) {
                    paint.setColor(colors[i]);
                    canvas.drawArc(circle, -90 + i * 120, 120, true, paint);
                }
            } else {
                paint.setColor(color);
                canvas.drawOval(circle, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, size / 24));
            paint.setColor(stroke);
            canvas.drawOval(circle, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
