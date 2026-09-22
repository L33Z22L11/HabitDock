package dev.habitdock;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import java.io.InputStream;

/**
 * Separate test APK/UID: reads a shared file without forwarding or persisting
 * it.
 */
public final class ShareReceiver extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView result = new TextView(this);
        result.setText("Reading shared package…");
        result.setTextSize(20);
        result.setGravity(android.view.Gravity.CENTER);
        setContentView(result);
        new Thread(() -> {
            String message;
            try {
                Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
                long bytes = 0;
                byte[] buffer = new byte[32768];
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    int n;
                    while ((n = in.read(buffer)) != -1)
                        bytes += n;
                }
                boolean denied = false;
                try {
                    getContentResolver().openOutputStream(uri).close();
                } catch (SecurityException expected) {
                    denied = true;
                }
                if (bytes == 0 || !denied)
                    throw new AssertionError("invalid bytes or write permission");
                message = "PASS: received " + bytes + " bytes; write access denied";
            } catch (Throwable e) {
                message = "FAIL: " + e;
            }
            String value = message;
            runOnUiThread(() -> result.setText(value));
        }, "share-test").start();
    }
}
