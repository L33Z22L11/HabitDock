package dev.habitdock;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.viewpager.widget.ViewPager;

/**
 * Coordinates a navigation-bar gesture with the pager's own scroll lifecycle.
 */
final class NavigationPager extends ViewPager {
    NavigationPager(Context context) {
        super(context);
    }

    float prepareNavigation() {
        int visibleX = getScrollX(), visibleY = getScrollY();
        if (isFakeDragging())
            endFakeDrag();
        // ViewPager 1.0 beginFakeDrag does not abort its Scroller. Its normal DOWN
        // handling does, preserving the displayed scroll offset. End the synthetic
        // touch bookkeeping; fake dragging begins only when the page must move.
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0, 0, 0);
        MotionEvent cancel = MotionEvent.obtain(time, time, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        try {
            super.onTouchEvent(down);
            super.onInterceptTouchEvent(cancel);
            // Keep the visible frame, not the last selected item's scroll destination.
            scrollTo(visibleX, visibleY);
        } finally {
            down.recycle();
            cancel.recycle();
        }
        return visibleX / (float) Math.max(1, getWidth());
    }

    void dragNavigationTo(float position) {
        float targetX = Math.max(0, Math.min(2, position)) * getWidth();
        if (Math.abs(getScrollX() - targetX) < .5f)
            return;
        if (!isFakeDragging() && !beginFakeDrag())
            return;
        // Read the actual offset after clamping instead of carrying an old delta
        // anchor.
        fakeDragBy(getScrollX() - targetX);
    }
}
