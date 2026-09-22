import dev.habitdock.Predictor;
import dev.habitdock.PrivacyPolicy;
import java.time.*;
import java.util.*;

public class PredictorTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final long NOW = at("2026-09-19T12:00");
    static int checks;
    static long at(String time) {
        return LocalDateTime.parse(time).atZone(ZONE).toInstant().toEpochMilli();
    }

    static void check(boolean ok, String label) {
        if (!ok)
            throw new AssertionError(label);
        checks++;
    }

    static List<Predictor.Prediction> rank(List<Predictor.Launch> history, String target) {
        return Predictor.rank(history, NOW, at(target), ZONE, Set.of("commute", "video", "work", "weekend"));
    }

    static void add(List<Predictor.Launch> events, String pkg, LocalDate day, int hour, int minute) {
        events.add(new Predictor.Launch(pkg, day.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()));
    }

    public static void main(String[] args) {
        List<Predictor.Launch> events = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            LocalDate day = LocalDate.of(2026, 9, 19).minusDays(i);
            add(events, "commute", day, 8, 0);
            add(events, "video", day, 21, 0);
        }
        check(rank(events, "2026-09-21T08:10").get(0).pkg.equals("commute"), "morning predicts commute");
        check(rank(events, "2026-09-21T21:10").get(0).pkg.equals("video"), "evening predicts video");
        List<Predictor.Launch> weekends = new ArrayList<>();
        for (int i = 1; i <= 42; i++) {
            LocalDate day = LocalDate.of(2026, 9, 19).minusDays(i);
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            add(weekends, weekend ? "weekend" : "work", day, 10, 0);
        }
        check(rank(weekends, "2026-09-20T10:00").get(0).pkg.equals("weekend"),
                "weekend differs from weekday despite fewer samples");
        check(rank(weekends, "2026-09-21T10:00").get(0).pkg.equals("work"), "weekday predicts work");
        List<Predictor.Launch> midnight = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            LocalDate day = LocalDate.of(2026, 9, 19).minusDays(i);
            add(midnight, "video", day, 23, 50);
            add(midnight, "work", day, 12, 0);
        }
        check(rank(midnight, "2026-09-20T00:10").get(0).pkg.equals("video"), "circular midnight distance");
        List<Predictor.Launch> changed = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            add(changed, "work", LocalDate.of(2026, 9, 19).minusDays(40 + i), 8, 0);
            add(changed, "commute", LocalDate.of(2026, 9, 19).minusDays(i), 8, 0);
        }
        check(rank(changed, "2026-09-21T08:00").get(0).pkg.equals("commute"), "recent habit replaces older habit");
        check(Predictor.rank(events, NOW, at("2026-09-21T08:10"), ZONE, Set.of("video")).size() == 1,
                "hidden and removed apps excluded");
        check(rank(List.of(), "2026-09-21T08:10").isEmpty(), "no fabricated apps without data");
        check(rank(List.of(new Predictor.Launch("video", NOW + 1)), "2026-09-21T08:10").isEmpty(),
                "future events ignored");
        check(rank(List.of(new Predictor.Launch("video", NOW - 61 * Predictor.DAY)), "2026-09-21T08:10").isEmpty(),
                "expired events ignored");
        List<Predictor.Prediction> one = rank(List.of(new Predictor.Launch("video", NOW - 1000)), "2026-09-21T08:10");
        check(one.size() == 1 && Double.isFinite(one.get(0).score), "sparse cold start has finite score");
        check(Math.abs(rank(events, "2026-09-21T08:10").stream().mapToDouble(p -> p.score).sum() - 1) < 0.00001,
                "ranking weights normalized");
        // Changing the timezone reinterprets historical wall-clock habits consistently.
        check(Predictor.rank(events, NOW, at("2026-09-21T08:10"), ZoneId.of("UTC"), Set.of("video", "commute"))
                .get(0).pkg.equals("commute"), "timezone consistency");
        Set<String> installed = Set.of("work", "video", "new-app");
        check(!PrivacyPolicy.eligible(installed, Set.of("video")).contains("video"), "private app excluded");
        check(PrivacyPolicy.eligible(installed, Set.of()).equals(installed), "all ordinary apps eligible by default");
        check(PrivacyPolicy.eligible(installed, installed).isEmpty(), "all apps may be private");
        check(PrivacyPolicy.eligible(installed, Set.of("uninstalled")).equals(installed),
                "stale privacy entries harmless");
        System.out.println("PASS: " + checks + " prediction scenarios");
    }
}
