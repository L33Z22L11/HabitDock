package dev.habitdock;

import android.content.Context;
import java.util.*;

/** Limits learned suggestions; user-pinned apps always keep their place. */
final class RecommendationLimit {
    static final int DEFAULT = 20, MAX = 100;

    static int load(Context context) {
        return Math.max(1, Math.min(MAX, Repository.prefs(context).getInt("recommendation_limit", DEFAULT)));
    }

    static void save(Context context, int count) {
        Repository.prefs(context).edit().putInt("recommendation_limit", Math.max(1, Math.min(MAX, count))).apply();
        WidgetAppearance.request(context);
    }

    static List<Predictor.Prediction> apply(List<Predictor.Prediction> ranked, Set<String> pinned, int limit) {
        List<Predictor.Prediction> result = new ArrayList<>();
        int suggestions = 0;
        for (Predictor.Prediction prediction : ranked) {
            if (pinned.contains(prediction.pkg))
                result.add(prediction);
            else if (suggestions < limit) {
                result.add(prediction);
                suggestions++;
            }
        }
        return result;
    }
}
