package dev.habitdock;

import android.content.*;

/**
 * Pin requests have no per-instance span API. Separate providers preserve
 * existing spans.
 */
public final class WidgetSizes {
    static final class Size {
        final int columns, rows;
        final Class<? extends HabitWidget> receiver;
        Size(int columns, int rows, Class<? extends HabitWidget> receiver) {
            this.columns = columns;
            this.rows = rows;
            this.receiver = receiver;
        }
    }

    static final Size[] ALL = {
            new Size(4, 2, HabitWidget.class),
            new Size(2, 2, CompactHabitWidget.class)
    };

    static ComponentName provider(Context context, int columns, int rows) {
        for (Size size : ALL)
            if (size.columns == columns && size.rows == rows)
                return new ComponentName(context, size.receiver);
        throw new IllegalArgumentException("Unsupported widget span: " + columns + "x" + rows);
    }

}
