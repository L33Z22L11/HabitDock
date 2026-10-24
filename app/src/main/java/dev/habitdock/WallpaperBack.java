package dev.habitdock;

import android.animation.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import androidx.activity.*;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Wallpaper windows cannot use Android's cross-Activity predictive animation.
 */
final class WallpaperBack {
    private static final Map<Long, WeakReference<ThemedActivity>> screens = new HashMap<>();
    private static long sequence, foreground;
    private final ThemedActivity activity;
    private final long id, parent;
    final OnBackPressedCallback callback;
    private boolean first = true, originalWallpaper, committing;
    private int originalFormat;
    private View content;
    private FrameLayout host;
    private Scene layer;
    private ValueAnimator animation;
    private float gestureDirection = 1;

    WallpaperBack(ThemedActivity activity, Bundle state) {
        this.activity = activity;
        first = state == null;
        id = state == null ? ++sequence : state.getLong("wallpaper-screen", ++sequence);
        parent = state == null ? foreground : state.getLong("wallpaper-parent", 0);
        sequence = Math.max(sequence, id);
        screens.put(id, new WeakReference<>(activity));
        callback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackStarted(BackEventCompat event) {
                gestureDirection = event.getSwipeEdge() == BackEventCompat.EDGE_LEFT ? 1 : -1;
            }

            @Override
            public void handleOnBackProgressed(BackEventCompat event) {
                // Android establishes the touch origin AFTER onBackStarted. Keep that
                // callback free of layout/window work; attach only once progress exists.
                if (layer == null && event.getProgress() > 0 && !committing)
                    begin(gestureDirection);
                if (layer != null && !committing) {
                    layer.progress = Math.max(0, Math.min(1, event.getProgress()));
                    layer.invalidate();
                }
            }

            @Override
            public void handleOnBackCancelled() {
                cancel();
            }

            @Override
            public void handleOnBackPressed() {
                commit();
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, callback);
    }

    private ThemedActivity previous() {
        WeakReference<ThemedActivity> reference = screens.get(parent);
        ThemedActivity previous = reference == null ? null : reference.get();
        return previous == null || previous == activity || previous.isDestroyed() || previous.isFinishing()
                || previous.getTaskId() != activity.getTaskId() || activity.isTaskRoot() ? null : previous;
    }

    void resumed() {
        foreground = id;
        ThemedActivity previous = previous();
        if (previous != null)
            previous.prepareReturnPreview();
        boolean needed = previous != null && (activity.wallpaperScene() || previous.wallpaperScene());
        callback.setEnabled(needed);
        if (first && needed) {
            // Animate the complete page boundary, not the transparent preview holes.
            activity.overridePendingTransition(0, 0);
            View root = activity.findViewById(android.R.id.content);
            root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (begin(1)) {
                        layer.exit = 1;
                        animate(false, true);
                        return false;
                    }
                    return true;
                }
            });
        }
        first = false;
    }

    boolean begin(float direction) {
        reset();
        ThemedActivity previous = previous();
        if (previous == null)
            return false;
        previous.prepareReturnPreview();
        FrameLayout oldHost = previous.findViewById(android.R.id.content);
        host = activity.findViewById(android.R.id.content);
        if (oldHost == null || host == null || host.getChildCount() == 0 || oldHost.getChildCount() == 0)
            return false;
        View old = oldHost.getChildAt(0);
        if (old.isLayoutRequested() && old.getWidth() > 0 && old.getHeight() > 0) {
            old.measure(View.MeasureSpec.makeMeasureSpec(old.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(old.getHeight(), View.MeasureSpec.EXACTLY));
            old.layout(old.getLeft(), old.getTop(), old.getRight(), old.getBottom());
        }
        content = host.getChildAt(0);
        if (old.getWidth() <= 0 || old.getHeight() <= 0 || content.getWidth() <= 0 || content.getHeight() <= 0)
            return false;
        originalWallpaper = (activity.getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0;
        originalFormat = activity.getWindow().getAttributes().format;
        if (originalFormat != PixelFormat.TRANSLUCENT)
            activity.getWindow().setFormat(PixelFormat.TRANSLUCENT);
        if (!originalWallpaper)
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.wallpaperBlur.set(Math.max(activity.wallpaperBlurRadius(), previous.wallpaperBlurRadius()));
        layer = new Scene(activity, old, content, direction);
        layer.setTag("wallpaper-back-scene");
        host.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        layer.layout(0, 0, host.getWidth(), host.getHeight());
        content.setVisibility(View.INVISIBLE);
        return true;
    }

    void cancel() {
        if (layer != null && !committing) {
            if (layer.progress <= .005f)
                reset();
            else
                animate(false, false);
        }
    }

    private void commit() {
        if (committing)
            return;
        if (layer == null && !begin(gestureDirection)) {
            activity.finish();
            return;
        }
        committing = true;
        animate(true, false);
    }

    private void animate(boolean finish, boolean entering) {
        if (animation != null)
            animation.cancel();
        Scene scene = layer;
        if (scene == null)
            return;
        float start = entering || finish ? scene.exit : scene.progress;
        animation = ValueAnimator.ofFloat(start, finish ? 1 : 0);
        animation.setDuration(finish ? 200 : 180);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> {
            if (entering || finish)
                scene.exit = (float) value.getAnimatedValue();
            else
                scene.progress = (float) value.getAnimatedValue();
            scene.invalidate();
        });
        animation.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;
            @Override
            public void onAnimationCancel(Animator value) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator value) {
                if (canceled)
                    return;
                animation = null;
                if (finish) {
                    activity.finish();
                    activity.overridePendingTransition(0, 0);
                } else
                    reset();
            }
        });
        animation.start();
    }

    private void reset() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        if (layer != null) {
            if (host != null)
                host.removeView(layer);
            if (content != null)
                content.setVisibility(View.VISIBLE);
            if (activity.getWindow().getAttributes().format != originalFormat)
                activity.getWindow().setFormat(originalFormat);
            if (!originalWallpaper)
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
        activity.wallpaperBlur.set(activity.wallpaperBlurRadius());
        layer = null;
        content = null;
        host = null;
        committing = false;
    }

    void paused() {
        if (!committing)
            reset();
    }

    void save(Bundle state) {
        state.putLong("wallpaper-screen", id);
        state.putLong("wallpaper-parent", parent);
    }

    void destroy() {
        // A finishing window can remain on screen until the compositor removes it.
        // Preserve its final frame instead of exposing the old page for one frame.
        if (committing) {
            if (animation != null)
                animation.cancel();
            animation = null;
            layer = null;
            content = null;
            host = null;
        } else
            reset();
        WeakReference<ThemedActivity> current = screens.get(id);
        if (current != null && current.get() == activity)
            screens.remove(id);
    }

    /** The previous page is clipped out of the ENTIRE foreground footprint. */
    @android.annotation.SuppressLint("ViewConstructor") // Scene data is supplied by the owning Activity.
    static final class Scene extends View {
        private final Bitmap behind, front;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path boundary = new Path();
        private final RectF page = new RectF(), canvasBounds = new RectF();
        final float direction;
        float progress, exit;
        Scene(android.content.Context context, Bitmap behind, Bitmap front, float direction) {
            super(context);
            this.behind = behind;
            this.front = front;
            this.direction = direction;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        Scene(android.content.Context context, View behind, View front, float direction) {
            this(context, snapshot(behind), snapshot(front), direction);
        }

        private static Bitmap snapshot(View view) {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            return bitmap;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight();
            float motion = (float) Math.sqrt(Math.max(0, Math.min(1, progress)));
            float scale = 1 - .12f * motion;
            float dx = direction * (Math.min(width * .1f, Ui.dp(getContext(), 40)) * motion
                    + (width + Ui.dp(getContext(), 80)) * exit);
            page.set(width * (1 - scale) / 2 + dx, height * (1 - scale) / 2,
                    width * (1 + scale) / 2 + dx, height * (1 + scale) / 2);
            boundary.rewind();
            float radius = Ui.dp(getContext(), 24) * motion;
            boundary.addRoundRect(page, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipOutPath(boundary);
            canvasBounds.set(0, 0, width, height);
            canvas.drawBitmap(behind, null, canvasBounds, paint);
            canvas.restoreToCount(save);
            save = canvas.save();
            canvas.clipPath(boundary);
            canvas.drawBitmap(front, null, page, paint);
            canvas.restoreToCount(save);
        }
    }
}
