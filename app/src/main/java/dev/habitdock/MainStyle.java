package dev.habitdock;

import android.content.*;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import java.util.*;

/**
 * Shared page background and main-list presentation; never changes ranking.
 */
final class MainStyle {
    static final int APP = 0, WALLPAPER = 2, IMAGE = 3, CUSTOM_IMAGE = 4;
    static final String[] REASONS = {"不显示", "简略", "详细"};
    final int iconDp, labelSp, background, reasons, transparency, blur;
    final boolean showHidden;

    MainStyle(int iconDp, int labelSp, int background, int reasons, boolean showHidden) {
        this(iconDp, labelSp, background, reasons, showHidden, 20);
    }

    MainStyle(int iconDp, int labelSp, int background, int reasons, boolean showHidden, int transparency) {
        this(iconDp, labelSp, background, reasons, showHidden, transparency, 0);
    }

    MainStyle(int iconDp, int labelSp, int background, int reasons, boolean showHidden, int transparency, int blur) {
        this.blur = Math.max(0, Math.min(100, blur));
        this.transparency = Math.max(0, Math.min(100, transparency));
        this.iconDp = Math.max(32, Math.min(64, iconDp));
        this.labelSp = Math.max(14, Math.min(20, labelSp));
        this.background = imageMode(background) ? background : background == WALLPAPER ? WALLPAPER : APP;
        this.reasons = Math.max(0, Math.min(2, reasons));
        this.showHidden = showHidden;
    }

    static MainStyle defaults() {
        return new MainStyle(44, 16, APP, 1, false);
    }

    static MainStyle load(Context context) {
        SharedPreferences prefs = Repository.prefs(context);
        MainStyle defaults = defaults();
        return new MainStyle(prefs.getInt("main.icon_dp", defaults.iconDp),
                prefs.getInt("main.label_sp", defaults.labelSp),
                prefs.getInt("main.background", defaults.background), prefs.getInt("main.reasons", defaults.reasons),
                prefs.getBoolean("main.show_hidden", defaults.showHidden),
                prefs.getInt("main.transparency", defaults.transparency), prefs.getInt("main.blur", 0));
    }

    void save(Context context) {
        Repository.prefs(context).edit().putInt("main.icon_dp", iconDp).putInt("main.label_sp", labelSp)
                .putInt("main.background", background).putInt("main.reasons", reasons)
                .putBoolean("main.show_hidden", showHidden).putInt("main.transparency", transparency)
                .putInt("main.blur", blur).apply();
    }

    boolean sameAs(MainStyle other) {
        return other != null && iconDp == other.iconDp && labelSp == other.labelSp && background == other.background
                && reasons == other.reasons && showHidden == other.showHidden && transparency == other.transparency
                && blur == other.blur;
    }

    String key() {
        return iconDp + ":" + labelSp + ":" + background + ":" + reasons + ":" + showHidden + ":" + transparency + ":"
                + blur;
    }

    static boolean imageMode(int mode) {
        return mode == IMAGE || mode == CUSTOM_IMAGE;
    }

    boolean hasBackground() {
        return background == WALLPAPER || imageMode(background);
    }

    int page(Context context) {
        return Ui.color(context, R.color.page);
    }

    int wallpaperVeil(Context context) {
        return veil(context, Math.round(255 * (100 - transparency) / 100f));
    }

    int extraWallpaperVeil(Context context) {
        return veil(context, Math.round(255 * .33f));
    }

    private int veil(Context context, int alpha) {
        return ColorUtils.setAlphaComponent(page(context), alpha);
    }

    int toolbar(Context context) {
        return hasBackground()
                ? Color.TRANSPARENT
                : page(context);
    }

    int surface(Context context) {
        return Ui.color(context, R.color.surface);
    }

    int ink(Context context) {
        return Ui.color(context, R.color.ink);
    }

    int accent(Context context) {
        return Ui.color(context, R.color.accent);
    }

    int columns(Context context, int availablePx) {
        float fontScale = Math.max(1, context.getResources().getConfiguration().fontScale);
        int minimum = Ui.dp(context, Math.max(160, iconDp + 26 + 100 * fontScale * labelSp / 16f));
        int gap = Ui.dp(context, 4);
        return Math.max(1, (availablePx + gap) / (minimum + gap));
    }

    int rowDp(Context context) {
        float scale = Math.max(1, context.getResources().getConfiguration().fontScale);
        float textHeight = (labelSp * 1.25f + (reasons == 0 ? 0 : 2 * 12 * 1.25f + 4)) * scale;
        return Math.round(Math.max(68, Math.max(iconDp + 24, textHeight + 20)));
    }

    String reason(Predictor.Prediction prediction) {
        if (reasons == 0)
            return "";
        String text = reasons == 2 ? prediction.detailReason : prediction.reason;
        return "刚开始了解你的习惯".equals(text) ? "学习中" : "已固定".equals(text) ? "已置顶" : text;
    }

    List<Predictor.Prediction> visiblePredictions(Context context, Repository.Snapshot data) {
        Set<String> hidden = Repository.selection(context, "hidden");
        List<Predictor.Prediction> visible = new ArrayList<>();
        for (Predictor.Prediction prediction : data.ranked)
            if (showHidden || !hidden.contains(prediction.pkg))
                visible.add(prediction);
        return RecommendationLimit.apply(visible, Repository.selection(context, "pinned"),
                RecommendationLimit.load(context));
    }
}
