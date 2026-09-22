package dev.habitdock;

public final class WidgetSizing {
    public static float iconDp(float width, float height) {
        return iconDp(width, height, 5, 2, 82);
    }

    public static float iconDp(float width, float height, int columns, int rows, int percent) {
        float cell = Math.min(Math.max(0, width - 16) / Math.max(1, columns),
                Math.max(0, height - 16) / Math.max(1, rows));
        return cell * Math.max(1, Math.min(100, percent)) / 100f;
    }
}
