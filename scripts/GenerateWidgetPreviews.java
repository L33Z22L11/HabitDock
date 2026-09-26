import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Build-time fallback images for launchers that cannot render dynamic previews.
 */
class GenerateWidgetPreviews {
    private static final int[] COLORS = {0x3a8872, 0x5681c7, 0xcf8653, 0x8e75b6, 0x578f9b,
            0xaf7979, 0x6d9980, 0x879454, 0x7084ad};

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        for (boolean dark : new boolean[]{false, true}) {
            Path folder = output.resolve(dark ? "drawable-night-nodpi" : "drawable-nodpi");
            Files.createDirectories(folder);
            draw(folder.resolve("widget_preview_image.png"), 480, 240, 5, 2, dark);
            draw(folder.resolve("widget_preview_compact.png"), 240, 240, 3, 3, dark);
        }
    }

    private static void draw(Path file, int width, int height, int columns, int rows, boolean dark) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float cw = (width - 24f) / columns, ch = (height - 24f) / rows;
        float side = Math.min(cw, ch) * .82f;
        for (int index = 0; index < columns * rows; index++) {
            float cx = 12 + cw * (index % columns + .5f), cy = 12 + ch * (index / columns + .5f);
            if (index == columns * rows - 1) {
                canvas.setColor(dark ? new Color(0xeeffffff, true) : new Color(0xcc26352e, true));
                float dot = side / 14;
                for (int n = -1; n <= 1; n++)
                    canvas.fill(new Ellipse2D.Float(cx + n * side / 5 - dot / 2, cy - dot / 2, dot, dot));
            } else {
                canvas.setColor(new Color(COLORS[index % COLORS.length]));
                canvas.fill(
                        new RoundRectangle2D.Float(cx - side / 2, cy - side / 2, side, side, side * .4f, side * .4f));
                canvas.setColor(new Color(0xeeffffff, true));
                canvas.setStroke(new BasicStroke(side / 22, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (index % 3 == 0) {
                    canvas.draw(new Ellipse2D.Float(cx - side / 4, cy - side / 4, side / 2, side / 2));
                    canvas.draw(new Line2D.Float(cx, cy - side / 6, cx, cy));
                    canvas.draw(new Line2D.Float(cx, cy, cx + side / 8, cy + side / 12));
                } else if (index % 3 == 1) {
                    canvas.draw(new RoundRectangle2D.Float(cx - side * .28f, cy - side * .22f,
                            side * .56f, side * .4f, side * .12f, side * .12f));
                    Path2D tail = new Path2D.Float();
                    tail.moveTo(cx - side * .14f, cy + side * .18f);
                    tail.lineTo(cx - side * .2f, cy + side * .3f);
                    tail.lineTo(cx + side * .02f, cy + side * .18f);
                    canvas.draw(tail);
                    for (int n = -1; n <= 1; n++)
                        canvas.fill(new Ellipse2D.Float(cx + n * side * .13f - side * .025f,
                                cy - side * .04f, side * .05f, side * .05f));
                } else {
                    canvas.draw(new RoundRectangle2D.Float(cx - side * .29f, cy - side * .2f,
                            side * .58f, side * .43f, side * .09f, side * .09f));
                    canvas.draw(new Ellipse2D.Float(cx - side * .12f, cy - side * .11f, side * .24f, side * .24f));
                    canvas.draw(
                            new Line2D.Float(cx - side * .13f, cy - side * .28f, cx + side * .09f, cy - side * .28f));
                }
            }
        }
        canvas.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
}
