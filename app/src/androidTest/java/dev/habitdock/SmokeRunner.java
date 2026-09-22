package dev.habitdock;

import android.app.*;
import android.app.job.JobScheduler;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.FrameLayout;
import java.util.*;

/** Runs only on a disposable emulator. No external test framework required. */
public final class SmokeRunner extends Instrumentation {
    private int checks;
    private void check(boolean value, String description) {
        if (!value)
            throw new AssertionError(description);
        checks++;
    }

    private byte[] hash(java.io.File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32768];
        try (java.io.InputStream input = new java.io.FileInputStream(file)) {
            int n;
            while ((n = input.read(buffer)) != -1)
                digest.update(buffer, 0, n);
        }
        return digest.digest();
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle results = new Bundle();
        try {
            if (!android.os.Build.HARDWARE.contains("ranchu") && !android.os.Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("this test may only reset an emulator");
            Context c = getTargetContext();
            Repository.prefs(c).edit().clear().commit();
            check(RecommendationLimit.load(c) == 20, "recommendation limit defaults to twenty");
            List<Predictor.Prediction> limitFixture = new ArrayList<>();
            Set<String> fixturePins = new HashSet<>();
            for (int i = 0; i < 75; i++) {
                String pkg = "test.limit." + i;
                limitFixture.add(new Predictor.Prediction(pkg, 75 - i, "fixture"));
                if (i < 25)
                    fixturePins.add(pkg);
            }
            List<Predictor.Prediction> capped = RecommendationLimit.apply(limitFixture, Set.of(), 20);
            check(capped.size() == 20 && capped.get(19).pkg.equals("test.limit.19"),
                    "cap keeps exactly the highest ranked twenty suggestions in order");
            capped = RecommendationLimit.apply(limitFixture, fixturePins, 20);
            check(capped.size() == 45 && capped.stream().filter(p -> fixturePins.contains(p.pkg)).count() == 25
                    && capped.get(44).pkg.equals("test.limit.44"),
                    "all pins remain visible and do not consume the suggestion limit");
            check(RecommendationLimit.apply(limitFixture.subList(0, 3), Set.of(), 20).size() == 3,
                    "limit never pads sparse results");
            check(new AppUpdates.Release("v0.4.10").newerThan("0.4.9")
                    && !new AppUpdates.Release("v0.4.9").newerThan("0.4.10")
                    && new AppUpdates.Release("v1.0.0").newerThan("0.99.99"),
                    "updates compare numeric version components rather than text");
            check(!new AppUpdates.Release("v0.4.5").newerThan("0.4.5")
                    && new AppUpdates.Release("v0.4.5").newerThan("0.4.5-beta.1")
                    && !new AppUpdates.Release("v0.4.5").newerThan("0.4.6-beta.1"),
                    "same version and newer local previews do not prompt a downgrade");
            AppUpdates.Release release = AppUpdates.parse(
                    "{\"tag_name\":\"v0.4.6\",\"draft\":false,\"prerelease\":false,\"html_url\":\"https://example.com\"}");
            check(release.newerThan("0.4.5")
                    && release.url().equals("https://github.com/L33Z22L11/HabitDock/releases/tag/v0.4.6"),
                    "release destination is always the project GitHub page");
            check(AppUpdates.fromRedirect(AppUpdates.RELEASES + "/tag/v0.4.6").newerThan("0.4.5"),
                    "public latest-release redirect supports API rate-limit fallback");
            for (String invalid : new String[]{null, "https://example.com/releases/tag/v0.4.6",
                    AppUpdates.RELEASES + "/tag/v0.4.6-beta.1", AppUpdates.RELEASES + "/tag/v0.4.6/extra"}) {
                boolean rejected = false;
                try {
                    AppUpdates.fromRedirect(invalid);
                } catch (java.io.IOException expected) {
                    rejected = true;
                }
                check(rejected, "unexpected latest-release destinations cannot prompt or navigate elsewhere");
            }
            for (String badRelease : new String[]{"not-json", "{}",
                    "{\"tag_name\":\"v0.4.6\",\"draft\":true,\"prerelease\":false}",
                    "{\"tag_name\":\"v0.4.6\",\"draft\":false,\"prerelease\":true}",
                    "{\"tag_name\":\"v0.4.6-beta.1\",\"draft\":false,\"prerelease\":false}",
                    "{\"tag_name\":\"unexpected\",\"draft\":false,\"prerelease\":false}"}) {
                boolean rejected = false;
                try {
                    AppUpdates.parse(badRelease);
                } catch (java.io.IOException expected) {
                    rejected = true;
                }
                check(rejected, "malformed, draft and prerelease responses cannot report an update");
            }
            List<String> installed = new ArrayList<>(Repository.apps(c).keySet());
            check(installed.size() >= 2, "launcher app discovery");
            String visible = installed.get(0), secret = installed.get(1);
            check(!installed.contains(c.getPackageName()), "self excluded");
            long now = System.currentTimeMillis();
            try (UsageStore store = new UsageStore(c)) {
                SQLiteDatabase db = store.getWritableDatabase();
                db.delete("launches", null, null);
                db.delete("meta", null, null);
                for (int i = 1; i <= 7; i++) {
                    db.execSQL("INSERT INTO launches(pkg,stamp) VALUES (?,?)",
                            new Object[]{visible, now - i * Predictor.DAY});
                    db.execSQL("INSERT INTO launches(pkg,stamp) VALUES (?,?)",
                            new Object[]{secret, now - i * Predictor.DAY + 1000});
                }
                check(store.read(now).size() == 14, "persisted history readable");
                Repository.prefs(c).edit().putStringSet("hidden", Set.of(secret)).putStringSet("pinned", Set.of(secret))
                        .commit();
                store.enforcePrivacy(Repository.eligible(c, Repository.apps(c).keySet()));
                check(store.read(now).stream().noneMatch(e -> e.pkg.equals(secret)),
                        "privacy erases old local samples");
                check(Repository.load(c, now, false).predictions.stream().noneMatch(p -> p.pkg.equals(secret)),
                        "privacy overrides pinned apps");
                store.sync(now);
                check(store.read(now).stream().noneMatch(e -> e.pkg.equals(secret)), "privacy blocks history reimport");
                Repository.prefs(c).edit().putStringSet("hidden", new HashSet<>(installed)).commit();
                store.enforcePrivacy(Set.of());
                check(store.read(now).isEmpty(), "privacy exclusions purge all selected history");
                check(Repository.load(c, now, false).predictions.isEmpty(), "private apps never used as fallback");
                Repository.prefs(c).edit().clear().commit();
                store.reset(now);
                store.sync(now + 1);
                check(store.read(now).isEmpty(), "reset does not backfill past data");
            }
            runOnMainSync(() -> {
                FrameLayout host = new FrameLayout(c);
                HabitWidget.base(c).apply(c, host);
            });
            checks++;
            android.content.res.Configuration lightConfig = new android.content.res.Configuration(
                    c.getResources().getConfiguration());
            lightConfig.uiMode = (lightConfig.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    | android.content.res.Configuration.UI_MODE_NIGHT_NO;
            android.content.res.Configuration darkConfig = new android.content.res.Configuration(lightConfig);
            darkConfig.uiMode = (darkConfig.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    | android.content.res.Configuration.UI_MODE_NIGHT_YES;
            Context light = c.createConfigurationContext(lightConfig), dark = c.createConfigurationContext(darkConfig);
            check(light.getColor(R.color.page) != dark.getColor(R.color.page),
                    "application follows day and night resources");
            check(light.getColor(R.color.widget_tint) != dark.getColor(R.color.widget_tint),
                    "widget follows day and night resources");
            android.content.SharedPreferences runtime = RefreshPolicy.runtime(c);
            runtime.edit().putLong("last_success", now).putBoolean("permitted", UsageStore.permitted(c)).commit();
            check(!RefreshPolicy.due(c, now + 29 * 60 * 1000L), "app clicks skip refresh before thirty minutes");
            check(RefreshPolicy.due(c, now + 30 * 60 * 1000L), "app clicks refresh at the thirty-minute boundary");
            check(RefreshPolicy.due(c, now - 1), "clock correction cannot suppress refresh indefinitely");
            Repository.prefs(c).edit().putInt("refresh_minutes", 60).commit();
            check(!RefreshPolicy.due(c, now + 59 * 60 * 1000L) && RefreshPolicy.due(c, now + 60 * 60 * 1000L),
                    "custom refresh interval applies to the same shared clock");
            Repository.prefs(c).edit().remove("refresh_minutes").commit();
            runtime.edit().clear().commit();
            check(RefreshPolicy.due(c, now), "first click after upgrade can refresh");
            Repository.Current initial = Repository.current(c, true), reopened = Repository.current(c, false);
            check(initial.stamp == reopened.stamp && initial.data.samples == reopened.data.samples,
                    "opening a fresh recommendation preserves timestamp and history cutoff");
            check(initial.data.predictions.stream().map(p -> p.pkg).collect(java.util.stream.Collectors.toList())
                    .equals(reopened.data.predictions.stream().map(p -> p.pkg)
                            .collect(java.util.stream.Collectors.toList())),
                    "fresh recommendation order remains unchanged on reopen");
            Repository.prefs(c).edit().putInt("recommendation_limit", 1).commit();
            Repository.Current limited = Repository.current(c, false);
            check(limited.data.predictions.size() <= 1 && limited.stamp == reopened.stamp,
                    "repository applies the changed cap without advancing recommendation time");
            Repository.prefs(c).edit().remove("recommendation_limit").commit();
            runtime.edit().putLong("last_success", now - 31 * 60 * 1000L).commit();
            check(Repository.current(c, false).stamp > now - 31 * 60 * 1000L,
                    "overdue automatic entry recomputes recommendations");
            RefreshPolicy.invalidate(c);
            check(RefreshPolicy.due(c, System.currentTimeMillis()), "privacy changes bypass recommendation freshness");
            android.graphics.Bitmap square = WidgetIcons
                    .source(new android.graphics.drawable.ColorDrawable(android.graphics.Color.RED));
            android.graphics.Bitmap rounded = WidgetIcons.clip(square, 40), circle = WidgetIcons.clip(square, 100);
            check(android.graphics.Color.alpha(WidgetIcons.clip(square, 0).getPixel(0, 0)) == 255,
                    "zero roundness keeps square corners");
            check(android.graphics.Color.alpha(rounded.getPixel(0, 0)) == 0
                    && rounded.getPixel(64, 64) == android.graphics.Color.RED,
                    "rounded clipping clears corners and preserves center");
            check(android.graphics.Color.alpha(rounded.getPixel(12, 12)) > 250
                    && android.graphics.Color.alpha(circle.getPixel(12, 12)) == 0, "roundness changes the actual mask");
            android.graphics.Bitmap adaptive = WidgetIcons.source(new android.graphics.drawable.AdaptiveIconDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE),
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)));
            check(adaptive.getPixel(0, 0) == android.graphics.Color.BLUE,
                    "adaptive icon is drawn before the system mask");
            android.graphics.Bitmap transparent = android.graphics.Bitmap.createBitmap(128, 128,
                    android.graphics.Bitmap.Config.ARGB_8888);
            transparent.setPixel(64, 64, android.graphics.Color.RED);
            android.graphics.Bitmap filledLight = WidgetIcons.style(transparent, 40, 0xfff5f6f5),
                    filledDark = WidgetIcons.style(transparent, 40, 0xff252b29);
            check(filledLight.getPixel(64, 20) == 0xfff5f6f5
                    && filledDark.getPixel(64, 20) == 0xff252b29
                    && filledLight.getPixel(64, 20) != filledDark.getPixel(64, 20),
                    "custom light and dark background fills retain the selected RGB");
            check(filledLight.getPixel(64, 64) == android.graphics.Color.RED
                    && android.graphics.Color.alpha(filledLight.getPixel(0, 0)) == 0,
                    "background fill preserves opaque artwork and clipped corners");
            check(android.graphics.Color.alpha(
                    WidgetIcons.style(transparent, 40, android.graphics.Color.TRANSPARENT).getPixel(64, 20)) == 0,
                    "disabled background fill preserves transparent pixels");
            android.graphics.Bitmap edges = android.graphics.Bitmap.createBitmap(128, 128,
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas edgeCanvas = new android.graphics.Canvas(edges);
            android.graphics.Paint edgePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            edgePaint.setColor(0xff5681c7);
            edgeCanvas.drawCircle(64, 64, 45, edgePaint);
            edgePaint.setColor(android.graphics.Color.WHITE);
            edgeCanvas.drawCircle(64, 64, 24, edgePaint);
            IconStyle adaptiveFill = new IconStyle(40, true, 0);
            check(WidgetIcons.style(light, edges, adaptiveFill).getPixel(64, 5) == 0xff5681c7,
                    "circular contour sampling ignores transparent padding and center artwork");
            edges.setPixel(0, 0, 0x20000000);
            edges.setPixel(127, 127, 0x20000000);
            android.graphics.Bitmap completed = WidgetIcons.style(light, edges, adaptiveFill);
            check(completed.getPixel(64, 5) == 0xff5681c7
                    && completed.getPixel(64, 64) == android.graphics.Color.WHITE
                    && android.graphics.Color.alpha(completed.getPixel(0, 0)) == 0,
                    "adaptive fill ignores shadows, preserves artwork and uses chosen corner mask");
            check(edges.getPixel(64, 5) == 0 && edges.getPixel(0, 0) == 0x20000000,
                    "analysis and rendering leave source artwork unchanged");
            android.graphics.Bitmap gradient = android.graphics.Bitmap.createBitmap(128, 128,
                    android.graphics.Bitmap.Config.ARGB_8888);
            edgePaint.setShader(new android.graphics.LinearGradient(0, 0, 0, 128,
                    0xff4060b0, 0xffa0b050, android.graphics.Shader.TileMode.CLAMP));
            new android.graphics.Canvas(gradient).drawCircle(64, 64, 48, edgePaint);
            edgePaint.setShader(null);
            android.graphics.Bitmap gradientFill = WidgetIcons.style(light, gradient, adaptiveFill);
            check(android.graphics.Color.red(gradientFill.getPixel(64, 4)) < 75
                    && android.graphics.Color.red(gradientFill.getPixel(64, 123)) > 150
                    && gradientFill.getPixel(64, 64) == gradient.getPixel(64, 64),
                    "Android antialiased circular gradient extends top and bottom independently");
            android.graphics.Bitmap multicolor = android.graphics.Bitmap.createBitmap(128, 128,
                    android.graphics.Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 128; y++)
                for (int x = 0; x < 128; x++)
                    if (Math.hypot(x - 64, y - 64) <= 48)
                        multicolor.setPixel(x, y, x < 64 ? android.graphics.Color.RED : android.graphics.Color.BLUE);
            check(WidgetIcons.style(light, multicolor, adaptiveFill).getPixel(64, 5) == light
                    .getColor(R.color.widget_icon_background)
                    && WidgetIcons.style(dark, multicolor, adaptiveFill).getPixel(64, 5) == dark
                            .getColor(R.color.widget_icon_background),
                    "abrupt multicolor edges fall back to theme-specific plate");
            check(WidgetIcons.style(light, transparent, adaptiveFill).getPixel(64, 5) == light
                    .getColor(R.color.widget_icon_background)
                    && WidgetIcons.style(dark, transparent, adaptiveFill).getPixel(64, 5) == dark
                            .getColor(R.color.widget_icon_background),
                    "tiny irregular artwork uses themed fallback");
            android.graphics.Bitmap blank = android.graphics.Bitmap.createBitmap(128, 128,
                    android.graphics.Bitmap.Config.ARGB_8888);
            check(WidgetIcons.style(light, blank, adaptiveFill).getPixel(64, 64) == 0,
                    "fully transparent artwork remains empty");
            check(WidgetIcons.style(light, edges, new IconStyle(40, true, 0xffabcdef)).getPixel(64, 5) == 0xffabcdef
                    && WidgetIcons.style(light, edges, new IconStyle(40, false, 0)).getPixel(64, 5) == 0,
                    "explicit color and disabled fill remain independent of adaptive sampling");
            android.graphics.Rect frame = new android.graphics.Rect(0, 24, 400, 776),
                    bottomAnchor = new android.graphics.Rect(8, 704, 196, 810);
            android.graphics.Rect bubble = BubblePlacement.place(frame, bottomAnchor, 336, 80, 12, 4);
            check(frame.contains(bubble) && bubble.bottom < bottomAnchor.top,
                    "partly clipped bottom row puts complete bubble above");
            bubble = BubblePlacement.place(frame, new android.graphics.Rect(8, 80, 196, 152), 336, 80, 12, 4);
            check(bubble.top == 156 && frame.contains(bubble), "top row puts bubble below within screen");
            bubble = BubblePlacement.place(frame, bottomAnchor, 336, 1000, 12, 4);
            check(frame.contains(bubble) && bubble.height() == 728,
                    "oversized bubble is limited to the visible frame for scrolling");
            Map<String, Repository.App> catalog = Repository.apps(c);
            List<Predictor.Prediction> ranked = new ArrayList<>();
            Map<String, android.graphics.Bitmap> icons = new HashMap<>();
            for (String pkg : catalog.keySet()) {
                ranked.add(new Predictor.Prediction(pkg, 1, "test"));
                icons.put(pkg,
                        android.graphics.Bitmap.createBitmap(144, 144, android.graphics.Bitmap.Config.ARGB_8888));
            }
            check(ranked.size() >= 9, "emulator has enough apps for nine-slot layout");
            Repository.Snapshot fixture = new Repository.Snapshot(catalog, ranked, 10, 1, true);
            runOnMainSync(() -> {
                for (Context themed : new Context[]{light, dark}) {
                    android.view.View root = HabitWidget.render(themed, fixture, icons, "12:34", 400, 160).apply(themed,
                            new FrameLayout(themed));
                    int width = Ui.dp(themed, 400), height = Ui.dp(themed, 160);
                    root.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(height,
                                    android.view.View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, width, height);
                    android.widget.LinearLayout row1 = root.findViewById(R.id.row1),
                            row2 = root.findViewById(R.id.row2);
                    check(row1.getChildCount() == 5 && row2.getChildCount() == 5, "five by two widget in both themes");
                    check(row2.getChildAt(4).findViewById(R.id.more_time) != null, "last slot always more entry");
                    android.view.View timestamp = root.findViewById(R.id.more_time),
                            moreIcon = root.findViewById(R.id.more_icon);
                    check(timestamp.getAlpha() <= 0.35f
                            && timestamp.getTop() == moreIcon.getBottom(),
                            "timestamp is subtler and lifted toward the more icon");
                    check(Math.abs(moreIcon.getTop() * 2 + moreIcon.getHeight() - row2.getChildAt(4).getHeight()) <= 1,
                            "dots stay vertically centered independently of timestamp");
                    check(timestamp.getBottom() <= row2.getChildAt(4).getHeight(),
                            "timestamp below dots remains inside cell");
                    android.view.View cell = row1.getChildAt(0), icon = cell.findViewById(R.id.app_icon);
                    check(icon.getWidth() <= cell.getWidth() && icon.getHeight() <= cell.getHeight(),
                            "adaptive icons fit their cells");
                    HabitWidget.render(themed, fixture, icons, "12:35", 400, 160).reapply(themed, root);
                    check(row1.getChildCount() == 5 && row2.getChildCount() == 5,
                            "refresh reapply never duplicates widget children");
                }
            });
            WidgetPreferences original = new WidgetPreferences(3, 3, 80, false, true, false);
            original.save(c, 101);
            WidgetPreferences.ensure(c, 102);
            new WidgetPreferences(6, 5, 50, true, false).save(c,
                    android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
            WidgetPreferences saved = WidgetPreferences.load(c, 101), other = WidgetPreferences.load(c, 102);
            check(saved.columns == 3 && saved.rows == 3 && saved.percent == 80 && !saved.more && saved.actions,
                    "per-widget layout and tap mode survive reload");
            check(other.columns == 5 && other.rows == 2, "new default does not change an existing widget");
            check(!saved.moreTime && other.moreTime,
                    "timestamp setting persists separately with backward-compatible defaults");
            Repository.prefs(c).edit().remove("icon.roundness").putInt("recommendation_style_widget", 101)
                    .putInt("widget.default.roundness", 75).putBoolean("widget.default.fill_background", true)
                    .putInt("widget.default.fill_color", 0xffe2c4dd).commit();
            IconStyle migrated = IconStyle.load(c);
            check(migrated.roundness == 75 && migrated.fillBackground && migrated.fillColor == 0xffe2c4dd,
                    "upgrade migrates legacy appearance with safe fallback for a removed widget");
            check(WidgetIcons.style(light, edges, migrated).getPixel(64, 5) == 0xffe2c4dd
                    && WidgetIcons.style(dark, edges, migrated).getPixel(64, 5) == 0xffe2c4dd,
                    "custom global fill persists across themes");
            check(!migrated.cacheKey(c).equals(new IconStyle(75, true, 0xffabcdef).cacheKey(c)),
                    "bitmap cache distinguishes custom colors");
            IconStyle automatic = new IconStyle(40, true, 0);
            check(!automatic.cacheKey(light).equals(automatic.cacheKey(dark)),
                    "theme-specific source icons have separate cache entries in adaptive mode");
            long stampBeforeStyle = RefreshPolicy.last(c);
            new IconStyle(100, true, 0xffe2c4dd).save(c);
            Repository.prefs(c).edit().putInt("widget.default.roundness", 0).putInt("recommendation_style_widget", 0)
                    .commit();
            original.save(c, 101);
            check(IconStyle.load(c).roundness == 100 && IconStyle.load(c).fillColor == 0xffe2c4dd,
                    "global appearance survives widget layout saves and is migrated only once");
            check(stampBeforeStyle == RefreshPolicy.last(c), "saving appearance does not advance recommendation clock");
            WidgetPreferences.ensure(c, 103);
            check(WidgetPreferences.load(c, 103).capacity() == 29, "new widgets inherit changed default");
            runOnMainSync(() -> {
                android.view.View root = HabitWidget.render(c, fixture, icons, "12:35", 160, 160, original).apply(c,
                        new FrameLayout(c));
                int pixels = Ui.dp(c, 160);
                root.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(pixels, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(pixels, android.view.View.MeasureSpec.EXACTLY));
                root.layout(0, 0, pixels, pixels);
                int count = 0;
                for (int row : HabitWidget.ROWS)
                    count += ((android.widget.LinearLayout) root.findViewById(row)).getChildCount();
                check(count == 9 && root.findViewById(R.id.more_time) == null,
                        "small 3x3 grid holds nine apps with more disabled");
                android.widget.LinearLayout row = root.findViewById(R.id.row1);
                android.view.View cell = row.getChildAt(0), icon = cell.findViewById(R.id.app_icon);
                check(icon.getWidth() <= cell.getWidth() && icon.getHeight() <= cell.getHeight(),
                        "relative icon fits dense small widget");
                HabitWidget.render(c, fixture, icons, "12:36", 160, 160,
                        new WidgetPreferences(3, 3, 80, true, false, false)).reapply(c, root);
                root.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(pixels, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(pixels, android.view.View.MeasureSpec.EXACTLY));
                root.layout(0, 0, pixels, pixels);
                android.view.View moreIcon = root.findViewById(R.id.more_icon),
                        moreTime = root.findViewById(R.id.more_time);
                android.view.View moreCell = (android.view.View) moreIcon.getParent();
                check(moreTime.getVisibility() == android.view.View.GONE
                        && moreIcon.getVisibility() == android.view.View.VISIBLE,
                        "more dots remain when only time is disabled");
                check(Math.abs(moreIcon.getTop() * 2 + moreIcon.getHeight() - moreCell.getHeight()) <= 1,
                        "dots remain centered when time is disabled");
                HabitWidget.render(c, fixture, icons, "12:36", 160, 160, new WidgetPreferences(6, 5, 100, true))
                        .reapply(c, root);
                count = 0;
                for (int id : HabitWidget.ROWS)
                    count += ((android.widget.LinearLayout) root.findViewById(id)).getChildCount();
                check(count == 30 && root.findViewById(R.id.row5).getVisibility() == android.view.View.VISIBLE,
                        "maximum grid has thirty cells");
                HabitWidget.render(c, fixture, icons, "12:37", 160, 160, new WidgetPreferences(2, 1, 50, false))
                        .reapply(c, root);
                check(row.getChildCount() == 2
                        && root.findViewById(R.id.row5).getVisibility() == android.view.View.GONE,
                        "reducing rows clears stale content during reapply");
            });
            android.widget.ImageView[] styledView = {null};
            IconStyle style = new IconStyle(100, true, 0xffe2c4dd);
            android.graphics.Bitmap expectedIcon = WidgetIcons
                    .style(WidgetIcons.source(c.getPackageManager().getApplicationIcon(visible)), 100, 0xffe2c4dd);
            runOnMainSync(() -> {
                styledView[0] = new android.widget.ImageView(c);
                IconLoader.bind(c, visible, styledView[0], new IconStyle(0, false, 0));
                IconLoader.bind(c, visible, styledView[0], style);
            });
            boolean[] matched = {false};
            for (int i = 0; i < 60 && !matched[0]; i++) {
                runOnMainSync(() -> {
                    if (styledView[0].getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
                        matched[0] = ((android.graphics.drawable.BitmapDrawable) styledView[0].getDrawable())
                                .getBitmap().sameAs(expectedIcon);
                });
                if (!matched[0])
                    Thread.sleep(50);
            }
            check(matched[0],
                    "recommendation icon uses global clipping and color, ignoring stale asynchronous results");
            android.widget.ImageView[] sharedViews = new android.widget.ImageView[2];
            runOnMainSync(() -> {
                PickerCell picker = new PickerCell(c);
                picker.bind(catalog.get(visible), false, (v, checked) -> {
                });
                sharedViews[0] = (android.widget.ImageView) picker.getChildAt(0);
                android.widget.LinearLayout header = AppActions.header(c, catalog.get(visible), Ui.dp(c, 320));
                sharedViews[1] = (android.widget.ImageView) header.getChildAt(0);
            });
            for (android.widget.ImageView view : sharedViews) {
                boolean[] ready = {false};
                for (int i = 0; i < 60 && !ready[0]; i++) {
                    runOnMainSync(() -> {
                        if (view.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
                            ready[0] = ((android.graphics.drawable.BitmapDrawable) view.getDrawable()).getBitmap()
                                    .sameAs(expectedIcon);
                    });
                    if (!ready[0])
                        Thread.sleep(50);
                }
                check(ready[0], "picker and action header share the exact global icon bitmap");
            }
            runOnMainSync(() -> {
                for (float fontScale : new float[]{1f, 1.6f}) {
                    android.content.res.Configuration config = new android.content.res.Configuration(
                            c.getResources().getConfiguration());
                    config.fontScale = fontScale;
                    Context large = c.createConfigurationContext(config);
                    android.widget.LinearLayout header = AppActions.header(large,
                            new Repository.App("com.example.very.long.package.name.for.ellipsis",
                                    "A very long application name that cannot fit"),
                            Ui.dp(large, 296));
                    int width = Ui.dp(large, 296);
                    header.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(0,
                                    android.view.View.MeasureSpec.UNSPECIFIED));
                    header.layout(0, 0, width, header.getMeasuredHeight());
                    android.widget.TextView name = header.findViewWithTag("app-actions-name"),
                            pkg = header.findViewWithTag("app-actions-package");
                    check(name.getLineCount() == 1 && pkg.getLineCount() == 1
                            && name.getLayout().getEllipsisCount(0) > 0 && pkg.getLayout().getEllipsisCount(0) > 0,
                            "long name and package each ellipsize on one line at normal and large font");
                    android.view.View copyButton = header.findViewWithTag("app-actions-copy");
                    check(header.getChildCount() == 4
                            && Math.abs((name.getTop() + name.getBottom()) - (pkg.getTop() + pkg.getBottom())) <= 1
                            && pkg.getRight() <= copyButton.getLeft() && copyButton.getRight() <= width,
                            "icon name package and small copy button stay in one compact horizontal header");
                }
            });
            java.io.File shareDir = new java.io.File(c.getCacheDir(), "shared-apks");
            shareDir.mkdirs();
            java.io.File originalApk = new java.io.File(c.getApplicationInfo().sourceDir),
                    copy = new java.io.File(shareDir, "test-copy.apk");
            copy.delete();
            ApkShare.export(originalApk, null, copy);
            check(java.util.Arrays.equals(hash(originalApk), hash(copy)),
                    "single APK export preserves original signed bytes");
            android.net.Uri shared = androidx.core.content.FileProvider.getUriForFile(c,
                    c.getPackageName() + ".apkfiles", copy);
            try (java.io.InputStream input = c.getContentResolver().openInputStream(shared)) {
                check(input.read() == 0x50 && input.read() == 0x4b, "temporary content URI reads exported APK");
            }
            boolean denied = false;
            try {
                androidx.core.content.FileProvider.getUriForFile(c, c.getPackageName() + ".apkfiles",
                        new java.io.File(c.getFilesDir(), "private.db"));
            } catch (IllegalArgumentException expected) {
                denied = true;
            }
            check(denied, "sharing provider cannot expose private data outside export cache");
            java.io.File split = new java.io.File(c.getCacheDir(), "fixture-split.apk"),
                    bundle = new java.io.File(shareDir, "test-split.zip");
            java.nio.file.Files.write(split.toPath(), new byte[]{1, 2, 3});
            bundle.delete();
            ApkShare.export(originalApk, new String[]{split.getPath()}, bundle);
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(bundle)) {
                check(zip.getEntry("base.apk").getSize() == originalApk.length()
                        && zip.getEntry("split_0_fixture-split.apk").getSize() == 3 && zip.getEntry("安装说明.txt") != null,
                        "split export includes base, every split and installation note");
            }
            java.io.File failed = new java.io.File(shareDir, "test-failed.zip");
            boolean failedCleanly = false;
            try {
                ApkShare.export(originalApk, new String[]{"/missing.apk"}, failed);
            } catch (java.io.IOException expected) {
                failedCleanly = !failed.exists();
            }
            check(failedCleanly, "failed export leaves no partial package");
            long cleanupNow = System.currentTimeMillis(), day = 24 * 60 * 60 * 1000L;
            java.io.File boundary = new java.io.File(shareDir, "test-boundary.apk");
            java.nio.file.Files.write(boundary.toPath(), new byte[]{4});
            check(copy.setLastModified(cleanupNow - 2 * day) && boundary.setLastModified(cleanupNow - day)
                    && split.setLastModified(cleanupNow - 2 * day), "cache cleanup fixture timestamps set");
            ApkShare.prune(shareDir, cleanupNow);
            check(!copy.exists() && bundle.exists() && boundary.exists(),
                    "cleanup removes expired exports but retains recent and boundary-age shares");
            check(split.exists(), "cleanup is limited to the shared-apks directory");
            for (int attempt = 0; attempt < 2; attempt++) {
                java.nio.file.Files.write(copy.toPath(), new byte[]{5});
                if (!copy.setLastModified(cleanupNow - 2 * day))
                    throw new AssertionError("cannot age launch-cleanup fixture");
                Activity main = startActivitySync(new Intent(c, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                waitForIdleSync();
                Repository.WORK.submit(() -> {
                }).get();
                check(!copy.exists() && bundle.exists() && split.exists(),
                        "opening app clears expired shares without another export, attempt " + attempt);
                runOnMainSync(main::finish);
                waitForIdleSync();
            }
            boundary.delete();
            copy.delete();
            split.delete();
            bundle.delete();
            RefreshJob.schedule(c);
            check(c.getSystemService(JobScheduler.class).getPendingJob(1919) == null,
                    "no periodic work without widgets");
            Repository.prefs(c).edit().clear().commit();
            // Remove test-only cutoff so manual emulator QA starts with real system
            // records.
            try (UsageStore store = new UsageStore(c)) {
                store.getWritableDatabase().delete("meta", null, null);
            }
            results.putString("stream", "\nPASS: " + checks + " Android integration checks\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable failure) {
            results.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, results);
        }
    }
}
