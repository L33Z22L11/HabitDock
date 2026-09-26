package dev.habitdock;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Measures rendered artwork when launcher size hints disagree with real cells.
 */
public final class WidgetSizingRunner extends Instrumentation {
    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle report = new Bundle();
        int[] checks = {0};
        int status = Activity.RESULT_OK;
        try {
            Context c = getTargetContext();
            String pkg = c.getPackageName();
            Repository.Snapshot fixture = new Repository.Snapshot(
                    Map.of(pkg, new Repository.App(pkg, "Test")),
                    List.of(new Predictor.Prediction(pkg, 1, "Test")), 0, 0, true);
            Bitmap source = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            source.eraseColor(Color.RED);
            runOnMainSync(() -> {
                for (int[] actual : new int[][]{{160, 160}, {320, 100}, {100, 260}}) {
                    View root = null;
                    for (int percent : new int[]{82, 100, 50}) {
                        RemoteViews views = HabitWidget.render(c, fixture, Map.of(pkg, source), "12:34", 220, 220,
                                new WidgetPreferences(3, 3, percent, false));
                        if (root == null)
                            root = views.apply(c, new FrameLayout(c));
                        else
                            views.reapply(c, root);
                        int width = Ui.dp(c, actual[0]), height = Ui.dp(c, actual[1]);
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                        root.layout(0, 0, width, height);
                        ImageView image = root.findViewById(R.id.app_icon);
                        View cell = (View) image.getParent();
                        Bitmap rendered = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                                Bitmap.Config.ARGB_8888);
                        image.draw(new Canvas(rendered));
                        Rect ink = new Rect();
                        for (int y = 0; y < rendered.getHeight(); y++)
                            for (int x = 0; x < rendered.getWidth(); x++)
                                if (Color.alpha(rendered.getPixel(x, y)) >= 128)
                                    ink.union(x, y, x + 1, y + 1);
                        float expected = Math.min(cell.getWidth(), cell.getHeight()) * percent / 100f;
                        if (Math.abs(ink.width() - expected) > 2 || Math.abs(ink.height() - expected) > 2)
                            throw new AssertionError(
                                    "visible artwork " + ink + ", expected " + expected + " at " + percent + "%");
                        checks[0]++;
                        if (Math.abs(ink.exactCenterX() + image.getLeft() - cell.getWidth() / 2f) > 1
                                || Math.abs(ink.exactCenterY() + image.getTop() - cell.getHeight() / 2f) > 1)
                            throw new AssertionError("artwork is not centered");
                        checks[0]++;
                        rendered.recycle();
                    }
                }
            });
            report.putString("stream", "\nPASS: " + checks[0] + " actual-cell sizing checks\n");
        } catch (Throwable error) {
            status = Activity.RESULT_CANCELED;
            report.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(error));
        }
        finish(status, report);
    }
}
