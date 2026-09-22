package dev.habitdock;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Native RGB/hex picker. Zero is reserved for the theme-following option. */
final class IconColorDialog {
    static void show(Activity activity, int selected, IntConsumer result) {
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
        int initial = selected == 0 ? activity.getColor(R.color.widget_icon_background) : selected;
        int[] color = {initial};
        boolean[] binding = {false};
        SeekBar[] channels = new SeekBar[3];
        TextView[] labels = new TextView[3];
        String[] names = {"红", "绿", "蓝"};
        Runnable update = () -> {
            binding[0] = true;
            String code = String.format(Locale.ROOT, "#%06X", color[0] & 0xffffff);
            hex.setText(code);
            swatch.setContentDescription(code);
            GradientDrawable background = new GradientDrawable();
            background.setColor(color[0]);
            background.setCornerRadius(Ui.dp(activity, 8));
            background.setStroke(Ui.dp(activity, 1), activity.getColor(R.color.stroke));
            swatch.setBackground(background);
            for (int i = 0; i < 3; i++) {
                int value = (color[0] >> (16 - i * 8)) & 255;
                channels[i].setProgress(value);
                labels[i].setText(activity.getString(R.string.color_channel, names[i], value));
            }
            binding[0] = false;
        };
        for (int i = 0; i < 3; i++) {
            labels[i] = Ui.text(activity, "", 12, R.color.muted);
            body.addView(labels[i]);
            SeekBar channel = new SeekBar(activity);
            channel.setMax(255);
            channel.setContentDescription(names[i] + "色");
            channel.setTag("color-channel:" + i);
            channels[i] = channel;
            body.addView(channel, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 40)));
            int index = i;
            channel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar v, int value, boolean user) {
                    if (binding[0])
                        return;
                    int shift = 16 - index * 8;
                    color[0] = (color[0] & ~(255 << shift)) | (value << shift);
                    update.run();
                }

                public void onStartTrackingTouch(SeekBar v) {
                }

                public void onStopTrackingTouch(SeekBar v) {
                }
            });
        }
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
            shape.setStroke(Ui.dp(activity, 1), activity.getColor(R.color.stroke));
            chip.setBackground(shape);
            target.addView(chip,
                    new FrameLayout.LayoutParams(Ui.dp(activity, 28), Ui.dp(activity, 28), Gravity.CENTER));
            palette.addView(target, new LinearLayout.LayoutParams(0, -1, 1));
            target.setOnClickListener(v -> {
                color[0] = preset;
                update.run();
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
                    int cursor = hex.getSelectionStart();
                    update.run();
                    hex.setSelection(Math.min(hex.length(), Math.max(0, cursor)));
                    hex.setError(null);
                }
            }

            public void afterTextChanged(Editable e) {
            }
        });
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("图标背景色").setView(scroll)
                .setNegativeButton("取消", null)
                .setNeutralButton("跟随系统", (d, w) -> result.accept(0)).setPositiveButton("应用", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!hex.getText().toString().trim().matches("#?[0-9a-fA-F]{6}")) {
                    hex.setError("请输入 6 位颜色，例如 #DCE8FF");
                    return;
                }
                result.accept(color[0]);
                dialog.dismiss();
            });
        });
        body.setFocusableInTouchMode(true);
        body.requestFocus();
        dialog.show();
    }
}
