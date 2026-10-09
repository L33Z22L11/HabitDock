package dev.habitdock;
import java.util.Arrays;
public final class BackgroundBlurTest {
    static int checks;
    static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        int[] uniform = new int[35];
        Arrays.fill(uniform, 0xff547392);
        check(Arrays.equals(uniform, BackgroundBlur.apply(uniform, 7, 5, 20)), "uniform image stays uniform");
        int[] original = {0xff000000, 0xff000000, 0xffffffff, 0xff000000, 0xff000000};
        int[] copy = original.clone();
        int[] blurred = BackgroundBlur.apply(original, 5, 1, 1);
        check(Arrays.equals(original, copy), "blur never modifies cached original");
        check(Arrays.equals(original, BackgroundBlur.apply(original, 5, 1, 0)), "zero blur retains source pixels");
        check((blurred[1] & 255) > 0 && (blurred[2] & 255) < 255, "blur spreads contrast instead of dimming image");
        check(blurred[0] == blurred[4] && blurred[1] == blurred[3], "symmetric image stays symmetric");
        check(Arrays.equals(blurred, BackgroundBlur.apply(original, 1, 5, 1)), "vertical and horizontal passes agree");
        check(BackgroundBlur.apply(new int[]{0x88556677}, 1, 1, 100)[0] == 0x88556677,
                "single pixel preserves alpha and color");
        check(Arrays.equals(BackgroundBlur.apply(uniform, 7, 5, 64), BackgroundBlur.apply(uniform, 7, 5, 1000)),
                "radius is bounded");
        boolean rejected = false;
        try {
            BackgroundBlur.apply(new int[3], 2, 2, 1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid shape rejected");
        int[] rectangle = new int[24];
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 6; x++)
                rectangle[y * 6 + x] = 0xff000000 | (x < 3 ? 255 : 0);
        int[] result = BackgroundBlur.apply(rectangle, 6, 4, 2);
        check(Arrays.equals(Arrays.copyOfRange(result, 0, 6), Arrays.copyOfRange(result, 18, 24)),
                "rectangular image uses correct row stride");
        for (int color : result)
            check((color >>> 24) == 255, "opaque source remains opaque");
        System.out.println("PASS: " + checks + " background blur checks");
    }
}
