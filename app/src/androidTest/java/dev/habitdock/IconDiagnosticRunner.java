package dev.habitdock;

import android.app.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.*;
import android.os.Bundle;
import java.io.*;

/**
 * Read-only icon diagnostics for explicitly named packages; never resets user
 * data.
 */
public final class IconDiagnosticRunner extends Instrumentation {
    private String packages;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        packages = args.getString("packages", "");
        start();
    }

    private void save(Bitmap bitmap, File directory, String name) throws IOException {
        try (OutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        StringBuilder report = new StringBuilder();
        int status = Activity.RESULT_OK;
        try {
            Context c = getTargetContext();
            File directory = new File(c.getCacheDir(), "icon-diagnostics");
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IOException("Cannot create diagnostics directory");
            for (String pkg : packages.split(",")) {
                if (!pkg.matches("[a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+"))
                    throw new IllegalArgumentException("Explicit package names required");
                Drawable drawable = c.getPackageManager().getApplicationIcon(pkg);
                Bitmap source = WidgetIcons.source(drawable);
                int[] pixels = new int[source.getWidth() * source.getHeight()];
                source.getPixels(pixels, 0, source.getWidth(), 0, 0, source.getWidth(), source.getHeight());
                IconBackground.Model model = IconBackground.analyze(pixels, source.getWidth(), source.getHeight());
                report.append(pkg).append(": ").append(drawable.getClass().getName()).append(" -> ")
                        .append(model.kind).append('\n');
                save(source, directory, pkg + "-source");
                save(WidgetIcons.style(c, source, new IconStyle(40, true, 0)), directory, pkg + "-filled");
                if (drawable instanceof AdaptiveIconDrawable) {
                    AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) drawable;
                    for (int i = 0; i < 2; i++) {
                        Drawable layer = i == 0 ? adaptive.getBackground() : adaptive.getForeground();
                        if (layer == null)
                            continue;
                        report.append(i == 0 ? "  background: " : "  foreground: ")
                                .append(layer.getClass().getName()).append('\n');
                        save(WidgetIcons.source(layer), directory, pkg + "-layer" + i);
                    }
                }
            }
        } catch (Throwable failure) {
            status = Activity.RESULT_CANCELED;
            report.append(android.util.Log.getStackTraceString(failure));
        }
        result.putString("stream", "\n" + report);
        finish(status, result);
    }
}
