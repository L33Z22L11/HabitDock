package dev.habitdock;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** No preference, database, permission or launcher changes. */
public final class GlassHomeRunner extends Instrumentation {
    private int checks;
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            Throwable[] failure = {null};
            runOnMainSync(() -> {
                try {
                    checkEntryRouting();
                    checkMaterialSwitch();
                    for (int orientation : new int[]{Configuration.ORIENTATION_PORTRAIT,
                            Configuration.ORIENTATION_LANDSCAPE}) {
                        Configuration config = new Configuration(getTargetContext().getResources().getConfiguration());
                        config.orientation = orientation;
                        Context context = isolatedMaterialContext(getTargetContext().createConfigurationContext(config),
                                true);
                        Configuration normalText = new Configuration(config);
                        normalText.fontScale = 1f;
                        Context sizing = context.createConfigurationContext(normalText);
                        int pickerPadding = 2 * Ui.dp(sizing, 8);
                        LinearLayout switchContainer = Ui.column(context);
                        com.google.android.material.materialswitch.MaterialSwitch switchControl = Ui.switchRow(
                                switchContainer, "液态材质", "test-switch");
                        FormRow switchRow = (FormRow) switchContainer.getChildAt(0);
                        TextView switchLabel = (TextView) ((LinearLayout) switchRow.getChildAt(0)).getChildAt(0);
                        layout(switchRow, Ui.dp(context, 320), Ui.dp(context, 56));
                        check(Math.abs(switchLabel.getTextSize()
                                / context.getResources().getDisplayMetrics().scaledDensity - 16) < .01f,
                                "switch and other form labels share 16sp text");
                        check("液态材质".contentEquals(switchControl.getContentDescription()),
                                "the switch retains an accessible label");
                        switchRow.performClick();
                        check(switchControl.isChecked(), "the whole form row toggles its switch");
                        check(AppPickerView.columnsForWidth(sizing, Ui.dp(sizing, 360), pickerPadding) == 2,
                                "picker uses two columns on a typical phone");
                        check(AppPickerView.columnsForWidth(sizing, Ui.dp(sizing, 720), pickerPadding) == 4,
                                "picker adapts to four columns on a tablet");
                        checkPickerWidths(sizing);
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            android.app.Dialog blurDialog = new android.app.Dialog(context,
                                    android.R.style.Theme_Translucent_NoTitleBar);
                            ScopedWindowBlur.configure(blurDialog.getWindow(), context, 28);
                            check(blurDialog.getWindow().peekDecorView() != null,
                                    "background blur initializes a dialog's decor before configuring it");
                            if (ScopedWindowBlur.available(context)) {
                                androidx.appcompat.app.AlertDialog modal = new GlassDialogBuilder(context)
                                        .setTitle("更新频率").setMessage("test").create();
                                android.graphics.Rect insets = new android.graphics.Rect();
                                android.graphics.drawable.Drawable background = modal.getWindow().getDecorView()
                                        .getBackground();
                                if (background != null)
                                    background.getPadding(insets);
                                check(insets.left == 0 && insets.top == 0 && insets.right == 0 && insets.bottom == 0,
                                        "window blur shares the dialog panel bounds");
                            }
                        }
                        SlidingChoice segments = new SlidingChoice(context, new String[]{"平铺", "宫格"}, "test-",
                                value -> {
                                });
                        segments.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                View.MeasureSpec.makeMeasureSpec(Ui.dp(context, 40), View.MeasureSpec.EXACTLY));
                        check(segments.getMeasuredWidth() >= Ui.dp(context, 64 * 2 + 6),
                                "segments have consistent minimum width");
                        LinearLayout segmentForm = Ui.column(context);
                        SlidingChoice longSegments = new SlidingChoice(context,
                                new String[]{"普通圆角", "超椭圆"}, "long-segment-", value -> {
                                });
                        longSegments.addRow(segmentForm, "圆角模式", 0);
                        FormRow segmentRow = (FormRow) segmentForm.getChildAt(0);
                        segmentRow.measure(
                                View.MeasureSpec.makeMeasureSpec(Ui.dp(context, 320), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        segmentRow.layout(0, 0, segmentRow.getMeasuredWidth(), segmentRow.getMeasuredHeight());
                        TextView longLabel = longSegments.findViewWithTag("long-segment-0");
                        check(Math.abs(longLabel.getTextSize()
                                / context.getResources().getDisplayMetrics().scaledDensity - Ui.FORM_TEXT_SP) < .01f,
                                "segment labels use the same font size as form labels");
                        check(longLabel.getWidth() - longLabel.getPaddingLeft() - longLabel
                                .getPaddingRight() >= longLabel.getPaint().measureText(longLabel.getText().toString()),
                                "long segment text fits on a phone-sized form");
                        int[] selection = {-1};
                        GlassNavigation nav = new GlassNavigation(context, value -> selection[0] = value);
                        boolean vertical = orientation == Configuration.ORIENTATION_LANDSCAPE;
                        int width = Ui.dp(context, vertical ? 80 : 360), height = Ui.dp(context, vertical ? 300 : 76);
                        FrameLayout navHost = new FrameLayout(context);
                        View page = new View(context);
                        page.setBackgroundColor(0xff336699);
                        navHost.addView(page, new FrameLayout.LayoutParams(-1, -1));
                        navHost.addView(nav, new FrameLayout.LayoutParams(-1, -1));
                        layout(navHost, width, height);
                        check(nav.getLayerType() != View.LAYER_TYPE_SOFTWARE,
                                "glass stays on hardware renderer without screenshot feedback");
                        nav.select(1, false);
                        check(nav.getChildCount() == 3, "three navigation destinations");
                        check(nav.getOrientation() == (vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL),
                                "navigation follows orientation");
                        float insetPx = Ui.dp(context, 4);
                        for (int endIndex : new int[]{0, 2}) {
                            View cell = nav.getChildAt(endIndex);
                            android.graphics.RectF slot = new android.graphics.RectF(
                                    cell.getLeft(), cell.getTop(), cell.getRight(), cell.getBottom());
                            android.graphics.RectF indicator = new android.graphics.RectF();
                            GlassNavigation.indicatorBounds(slot, indicator, width, height,
                                    vertical ? nav.getPaddingTop() : nav.getPaddingLeft(), insetPx, 1, vertical);
                            float edgeGap = vertical
                                    ? (endIndex == 0 ? indicator.top : height - indicator.bottom)
                                    : (endIndex == 0 ? indicator.left : width - indicator.right);
                            float crossGap = vertical ? indicator.left : indicator.top;
                            check(Math.abs(edgeGap - crossGap) < 1,
                                    "indicator end and cross-axis gaps match at device density");
                            float idleAxis = vertical ? indicator.height() : indicator.width();
                            GlassNavigation.indicatorBounds(slot, indicator, width, height,
                                    vertical ? nav.getPaddingTop() : nav.getPaddingLeft(), insetPx, 1.15f, vertical);
                            check((vertical ? indicator.height() : indicator.width()) > idleAxis + 1,
                                    "indicator stretches inward even at the end of the bar");
                            check(indicator.left >= insetPx - .5f && indicator.top >= insetPx - .5f
                                    && indicator.right <= width - insetPx + .5f
                                    && indicator.bottom <= height - insetPx + .5f,
                                    "stretched indicator retains the outer margin");
                        }
                        for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                            Configuration colorConfig = new Configuration(config);
                            colorConfig.uiMode = (colorConfig.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
                            Context colorContext = isolatedMaterialContext(
                                    getTargetContext().createConfigurationContext(colorConfig), true);
                            GlassFallback material = new GlassFallback(colorContext);
                            int fw = Ui.dp(colorContext, 120), fh = Ui.dp(colorContext, 40);
                            android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(
                                    fw, fh, android.graphics.Bitmap.Config.ARGB_8888);
                            material.draw(new android.graphics.Canvas(image),
                                    new android.graphics.RectF(0, 0, fw, fh), fh / 2f);
                            int pixel = image.getPixel(fw / 2, fh / 2);
                            check(android.graphics.Color.alpha(pixel) >= 185,
                                    "fallback surface stays sufficiently opaque");
                            int surface = Ui.color(colorContext, R.color.surface);
                            double renderedLuminance = androidx.core.graphics.ColorUtils.calculateLuminance(
                                    androidx.core.graphics.ColorUtils.setAlphaComponent(pixel, 255));
                            double surfaceLuminance = androidx.core.graphics.ColorUtils.calculateLuminance(
                                    androidx.core.graphics.ColorUtils.setAlphaComponent(surface, 255));
                            check(Math.abs(renderedLuminance - surfaceLuminance) < .15,
                                    "fallback surface follows the resolved theme surface");
                            int topEdge = image.getPixel(fw / 2, Math.min(fh - 1, Ui.dp(colorContext, 1)));
                            int bottomEdge = image.getPixel(fw / 2, Math.max(0, fh - 1 - Ui.dp(colorContext, 1)));
                            java.lang.reflect.Field edgeField = GlassFallback.class.getDeclaredField("edge");
                            edgeField.setAccessible(true);
                            android.graphics.Bitmap edgePixels = (android.graphics.Bitmap) edgeField.get(material);
                            GlassFallback repeatedMaterial = new GlassFallback(colorContext);
                            repeatedMaterial.draw(new android.graphics.Canvas(image),
                                    new android.graphics.RectF(0, 0, fw, fh), fh / 2f);
                            check(edgeField.get(repeatedMaterial) == edgePixels,
                                    "identical glass outlines reuse their cached edge across views");
                            int rawTop = edgePixels.getPixel(fw / 2, Ui.dp(colorContext, 1));
                            int rawBottom = edgePixels.getPixel(fw / 2, fh - 1 - Ui.dp(colorContext, 1));
                            check(android.graphics.Color.red(topEdge) > android.graphics.Color.red(pixel)
                                    && android.graphics.Color.red(bottomEdge) > android.graphics.Color.red(pixel),
                                    "fallback glass brightens both contour edges: mode=" + mode
                                            + " center=" + Integer.toHexString(pixel)
                                            + " top=" + Integer.toHexString(topEdge)
                                            + " bottom=" + Integer.toHexString(bottomEdge)
                                            + " rawTop=" + Integer.toHexString(rawTop)
                                            + " rawBottom=" + Integer.toHexString(rawBottom)
                                            + " size=" + fw + "x" + fh);
                            if (mode == Configuration.UI_MODE_NIGHT_YES)
                                check(android.graphics.Color.alpha(rawTop) <= 100,
                                        "dark edge highlight remains subdued");
                            image.recycle();
                            GlassFallback sheetMaterial = new GlassFallback(colorContext, true);
                            android.graphics.Bitmap sheetImage = android.graphics.Bitmap.createBitmap(
                                    fw, fh * 3, android.graphics.Bitmap.Config.ARGB_8888);
                            sheetMaterial.draw(new android.graphics.Canvas(sheetImage),
                                    new android.graphics.RectF(0, 0, fw, fh * 3), fh / 2f);
                            int sheetCenter = sheetImage.getPixel(fw / 2, fh * 2);
                            int sheetBottom = sheetImage.getPixel(fw / 2, fh * 3 - 1);
                            check(sheetCenter == sheetBottom && android.graphics.Color.alpha(
                                    sheetImage.getPixel(0, fh * 3 - 1)) > 0,
                                    "sheet has a square bottom with no bottom highlight");
                            sheetImage.recycle();
                        }
                        int[] dragUpdates = {0};
                        nav.onDrag = value -> dragUpdates[0]++;
                        nav.select(1, false);
                        View middleCell = nav.getChildAt(1);
                        float axisCenter = vertical
                                ? (middleCell.getTop() + middleCell.getBottom()) / 2f
                                : (middleCell.getLeft() + middleCell.getRight()) / 2f;
                        float cellAxis = vertical ? middleCell.getHeight() : middleCell.getWidth();
                        float offCenter = axisCenter + cellAxis * .35f;
                        float pressX = vertical ? width / 2f : offCenter;
                        float pressY = vertical ? offCenter : height / 2f;
                        motion(nav, android.view.MotionEvent.ACTION_DOWN, pressX, pressY);
                        motion(nav, android.view.MotionEvent.ACTION_MOVE,
                                vertical ? pressX : pressX + Ui.dp(context, 3),
                                vertical ? pressY + Ui.dp(context, 3) : pressY);
                        check(dragUpdates[0] == 0 && Math.abs(number(nav, "position") - 1) < .001f,
                                "off-center press and small finger jitter do not move the page");
                        motion(nav, android.view.MotionEvent.ACTION_UP,
                                vertical ? pressX : pressX + Ui.dp(context, 3),
                                vertical ? pressY + Ui.dp(context, 3) : pressY);
                        check(selection[0] == 1, "small movement remains a click on the pressed item");
                        nav.select(1, false);
                        dragUpdates[0] = 0;
                        motion(nav, android.view.MotionEvent.ACTION_DOWN, pressX, pressY);
                        int delta = Math.max(Ui.dp(context, 10),
                                ViewConfiguration.get(context).getScaledTouchSlop()) + Ui.dp(context, 4);
                        motion(nav, android.view.MotionEvent.ACTION_MOVE,
                                vertical ? pressX : pressX + delta, vertical ? pressY + delta : pressY);
                        float expectedPosition = Math.min(2, 1 + delta / cellAxis);
                        check(dragUpdates[0] > 0 && Math.abs(number(nav, "position") - expectedPosition) < .01f,
                                "drag begins from the pressed item, not the off-center finger location");
                        motion(nav, android.view.MotionEvent.ACTION_CANCEL, pressX, pressY);
                        nav.onDrag = value -> {
                        };
                        nav.select(0, false);
                        gesture(nav, vertical ? width / 2f : width * .15f, vertical ? height * .15f : height / 2f,
                                vertical ? width / 2f : width * .85f, vertical ? height * .85f : height / 2f);
                        check(selection[0] == 2, "drag selects last destination");
                        check(nav.getChildAt(2).isSelected(), "selection accessible to navigation item");
                        int[] interruptions = {0};
                        nav.onPressStart = () -> interruptions[0]++;
                        nav.select(0, false);
                        nav.select(2, true);
                        android.animation.ValueAnimator oldSettle = animator(nav, "motion");
                        oldSettle.setCurrentPlayTime(790);
                        float middleX = width / 2f, middleY = height / 2f;
                        motion(nav, android.view.MotionEvent.ACTION_DOWN, middleX, middleY);
                        check(!oldSettle.isRunning(), "new press cancels the old rebound at 790ms");
                        check(interruptions[0] == 1, "new press interrupts the pager immediately");
                        animator(nav, "catchUp").setCurrentPlayTime(110);
                        check(Math.abs(number(nav, "position") - 1) < .01f,
                                "stationary press reaches the new absolute tab position");
                        motion(nav, android.view.MotionEvent.ACTION_UP, middleX, middleY);
                        check(selection[0] == 1, "release selects the new tab before the old rebound could finish");
                        android.animation.ValueAnimator secondSettle = animator(nav, "motion");
                        secondSettle.setCurrentPlayTime(799);
                        motion(nav, android.view.MotionEvent.ACTION_DOWN,
                                vertical ? width / 2f : width * .85f,
                                vertical ? height * .85f : height / 2f);
                        check(!secondSettle.isRunning() && interruptions[0] == 2,
                                "repeated press also interrupts the final rebound frame");
                        motion(nav, android.view.MotionEvent.ACTION_CANCEL, middleX, middleY);
                        check(selection[0] == 1, "cancel restores selection from before the interrupted press");
                        nav.select(0, false);
                        NavigationPager pager = new NavigationPager(context);
                        layout(pager, width, height);
                        for (float visible : new float[]{0, .35f, 1, 1.65f, 2}) {
                            int offset = Math.round(visible * width);
                            pager.scrollTo(offset, 0);
                            nav.select(2, false); // Deliberately stale indicator endpoint.
                            float start = pager.prepareNavigation();
                            nav.startFromPage(start);
                            check(pager.getScrollX() == offset && !pager.isFakeDragging(),
                                    "press preserves the visible frame without starting fake drag");
                            check(Math.abs(number(nav, "position") - offset / (float) width) < .001f,
                                    "new gesture rebases from visible page, not stale settings endpoint");
                            pager.dragNavigationTo(start);
                            check(!pager.isFakeDragging(), "stationary press does not begin page dragging");
                        }
                        nav.select(0, false);
                        FadingListFrame fade = new FadingListFrame(context);
                        View sample = new View(context);
                        sample.setBackgroundColor(0xffff0000);
                        fade.addView(sample, new FrameLayout.LayoutParams(-1, -1));
                        int inset = Ui.dp(context, 24);
                        fade.topInset(inset);
                        int fadeWidth = Ui.dp(context, 20), fadeHeight = Ui.dp(context, 140);
                        layout(fade, fadeWidth, fadeHeight);
                        android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(
                                fadeWidth, fadeHeight, android.graphics.Bitmap.Config.ARGB_8888);
                        fade.draw(new android.graphics.Canvas(pixels));
                        check(android.graphics.Color.alpha(pixels.getPixel(fadeWidth / 2, 0)) <= 2,
                                "list content reaches transparency only at the screen top");
                        check(android.graphics.Color.alpha(pixels.getPixel(fadeWidth / 2, inset)) > 0,
                                "fade extends through the status bar instead of disappearing below it");
                        int middleAlpha = android.graphics.Color
                                .alpha(pixels.getPixel(fadeWidth / 2, inset + Ui.dp(context, 44)));
                        check(middleAlpha > 0 && middleAlpha < 255, "list content fades through header band");
                        check(android.graphics.Color
                                .alpha(pixels.getPixel(fadeWidth / 2, inset + Ui.dp(context, 70))) == 255,
                                "list below header remains fully opaque");
                        int band = inset + Ui.dp(context, FloatingTitleBar.CONTENT_TOP_DP);
                        int lower = android.graphics.Color
                                .alpha(pixels.getPixel(fadeWidth / 2, Math.round(band * .85f)));
                        int upper = android.graphics.Color
                                .alpha(pixels.getPixel(fadeWidth / 2, Math.round(band * .5f)));
                        int nearTop = android.graphics.Color
                                .alpha(pixels.getPixel(fadeWidth / 2, Math.round(band * .15f)));
                        check(255 - lower > upper - nearTop,
                                "content fades faster on entering the header than near the top");
                        int quarter = android.graphics.Color
                                .alpha(pixels.getPixel(fadeWidth / 2, Math.round(band * .325f)));
                        check(Math.abs((upper - quarter) - (quarter - nearTop)) <= 3,
                                "remaining opacity fades uniformly toward the top");
                        pixels.recycle();
                        FrameLayout dialogPanel = new FrameLayout(context);
                        GlassPanels.attach(dialogPanel, 28, 3);
                        android.graphics.drawable.Drawable panelBackground = dialogPanel.getBackground();
                        GlassPanels.attach(dialogPanel, 28, 3);
                        check(panelBackground != null && dialogPanel.getBackground() == panelBackground,
                                "reopening a panel does not stack material listeners or backgrounds");
                        int panelWidth = Ui.dp(context, 120), panelHeight = Ui.dp(context, 80);
                        layout(dialogPanel, panelWidth, panelHeight);
                        android.graphics.Bitmap panelPixels = android.graphics.Bitmap.createBitmap(
                                panelWidth, panelHeight, android.graphics.Bitmap.Config.ARGB_8888);
                        dialogPanel.draw(new android.graphics.Canvas(panelPixels));
                        check(android.graphics.Color
                                .alpha(panelPixels.getPixel(panelWidth / 2, panelHeight / 2)) >= 185,
                                "dialog panel has a readable fallback surface");
                        panelPixels.recycle();
                        LinearLayout formBody = Ui.column(context);
                        int sliderStart = -1;
                        for (String title : new String[]{"明度", "饱和度", "圆角比例", "背景透明度"}) {
                            com.google.android.material.slider.Slider formSlider = new com.google.android.material.slider.Slider(
                                    context);
                            formSlider.setValueTo(200);
                            formSlider.setValue(100);
                            formSlider.setTag("form-" + title);
                            Ui.sliderRow(formBody, title, formSlider, Ui.text(context, "100%", 13, R.color.muted),
                                    100f);
                            FormRow form = (FormRow) formSlider.getParent();
                            layout(form, Ui.dp(context, 320), Ui.dp(context, 56));
                            formSlider.setValue(120);
                            LinearLayout heading = (LinearLayout) form.getChildAt(0);
                            View label = heading.getChildAt(0), resetButton = heading.getChildAt(1);
                            check(resetButton.getLeft() == label.getRight(),
                                    "reset touch target follows label with no gap");
                            check(resetButton.getWidth() == Ui.dp(context, 48), "reset retains a 48dp touch target");
                            check(sliderStart < 0 || formSlider.getLeft() == sliderStart,
                                    "common label and reset group aligns typical sliders");
                            sliderStart = formSlider.getLeft();
                        }
                        GlassCatalog catalog = new GlassCatalog(context, (app, anchor) -> {
                        });
                        Map<String, Repository.App> apps = new LinkedHashMap<>();
                        apps.put("test.wechat", new Repository.App("test.wechat", "微信"));
                        apps.put("test.alipay", new Repository.App("test.alipay", "支付宝"));
                        apps.put("test.calc", new Repository.App("test.calc", "Calculator"));
                        catalog.show(apps, Collections.singleton("test.calc"));
                        layout(catalog, Ui.dp(context, vertical ? 720 : 360), Ui.dp(context, 650));
                        GridView grid = catalog.findViewWithTag("catalog-grid");
                        check(vertical ? grid.getNumColumns() > 5 : grid.getNumColumns() == 4,
                                "grid expands beyond five columns in landscape");
                        check(grid.getAdapter().getCount() == 3, "all apps initially visible");
                        check(grid.getPaddingLeft() == grid.getPaddingRight(), "catalog grid has equal side margins");
                        java.lang.reflect.Field iconSize = GlassCatalog.class.getDeclaredField("iconDp");
                        iconSize.setAccessible(true);
                        int originalIconSize = iconSize.getInt(catalog);
                        iconSize.setInt(catalog, 64);
                        grid.requestLayout();
                        layout(catalog, Ui.dp(context, 420), Ui.dp(context, 650));
                        check(grid.getNumColumns() == 5, "420dp phone shows five columns with 64dp icons");
                        iconSize.setInt(catalog, 72);
                        grid.requestLayout();
                        layout(catalog, Ui.dp(context, 420), Ui.dp(context, 650));
                        check(grid.getNumColumns() == 4, "420dp phone adapts to four columns with 72dp icons");
                        iconSize.setInt(catalog, 96);
                        grid.requestLayout();
                        layout(catalog, Ui.dp(context, 420), Ui.dp(context, 650));
                        check(grid.getNumColumns() == 3, "larger icons can reduce the column count further");
                        iconSize.setInt(catalog, originalIconSize);
                        grid.requestLayout();
                        layout(catalog, Ui.dp(context, 420), Ui.dp(context, 650));
                        View rail = catalog.findViewWithTag("catalog-index");
                        check(rail.getRight() + ((View) rail.getParent()).getLeft() == catalog.getWidth(),
                                "index touch area reaches the page edge");
                        float y = rail.getHeight() * (22.5f / 27); // W
                        float edgeY = ((View) rail.getParent()).getTop() + rail.getTop() + y;
                        motion(catalog, android.view.MotionEvent.ACTION_DOWN, catalog.getWidth() - 1, edgeY);
                        motion(catalog, android.view.MotionEvent.ACTION_MOVE, catalog.getWidth() - Ui.dp(context, 70),
                                edgeY);
                        for (int i = 0; i < 3; i++) {
                            Repository.App app = (Repository.App) grid.getAdapter().getItem(i);
                            View cell = grid.getAdapter().getView(i, null, grid);
                            ImageView icon = (ImageView) ((LinearLayout) cell).getChildAt(0);
                            check(app.label.equals("微信") ? icon.getImageAlpha() == 255 : icon.getImageAlpha() < 80,
                                    "Chinese initials highlight W while dimming other apps");
                        }
                        motion(catalog, android.view.MotionEvent.ACTION_UP, catalog.getWidth() - Ui.dp(context, 70),
                                edgeY);
                        LinearLayout restoredCell = (LinearLayout) grid.getAdapter().getView(0, null, grid);
                        check(((ImageView) restoredCell.getChildAt(0)).getImageAlpha() == 255,
                                "release restores opacity");
                        EditText query = catalog.findViewWithTag("catalog-search");
                        query.setText("支付宝");
                        check(grid.getAdapter().getCount() == 1, "Chinese search filters app names");
                        query.setText("missing-app");
                        check(grid.getAdapter().getCount() == 0, "empty search results handled");
                        catalog.findViewWithTag("catalog-clear").performClick();
                        check(grid.getAdapter().getCount() == 3, "clear restores full catalog");
                        Bundle filterState = new Bundle();
                        filterState.putInt("all.type", 2);
                        catalog.restore(filterState, "all");
                        check(grid.getAdapter().getCount() == 1, "system-only filter");
                        filterState.putInt("all.type", 1);
                        catalog.restore(filterState, "all");
                        check(grid.getAdapter().getCount() == 2, "non-system filter");
                        Map<String, Repository.App> manyApps = new LinkedHashMap<>();
                        for (int i = 0; i < 100; i++) {
                            String pkg = "test.item" + i;
                            manyApps.put(pkg, new Repository.App(pkg, String.format(Locale.ROOT, "Item %03d", i)));
                        }
                        catalog.show(manyApps);
                        layout(catalog, Ui.dp(context, 420), Ui.dp(context, 650));
                        // The scrolling and its layout are asynchronous; a detached
                        // view cannot be expected to finish them inside this same
                        // main-thread callback. Check the target geometry directly.
                        java.lang.reflect.Method offset = GlassCatalog.class.getDeclaredMethod("indexScrollOffset",
                                int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                                int.class, int.class);
                        offset.setAccessible(true);
                        int columns = grid.getNumColumns(), chosen = 50, target = Ui.dp(context, 340);
                        int rowHeight = grid.getChildAt(0).getHeight(), spacing = grid.getVerticalSpacing();
                        int end = (int) offset.invoke(null, chosen, columns, 100, rowHeight, spacing,
                                grid.getHeight(), grid.getPaddingTop(), grid.getPaddingBottom(), target);
                        int center = chosen / columns * (rowHeight + spacing) + grid.getPaddingTop()
                                + rowHeight / 2 - end;
                        check(Math.abs(center - target) <= Ui.dp(context, 1),
                                "index target places its row near the anchor away from list boundaries");
                        check((int) offset.invoke(null, 0, columns, 100, rowHeight, spacing, grid.getHeight(),
                                grid.getPaddingTop(), grid.getPaddingBottom(), target) == 0,
                                "index target is clamped at the start of the list");
                        java.lang.reflect.Method scroll = GlassCatalog.class.getDeclaredMethod("scrollToIndex",
                                int.class, int.class, int.class);
                        scroll.setAccessible(true);
                        java.lang.reflect.Field posted = GlassCatalog.class.getDeclaredField("indexScrollPosted");
                        java.lang.reflect.Field scrollTarget = GlassCatalog.class.getDeclaredField("indexScrollTarget");
                        java.lang.reflect.Field frameTime = GlassCatalog.class.getDeclaredField("indexScrollFrameTime");
                        posted.setAccessible(true);
                        scrollTarget.setAccessible(true);
                        frameTime.setAccessible(true);
                        int firstBefore = grid.getFirstVisiblePosition();
                        int topBefore = grid.getChildAt(0).getTop();
                        scroll.invoke(catalog, chosen, 90, target);
                        check(posted.getBoolean(catalog), "index scrolling schedules an animation frame");
                        frameTime.setLong(catalog, 123);
                        scroll.invoke(catalog, 75, 90, target);
                        check(posted.getBoolean(catalog) && frameTime.getLong(catalog) == 123,
                                "a new index target retargets the pending animation without restarting it");
                        scroll.invoke(catalog, 0, 90, target);
                        check(posted.getBoolean(catalog) && scrollTarget.getInt(catalog) == 0,
                                "dragging back to the start retargets the same animation");
                        scroll.invoke(catalog, 0, 90, target + Ui.dp(context, 20));
                        check(posted.getBoolean(catalog) && frameTime.getLong(catalog) == 123,
                                "repeated gestures clamped to the top do not restart animation");
                        check(grid.getTranslationY() == 0 && grid.getFirstVisiblePosition() == firstBefore
                                && grid.getChildAt(0).getTop() == topBefore,
                                "retargeting does not translate or instantly jump the grid");
                        query.setText("item");
                        check(!posted.getBoolean(catalog), "changing the catalog cancels its pending scroll frame");
                        Bundle restored = new Bundle();
                        restored.putString("all.query", "item");
                        restored.putInt("all.position", 45);
                        catalog.restore(restored, "all");
                        java.lang.reflect.Field pendingRestore = GlassCatalog.class.getDeclaredField("pendingRestore");
                        pendingRestore.setAccessible(true);
                        check(pendingRestore.get(catalog) != null, "restoring position posts one deferred selection");
                        java.lang.reflect.Method jump = GlassCatalog.class.getDeclaredMethod("jump", String.class,
                                boolean.class);
                        jump.setAccessible(true);
                        jump.invoke(catalog, "I", true);
                        check(pendingRestore.get(catalog) == null,
                                "index interaction cancels an outdated deferred restore");
                        int sameLetterTarget = scrollTarget.getInt(catalog);
                        long sameLetterFrame = frameTime.getLong(catalog);
                        jump.invoke(catalog, "I", true);
                        check(scrollTarget.getInt(catalog) == sameLetterTarget
                                && frameTime.getLong(catalog) == sameLetterFrame,
                                "moving within one index letter does not retarget the list");
                    }
                } catch (Throwable error) {
                    failure[0] = error;
                }
            });
            if (failure[0] != null)
                throw failure[0];
            checkIconRebinding();
            android.content.pm.ActivityInfo legacy = getTargetContext().getPackageManager().getActivityInfo(
                    new android.content.ComponentName(getTargetContext(), "dev.habitdock.MainActivity"), 0);
            check("dev.habitdock.GlassHomeActivity".equals(legacy.targetActivity),
                    "widget legacy entry resolves to new home");
            android.content.pm.ActivityInfo more = getTargetContext().getPackageManager().getActivityInfo(
                    new android.content.ComponentName(getTargetContext(), "dev.habitdock.MoreActivity"), 0);
            check("dev.habitdock.GlassHomeActivity".equals(more.targetActivity),
                    "old MoreActivity entry resolves to new home");
            android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME).setPackage(getTargetContext().getPackageName());
            check(!getTargetContext().getPackageManager().queryIntentActivities(home, 0).isEmpty(),
                    "system home entry available");
            result.putString("stream", "PASS " + checks + " glass navigation and catalog checks\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(0, result);
        }
    }

    private void check(boolean condition, String description) {
        if (!condition)
            throw new AssertionError(description);
        checks++;
    }

    private void checkEntryRouting() {
        Context context = getTargetContext();
        android.content.Intent icon = android.content.Intent.makeMainActivity(
                new android.content.ComponentName(context, GlassHomeActivity.class));
        android.content.Intent widget = GlassHomeActivity.widgetIntent(context, 42);
        android.content.Intent oldWidget = new android.content.Intent().setComponent(
                new android.content.ComponentName(context, "dev.habitdock.MoreActivity"));
        android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME);
        android.content.Intent actions = GlassHomeActivity.actionIntent(context, "example.app", 42);
        android.content.ComponentName legacy = new android.content.ComponentName(context, "dev.habitdock.MainActivity");
        check(legacy.equals(GlassHomeActivity.appIntent(context).getComponent())
                && legacy.equals(widget.getComponent()) && legacy.equals(actions.getComponent()),
                "current and widget intents retain the legacy MainActivity component");
        check("habitdock://widget/42".equals(widget.getDataString())
                && "habitdock://app-actions/42/example.app".equals(actions.getDataString())
                && "example.app".equals(actions.getStringExtra(GlassHomeActivity.APP_ACTIONS)),
                "widget and app-action intents retain their identity and payload");
        for (android.content.Intent entry : new android.content.Intent[]{icon, widget, oldWidget}) {
            check(GlassHomeActivity.entryTab(entry, null, 0) == 0, "entry defaults to recommendations");
            check(GlassHomeActivity.entryTab(entry, null, 2) == 2, "entry honors settings preference");
            check(GlassHomeActivity.entryTab(entry, null, 1) == 0, "unknown preference defaults to recommendations");
        }
        check(GlassHomeActivity.entryTab(home, null, 2) == 0, "system home prioritizes recommendations");
        check(GlassHomeActivity.entryTab(actions, null, 2) == 0, "app actions prioritize recommendations");
        Bundle state = new Bundle();
        for (int saved = 0; saved <= 2; saved++) {
            state.putInt("glass.tab", saved);
            for (android.content.Intent entry : new android.content.Intent[]{icon, widget, oldWidget, home, actions})
                check(GlassHomeActivity.entryTab(entry, state, 2) == saved,
                        "saved tab takes precedence when the activity is restored");
        }
        check(GlassHomeActivity.entryTab(widget, null, 2) == 2
                && GlassHomeActivity.entryTab(widget, null, 0) == 0,
                "a subsequent entry uses the current preference");
    }

    private void checkPickerWidths(Context context) {
        for (int densityDpi : new int[]{160, 220, 280, 360, 420}) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.densityDpi = densityDpi;
            config.fontScale = 1f;
            Context normal = context.createConfigurationContext(config);
            int padding = 2 * Ui.dp(normal, 8);
            int width = Ui.dp(normal, 360);
            int gap = Ui.dp(normal, 4);
            int threshold = (int) Math.ceil(340f * normal.getResources().getDisplayMetrics().density + gap);
            check(AppPickerView.columnsForWidth(normal, width, padding) == 2,
                    "360dp picker has two columns at " + densityDpi + "dpi");
            check(AppPickerView.columnsForWidth(normal, padding + threshold - 2, padding) == 1,
                    "picker respects the measured width below the two-column threshold at " + densityDpi + "dpi");
            check(AppPickerView.columnsForWidth(normal, Ui.dp(normal, 320), padding) == 1,
                    "narrow picker has one column at " + densityDpi + "dpi");
            config.fontScale = 1.5f;
            Context large = context.createConfigurationContext(config);
            check(AppPickerView.columnsForWidth(large, width, padding) == 1,
                    "large text reduces the picker column count at " + densityDpi + "dpi");
            check(AppPickerView.columnsForWidth(large, Ui.dp(large, 720), padding) == 2,
                    "large text still permits extra columns when there is room at " + densityDpi + "dpi");
        }
    }

    private void checkIconRebinding() throws Exception {
        Context context = getTargetContext();
        String[] packages = {context.getPackageName(), "android"};
        context.getPackageManager().getApplicationInfo(packages[1], 0);
        IconStyle style = IconStyle.defaults();
        ImageView[] views = new ImageView[8];
        runOnMainSync(() -> {
            for (int i = 0; i < views.length; i++)
                views[i] = new ImageView(context);
            for (int pass = 0; pass < 12; pass++)
                for (int i = 0; i < views.length; i++)
                    IconLoader.bind(context, packages[(i + pass) % packages.length], views[i], style);
        });
        java.lang.reflect.Field workField = IconLoader.class.getDeclaredField("WORK");
        workField.setAccessible(true);
        java.util.concurrent.ThreadPoolExecutor work = (java.util.concurrent.ThreadPoolExecutor) workField.get(null);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Runnable barrier = () -> {
            ready.countDown();
            try {
                release.await(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        };
        work.prestartAllCoreThreads();
        java.util.concurrent.BlockingDeque<Runnable> queue = (java.util.concurrent.BlockingDeque<Runnable>) work
                .getQueue();
        queue.offerLast(barrier);
        queue.offerLast(barrier);
        boolean completed = ready.await(30, java.util.concurrent.TimeUnit.SECONDS);
        release.countDown();
        check(completed, "icon loading finishes after rapid view reuse");
        waitForIdleSync();
        android.graphics.Bitmap[] expected = {
                IconPacks.styled(context, packages[0], style), IconPacks.styled(context, packages[1], style)};
        runOnMainSync(() -> {
            for (int i = 0; i < views.length; i++) {
                android.graphics.drawable.Drawable drawable = views[i].getDrawable();
                check(drawable instanceof android.graphics.drawable.BitmapDrawable
                        && ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap().sameAs(
                                expected[(i + 11) % packages.length]),
                        "recycled cell displays the last requested application's icon");
            }
            ImageView other = new ImageView(context);
            IconLoader.bind(context, packages[1], other, style);
            views[0].setImageAlpha(59);
            check(views[0].getDrawable() != other.getDrawable()
                    && views[0].getDrawable().getAlpha() == 59 && other.getDrawable().getAlpha() == 255,
                    "dimming a recycled icon does not alter another view's cached drawable");
        });
    }

    private Context isolatedMaterialContext(Context base, boolean liquid) {
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("material-check", 0);
        preferences.edit().putBoolean("main.liquid", liquid).commit();
        Context isolated = new android.content.ContextWrapper(base) {
            @Override
            public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return preferences;
            }
        };
        return new ContextThemeWrapper(isolated, R.style.AppTheme);
    }

    private void checkMaterialSwitch() {
        for (int night : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
            Configuration config = new Configuration(getTargetContext().getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
            Context context = isolatedMaterialContext(getTargetContext().createConfigurationContext(config), false);
            check(!MainStyle.liquid(context), "material switch reads its saved disabled state");
            check(!ObricGlass.available(context) && !ScopedWindowBlur.available(context),
                    "disabled material does not request vendor or window blur");
            int width = Ui.dp(context, 120), height = Ui.dp(context, 40);
            android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(width, height,
                    android.graphics.Bitmap.Config.ARGB_8888);
            new GlassFallback(context).draw(new android.graphics.Canvas(image),
                    new android.graphics.RectF(0, 0, width, height), height / 2f);
            int center = image.getPixel(width / 2, height / 2);
            int inset = Ui.dp(context, 2);
            check(android.graphics.Color.alpha(center) == 255
                    && center == image.getPixel(width / 2, inset)
                    && center == image.getPixel(width / 2, height - 1 - inset)
                    && center == image.getPixel(inset, height / 2)
                    && center == image.getPixel(width - 1 - inset, height / 2),
                    "disabled material is opaque without an edge highlight");
            image.recycle();
            context = isolatedMaterialContext(getTargetContext().createConfigurationContext(config), true);
            check(MainStyle.liquid(context), "material switch reads its saved enabled state");
        }
    }

    private static android.animation.ValueAnimator animator(GlassNavigation view, String field)
            throws ReflectiveOperationException {
        java.lang.reflect.Field value = GlassNavigation.class.getDeclaredField(field);
        value.setAccessible(true);
        return (android.animation.ValueAnimator) value.get(view);
    }

    private static float number(GlassNavigation view, String field) throws ReflectiveOperationException {
        java.lang.reflect.Field value = GlassNavigation.class.getDeclaredField(field);
        value.setAccessible(true);
        return value.getFloat(view);
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static void gesture(View view, float x, float y, float endX, float endY) {
        motion(view, android.view.MotionEvent.ACTION_DOWN, x, y);
        motion(view, android.view.MotionEvent.ACTION_MOVE, endX, endY);
        motion(view, android.view.MotionEvent.ACTION_UP, endX, endY);
    }

    private static void motion(View view, int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent event = android.view.MotionEvent.obtain(now, now, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
}
