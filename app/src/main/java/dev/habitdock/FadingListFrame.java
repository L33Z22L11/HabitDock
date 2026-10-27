package dev.habitdock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.widget.FrameLayout;

/**
 * Fades only scrolling content as it enters the header; wallpaper and material
 * are outside.
 */
final class FadingListFrame extends FrameLayout {
    private final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int inset;
    private final int contentTopDp;

    FadingListFrame(Context context) {
        this(context, FloatingTitleBar.CONTENT_TOP_DP);
    }

    FadingListFrame(Context context, int contentTopDp) {
        super(context);
        this.contentTopDp = contentTopDp;
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        updateMask();
    }

    void topInset(int value) {
        if (inset == value)
            return;
        inset = value;
        updateMask();
    }

    private void updateMask() {
        // Fade across the entire header, including the status bar, rather than
        // reaching zero opacity partway up the title controls.
        float end = inset + Ui.dp(getContext(), contentTopDp);
        // Moving upward: lose 75% opacity in the first 35% of the band,
        // then fade the remaining 25% uniformly all the way to the screen top.
        mask.setShader(new LinearGradient(0, 0, 0, end,
                new int[]{Color.TRANSPARENT, 0x40ffffff, Color.WHITE},
                new float[]{0, .65f, 1}, Shader.TileMode.CLAMP));
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);
        // Outside this band content stays at full opacity.
        canvas.drawRect(0, 0, getWidth(), Math.min(getHeight(),
                inset + Ui.dp(getContext(), contentTopDp)), mask);
        canvas.restoreToCount(layer);
    }
}
