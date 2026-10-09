package dev.habitdock;

import android.app.UiAutomation;
import android.view.InputEvent;

/**
 * Test-only input that preserves gesture timing while window animations run.
 */
final class TouchInput {
    private TouchInput() {
    }

    static void prepare(UiAutomation automation) {
        try {
            // Flush before creating DOWN's timestamps, rather than aging the event
            // while the platform synchronizes the previous window transition.
            UiAutomation.class.getMethod("syncInputTransactions").invoke(automation);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("emulator input synchronization unavailable", error);
        }
    }

    static boolean inject(UiAutomation automation, InputEvent event, boolean sync) {
        try {
            // The public two-argument method waits for surface animations before DOWN,
            // which can make an already-created event look like a long press. Use
            // the TestApi overload; run the emulator runner with --no-hidden-api-checks.
            long before = android.os.SystemClock.uptimeMillis();
            boolean accepted = (boolean) UiAutomation.class
                    .getMethod("injectInputEvent", InputEvent.class, boolean.class, boolean.class)
                    .invoke(automation, event, sync, false);
            if (event instanceof android.view.MotionEvent) {
                android.view.MotionEvent motion = (android.view.MotionEvent) event;
                if (motion.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                        || motion.getEventTime() - motion.getDownTime() < 150)
                    android.util.Log.d("HabitInput", "action=" + motion.getActionMasked()
                            + " event-age=" + (before - motion.getEventTime())
                            + " down-age=" + (before - motion.getDownTime())
                            + " injection-ms=" + (android.os.SystemClock.uptimeMillis() - before));
            }
            if (!accepted)
                throw new AssertionError("touch input rejected");
            return true;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("emulator input TestApi unavailable", error);
        }
    }
}
