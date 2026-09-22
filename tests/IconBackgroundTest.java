import dev.habitdock.IconBackground;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Numeric ground truth and a contact sheet for transparent icon completion. */
public final class IconBackgroundTest {
    private static int checks;
    private static final int SIZE = 128, LIGHT = 0xfff5f6f5, DARK = 0xff252b29;
    private static final java.util.List<int[]> fixtures = new ArrayList<>();
    private static final java.util.List<String> names = new ArrayList<>();
    interface ColorAt {
        int get(double x, double y);
    }
    interface Shape {
        boolean contains(double x, double y);
    }

    private static void check(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
        checks++;
    }

    private static int rgb(double r, double g, double b) {
        return 0xff000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }

    private static int[] fixture(String name, Shape shape, ColorAt color) {
        int[] pixels = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                double nx = (x - 63.5) / 49.5, ny = (y - 63.5) / 49.5;
                if (shape.contains(nx, ny))
                    pixels[y * SIZE + x] = color.get(nx, ny);
                // Foreground artwork is deliberately unrelated to its background.
                if (Math.abs(nx) < .16 && Math.abs(ny) < .38)
                    pixels[y * SIZE + x] = 0xffffffff;
            }
        fixtures.add(pixels);
        names.add(name);
        return pixels;
    }

    private static IconBackground.Model model(int[] pixels) {
        return IconBackground.analyze(pixels, SIZE, SIZE);
    }

    private static void gradient(String name, Shape shape, ColorAt color, IconBackground.Kind expected) {
        int[] pixels = fixture(name, shape, color), before = pixels.clone();
        IconBackground.Model model = model(pixels);
        check(model.kind == expected, name + " model: " + model.kind);
        double worst = 0;
        // Verify every transparent pixel against the known original gradient.
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++)
                if (pixels[y * SIZE + x] == 0) {
                    int actual = model.colorAt(x, y, LIGHT), wanted = color.get((x - 63.5) / 49.5, (y - 63.5) / 49.5);
                    for (int shift : new int[]{0, 8, 16})
                        worst = Math.max(worst, Math.abs(((actual >> shift) & 255) - ((wanted >> shift) & 255)));
                }
        check(worst <= 4, name + " extrapolation error: " + worst);
        check(Arrays.equals(pixels, before), name + " preserves source pixels");
    }

    private static void fallback(String name, Shape shape, ColorAt color) {
        IconBackground.Model model = model(fixture(name, shape, color));
        check(model.kind == IconBackground.Kind.FALLBACK, name + " rejects unsafe completion: " + model.kind);
        check(model.colorAt(0, 0, LIGHT) == LIGHT && model.colorAt(0, 0, DARK) == DARK,
                name + " follows light and dark theme");
    }

    public static void main(String[] args) throws Exception {
        Shape circle = (x, y) -> x * x + y * y <= 1;
        Shape rounded = (x, y) -> Math.hypot(Math.max(0, Math.abs(x) - .6), Math.max(0, Math.abs(y) - .6)) <= .4;
        gradient("solid circle", circle, (x, y) -> 0xff5681c7, IconBackground.Kind.SOLID);
        gradient("diagonal circle", circle, (x, y) -> rgb(110 + 22 * x - 15 * y, 130 - 28 * y, 180 + 24 * x),
                IconBackground.Kind.LINEAR);
        gradient("vertical rounded", rounded, (x, y) -> rgb(190 + 22 * y, 130 - 30 * y, 120 + 35 * y),
                IconBackground.Kind.LINEAR);
        gradient("four-corner circle", circle, (x, y) -> rgb(120 + 26 * x + 23 * x * y, 120 - 35 * y, 150 - 34 * x * y),
                IconBackground.Kind.BILINEAR);
        gradient("radial circle", circle,
                (x, y) -> rgb(100 + 65 * Math.hypot(x, y), 175 - 45 * Math.hypot(x, y), 190 - 60 * Math.hypot(x, y)),
                IconBackground.Kind.RADIAL);
        gradient("offset radial", circle,
                (x, y) -> rgb(80 + 60 * Math.hypot(x - .35, y + .35), 180 - 45 * Math.hypot(x - .35, y + .35), 160),
                IconBackground.Kind.RADIAL);
        // Curved/multi-stop gradients are common in real circular weather icons.
        ColorAt vertical = (x, y) -> {
            double position = Math.max(-1, Math.min(1, y));
            return rgb(55 + 42 * position * position, 140 + 48 * position, 230 - 20 * position * position);
        };
        gradient("curved vertical", circle, vertical, IconBackground.Kind.VERTICAL);
        gradient("curved horizontal", circle, (x, y) -> vertical.get(y, x), IconBackground.Kind.HORIZONTAL);
        int[] bordered = fixture("inner border", circle, (x, y) -> {
            double radius = Math.hypot(x, y);
            return radius > .85 && radius < .90 ? 0xffdddddd : rgb(245 + 5 * y, 245 + 5 * y, 245 + 5 * y);
        });
        IconBackground.Model border = model(bordered);
        check(border.kind != IconBackground.Kind.FALLBACK && border.kind != IconBackground.Kind.EMPTY,
                "shallow samples stay outside the inner border");
        check((border.colorAt(64, 0, DARK) & 255) > 230, "inner border is not extrapolated into transparent padding");
        fallback("sharp multicolor", circle,
                (x, y) -> x < 0 ? (y < 0 ? 0xffff2020 : 0xff2050ff) : (y < 0 ? 0xff20ff30 : 0xffffe020));
        fallback("abrupt vertical split", circle, (x, y) -> y < 0 ? 0xff2244ff : 0xffff4411);
        fallback("irregular cross",
                (x, y) -> Math.abs(x) < .3 && Math.abs(y) < 1 || Math.abs(y) < .3 && Math.abs(x) < 1,
                (x, y) -> 0xff5588cc);
        fallback("notched circle", (x, y) -> circle.contains(x, y) && !(x > .5 && Math.abs(y) < .3),
                (x, y) -> 0xffcc8844);
        Random random = new Random(19);
        fallback("noisy edge", circle, (x, y) -> 0xff000000 | random.nextInt(0xffffff));
        int[] shadow = fixtures.get(1).clone();
        shadow[0] = shadow[shadow.length - 1] = 0x20000000;
        check(model(shadow).kind == IconBackground.Kind.LINEAR, "soft shadows do not change contour sampling");
        check(model(new int[SIZE * SIZE]).kind == IconBackground.Kind.EMPTY, "empty icons remain empty");
        int[] full = new int[SIZE * SIZE];
        Arrays.fill(full, 0xffabcdef);
        check(model(full).kind == IconBackground.Kind.EMPTY, "opaque icons need no completion");
        int[] opaqueCircle = fixtures.get(1).clone();
        for (int p = 0; p < opaqueCircle.length; p++)
            if (opaqueCircle[p] == 0)
                opaqueCircle[p] = 0xffffffff;
        check(model(opaqueCircle).kind == IconBackground.Kind.EMPTY,
                "opaque outer plate is never removed or inferred as transparency");
        int[] tiny = new int[SIZE * SIZE];
        tiny[64 * SIZE + 64] = 0xffff0000;
        check(model(tiny).kind == IconBackground.Kind.FALLBACK, "tiny artwork falls back");
        sheet();
        System.out.println("PASS: " + checks + " icon background checks");
    }

    private static void sheet() throws Exception {
        BufferedImage sheet = new BufferedImage(630, 52 + fixtures.size() * 154, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0xe5e5e5));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Source / inferred model", 12, 26);
        g.drawString("Light", 295, 26);
        g.drawString("Dark", 465, 26);
        for (int i = 0; i < fixtures.size(); i++) {
            int[] source = fixtures.get(i);
            IconBackground.Model model = model(source);
            int top = 52 + i * 154;
            g.setColor(Color.BLACK);
            g.drawString(names.get(i) + " / " + model.kind, 12, top);
            for (int column = 0; column < 3; column++) {
                BufferedImage icon = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                int[] pixels = source.clone();
                for (int p = 0; p < pixels.length; p++)
                    if (pixels[p] == 0)
                        pixels[p] = column == 0
                                ? 0xffdddddd
                                : model.colorAt(p % SIZE, p / SIZE, column == 1 ? LIGHT : DARK);
                icon.setRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
                g.drawImage(icon, 30 + column * 210, top + 8, null);
            }
        }
        g.dispose();
        File output = new File("build/predictor-tests/icon-backgrounds.png");
        ImageIO.write(sheet, "png", output);
    }
}
