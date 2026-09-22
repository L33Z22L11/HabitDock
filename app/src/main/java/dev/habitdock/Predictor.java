package dev.habitdock;

import java.time.*;
import java.util.*;

/**
 * Deterministic, offline ranking. Scores are relative weights, not
 * probabilities.
 */
public final class Predictor {
    public static final long DAY = 86_400_000L;
    public static final class Launch {
        public final String pkg;
        public final long time;
        public Launch(String pkg, long time) {
            this.pkg = pkg;
            this.time = time;
        }
    }
    public static final class Prediction {
        public final String pkg;
        public final double score;
        public final String reason;
        public Prediction(String pkg, double score, String reason) {
            this.pkg = pkg;
            this.score = score;
            this.reason = reason;
        }
    }
    private static final class Signal {
        double frequency, temporal, recent;
        int count;
    }
    private static boolean weekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static List<Prediction> rank(List<Launch> events, long observedNow, long targetTime, ZoneId zone,
            Set<String> eligible) {
        ZonedDateTime target = Instant.ofEpochMilli(targetTime).atZone(zone);
        double targetMinute = target.getHour() * 60 + target.getMinute();
        Map<String, Signal> signals = new HashMap<>();
        double frequencySum = 0, temporalSum = 0, recentSum = 0;
        for (Launch event : events) {
            long age = observedNow - event.time;
            if (age < 0 || age > 60 * DAY || !eligible.contains(event.pkg))
                continue;
            ZonedDateTime local = Instant.ofEpochMilli(event.time).atZone(zone);
            double distance = Math.abs(local.getHour() * 60 + local.getMinute() - targetMinute);
            distance = Math.min(distance, 1440 - distance); // midnight wraps, not a 24-hour gap
            double decay = Math.pow(0.5, age / (14.0 * DAY));
            double dayMatch = local.getDayOfWeek() == target.getDayOfWeek()
                    ? 1.5
                    : weekend(local.getDayOfWeek()) == weekend(target.getDayOfWeek()) ? 1.0 : 0.12;
            double temporal = decay * Math.exp(-0.5 * Math.pow(distance / 75.0, 2)) * dayMatch;
            double recent = Math.exp(-age / (2.0 * DAY));
            Signal s = signals.computeIfAbsent(event.pkg, k -> new Signal());
            s.frequency += decay;
            s.temporal += temporal;
            s.recent += recent;
            s.count++;
            frequencySum += decay;
            temporalSum += temporal;
            recentSum += recent;
        }
        List<Prediction> result = new ArrayList<>();
        // Sparse history falls back smoothly to frequency instead of inventing
        // confidence.
        double timeWeight = 0.65 * Math.min(1.0, temporalSum / 4.0);
        double frequencyWeight = 0.85 - timeWeight;
        for (Map.Entry<String, Signal> e : signals.entrySet()) {
            Signal s = e.getValue();
            double score = timeWeight * s.temporal / Math.max(temporalSum, 1e-9)
                    + frequencyWeight * s.frequency / Math.max(frequencySum, 1e-9)
                    + 0.15 * s.recent / Math.max(recentSum, 1e-9);
            String reason = s.count < 3
                    ? "刚开始了解你的习惯"
                    : s.temporal / s.frequency > 0.45
                            ? (weekend(target.getDayOfWeek()) ? "周末这个时间常用" : "这个时间常用")
                            : "最近经常打开";
            result.add(new Prediction(e.getKey(), score, reason));
        }
        result.sort(Comparator.comparingDouble((Prediction p) -> p.score).reversed().thenComparing(p -> p.pkg));
        return result;
    }
}
