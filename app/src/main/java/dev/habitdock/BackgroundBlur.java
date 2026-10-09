package dev.habitdock;

/** Separable box passes approximate a Gaussian without blurring UI content. */
final class BackgroundBlur {
    static int[] apply(int[] source, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || source.length != width * height)
            throw new IllegalArgumentException("Invalid image dimensions");
        int[] pixels = source.clone();
        if (radius <= 0)
            return pixels;
        radius = Math.min(64, radius);
        int[] scratch = new int[pixels.length];
        for (int pass = 0; pass < 3; pass++) {
            line(pixels, scratch, width, height, radius, false);
            line(scratch, pixels, height, width, radius, true);
        }
        return pixels;
    }

    private static void line(int[] from, int[] to, int length, int lines, int radius, boolean vertical) {
        int span = radius * 2 + 1;
        for (int row = 0; row < lines; row++) {
            long a = 0, r = 0, g = 0, b = 0;
            for (int x = -radius; x <= radius; x++) {
                int color = from[index(Math.max(0, Math.min(length - 1, x)), row, lines, length, vertical)];
                a += color >>> 24;
                r += color >> 16 & 255;
                g += color >> 8 & 255;
                b += color & 255;
            }
            for (int x = 0; x < length; x++) {
                to[index(x, row, lines, length, vertical)] = ((int) (a / span) << 24) | ((int) (r / span) << 16)
                        | ((int) (g / span) << 8) | (int) (b / span);
                int remove = from[index(Math.max(0, x - radius), row, lines, length, vertical)];
                int add = from[index(Math.min(length - 1, x + radius + 1), row, lines, length, vertical)];
                a += (add >>> 24) - (remove >>> 24);
                r += (add >> 16 & 255) - (remove >> 16 & 255);
                g += (add >> 8 & 255) - (remove >> 8 & 255);
                b += (add & 255) - (remove & 255);
            }
        }
    }

    private static int index(int x, int row, int lines, int length, boolean vertical) {
        return vertical ? x * lines + row : row * length + x;
    }
}
