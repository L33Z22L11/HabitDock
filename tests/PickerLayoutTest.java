import dev.habitdock.AppCatalog;
import dev.habitdock.WidgetSizing;
import java.util.*;

public final class PickerLayoutTest {
    static int checks;
    static void check(boolean result, String message) {
        if (!result)
            throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        var apps = List.of(new AppCatalog.Entry("org.alpha.clock", "时钟"),
                new AppCatalog.Entry("com.beta.camera", "Camera"), new AppCatalog.Entry("com.mail.app", "邮件"));
        check(AppCatalog.filter(apps, Set.of(), "时钟").get(0).pkg.equals("org.alpha.clock"),
                "Chinese application name search");
        check(AppCatalog.filter(apps, Set.of(), "BETA.CAMERA").size() == 1, "package search ignores case");
        check(AppCatalog.filter(apps, Set.of(), "camera beta").size() == 1,
                "multiple keywords match name and package together");
        check(AppCatalog.filter(apps, Set.of("com.mail.app"), "").get(0).pkg.equals("com.mail.app"),
                "selected items first");
        check(AppCatalog.filter(apps, Set.of("com.mail.app"), "camera").size() == 1,
                "search applies even to selected items");
        check(AppCatalog.filter(apps, Set.of(), "  ").size() == 3, "empty query restores all apps");
        check(AppCatalog.filter(apps, Set.of(), "missing").isEmpty(), "no match is empty");
        Set<String> selected = new HashSet<>(Set.of("com.mail.app"));
        AppCatalog.filter(apps, selected, "camera");
        check(selected.contains("com.mail.app"), "filtering never clears offscreen selections");
        check(WidgetSizing.iconDp(400, 180) > WidgetSizing.iconDp(280, 110), "larger widgets grow icons");
        check(WidgetSizing.iconDp(400, 180) > 48, "phone width supports larger icons");
        check(WidgetSizing.iconDp(160, 160, 3, 3, 80) == 38.4f, "three by three icons fit a small square widget");
        check(WidgetSizing.iconDp(160, 160, 3, 3, 100) == 2 * WidgetSizing.iconDp(160, 160, 3, 3, 50),
                "percentage is relative to each cell");
        check(WidgetSizing.iconDp(1200, 600) > 72, "large widget icons have no arbitrary absolute size cap");
        check(WidgetSizing.iconDp(160, 160, 6, 5, 100) <= 24, "dense grid stays inside narrow cells");
        check(WidgetSizing.iconDp(336, 336, 3, 3, 80) == 2 * WidgetSizing.iconDp(176, 176, 3, 3, 80),
                "doubling available space doubles icon size");
        check(WidgetSizing.iconDp(8, 8, 3, 3, 80) == 0, "no negative size when host supplies no usable space");
        System.out.println("PASS: " + checks + " picker and widget sizing scenarios");
    }
}
