package dev.habitdock;
import com.google.android.material.slider.Slider;
import androidx.appcompat.app.AlertDialog;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * HSV plane and hex picker. Zero is reserved for per-icon adaptive edge colors.
 */
final class IconColorDialog {
    static void show(Activity activity, int selected, IntConsumer result) {
        show(activity, "图标填充", selected, true, result);
    }

    static void show(Activity activity, String title, int selected, boolean adaptive, IntConsumer result) {
        show(activity, title, selected, adaptive, true, result);
    }

    static void show(Activity activity, String title, int selected, boolean adaptive, boolean live,
            IntConsumer result) {
        LinearLayout body = Ui.column(activity);
        int padding = Ui.dp(activity, 20);
        body.setPadding(padding, Ui.dp(activity, 12), padding, Ui.dp(activity, 8));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        View swatch = new View(activity);
        swatch.setTag("color-preview");
        body.addView(swatch, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 40)));
        Ui.space(body, 8);
        EditText hex = new EditText(activity);
        hex.setTag("color-hex");
        hex.setHint("#RRGGBB");
        hex.setContentDescription("十六进制颜色");
        hex.setSingleLine(true);
        hex.setSelectAllOnFocus(true);
        hex.setTextSize(16);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        body.addView(hex, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48)));
        int initial = selected == 0 ? 0xfff5f6f5 : selected;
        int[] color = {initial};
        boolean[] binding = {false};
        boolean[] edited = {false};
        ColorPlane plane = new ColorPlane(activity);
        plane.setTag("color-plane");
        body.addView(plane, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 168)));
        Ui.space(body, 8);
        body.addView(Ui.text(activity, "色相", 12, R.color.muted));
        View spectrum = new View(activity) {
            private final Paint paint = new Paint();
            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                paint.setShader(new LinearGradient(0, 0, getWidth(), 0,
                        new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA,
                                Color.RED},
                        null, Shader.TileMode.CLAMP));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawRoundRect(0, 0, getWidth(), getHeight(), getHeight() / 2f, getHeight() / 2f, paint);
            }
        };
        FrameLayout hueTrack = new FrameLayout(activity);
        body.addView(hueTrack, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48)));
        FrameLayout.LayoutParams spectrumParams = new FrameLayout.LayoutParams(-1, Ui.dp(activity, 10), Gravity.CENTER);
        spectrumParams.leftMargin = spectrumParams.rightMargin = Ui.dp(activity, 16);
        hueTrack.addView(spectrum, spectrumParams);
        Slider hue = new Slider(activity);
        Ui.styleSlider(hue);
        hue.setValueTo(360);
        hue.setContentDescription("色相");
        hue.setTag("color-hue");
        hue.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
        hue.setTrackStopIndicatorSize(0);
        hue.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int inset = hue.getTrackSidePadding();
            if (spectrumParams.leftMargin != inset) {
                spectrumParams.leftMargin = spectrumParams.rightMargin = inset;
                spectrum.setLayoutParams(spectrumParams);
            }
        });
        hue.setTrackActiveTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        hue.setTrackInactiveTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        hueTrack.addView(hue, new FrameLayout.LayoutParams(-1, -1));
        float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);
        Runnable update = () -> {
            binding[0] = true;
            String code = String.format(Locale.ROOT, "#%06X", color[0] & 0xffffff);
            hex.setText(code);
            swatch.setContentDescription(code);
            GradientDrawable background = new GradientDrawable();
            background.setColor(color[0]);
            background.setCornerRadius(Ui.dp(activity, 8));
            background.setStroke(Ui.dp(activity, 1), Ui.color(activity, R.color.stroke));
            swatch.setBackground(background);
            hue.setValue(hsv[0]);
            plane.setColor(hsv);
            binding[0] = false;
        };
        plane.changed = value -> {
            color[0] = value;
            System.arraycopy(plane.hsv, 0, hsv, 0, 3);
            update.run();
            edited[0] = true;
            if (live)
                result.accept(color[0]);
        };
        hue.addOnChangeListener((slider, value, user) -> {
            if (binding[0])
                return;
            hsv[0] = value;
            color[0] = Color.HSVToColor(hsv);
            update.run();
            edited[0] = true;
            if (live)
                result.accept(color[0]);
        });
        LinearLayout palette = new LinearLayout(activity);
        body.addView(palette, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48)));
        for (int preset : new int[]{0xfff5f6f5, 0xff252b29, 0xffdce8ff, 0xffdbefe2, 0xfff7dfeb, 0xfff8e4cc}) {
            FrameLayout target = new FrameLayout(activity);
            Ui.nativeTouch(target);
            target.setContentDescription(String.format(Locale.ROOT, "选择 #%06X", preset & 0xffffff));
            View chip = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(preset);
            shape.setCornerRadius(Ui.dp(activity, 16));
            shape.setStroke(Ui.dp(activity, 1), Ui.color(activity, R.color.stroke));
            chip.setBackground(shape);
            target.addView(chip,
                    new FrameLayout.LayoutParams(Ui.dp(activity, 28), Ui.dp(activity, 28), Gravity.CENTER));
            palette.addView(target, new LinearLayout.LayoutParams(0, -1, 1));
            target.setOnClickListener(v -> {
                color[0] = preset;
                Color.colorToHSV(color[0], hsv);
                update.run();
                edited[0] = true;
                if (live)
                    result.accept(color[0]);
            });
        }
        update.run();
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding[0])
                    return;
                String code = s.toString().trim();
                if (code.matches("#?[0-9a-fA-F]{6}")) {
                    color[0] = Color.parseColor(code.startsWith("#") ? code : "#" + code);
                    Color.colorToHSV(color[0], hsv);
                    int cursor = hex.getSelectionStart();
                    update.run();
                    hex.setSelection(Math.min(hex.length(), Math.max(0, cursor)));
                    hex.setError(null);
                    edited[0] = true;
                    if (live)
                        result.accept(color[0]);
                }
            }

            public void afterTextChanged(Editable e) {
            }
        });
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(title).setView(scroll)
                .setNegativeButton("取消", (d, w) -> result.accept(selected))
                .setPositiveButton("完成", null).create();
        if (adaptive)
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "自适应颜色", (d, w) -> result.accept(0));
        dialog.setOnCancelListener(d -> result.accept(selected));
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!hex.getText().toString().trim().matches("#?[0-9a-fA-F]{6}")) {
                    hex.setError("请输入 6 位颜色，例如 #DCE8FF");
                    return;
                }
                result.accept(selected == 0 && !edited[0] ? 0 : color[0]);
                dialog.dismiss();
            });
        });
        body.setFocusableInTouchMode(true);
        body.requestFocus();
        dialog.show();
    }
    static final class ColorPlane extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv = {0, 0, 1};
        private Shader saturation, brightness;
        IntConsumer changed;
        public ColorPlane(android.content.Context activity) {
            super(activity);
            setContentDescription("选择颜色：横向调整饱和度，纵向调整明暗");
            setFocusable(true);
        }

        void setColor(float[] value) {
            boolean hueChanged = value[0] != hsv[0];
            System.arraycopy(value, 0, hsv, 0, 3);
            if (hueChanged || saturation == null)
                updateShaders();
            invalidate();
        }

        private void updateShaders() {
            float inset = Ui.dp(getContext(), 8);
            int base = Color.HSVToColor(new float[]{hsv[0], 1, 1});
            saturation = new LinearGradient(inset, 0, Math.max(inset + 1, getWidth() - inset), 0, Color.WHITE, base,
                    Shader.TileMode.CLAMP);
            brightness = new LinearGradient(0, inset, 0, Math.max(inset + 1, getHeight() - inset), Color.TRANSPARENT,
                    Color.BLACK, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            updateShaders();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float inset = Ui.dp(getContext(), 8), w = getWidth() - inset * 2, h = getHeight() - inset * 2;
            paint.setShader(saturation);
            canvas.drawRect(inset, inset, inset + w, inset + h, paint);
            paint.setShader(brightness);
            canvas.drawRect(inset, inset, inset + w, inset + h, paint);
            paint.setShader(null);
            float x = inset + w * hsv[1], y = inset + h * (1 - hsv[2]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 3));
            paint.setColor(Color.BLACK);
            canvas.drawCircle(x, y, Ui.dp(getContext(), 6), paint);
            paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, Ui.dp(getContext(), 6), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE
                    || event.getActionMasked() == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float inset = Ui.dp(getContext(), 8);
                hsv[1] = Math.max(0, Math.min(1, (event.getX() - inset) / Math.max(1, getWidth() - inset * 2)));
                hsv[2] = 1 - Math.max(0, Math.min(1, (event.getY() - inset) / Math.max(1, getHeight() - inset * 2)));
                if (changed != null)
                    changed.accept(Color.HSVToColor(hsv));
                invalidate();
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    performClick();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
