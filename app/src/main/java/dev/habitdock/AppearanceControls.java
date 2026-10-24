package dev.habitdock;

import android.app.Activity;
import android.widget.*;

/** Independent brightness and palette controls for application pages. */
@android.annotation.SuppressLint("ViewConstructor")
final class AppearanceControls extends LinearLayout {
    AppearanceControls(Activity activity) {
        super(activity);
        setOrientation(VERTICAL);
        SlidingChoice mode = new SlidingChoice(activity, new String[]{"系统", "浅色", "深色"},
                "app-color-mode-", selected -> configure(activity, selected, Appearance.scheme(activity)));
        mode.select(Appearance.colorMode(activity), false);
        mode.addRow(this, "明暗模式", 0);
        SlidingChoice scheme = new SlidingChoice(activity, new String[]{"默认", "自动", "自定义"},
                "app-scheme-", selected -> configure(activity, Appearance.colorMode(activity), selected));
        scheme.select(Appearance.scheme(activity), false);
        scheme.addRow(this, "主题配色", 1);
        if (Appearance.scheme(activity) == 2) {
            ColorSettingRow color = new ColorSettingRow(activity, "应用主色",
                    () -> IconColorDialog.show(activity, "应用主色", Appearance.seed(activity), false, false, value -> {
                        if (value != Appearance.seed(activity)) {
                            Repository.prefs(activity).edit().putInt("appearance.seed", value).apply();
                            activity.recreate();
                        }
                    }));
            color.setTag("app-style-color");
            color.bind(Appearance.seed(activity), false);
            addView(color);
        }

    }

    static void configure(Activity activity, int mode, int scheme) {
        if (Appearance.colorMode(activity) == mode && Appearance.scheme(activity) == scheme)
            return;
        int before = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        Appearance.configure(activity, mode, scheme);
        int target = mode == 1
                ? android.content.res.Configuration.UI_MODE_NIGHT_NO
                : mode == 2
                        ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                        : android.content.res.Resources.getSystem().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (before == target)
            activity.recreate();
    }

}
