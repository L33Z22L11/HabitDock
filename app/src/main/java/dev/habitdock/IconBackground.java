package dev.habitdock;

import java.util.*;

/**
 * Bounded, dependency-free gradient fitting on an icon's visible outer band.
 */
public final class IconBackground {
    public enum Kind {
        EMPTY, SOLID, LINEAR, BILINEAR, RADIAL, VERTICAL, HORIZONTAL, FALLBACK
    }

    public static final class Model {
        public final Kind kind;
        private final double[][] coefficients;
        private final double cx, cy, rx, ry, radialX, radialY;

        private Model(Kind kind, double[][] coefficients, double cx, double cy, double rx, double ry,
                double radialX, double radialY) {
            this.kind = kind;
            this.coefficients = coefficients;
            this.cx = cx;
            this.cy = cy;
            this.rx = rx;
            this.ry = ry;
            this.radialX = radialX;
            this.radialY = radialY;
        }

        public int colorAt(double x, double y, int fallback) {
            if (kind == Kind.EMPTY)
                return 0;
            if (kind == Kind.FALLBACK)
                return fallback;
            double nx = (x - cx) / rx, ny = (y - cy) / ry;
            double radial = kind == Kind.RADIAL ? Math.hypot(nx - radialX, ny - radialY) : 0;
            int color = 0xff000000;
            for (int channel = 0; channel < 3; channel++) {
                double[] c = coefficients[channel];
                double value = c[0];
                if (kind == Kind.VERTICAL || kind == Kind.HORIZONTAL) {
                    double position = axisPosition(kind == Kind.VERTICAL ? ny : nx);
                    int low = (int) position, high = Math.min(low + 1, c.length - 1);
                    double fraction = position - low;
                    value = c[low] * (1 - fraction) + c[high] * fraction;
                } else if (kind == Kind.RADIAL)
                    value += c[1] * radial;
                else if (c.length > 1) {
                    value += c[1] * nx + c[2] * ny;
                    if (c.length > 3)
                        value += c[3] * nx * ny;
                }
                color |= Math.max(0, Math.min(255, (int) Math.round(value))) << (16 - channel * 8);
            }
            return color;
        }
    }

    private static final class Sample {
        final double x, y;
        final double[] rgb;

        Sample(double x, double y, double red, double green, double blue) {
            this.x = x;
            this.y = y;
            rgb = new double[]{red, green, blue};
        }
    }

    private static Model special(Kind kind) {
        return new Model(kind, null, 0, 0, 1, 1, 0, 0);
    }

    public static Model analyze(int[] pixels, int width, int height) {
        if (width < 1 || height < 1 || pixels.length != width * height)
            throw new IllegalArgumentException("Invalid icon dimensions");
        int maxAlpha = 0, opaque = 0;
        for (int pixel : pixels) {
            maxAlpha = Math.max(maxAlpha, pixel >>> 24);
            if ((pixel >>> 24) == 255)
                opaque++;
        }
        if (maxAlpha == 0 || opaque == pixels.length)
            return special(Kind.EMPTY);
        int threshold = Math.min(224, maxAlpha), left = width, right = -1, top = height, bottom = -1, visible = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if ((pixels[y * width + x] >>> 24) >= threshold) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    visible++;
                }
        double rx = (right - left) / 2.0, ry = (bottom - top) / 2.0;
        // Filled circles/ellipses and rounded rectangles pass; sparse glyphs, rings
        // and disconnected silhouettes should retain a contrasting theme plate.
        if (rx < 4 || ry < 4 || visible < (right - left + 1) * (bottom - top + 1) * .68)
            return special(Kind.FALLBACK);
        double cx = (left + right) / 2.0, cy = (top + bottom) / 2.0;
        List<Sample> samples = new ArrayList<>(64);
        double step = .5 / Math.max(rx, ry), size = 2 * Math.min(rx, ry);
        // 32 directions include top/bottom/left/right and the four diagonals. Shallow
        // depths
        // recover color changes without crossing the inner border or center artwork.
        for (int direction = 0; direction < 32; direction++) {
            double angle = direction * Math.PI / 16, dx = Math.cos(angle) * rx, dy = Math.sin(angle) * ry;
            double edge = -1;
            for (double distance = 1.5; distance >= 0; distance -= step) {
                int x = (int) Math.round(cx + dx * distance), y = (int) Math.round(cy + dy * distance);
                if (x >= 0 && x < width && y >= 0 && y < height
                        && (pixels[y * width + x] >>> 24) >= threshold) {
                    edge = distance;
                    break;
                }
            }
            if (edge < .85)
                return special(Kind.FALLBACK);
            for (double depth : new double[]{.01, .03}) {
                double inward = Math.max(1, size * depth) / Math.hypot(dx, dy);
                int x = (int) Math.round(cx + dx * (edge - inward));
                int y = (int) Math.round(cy + dy * (edge - inward));
                Sample sample = patch(pixels, width, height, x, y, threshold, cx, cy, rx, ry);
                if (sample == null)
                    return special(Kind.FALLBACK);
                samples.add(sample);
            }
        }
        double[][] solid = fit(samples, Kind.SOLID, 0, 0);
        if (quality(samples, Kind.SOLID, solid, 0, 0) <= .8)
            return new Model(Kind.SOLID, solid, cx, cy, rx, ry, 0, 0);
        Model best = special(Kind.FALLBACK);
        double bestScore = Double.POSITIVE_INFINITY;
        for (Kind kind : new Kind[]{Kind.LINEAR, Kind.BILINEAR, Kind.VERTICAL, Kind.HORIZONTAL}) {
            double[][] coefficients = fit(samples, kind, 0, 0);
            double error = quality(samples, kind, coefficients, 0, 0);
            double score = error + (kind == Kind.LINEAR ? .15 : kind == Kind.BILINEAR ? .35 : .6);
            if (error <= 3 && score < bestScore) {
                best = new Model(kind, coefficients, cx, cy, rx, ry, 0, 0);
                bestScore = score;
            }
        }
        // A small bounded set of centers covers centered and gently offset radial
        // gradients. A complex multicolor contour deliberately falls back.
        for (double radialX : new double[]{0, -.35, .35})
            for (double radialY : new double[]{0, -.35, .35}) {
                double[][] coefficients = fit(samples, Kind.RADIAL, radialX, radialY);
                double error = quality(samples, Kind.RADIAL, coefficients, radialX, radialY);
                if (error <= 3 && error + .45 < bestScore) {
                    best = new Model(Kind.RADIAL, coefficients, cx, cy, rx, ry, radialX, radialY);
                    bestScore = error + .45;
                }
            }
        return best;
    }

    private static Sample patch(int[] pixels, int width, int height, int x, int y, int threshold,
            double cx, double cy, double rx, double ry) {
        double red = 0, green = 0, blue = 0, sx = 0, sy = 0, weight = 0;
        for (int py = Math.max(0, y - 1); py <= Math.min(height - 1, y + 1); py++)
            for (int px = Math.max(0, x - 1); px <= Math.min(width - 1, x + 1); px++) {
                int color = pixels[py * width + px], alpha = color >>> 24;
                if (alpha < threshold)
                    continue;
                red += ((color >> 16) & 255) * alpha;
                green += ((color >> 8) & 255) * alpha;
                blue += (color & 255) * alpha;
                sx += px * alpha;
                sy += py * alpha;
                weight += alpha;
            }
        return weight == 0
                ? null
                : new Sample((sx / weight - cx) / rx, (sy / weight - cy) / ry,
                        red / weight, green / weight, blue / weight);
    }

    private static double axisPosition(double position) {
        return (Math.max(-1, Math.min(1, position)) + 1) * 3.5;
    }

    private static double[] basis(Sample sample, Kind kind, double radialX, double radialY) {
        if (kind == Kind.VERTICAL || kind == Kind.HORIZONTAL) {
            // Eight color stops describe a smooth multi-stop gradient. Use the same
            // fit/error gate as the simpler models; abrupt color changes still fail.
            double[] terms = new double[8];
            double position = axisPosition(kind == Kind.VERTICAL ? sample.y : sample.x);
            int low = (int) position, high = Math.min(low + 1, terms.length - 1);
            terms[low] = 1 - (position - low);
            terms[high] += position - low;
            return terms;
        }
        if (kind == Kind.SOLID)
            return new double[]{1};
        if (kind == Kind.RADIAL)
            return new double[]{1, Math.hypot(sample.x - radialX, sample.y - radialY)};
        if (kind == Kind.LINEAR)
            return new double[]{1, sample.x, sample.y};
        return new double[]{1, sample.x, sample.y, sample.x * sample.y};
    }

    private static double[][] fit(List<Sample> samples, Kind kind, double radialX, double radialY) {
        int n = basis(samples.get(0), kind, radialX, radialY).length;
        double[][] matrix = new double[n][n + 3];
        for (Sample sample : samples) {
            double[] terms = basis(sample, kind, radialX, radialY);
            for (int row = 0; row < n; row++) {
                for (int column = 0; column < n; column++)
                    matrix[row][column] += terms[row] * terms[column];
                for (int channel = 0; channel < 3; channel++)
                    matrix[row][n + channel] += terms[row] * sample.rgb[channel];
            }
        }
        // Partial pivoting; singular/unstable layouts are not guessed.
        for (int column = 0; column < n; column++) {
            int pivot = column;
            for (int row = column + 1; row < n; row++)
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column]))
                    pivot = row;
            if (Math.abs(matrix[pivot][column]) < 1e-6)
                return null;
            double[] swap = matrix[column];
            matrix[column] = matrix[pivot];
            matrix[pivot] = swap;
            double divisor = matrix[column][column];
            for (int entry = column; entry < n + 3; entry++)
                matrix[column][entry] /= divisor;
            for (int row = 0; row < n; row++) {
                if (row == column)
                    continue;
                double factor = matrix[row][column];
                for (int entry = column; entry < n + 3; entry++)
                    matrix[row][entry] -= factor * matrix[column][entry];
            }
        }
        double[][] result = new double[3][n];
        for (int channel = 0; channel < 3; channel++)
            for (int term = 0; term < n; term++)
                result[channel][term] = matrix[term][n + channel];
        return result;
    }

    private static double quality(List<Sample> samples, Kind kind, double[][] coefficients,
            double radialX, double radialY) {
        if (coefficients == null)
            return Double.POSITIVE_INFINITY;
        double squared = 0, worst = 0;
        for (Sample sample : samples) {
            double[] terms = basis(sample, kind, radialX, radialY);
            for (int channel = 0; channel < 3; channel++) {
                double estimate = 0;
                for (int term = 0; term < terms.length; term++)
                    estimate += terms[term] * coefficients[channel][term];
                double error = Math.abs(estimate - sample.rgb[channel]);
                squared += error * error;
                worst = Math.max(worst, error);
            }
        }
        return worst <= 12 ? Math.sqrt(squared / (samples.size() * 3)) : Double.POSITIVE_INFINITY;
    }
}
