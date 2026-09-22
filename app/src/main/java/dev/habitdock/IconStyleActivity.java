package dev.habitdock;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/**
 * Global appearance editor with immediate persistence and a session restore
 * point.
 */
public final class IconStyleActivity extends Activity {
    private SeekBar roundness;
    private Switch fill;
    private ImageButton color;
    private TextView roundnessLabel;
    private int fillColor;
    private Preview preview;
    private IconStyle original;
    private ImageButton restore;
    private boolean binding;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        IconStyle style = IconStyle.load(this);
        original = state == null
                ? style
                : new IconStyle(state.getInt("original-roundness"),
                        state.getBoolean("original-fill"), state.getInt("original-color"));
        fillColor = style.fillColor;
        LinearLayout root = Ui.screen(this), bar = Ui.toolbar(this, root, "图标样式", true);
        restore = Ui.icon(this, R.drawable.ic_undo, "撤销更改",
                v -> StyleRestore.show(this, "恢复图标样式", () -> bind(original), () -> bind(IconStyle.defaults())));
        restore.setTag("icon-style-save");
        bar.addView(restore);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 20));
        scroll.addView(body);
        preview = new Preview(this);
        body.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(this, 104)));
        Ui.space(body, 12);
        body.addView(Ui.text(this, "调整立即生效，推荐、应用选择器与所有桌面组件共用。", 12, R.color.muted));
        Ui.space(body, 24);
        roundnessLabel = Ui.text(this, "", 14, R.color.ink);
        body.addView(roundnessLabel);
        roundness = new SeekBar(this);
        roundness.setTag("icon-roundness");
        roundness.setContentDescription("图标圆角");
        roundness.setMax(100);
        roundness.setProgress(style.roundness);
        body.addView(roundness, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        body.addView(Ui.text(this, "0% 为直角，100% 为圆形。", 12, R.color.muted));
        Ui.space(body, 16);
        LinearLayout row = new LinearLayout(this);
        row.setTag("icon-fill-row");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 56));
        TextView label = Ui.text(this, "填充图标背景", 14, R.color.ink);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        color = Ui.icon(this, R.drawable.ic_contrast, "图标背景色", v -> IconColorDialog.show(this, fillColor, value -> {
            fillColor = value;
            changed();
        }));
        color.setTag("icon-fill-color");
        color.setImageTintList(null);
        row.addView(color);
        fill = new Switch(this);
        fill.setId(View.generateViewId());
        fill.setTag("icon-fill-background");
        fill.setContentDescription("填充图标背景");
        fill.setChecked(style.fillBackground);
        fill.setMinimumHeight(Ui.dp(this, 48));
        fill.setMinimumWidth(Ui.dp(this, 48));
        label.setLabelFor(fill.getId());
        label.setOnClickListener(v -> fill.toggle());
        row.addView(fill, new LinearLayout.LayoutParams(-2, Ui.dp(this, 48)));
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        body.addView(Ui.text(this, "仅填充透明区域。自适应颜色沿边缘延续渐变，复杂图标使用系统深浅底色；也可点按自选颜色。", 12, R.color.muted));
        fill.setOnCheckedChangeListener((v, checked) -> changed());
        roundness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean user) {
                changed();
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        changed();
    }

    private IconStyle input() {
        return new IconStyle(roundness.getProgress(), fill.isChecked(), fillColor);
    }

    private void changed() {
        if (binding)
            return;
        IconStyle style = input();
        if (!style.sameAs(IconStyle.load(this))) {
            style.save(this);
            WidgetAppearance.request(this);
        }
        StyleRestore.update(restore, !style.sameAs(original));
        roundnessLabel.setText(getString(R.string.widget_roundness, style.roundness));
        color.setImageDrawable(new ColorChip(fillColor, getColor(R.color.muted), Ui.dp(this, 24)));
        color.setEnabled(style.fillBackground);
        color.setAlpha(style.fillBackground ? 1f : .4f);
        String description = fillColor == 0
                ? "图标背景色，自适应颜色"
                : String.format(Locale.ROOT, "图标背景色，#%06X", fillColor & 0xffffff);
        color.setContentDescription(description);
        color.setTooltipText(description);
        preview.setStyle(style);
    }

    private void bind(IconStyle style) {
        binding = true;
        fillColor = style.fillColor;
        roundness.setProgress(style.roundness);
        fill.setChecked(style.fillBackground);
        binding = false;
        changed();
    }

    @Override
    protected void onStop() {
        WidgetAppearance.flush();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("original-roundness", original.roundness);
        out.putBoolean("original-fill", original.fillBackground);
        out.putInt("original-color", original.fillColor);
        super.onSaveInstanceState(out);
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
    private static final class Preview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF destination = new RectF();
        private final Bitmap[] sources = new Bitmap[3], icons = new Bitmap[3];
        Preview(Context c) {
            super(c);
            setContentDescription("图标样式预览");
            int[] colors = {0xff3a8872, 0xff5681c7, 0xffcf8653};
            for (int i = 0; i < 3; i++) {
                Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                paint.setColor(colors[i]);
                if (i == 1)
                    paint.setShader(
                            new LinearGradient(16, 16, 112, 112, 0xff559bc9, 0xff7152b8, Shader.TileMode.CLAMP));
                else if (i == 2)
                    paint.setShader(new LinearGradient(0, 18, 0, 110, 0xffeab569, 0xffbb6658, Shader.TileMode.CLAMP));
                if (i == 0)
                    canvas.drawRect(0, 0, 128, 128, paint);
                else if (i == 1)
                    canvas.drawCircle(64, 64, 54, paint);
                else
                    canvas.drawRoundRect(12, 18, 116, 110, 18, 18, paint);
                paint.setShader(null);
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(7);
                paint.setStrokeCap(Paint.Cap.ROUND);
                if (i == 0) {
                    canvas.drawLine(39, 68, 56, 84, paint);
                    canvas.drawLine(56, 84, 90, 45, paint);
                } else if (i == 1) {
                    canvas.drawCircle(64, 64, 31, paint);
                    canvas.drawLine(64, 43, 64, 64, paint);
                    canvas.drawLine(64, 64, 80, 74, paint);
                } else {
                    canvas.drawLine(40, 49, 88, 49, paint);
                    canvas.drawLine(40, 65, 88, 65, paint);
                    canvas.drawLine(40, 81, 70, 81, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                sources[i] = bitmap;
            }
        }

        void setStyle(IconStyle style) {
            for (int i = 0; i < 3; i++)
                icons[i] = WidgetIcons.style(getContext(), sources[i], style);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(getContext().getColor(R.color.surface));
            float corner = Ui.dp(getContext(), 16);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), corner, corner, paint);
            float side = Ui.dp(getContext(), 48), gap = Math.min(Ui.dp(getContext(), 84), getWidth() / 3f),
                    cy = getHeight() / 2f;
            for (int i = 0; i < 3; i++)
                if (icons[i] != null) {
                    float cx = getWidth() / 2f + (i - 1) * gap;
                    destination.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
                    canvas.drawBitmap(icons[i], null, destination, paint);
                }
        }
    }
}
