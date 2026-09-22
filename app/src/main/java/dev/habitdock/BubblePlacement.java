package dev.habitdock;

import android.graphics.Rect;

final class BubblePlacement {
    static Rect place(Rect frame, Rect anchor, int width, int height, int margin, int gap) {
        int left = frame.left + margin, top = frame.top + margin;
        int right = frame.right - margin, bottom = frame.bottom - margin;
        width = Math.min(width, right - left);
        height = Math.min(height, bottom - top);
        int x = Math.max(left, Math.min(anchor.centerX() - width / 2, right - width));
        int below = anchor.bottom + gap, above = anchor.top - gap - height;
        int y = below + height <= bottom
                ? below
                : above >= top ? above : bottom - anchor.bottom >= anchor.top - top ? below : above;
        y = Math.max(top, Math.min(y, bottom - height));
        return new Rect(x, y, x + width, y + height);
    }
}
